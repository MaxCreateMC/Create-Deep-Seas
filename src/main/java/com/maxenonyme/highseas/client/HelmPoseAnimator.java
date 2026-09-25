package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.helm.HelmClient;
import com.maxenonyme.highseas.helm.HelmSeatEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

public final class HelmPoseAnimator {
    private HelmPoseAnimator() {
    }

    private static final float ARM_BACK = 1.75f;
    private static final float ARM_SWING = 0.41f;
    private static final float ARM_SPLAY = 0.10f;
    private static final float HEAD_LIMIT = (float) Math.toRadians(100.0);
    private static final float SHOULDER = 5.0f;

    public static void apply(AbstractClientPlayer player, HumanoidModel<?> model) {
        if (!(player.getVehicle() instanceof HelmSeatEntity seat))
            return;
        float lever = HelmClient.steerFor(seat.getEnginePos(), player.level().getGameTime());
        float twist = Mth.wrapDegrees(seat.getFacing().toYRot() - player.yBodyRot) * Mth.DEG_TO_RAD;
        float cos = Mth.cos(twist);
        float sin = Mth.sin(twist);

        model.body.xRot = 0.0f;
        model.body.yRot = twist;
        model.body.zRot = 0.0f;

        model.rightLeg.xRot = 0.0f;
        model.rightLeg.yRot = twist;
        model.rightLeg.zRot = 0.0f;
        model.leftLeg.xRot = 0.0f;
        model.leftLeg.yRot = twist;
        model.leftLeg.zRot = 0.0f;
        model.head.yRot = Mth.clamp(model.head.yRot - twist, -HEAD_LIMIT, HEAD_LIMIT);

        model.rightArm.x = -SHOULDER * cos;
        model.rightArm.z = SHOULDER * sin;
        model.rightArm.xRot = ARM_BACK;
        model.rightArm.yRot = ARM_SWING * lever + twist;
        model.rightArm.zRot = ARM_SPLAY;

        model.leftArm.x = SHOULDER * cos;
        model.leftArm.z = -SHOULDER * sin;
        model.leftArm.xRot = 0.0f;
        model.leftArm.yRot = twist;
        model.leftArm.zRot = -0.05f;

        model.hat.copyFrom(model.head);
    }
}
