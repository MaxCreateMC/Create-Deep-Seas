package com.maxenonyme.highseas.oar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class OarRowAnimator {
    private OarRowAnimator() {
    }

    private static final float STROKE_PERIOD = 24.0f;

    private static final Set<UUID> rowers = new HashSet<>();

    public static void updateRowers(Collection<UUID> uuids) {
        rowers.clear();
        rowers.addAll(uuids);
    }

    public static boolean isRowing(UUID id) {
        return rowers.contains(id);
    }

    public static void afterSetupAnim(Player player, HumanoidModel<?> model, float ageInTicks) {
        if (Minecraft.getInstance().isPaused())
            return;
        if (!rowers.contains(player.getUUID()))
            return;

        float xr = (float) Math.toRadians(-45.0) + (float) Math.toRadians(70.0) * swing();

        model.rightArm.xRot = xr;
        model.leftArm.xRot = xr - (float) Math.toRadians(8.0);
        model.rightArm.yRot = (float) Math.toRadians(-22.0);
        model.leftArm.yRot = (float) Math.toRadians(52.0);
        model.rightArm.zRot = 0.0f;
        model.leftArm.zRot = (float) Math.toRadians(12.0);
    }

    public static void applyFirstPerson(PoseStack pose) {
        float swing = swing();
        pose.translate(0.0f, swing * 0.09f, swing * 0.16f);
        pose.mulPose(Axis.XP.rotation(swing * 0.45f));
    }

    private static float swing() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return 0.0f;
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        float t = (float) (mc.level.getGameTime() % (long) STROKE_PERIOD) + partial;
        float phase = t * (float) (Math.PI * 2.0 / STROKE_PERIOD);
        return -Mth.cos(phase);
    }
}
