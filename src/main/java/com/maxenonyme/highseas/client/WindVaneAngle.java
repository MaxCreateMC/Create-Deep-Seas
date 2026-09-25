package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.block.entity.WindVaneBlockEntity;
import com.maxenonyme.highseas.wind.WindManager;
import com.maxenonyme.highseas.wind.WindSample;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

public final class WindVaneAngle {
    private WindVaneAngle() {
    }

    private static final float RESPONSE = 14.0f;
    private static final float MAX_FRAME_SECONDS = 0.1f;
    private static final double CALM = 1.0e-4;

    public static float update(WindVaneBlockEntity be) {
        float target = targetAngle(be);
        long now = System.nanoTime();
        long last = be.renderLastNanos;
        be.renderLastNanos = now;

        if (Float.isNaN(target)) {
            return be.renderAngle;
        }
        if (!be.hasRenderAngle || last == 0L) {
            be.renderAngle = target;
            be.hasRenderAngle = true;
            return target;
        }

        float dt = Math.min((now - last) / 1.0e9f, MAX_FRAME_SECONDS);
        float alpha = 1.0f - (float) Math.exp(-RESPONSE * dt);
        float delta = (float) Mth.atan2(Mth.sin(target - be.renderAngle), Mth.cos(target - be.renderAngle));
        be.renderAngle += delta * alpha;
        return be.renderAngle;
    }

    private static float targetAngle(WindVaneBlockEntity be) {
        Level beLevel = be.getLevel();
        if (beLevel == null) {
            return Float.NaN;
        }
        BlockPos pos = be.getBlockPos();

        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(beLevel, pos);
        Vector3d center = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Quaterniondc orientation = null;
        Level windLevel = beLevel;

        if (sub != null) {
            Pose3dc pose = sub.logicalPose();
            pose.transformPosition(center);
            orientation = pose.orientation();
            windLevel = Minecraft.getInstance().level;
        }
        if (windLevel == null) {
            return Float.NaN;
        }

        WindSample wind = WindManager.getWind(windLevel, center.x, center.y, center.z);
        Vec3 w = wind.vector();
        if (w.lengthSqr() < CALM) {
            return Float.NaN;
        }

        Vector3d local = new Vector3d(w.x, 0.0, w.z);
        if (orientation != null) {
            orientation.conjugate(new Quaterniond()).transform(local);
        }
        if (local.x * local.x + local.z * local.z < CALM) {
            return Float.NaN;
        }
        return (float) Math.atan2(local.x, local.z);
    }
}
