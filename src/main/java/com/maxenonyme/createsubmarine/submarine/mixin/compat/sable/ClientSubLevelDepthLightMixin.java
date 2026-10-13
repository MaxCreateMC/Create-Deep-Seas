package com.maxenonyme.createsubmarine.submarine.mixin.compat.sable;

import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientSubLevel.class, remap = false)
public abstract class ClientSubLevelDepthLightMixin {

    @Inject(method = "computeSubLevelSkyLight", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void createsubmarine$dimUnderwater(Pose3dc pose, CallbackInfoReturnable<Integer> cir) {
        int light = cir.getReturnValueI();
        if (light <= 0)
            return;
        ClientSubLevel self = (ClientSubLevel) (Object) this;
        ClientLevel level = self.getLevel();
        BoundingBox3dc box = self.boundingBox();
        if (box.volume() < 9)
            return;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(
                Mth.floor((box.minX() + box.maxX()) * 0.5),
                Mth.floor(box.maxY()),
                Mth.floor((box.minZ() + box.maxZ()) * 0.5));
        int water = 0;
        while (water < 15 && level.getFluidState(cursor).is(FluidTags.WATER)) {
            water++;
            cursor.move(Direction.UP);
        }
        if (water > 0)
            cir.setReturnValue(Math.min(light, 15 - water));
    }
}
