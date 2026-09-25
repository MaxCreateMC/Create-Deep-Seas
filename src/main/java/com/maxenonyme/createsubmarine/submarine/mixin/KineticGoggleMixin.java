package com.maxenonyme.createsubmarine.submarine.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.maxenonyme.createsubmarine.submarine.system.CableElectrificationSystem;
import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class KineticGoggleMixin {

    @ModifyReturnValue(method = "addToGoggleTooltip", at = @At("RETURN"))
    private boolean createsubmarine$steelCableTooltip(boolean added, List<Component> tooltip, boolean isPlayerSneaking) {
        if (!((Object) this instanceof RopeWinchBlockEntity winch))
            return added;
        if (!(winch.getBehaviour(RopeStrandHolderBehavior.TYPE) instanceof SteelCableHolderAccessor accessor)
                || !accessor.createsubmarine$isSteelCable())
            return added;
        tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_submarine.gui.goggles.steel_cable_network").withStyle(ChatFormatting.GRAY)));
        CableElectrificationSystem.ElectrifiedEnergyStorage storage = CableElectrificationSystem.WINCH_ENERGY.get(winch);
        int energy = storage != null ? storage.getEnergyStored() : 0;
        tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_submarine.gui.goggles.energy").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(": " + energy + " / " + 1000000 + " FE").withStyle(ChatFormatting.WHITE)));
        return true;
    }
}
