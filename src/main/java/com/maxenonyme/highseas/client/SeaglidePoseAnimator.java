package com.maxenonyme.highseas.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;

public final class SeaglidePoseAnimator {
    private SeaglidePoseAnimator() {
    }

    private static final float IDLE_ARMS = (float) Math.toRadians(-30.0);
    private static final float WALK_ARMS = (float) Math.toRadians(-66.0);
    private static final float SWIM_ARMS = (float) Math.toRadians(-80.0);
    private static final float HEAD_LIFT = (float) Math.toRadians(-60.0);

    private static final float LEG_LAUNCH = (float) Math.toRadians(-8.0);
    private static final float LEG_APEX = (float) Math.toRadians(-40.0);
    private static final float LEG_FOLD = (float) Math.toRadians(34.0);
    private static final float BODY_LAUNCH = (float) Math.toRadians(5.0);
    private static final float BODY_APEX = (float) Math.toRadians(18.0);
    private static final float BODY_FOLD = (float) Math.toRadians(-9.0);
    private static final float SPLAY = (float) Math.toRadians(6.0);
    private static final float SETTLED = 0.01f;

    public static boolean isSwimPose(AbstractClientPlayer player) {
        if (SeaglideClientHandler.isLeaping(player)) {
            return true;
        }
        return SeaglideClientHandler.isSpinning(player)
                && (player.isSwimming() || player.getPose() == Pose.SWIMMING);
    }

    public static float armPitch(AbstractClientPlayer player) {
        if (isSwimPose(player)) {
            return SWIM_ARMS;
        }
        return SeaglideClientHandler.isSpinning(player) ? WALK_ARMS : IDLE_ARMS;
    }

    private static float alongArc(float arc, float apex, float rising, float falling) {
        return arc >= 0.0f ? Mth.lerp(arc, apex, rising) : Mth.lerp(-arc, apex, falling);
    }

    public static void apply(AbstractClientPlayer player, HumanoidModel<?> model) {
        float pitch = armPitch(player);

        model.rightArm.xRot = pitch;
        model.rightArm.yRot = 0.0f;
        model.rightArm.zRot = 0.0f;
        model.leftArm.xRot = pitch;
        model.leftArm.yRot = 0.0f;
        model.leftArm.zRot = 0.0f;

        if (isSwimPose(player)) {
            model.head.xRot = HEAD_LIFT;
        }

        float blend = SeaglideClientHandler.arcBlend();
        if (blend > SETTLED) {
            float arc = SeaglideClientHandler.arcPhase();
            float legs = alongArc(arc, LEG_APEX, LEG_LAUNCH, LEG_FOLD);
            float torso = alongArc(arc, BODY_APEX, BODY_LAUNCH, BODY_FOLD);

            model.body.xRot = Mth.lerp(blend, model.body.xRot, torso);
            model.rightLeg.xRot = Mth.lerp(blend, model.rightLeg.xRot, legs);
            model.rightLeg.yRot = Mth.lerp(blend, model.rightLeg.yRot, 0.0f);
            model.rightLeg.zRot = Mth.lerp(blend, model.rightLeg.zRot, -SPLAY);
            model.leftLeg.xRot = Mth.lerp(blend, model.leftLeg.xRot, legs);
            model.leftLeg.yRot = Mth.lerp(blend, model.leftLeg.yRot, 0.0f);
            model.leftLeg.zRot = Mth.lerp(blend, model.leftLeg.zRot, SPLAY);
        }
        model.hat.copyFrom(model.head);
    }
}
