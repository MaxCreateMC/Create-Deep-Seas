package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.helm.HelmSeatEntity;
import com.maxenonyme.highseas.oar.OarRowAnimator;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import com.maxenonyme.highseas.block.entity.BuoySeatEntity;

public final class PlayerPoseDispatcher {
    private PlayerPoseDispatcher() {
    }

    public static void before(AbstractClientPlayer player, HumanoidModel<?> model) {
        model.head.resetPose();
        model.hat.resetPose();
        model.body.resetPose();
        model.rightArm.resetPose();
        model.leftArm.resetPose();
        model.rightLeg.resetPose();
        model.leftLeg.resetPose();
    }

    public static void after(AbstractClientPlayer player, HumanoidModel<?> model, float ageInTicks) {
        if (isAtHelm(player)) {
            HelmPoseAnimator.apply(player, model);
            return;
        }
        if (player.getVehicle() instanceof BuoySeatEntity) {
            BuoyPoseAnimator.apply(player, model);
            OarRowAnimator.afterSetupAnim(player, model, ageInTicks);
            return;
        }
        if (SeaglideClientHandler.isHolding(player)) {
            SeaglidePoseAnimator.apply(player, model);
            return;
        }
        OarRowAnimator.afterSetupAnim(player, model, ageInTicks);
    }

    public static boolean isAtHelm(Player player) {
        return player.getVehicle() instanceof HelmSeatEntity;
    }
}
