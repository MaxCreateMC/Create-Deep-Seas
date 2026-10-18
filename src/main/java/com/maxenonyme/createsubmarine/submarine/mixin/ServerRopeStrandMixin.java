package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.system.SteelCablePhysicsSystem;
import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerRopeStrand.class, remap = false)
public abstract class ServerRopeStrandMixin implements SteelCableHolderAccessor {

    @ModifyArg(method = "<init>(Ljava/util/UUID;Ljava/util/Collection;)V",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/object/rope/RopePhysicsObject;<init>(Ljava/util/Collection;D)V"),
            index = 1)
    private static double createsubmarine$fattenRopeCollider(double radius) {
        return 0.2;
    }

    @Shadow
    private PhysicsConstraintHandle constraint;

    @Unique
    private boolean createsubmarine$isSteelCable = false;

    @Override
    public boolean createsubmarine$isSteelCable() {
        return this.createsubmarine$isSteelCable;
    }

    @Override
    public void createsubmarine$setSteelCable(boolean val) {
        this.createsubmarine$isSteelCable = val;
    }

}
