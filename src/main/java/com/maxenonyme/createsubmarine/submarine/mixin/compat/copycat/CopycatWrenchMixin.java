package com.maxenonyme.createsubmarine.submarine.mixin.compat.copycat;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem;
import com.maxenonyme.createsubmarine.submarine.util.CrackUtil;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;
import java.util.UUID;

@Pseudo
@Mixin(targets = "com.copycatsplus.copycats.foundation.copycat.ICopycatBlock", remap = false)
public interface CopycatWrenchMixin {

    @Inject(method = "onWrenched", at = @At("HEAD"), cancellable = true)
    default void createsubmarine$interceptWrench(BlockState state, UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        
        UUID subId = null;
        BlockPos plotPos = clickedPos;
        SubLevelAccess subLevel = null;

        if (level instanceof SubLevelAccess sla) {
            subId = sla.getUniqueId();
            subLevel = sla;
        } else {
            subLevel = SableCompanion.INSTANCE.getContaining(level, clickedPos);
            if (subLevel != null) {
                subId = subLevel.getUniqueId();
                Vector3d local = new Vector3d(clickedPos.getX() + 0.5, clickedPos.getY() + 0.5, clickedPos.getZ() + 0.5);
                Pose3dc pose = level.isClientSide() ? CreateSubmarine.clientPoseGetter.apply(subLevel) : subLevel.logicalPose();
                pose.transformPositionInverse(local);
                plotPos = BlockPos.containing(local.x, local.y, local.z);
            }
        }

        if (subId != null && subLevel != null) {
            boolean sealed = CompartmentTracker.hasAnySealed(subId);
            boolean pressurized = false;
            boolean hasCrack = false;

            if (level.isClientSide()) {
                pressurized = subLevel.logicalPose().position().y() < 62;
                hasCrack = CrackUtil.hasCrack(subId, plotPos);
            } else {
                pressurized = SubmarinePressureSystem.isPressurized(subId);
                Map<BlockPos, Integer> cracks = SubmarinePressureSystem.getAllCracks().get(subId);
                hasCrack = cracks != null && cracks.containsKey(plotPos);
            }

            if (hasCrack || (sealed && pressurized)) {
                cir.setReturnValue(InteractionResult.PASS);
            }
        }
    }
}
