package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

public class WrenchRepairHandler {

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide())
            return;
        ItemStack held = event.getItemStack();
        if (!(held.getItem() instanceof WrenchItem))
            return;

        BlockPos clickedPos = event.getPos();
        UUID subId = SubLevelRegistry.findUUID(event.getLevel(), clickedPos);
        if (subId == null)
            return;
        
        SubLevelAccess subLevel = SubLevelRegistry.getAll().get(subId);
        if (subLevel == null)
            return;

        Level oceanLevel = subLevel instanceof SubLevel sl ? sl.getLevel() : event.getLevel();
        if (SubmarinePressureSystem.repairCrack(subId, clickedPos, oceanLevel)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
