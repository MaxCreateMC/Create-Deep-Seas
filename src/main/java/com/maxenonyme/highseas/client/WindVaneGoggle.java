package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.sail.BoatClassifier;
import com.maxenonyme.highseas.wind.WindManager;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.List;

public final class WindVaneGoggle {
    private WindVaneGoggle() {
    }

    public static double alignment(Level level, BlockPos pos) {
        if (level == null) {
            return Double.NaN;
        }
        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, pos);
        if (ship == null) {
            return Double.NaN;
        }
        Vector3d forward = SailShaderState.forwardFor(ship.getUniqueId());
        if (forward == null) {
            forward = crewForward(level, ship);
        }
        if (forward == null) {
            return Double.NaN;
        }

        Vector3d center = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        ship.logicalPose().transformPosition(center);

        Level windLevel = Minecraft.getInstance().level;
        if (windLevel == null) {
            return Double.NaN;
        }
        Vec3 wind = WindManager.getWind(windLevel, center.x, center.y, center.z).vector();
        double len = Math.sqrt(wind.x * wind.x + wind.z * wind.z);
        if (len < 1.0e-4) {
            return Double.NaN;
        }
        double dot = (wind.x / len) * forward.x + (wind.z / len) * forward.z;
        return Mth.clamp(dot, -1.0, 1.0);
    }

    private static Vector3d crewForward(Level level, SubLevelAccess ship) {
        if (!(ship instanceof SubLevel self)) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        List<? extends SubLevel> all = container.getAllSubLevels();
        long now = level.getGameTime();
        SubLevel root = BoatClassifier.rootOf(level, all, self, now);
        if (root == null) {
            return null;
        }
        for (SubLevel other : all) {
            if (other == self || BoatClassifier.rootOf(level, all, other, now) != root) {
                continue;
            }
            Vector3d f = SailShaderState.forwardFor(other.getUniqueId());
            if (f != null) {
                return f;
            }
        }
        return null;
    }
}
