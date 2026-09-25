package com.maxenonyme.createsubmarine.submarine.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion;
import dev.ryanhcode.sable.util.BoundedBitVolume3i;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.backend.engine.uniform.FogUniforms", remap = false)
public class FlywheelFogUniformsMixin {

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getShaderFogStart()F"), require = 0)
    private static float createsubmarine$defogStart(float original) {
        float hull = createsubmarine$pocketHullDistance();
        return hull > 0.0f ? hull + 16.0f : original;
    }

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getShaderFogEnd()F"), require = 0)
    private static float createsubmarine$defogEnd(float original) {
        float hull = createsubmarine$pocketHullDistance();
        return hull > 0.0f ? hull + 48.0f : original;
    }

    @Unique
    private static float createsubmarine$pocketHullDistance() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return -1.0f;
        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(mc.level);
        if (container == null)
            return -1.0f;
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        WaterOcclusionRegion region = container.getOccludingRegion(cameraPos);
        if (region == null)
            return -1.0f;
        BoundedBitVolume3i volume = region.getVolume();
        SubLevel sub = Sable.HELPER.getContaining(mc.level, volume.getMinBlockPos());
        Vec3 local = sub != null ? sub.logicalPose().transformPositionInverse(cameraPos) : cameraPos;
        BlockPos min = volume.getMinBlockPos();
        BlockPos max = volume.getMaxBlockPos();
        double dx = Math.max(local.x - min.getX(), max.getX() + 1 - local.x);
        double dy = Math.max(local.y - min.getY(), max.getY() + 1 - local.y);
        double dz = Math.max(local.z - min.getZ(), max.getZ() + 1 - local.z);
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
