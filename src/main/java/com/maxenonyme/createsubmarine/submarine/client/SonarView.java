package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.submarine.block.entity.CommandSubBlockEntity;
import com.maxenonyme.createsubmarine.submarine.block.entity.SonarBlockEntity;
import com.maxenonyme.createsubmarine.submarine.block.entity.renderer.CommandSubRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public final class SonarView {
    private SonarView() {
    }

    public static final int PAD = 272;

    static final int RANGE = 24;
    static final int DOWN = 16;
    static final int UP = 8;
    static final int SHELLS = (int) Math.ceil(Math.sqrt(RANGE * RANGE + DOWN * DOWN)) + 1;
    static final int FLOOR_REACH = 96;
    static final long SWEEP_NANOS = 1_200_000_000L;

    private static final long PING_NANOS = 2_000_000_000L;
    private static final int REBAKE_PINGS = 5;
    private static final long PREVIEW_NANOS = 50_000_000L;
    private static final long DETAIL_NANOS = 30_000_000L;
    private static final long FORGET_NANOS = 3_000_000_000L;
    private static final int PIXELS_PER_UNIT = 4;
    private static final float PITCH = 32f;
    private static final float PREVIEW_ZOOM = 1.8f;
    private static final float MIN_ZOOM = 1f;
    private static final float MAX_ZOOM = 6f;
    private static final double MAX_PAN = RANGE;

    private static final Map<CommandSubBlockEntity, SonarScan> PREVIEWS = new HashMap<>();
    private static final Map<CommandSubBlockEntity, SonarScan> DETAILS = new HashMap<>();

    public static SonarScan preview(CommandSubBlockEntity be) {
        SonarScan scan = PREVIEWS.computeIfAbsent(be, k -> new SonarScan());
        scan.lastSeen = System.nanoTime();
        return scan;
    }

    public static RenderType previewTexture(SonarScan scan) {
        return scan.result == null ? null : CommandSubRenderTypes.diagram(scan.textureId());
    }

    public static SonarScan detail(CommandSubBlockEntity be, int width, int height) {
        SonarScan scan = DETAILS.computeIfAbsent(be, k -> new SonarScan());
        scan.lastSeen = System.nanoTime();
        scan.wantWidth = width;
        scan.wantHeight = height;
        return scan;
    }

    public static void drag(CommandSubBlockEntity be, double dx, double dy) {
        SonarScan scan = DETAILS.get(be);
        if (scan == null || scan.unitsPerPixel <= 0)
            return;
        double right = -dx * scan.unitsPerPixel;
        double ahead = dy * scan.unitsPerPixel / Math.sin(Math.toRadians(PITCH));
        double cos = Math.cos(scan.yaw);
        double sin = Math.sin(scan.yaw);
        be.sonarPanX += right * cos - ahead * sin;
        be.sonarPanZ += -right * sin - ahead * cos;

        double length = Math.sqrt(be.sonarPanX * be.sonarPanX + be.sonarPanZ * be.sonarPanZ);
        if (length > MAX_PAN) {
            be.sonarPanX *= MAX_PAN / length;
            be.sonarPanZ *= MAX_PAN / length;
        }
    }

    public static void orbit(CommandSubBlockEntity be, double dx) {
        be.sonarOrbit = Mth.wrapDegrees(be.sonarOrbit + (float) dx * 0.5f);
    }

    public static void zoom(CommandSubBlockEntity be, double scroll) {
        be.sonarZoom = Mth.clamp(be.sonarZoom * (float) Math.pow(1.15, scroll), MIN_ZOOM, MAX_ZOOM);
    }

    public static Component floorLabel(float floor) {
        if (Float.isNaN(floor))
            return Component.translatable("create_submarine.command_sub.seabed_none", FLOOR_REACH);
        return Component.translatable("create_submarine.command_sub.seabed", String.format(Locale.ROOT, "%.1f", floor));
    }

    static AABB zone(Vector3d sonar) {
        return new AABB(sonar.x - RANGE, sonar.y - DOWN, sonar.z - RANGE, sonar.x + RANGE, sonar.y + UP, sonar.z + RANGE);
    }

    public static void onFrameEnd(RenderFrameEvent.Post event) {
        if (PREVIEWS.isEmpty() && DETAILS.isEmpty())
            return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        boolean drew = update(mc, PREVIEWS, false, now, partialTicks);
        drew |= update(mc, DETAILS, true, now, partialTicks);
        if (drew)
            mc.getMainRenderTarget().bindWrite(true);
    }

    private static boolean update(Minecraft mc, Map<CommandSubBlockEntity, SonarScan> scans, boolean detail, long now,
            float partialTicks) {
        boolean drew = false;
        Iterator<Map.Entry<CommandSubBlockEntity, SonarScan>> it = scans.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<CommandSubBlockEntity, SonarScan> entry = it.next();
            CommandSubBlockEntity be = entry.getKey();
            SonarScan scan = entry.getValue();
            if (forgotten(mc, be, scan, now)) {
                scan.free();
                it.remove();
                continue;
            }
            if (now - scan.lastDraw < (detail ? DETAIL_NANOS : PREVIEW_NANOS))
                continue;

            SonarBlockEntity sonar = SonarBlockEntity.onSameSub(be);
            SubLevel sub = Sable.HELPER.getContaining(be);
            if (sonar == null || !(sub instanceof ClientSubLevel csl) || csl.isRemoved() || csl.getPlot() == null)
                continue;

            scan.lastDraw = now;
            if (detail) {
                scan.resize(scan.wantWidth, scan.wantHeight);
                draw(mc, csl, sonar.getBlockPos(), scan, partialTicks, now,
                        be.sonarZoom, be.sonarPanX, be.sonarPanZ, be.sonarOrbit);
            } else {
                float[] area = CommandSubRenderer.SONAR;
                scan.resize(Math.round((area[2] - area[0]) * PIXELS_PER_UNIT),
                        Math.round((area[3] - area[1]) * PIXELS_PER_UNIT));
                draw(mc, csl, sonar.getBlockPos(), scan, partialTicks, now, PREVIEW_ZOOM, 0, 0, 0);
            }
            drew = true;
        }
        return drew;
    }

    private static boolean forgotten(Minecraft mc, CommandSubBlockEntity be, SonarScan scan, long now) {
        return mc.level == null || be.isRemoved() || be.getLevel() != mc.level || now - scan.lastSeen > FORGET_NANOS;
    }

    private static void draw(Minecraft mc, ClientSubLevel sub, BlockPos sonarPos, SonarScan scan, float partialTicks,
            long now, float zoom, double panX, double panZ, float orbit) {
        Pose3dc pose = sub.renderPose(partialTicks);
        scan.floor = SonarReadings.floorClearance(sub, pose);

        Vector3d sonar = pose.transformPosition(
                new Vector3d(sonarPos.getX() + 0.5, sonarPos.getY() + 0.5, sonarPos.getZ() + 0.5));
        Vector3d center = new Vector3d(sonar.x + panX, sonar.y, sonar.z + panZ);

        if (now - scan.lastPing >= PING_NANOS || scan.origin == null) {
            BlockPos sonarBlock = BlockPos.containing(sonar.x, sonar.y, sonar.z);
            scan.lastPing = now;
            if (!sonarBlock.equals(scan.origin) || ++scan.idlePings >= REBAKE_PINGS) {
                scan.idlePings = 0;
                scan.replace(SonarTerrain.ping(mc, sub.getLevel(), sonarBlock), sonarBlock);
            }
        }
        float front = scan.front(now);

        Camera camera = camera(sub, pose, scan, zoom, orbit);
        Matrix4f toScreen = new Matrix4f(camera.projection()).rotate(camera.view());
        SonarReadings.locateContacts(mc, sub, sonar, center, toScreen, scan, front);

        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        try {
            scan.fbo.bind(true);
            scan.fbo.clear();
            SonarTerrain.draw(mc, scan, camera.wide(), camera.view(), center, partialTicks, front);
            SonarOverlay.drawRopes(sub.getLevel(), sonar, center, camera.view(), camera.wide(), scan, partialTicks);
            SonarOverlay.outline(scan);
            SonarOverlay.drawWave(scan, camera.wide(), camera.view(), sonar, center, front);
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            AdvancedFbo.unbind();
        }
    }

    private record Camera(Quaternionf view, Matrix4f projection, Matrix4f wide) {
    }

    private static Camera camera(ClientSubLevel sub, Pose3dc pose, SonarScan scan, float zoom, float orbit) {
        BoundingBox3ic box = sub.getPlot().getBoundingBox();
        boolean alongX = box.maxX() - box.minX() >= box.maxZ() - box.minZ();
        Vector3d forward = pose.orientation().transform(alongX ? new Vector3d(1, 0, 0) : new Vector3d(0, 0, 1));
        scan.yaw = (float) Math.atan2(forward.x, forward.z) + (float) Math.PI + (float) Math.toRadians(45 + orbit);
        Quaternionf view = new Quaternionf()
                .rotateY(scan.yaw)
                .rotateX((float) -Math.toRadians(PITCH))
                .conjugate();

        float halfWidth = 0;
        float halfHeight = 0;
        float depth = 0;
        Vector3f corner = new Vector3f();
        for (int i = 0; i < 8; i++) {
            corner.set(
                    (i & 1) == 0 ? -RANGE : RANGE,
                    (i & 2) == 0 ? -DOWN : UP,
                    (i & 4) == 0 ? -RANGE : RANGE);
            view.transform(corner);
            halfWidth = Math.max(halfWidth, Math.abs(corner.x));
            halfHeight = Math.max(halfHeight, Math.abs(corner.y));
            depth = Math.max(depth, Math.abs(corner.z));
        }

        float aspect = (float) scan.width / scan.height;
        if (halfWidth / halfHeight < aspect)
            halfWidth = halfHeight * aspect;
        else
            halfHeight = halfWidth / aspect;
        halfWidth /= zoom;
        halfHeight /= zoom;
        scan.unitsPerPixel = halfWidth * 2 / scan.width;

        float near = -depth - 2;
        float far = depth + 2;
        Matrix4f projection = new Matrix4f().ortho(-halfWidth, halfWidth, -halfHeight, halfHeight, near, far);
        float padded = halfWidth + SonarView.PAD * scan.unitsPerPixel;
        Matrix4f wide = new Matrix4f().ortho(-padded, halfWidth, -halfHeight, halfHeight, near, far);
        return new Camera(view, projection, wide);
    }
}
