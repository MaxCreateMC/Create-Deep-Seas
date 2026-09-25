package com.maxenonyme.highseas.client;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

public final class BuoyPoseAnimator {
    private BuoyPoseAnimator() {
    }

    private static final float ARM_RAISE = (float) Math.toRadians(140.0);
    private static final float ARM_SPREAD = (float) Math.toRadians(11.0);
    private static final float LEG_RAISE = -1.4137167f;
    private static final float LEG_SPLAY = (float) (Math.PI / 10);
    private static final float LEG_ROLL = 0.07853982f;
    private static final float TORSO = 0.0f;
    private static final float IMPACT_ARMS = (float) Math.toRadians(26.0);
    private static final float IMPACT_LEGS = (float) Math.toRadians(-24.0);
    private static final float ARM_SWAY = (float) Math.toRadians(22.0);
    private static final float FLAIL_IDLE = (float) Math.toRadians(6.0);
    private static final float FLAIL_RUSH = (float) Math.toRadians(15.0);
    private static final float FLAIL_KICK = (float) Math.toRadians(26.0);
    private static final float FLAIL_FREQ = 0.35f;

    public static void apply(AbstractClientPlayer player, HumanoidModel<?> model) {
        model.body.xRot = TORSO;
        model.body.yRot = 0.0f;
        model.body.zRot = 0.0f;

        float jolt = BuoyRideEffects.jolt();
        float lean = BuoyRideEffects.sway() * ARM_SWAY;

        float t = AnimationTickHolder.getRenderTime();
        float amp = FLAIL_IDLE + BuoyRideEffects.wind() * FLAIL_RUSH + jolt * FLAIL_KICK;
        float rightSwing = Mth.sin(t * FLAIL_FREQ) * amp;
        float leftSwing = Mth.sin(t * FLAIL_FREQ + 2.1f) * amp;
        float rightRoll = Mth.cos(t * FLAIL_FREQ * 0.7f) * amp * 0.6f;
        float leftRoll = Mth.cos(t * FLAIL_FREQ * 0.7f + 1.3f) * amp * 0.6f;

        model.rightArm.xRot = ARM_RAISE + jolt * IMPACT_ARMS + rightSwing;
        model.rightArm.yRot = 0.0f;
        model.rightArm.zRot = -ARM_SPREAD + lean + rightRoll;
        model.leftArm.xRot = ARM_RAISE + jolt * IMPACT_ARMS + leftSwing;
        model.leftArm.yRot = 0.0f;
        model.leftArm.zRot = ARM_SPREAD + lean - leftRoll;

        model.rightLeg.xRot = LEG_RAISE + jolt * IMPACT_LEGS;
        model.rightLeg.yRot = LEG_SPLAY;
        model.rightLeg.zRot = LEG_ROLL;
        model.leftLeg.xRot = LEG_RAISE + jolt * IMPACT_LEGS;
        model.leftLeg.yRot = -LEG_SPLAY;
        model.leftLeg.zRot = -LEG_ROLL;

        model.hat.copyFrom(model.head);

        boolean showHead = !BuoyRideEffects.hidingHead();
        model.head.visible = showHead;
        model.hat.visible = showHead;
    }
}
