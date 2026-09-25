package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.sail.BoatClassifier;
import com.maxenonyme.highseas.sail.FurlState;
import com.maxenonyme.highseas.sail.RudderDetector;
import com.maxenonyme.highseas.sail.SailDetector;
import com.maxenonyme.highseas.sail.SailGroup;
import com.maxenonyme.highseas.wind.WindManager;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.maxenonyme.highseas.config.HighSeasConfig;
import java.util.concurrent.ConcurrentHashMap;

public final class SailShaderState {
    private SailShaderState() {
    }

    private static final int MAX_SAILS = 8;
    private static final String[] SAIL_MIN = uniforms("sailMin");
    private static final String[] SAIL_MAX = uniforms("sailMax");
    private static final String[] SAIL_AXIS = uniforms("sailAxis");
    private static final String[] SUPPORT_DIR = uniforms("supportDir");
    private static final String[] BULGE = uniforms("bulge");
    private static final String[] FURL = uniforms("furl");

    private static String[] uniforms(String name) {
        String[] out = new String[MAX_SAILS];
        for (int i = 0; i < MAX_SAILS; i++)
            out[i] = name + "[" + i + "]";
        return out;
    }
    private static final int SCAN_INTERVAL = 20;
    private static final double WIND_REF = 1.0;
    private static final double IDLE_FILL = 0.5;
    private static final double DEPTH_PER_BLOCK = 0.26;
    private static final double DEPTH_MIN = 0.16;
    private static final double DEPTH_MAX = 2.6;

    private static final Map<UUID, SailData> CACHE = new HashMap<>();
    private static final Map<UUID, Vector3d> FORWARD = new ConcurrentHashMap<>();

    public static Vector3d forwardFor(UUID id) {
        Vector3d f = FORWARD.get(id);
        return f == null ? null : new Vector3d(f);
    }

    private static class SailBox {
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final float axisX, axisY, axisZ;
        final int supportSign, area;
        long startTick;
        double smoothedPower = -1.0;
        double bulgeSign = 0.0;
        long lastUpdate = 0;
        double furlAmount = 0.0;
        double furlTarget = 0.0;
        long furlLastMs = 0;

        SailBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, float axisX, float axisY, float axisZ, int supportSign, int area, long startTick) {
            this.minX = minX; this.minY = minY; this.minZ = minZ; this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
            this.axisX = axisX; this.axisY = axisY; this.axisZ = axisZ; this.supportSign = supportSign; this.area = area;
            this.startTick = startTick;
        }

