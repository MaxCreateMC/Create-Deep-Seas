package com.maxenonyme.highseas.mixin;

import com.maxenonyme.highseas.client.PlayerPoseDispatcher;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public class HumanoidModelPoseMixin<T extends LivingEntity> {

    @Inject(method = "setupAnim*", at = @At("HEAD"))
    private void createhighseas$beforeSetupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player)
            PlayerPoseDispatcher.before(player, (HumanoidModel<?>) (Object) this);
    }

    @Inject(method = "setupAnim*", at = @At("RETURN"))
    private void createhighseas$afterSetupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player)
            PlayerPoseDispatcher.after(player, (HumanoidModel<?>) (Object) this, ageInTicks);
    }
}
