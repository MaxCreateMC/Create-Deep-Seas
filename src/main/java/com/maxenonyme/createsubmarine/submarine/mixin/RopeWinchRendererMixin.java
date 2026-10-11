package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.createmod.catnip.render.SpriteShiftEntry;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchRenderer;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RopeWinchRenderer.class, remap = false)
public class RopeWinchRendererMixin {

    private boolean createsubmarine$isSteelCable = false;

    @Inject(method = "renderComponents", at = @At("HEAD"))
    private void createsubmarine$captureSteel(
        RopeWinchBlockEntity be, float partialTicks,
        PoseStack ms,
        MultiBufferSource buffer,
        int light, int overlay,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci
    ) {
        createsubmarine$isSteelCable = false;
        if (be instanceof SmartBlockEntity smartBe) {
            RopeStrandHolderBehavior behavior = smartBe.getBehaviour(RopeStrandHolderBehavior.TYPE);
            if (behavior instanceof SteelCableHolderAccessor accessor && accessor.createsubmarine$isSteelCable()) {
                createsubmarine$isSteelCable = true;
            }
        }
    }

    @Redirect(
        method = "renderComponents",
        at = @At(
            value = "INVOKE",
            target = "Lnet/createmod/catnip/render/CachedBuffers;partial(Ldev/engine_room/flywheel/lib/model/baked/PartialModel;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/createmod/catnip/render/SuperByteBuffer;"
        )
    )
    private SuperByteBuffer createsubmarine$redirectCoil(PartialModel model, BlockState state) {
        if (model == SimPartialModels.ROPE_WINCH_ROPE_COIL && createsubmarine$isSteelCable) {
            return CachedBuffers.partial(AllPartialModels.WINCH_STEEL_COIL, state);
        }
        return CachedBuffers.partial(model, state);
    }

    @Inject(method = "getCoilShift", at = @At("RETURN"), cancellable = true)
    private void createsubmarine$redirectCoilShift(CallbackInfoReturnable<net.createmod.catnip.render.SpriteShiftEntry> cir) {
        if (createsubmarine$isSteelCable) {
            cir.setReturnValue(AllPartialModels.WINCH_STEEL_COIL_SCROLL);
        }
    }
}
