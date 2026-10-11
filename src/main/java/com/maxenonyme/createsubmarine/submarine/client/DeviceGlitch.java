package com.maxenonyme.createsubmarine.submarine.client;

import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class DeviceGlitch {
    private static final double REACH = 32.0;
    private static final float MAX_SPIN = 60f;

    private static float spin;
    private static float previousSpin;
    private static float spinSpeed;

    private DeviceGlitch() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused())
            return;
        float panic = PressureVision.panic();
        spinSpeed += (panic * panic * MAX_SPIN - spinSpeed) * 0.025f;
        previousSpin = spin;
        spin += spinSpeed;
        if (spin > 3600f) {
            spin -= 3600f;
            previousSpin -= 3600f;
        }
    }

    public static float at(Level level, BlockPos pos) {
        if (level == null || !level.isClientSide)
            return 0f;
        float panic = PressureVision.panic();
        if (panic <= 0f)
            return 0f;
        Player player = Minecraft.getInstance().player;
        if (player == null)
            return 0f;
        Vec3 world = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(pos));
        return world.distanceToSqr(player.position()) <= REACH * REACH ? panic : 0f;
    }

    public static float spinDegrees(float partialTick) {
        return Mth.lerp(partialTick, previousSpin, spin);
    }

    public static float noise(BlockPos pos, float time) {
        float seed = (pos.hashCode() & 0xFFFF) * 0.0137f;
        return (Mth.sin(time * 0.9f + seed) + 0.6f * Mth.sin(time * 2.3f + seed * 1.7f)
                + 0.4f * Mth.sin(time * 5.1f + seed * 2.9f)) / 2.0f;
    }

    public static float time(Level level, float partialTick) {
        return level.getGameTime() + partialTick;
    }
}