        boolean matches(SailBox o) {
            return minX == o.minX && minY == o.minY && minZ == o.minZ &&
                   maxX == o.maxX && maxY == o.maxY && maxZ == o.maxZ &&
                   axisX == o.axisX && axisY == o.axisY && axisZ == o.axisZ &&
                   area == o.area;
        }
    }

    private record DecayingSailBox(SailBox box, long startTick) {}

    private static class SailData {
        List<SailBox> sails;
        Vec3 rudder;
        long lastScan;
        List<DecayingSailBox> decaying = new ArrayList<>();
        
        SailData(List<SailBox> sails, Vec3 rudder, long lastScan) {
            this.sails = sails;
            this.rudder = rudder;
            this.lastScan = lastScan;
        }
    }

    public static void prepareForSublevel(ClientSubLevel sub, ShaderInstance shader, Pose3dc renderPose, double camX, double camY, double camZ) {
        Quaterniondc rot = renderPose.orientation();
        Vector3d offset = new Vector3d(renderPose.position()).sub(camX, camY, camZ);
        rot.transformInverse(offset);
        shader.safeGetUniform("offset").set((float) offset.x, (float) offset.y, (float) offset.z);
        shader.safeGetUniform("time").set(time());
        shader.safeGetUniform("tessLevel").set(
                (float) HighSeasConfig.sailSubdivisions);

        Vector3dc rp = renderPose.rotationPoint();
        ClientLevel level = Minecraft.getInstance().level;

        if (!isBoat(sub, level)) {
            shader.safeGetUniform("sailCount").set(0);
            return;
        }

        SailData data = lookup(sub);
        List<SailBox> activeSails = data.sails;
        List<DecayingSailBox> decayingSails = data.decaying;
        int totalSails = Math.min(MAX_SAILS, activeSails.size() + decayingSails.size());
        shader.safeGetUniform("sailCount").set(totalSails);

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        ClientSubLevel root = null;
        if (container != null) {
            root = (ClientSubLevel) BoatClassifier.rootOf(level, container.getAllSubLevels(), sub, level.getGameTime());
        }

        Vector3d keel;
        if (root != null && root != sub) {
            SailData rootData = lookup(root);
            BoundingBox3ic rbb = root.getPlot() != null ? root.getPlot().getBoundingBox() : null;
            boolean square = rbb != null && (rbb.maxX() - rbb.minX()) == (rbb.maxZ() - rbb.minZ());
            if (rootData.rudder == null && square) {
                keel = sailDir(rot, activeSails);
            } else {
                keel = forward(root, root.logicalPose().orientation(), rootData.sails, rootData.rudder);
            }
        } else {
            keel = forward(sub, rot, activeSails, data.rudder);
        }
        if (keel == null) {
            keel = sailDir(rot, activeSails);
        }
        if (keel != null) {
            FORWARD.put(sub.getUniqueId(), new Vector3d(keel));
        }

        UUID subId = sub.getUniqueId();
        int index = 0;
        for (SailBox b : activeSails) {
            if (index >= MAX_SAILS)
                return;
            long age = level.getGameTime() - b.startTick;
            double factor = Math.min(1.0, age / 60.0);
            b.furlTarget = FurlState.isFurled(subId, b.minX, b.minY, b.minZ) ? 1.0 : 0.0;
            setUniformsForBox(shader, b, index, rp, renderPose, rot, level, keel, factor);
            index++;
        }
        for (DecayingSailBox dec : decayingSails) {
            if (index >= MAX_SAILS)
                return;
            long age = level.getGameTime() - dec.startTick;
            double factor = Math.max(0.0, (60.0 - age) / 60.0);
            dec.box.furlTarget = 0.0;
            setUniformsForBox(shader, dec.box, index, rp, renderPose, rot, level, keel, factor);
            index++;
        }
    }

    private static void setUniformsForBox(ShaderInstance shader, SailBox b, int i, Vector3dc rp, Pose3dc renderPose, Quaterniondc rot, ClientLevel level, Vector3d keel, double decayFactor) {
        shader.safeGetUniform(SAIL_MIN[i]).set(
                (float) (b.minX - rp.x()),
                (float) (b.minY - rp.y()),
                (float) (b.minZ - rp.z()));
        shader.safeGetUniform(SAIL_MAX[i]).set(
                (float) (b.maxX - rp.x()),
                (float) (b.maxY - rp.y()),
                (float) (b.maxZ - rp.z()));
        shader.safeGetUniform(SAIL_AXIS[i]).set(b.axisX, b.axisY, b.axisZ);
        shader.safeGetUniform(SUPPORT_DIR[i]).set(
                b.axisX * b.supportSign, b.axisY * b.supportSign, b.axisZ * b.supportSign);

        Vec3 bulge = windBulge(b, renderPose, rot, level, keel, decayFactor);
        shader.safeGetUniform(BULGE[i]).set((float) bulge.x, (float) bulge.y, (float) bulge.z);

        long now = System.currentTimeMillis();
        if (b.furlLastMs == 0) {
            b.furlAmount = b.furlTarget;
        } else {
            double dt = Math.min((now - b.furlLastMs) / 1000.0, 0.1);
            double step = 1.6 * dt;
            double diff = b.furlTarget - b.furlAmount;
            if (Math.abs(diff) <= step) {
                b.furlAmount = b.furlTarget;
            } else {
                b.furlAmount += Math.signum(diff) * step;
            }
        }
        b.furlLastMs = now;
        shader.safeGetUniform(FURL[i]).set((float) b.furlAmount);
    }

    private static Vector3d sailDir(Quaterniondc rot, List<SailBox> sails) {
        Vector3d dir = new Vector3d();
        for (SailBox b : sails) {
            Vector3d n = rot.transform(new Vector3d(b.axisX, b.axisY, b.axisZ));
            n.y = 0;
            if (n.lengthSquared() < 1.0e-9) {
                continue;
            }
            n.normalize();
            double side = b.supportSign != 0 ? -b.supportSign : 1.0;
            dir.add(n.mul(b.area * side));
        }
        if (dir.lengthSquared() < 1.0e-9) {
            return null;
        }
        dir.y = 0;
        dir.normalize();
        return dir;
    }

    private static Vector3d forward(ClientSubLevel sub, Quaterniondc rot, List<SailBox> sails, Vec3 rudder) {
        return sailDir(rot, sails);
    }

    private static Vec3 windBulge(SailBox b, Pose3dc renderPose, Quaterniondc rot, ClientLevel level, Vector3d keel, double decayFactor) {
        Vector3d worldNormal = rot.transform(new Vector3d(b.axisX, b.axisY, b.axisZ));

        double fill = IDLE_FILL;
        if (level != null) {
            Vector3d worldCenter = new Vector3d(
                    (b.minX + b.maxX) * 0.5, (b.minY + b.maxY) * 0.5, (b.minZ + b.maxZ) * 0.5);
            renderPose.transformPosition(worldCenter);
            Vec3 w = WindManager.getWind(level, worldCenter.x, worldCenter.y, worldCenter.z).vector();
            double windDotN = w.x * worldNormal.x + w.y * worldNormal.y + w.z * worldNormal.z;
            if (Math.abs(windDotN) > 0.15 * WIND_REF)
                b.bulgeSign = Math.signum(windDotN);
            double targetPower = Mth.clamp(Math.abs(windDotN) / WIND_REF, 0.0, 1.0);
            
            long now = System.currentTimeMillis();
            if (b.smoothedPower < 0) {
                b.smoothedPower = targetPower;
                b.lastUpdate = now;
            } else {
            double dt = (now - b.lastUpdate) / 1000.0;
            b.lastUpdate = now;
            if (dt > 0 && dt < 1.0) {
                double maxStep = 0.5 * dt;
                double diff = targetPower - b.smoothedPower;
                if (diff > maxStep) b.smoothedPower += maxStep;
                else if (diff < -maxStep) b.smoothedPower -= maxStep;
                else b.smoothedPower = targetPower;
            }
        }
        fill = IDLE_FILL + (1.0 - IDLE_FILL) * b.smoothedPower;
    }

        fill *= decayFactor;

        Vector3d bulgeDir;
        if (b.supportSign != 0) {
            bulgeDir = new Vector3d(b.axisX, b.axisY, b.axisZ).mul(-b.supportSign);
        } else if (b.bulgeSign != 0.0) {
            bulgeDir = new Vector3d(b.axisX, b.axisY, b.axisZ).mul(b.bulgeSign);
        } else {
            double s = (keel != null && worldNormal.x * keel.x + worldNormal.z * keel.z < 0) ? -1.0 : 1.0;
            bulgeDir = new Vector3d(b.axisX, b.axisY, b.axisZ).mul(s);
        }

        double depth = fill * maxDepth(b);
        return new Vec3(bulgeDir.x * depth, bulgeDir.y * depth, bulgeDir.z * depth);
    }

    private static double maxDepth(SailBox b) {
        double dx = b.maxX - b.minX;
        double dy = b.maxY - b.minY;
        double dz = b.maxZ - b.minZ;
        double planeA, planeB;
        if (b.axisX > 0.5f) {
            planeA = dy;
            planeB = dz;
        } else {
            planeA = dx;
            planeB = dy;
        }
        return Mth.clamp(DEPTH_PER_BLOCK * Math.sqrt(planeA * planeB), DEPTH_MIN, DEPTH_MAX);
    }

    private static boolean isBoat(ClientSubLevel sub, ClientLevel level) {
        if (level == null) {
            return true;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return true;
        }
        return BoatClassifier.boats(level, container.getAllSubLevels(), level.getGameTime()).contains(sub.getUniqueId());
    }

    private static float time() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return 0.0f;
        }
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        return (level.getGameTime() % 100000L) / 20.0f + partial / 20.0f;
    }

    private static SailData lookup(ClientSubLevel sub) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return new SailData(List.of(), null, 0);
        long now = level.getGameTime();

        SailData data = CACHE.get(sub.getUniqueId());
        if (data == null || now < data.lastScan || now - data.lastScan >= SCAN_INTERVAL) {
            SailData newData = compute(sub, now);
            if (data != null) {
                for (SailBox oldBox : data.sails) {
                    boolean found = false;
                    for (SailBox newBox : newData.sails) {
                        if (newBox.matches(oldBox)) {
                            newBox.startTick = oldBox.startTick;
                            newBox.smoothedPower = oldBox.smoothedPower;
                            newBox.lastUpdate = oldBox.lastUpdate;
                            newBox.furlAmount = oldBox.furlAmount;
                            newBox.furlLastMs = oldBox.furlLastMs;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        newData.decaying.add(new DecayingSailBox(oldBox, now));
                    }
                }
                for (DecayingSailBox dec : data.decaying) {
                    if (now - dec.startTick < 60) {
                        newData.decaying.add(dec);
                    }
                }
            }
            CACHE.put(sub.getUniqueId(), newData);
            data = newData;
        }
        return data;
    }

    private static SailData compute(ClientSubLevel sub, long now) {
        if (sub.getPlot() == null || sub.getLevel() == null) {
            return new SailData(List.of(), null, now);
        }
        List<SailGroup> groups = new ArrayList<>(SailDetector.detect(sub.getLevel(), sub.getPlot().getBoundingBox()));
        groups.sort((a, b) -> Integer.compare(b.area(), a.area()));

        List<SailBox> boxes = new ArrayList<>();
        for (SailGroup g : groups) {
            if (g.axis() == Direction.Axis.Y) {
                continue;
            }
            if (boxes.size() >= MAX_SAILS) {
                break;
            }
            BlockPos mn = g.min();
            BlockPos mx = g.max();
            Vec3 axis = g.localNormal();
            boxes.add(new SailBox(
                    mn.getX(), mn.getY(), mn.getZ(),
                    mx.getX() + 1, mx.getY() + 1, mx.getZ() + 1,
                    (float) axis.x, (float) axis.y, (float) axis.z,
                    g.supportSign(), g.area(), now));
        }
        Vec3 rudder = RudderDetector.centroid(sub.getLevel(), sub.getPlot().getBoundingBox());
        return new SailData(boxes, rudder, now);
    }

    public static void clearAll() {
        CACHE.clear();
        FORWARD.clear();
    }
}
