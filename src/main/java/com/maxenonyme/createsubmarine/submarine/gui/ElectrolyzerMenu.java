package com.maxenonyme.createsubmarine.submarine.gui;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.entity.ElectrolyzerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.phys.Vec3;

public class ElectrolyzerMenu extends AbstractContainerMenu {
    public static final int MODULE_X = 26;
    public static final int MODULE_Y = 44;
    public static final int INV_X = 15;
    public static final int INV_Y = 145;

    public final BlockPos pos;
    private final ContainerData data;

    public ElectrolyzerMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv, extraData.readBlockPos(), new SimpleContainerData(8));
    }

    public ElectrolyzerMenu(int id, Inventory inv, BlockPos pos, ContainerData data) {
        super(CreateSubmarine.ELECTROLYZER_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);

        IItemHandler module = inv.player.level().getBlockEntity(pos) instanceof ElectrolyzerBlockEntity be
                ? be.module
                : new ItemStackHandler(1);
        addSlot(new SlotItemHandler(module, 0, MODULE_X, MODULE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ElectrolyzerBlockEntity.isElectronTube(stack);
            }
        });

        for (int row = 0; row < 3; ++row)
            for (int col = 0; col < 9; ++col)
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; ++col)
            addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));
    }

    public ElectrolyzerMenu(int id, Inventory inv, ElectrolyzerBlockEntity be, ContainerData data) {
        this(id, inv, be.getBlockPos(), data);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem())
            return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true))
                return ItemStack.EMPTY;
        } else if (!ElectrolyzerBlockEntity.isElectronTube(stack) || !moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty())
            slot.setByPlayer(ItemStack.EMPTY);
        else
            slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof ElectrolyzerBlockEntity
                && Sable.HELPER.distanceSquaredWithSubLevels(player.level(),
                        player.getEyePosition(), Vec3.atCenterOf(pos)) <= 64;
    }

    public int getEnergy() { return data.get(0); }
    public int getMaxEnergy() { return data.get(1); }
    public int getWater() { return data.get(2); }
    public int getMaxWater() { return data.get(3); }
    public int getOxygen() { return data.get(4); }
    public int getMaxOxygen() { return data.get(5); }
    public boolean isMachineEnabled() { return data.get(6) == 1; }
    public int getGenerated() { return data.get(7); }
}
