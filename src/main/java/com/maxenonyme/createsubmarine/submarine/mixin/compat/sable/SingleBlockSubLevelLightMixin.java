package com.maxenonyme.createsubmarine.submarine.mixin.compat.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.SingleBlockSubLevelWrapper;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = SingleBlockSubLevelWrapper.class, remap = false)
public abstract class SingleBlockSubLevelLightMixin {

    @Unique
    private int createsubmarine$blockLight = -1;
    @Unique
    private int createsubmarine$skyLight = -1;

    @Inject(method = "setup", at = @At("TAIL"), remap = false, require = 0)
    private void createsubmarine$findHull(ClientLevel level, double x, double y, double z, BlockPos localPos,
                                          BlockState state, CallbackInfo ci) {
        createsubmarine$blockLight = -1;
        createsubmarine$skyLight = -1;
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null)
            return;
        for (SubLevel other : container.getAllSubLevels()) {
            if (!(other instanceof ClientSubLevel hull) || hull.getPlot() == null)
                continue;
            BoundingBox3ic b = hull.getPlot().getBoundingBox();
            if ((long) (b.maxX() - b.minX() + 1) * (b.maxY() - b.minY() + 1) * (b.maxZ() - b.minZ() + 1) < 9)
                continue;
            Vector3d local = hull.renderPose().transformPositionInverse(new Vector3d(x, y, z));
            BlockPos at = BlockPos.containing(local.x, local.y, local.z);
            if (at.getX() < b.minX() || at.getX() > b.maxX() || at.getY() < b.minY() || at.getY() > b.maxY()
                    || at.getZ() < b.minZ() || at.getZ() > b.maxZ())
                continue;
            createsubmarine$blockLight = level.getBrightness(LightLayer.BLOCK, at);
            createsubmarine$skyLight = hull.scaleSkyLight(level.getBrightness(LightLayer.SKY, at));
            return;
        }
    }

    @Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void createsubmarine$hullBrightness(LightLayer layer, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (createsubmarine$blockLight >= 0)
            cir.setReturnValue(layer == LightLayer.BLOCK ? createsubmarine$blockLight : createsubmarine$skyLight);
    }

    @Inject(method = "getRawBrightness", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void createsubmarine$hullRawBrightness(BlockPos pos, int darkening, CallbackInfoReturnable<Integer> cir) {
        if (createsubmarine$blockLight >= 0)
            cir.setReturnValue(Math.max(createsubmarine$skyLight - darkening, createsubmarine$blockLight));
    }
}
