package com.maxenonyme.highseas.gui;

import com.maxenonyme.highseas.CreateHighSeas;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class BoatEngineMenu extends AbstractContainerMenu {

    public final BlockPos pos;
    private final Container fuel;
    private final ContainerData data;

    public BoatEngineMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv, extraData.readBlockPos(), new SimpleContainer(1), new SimpleContainerData(3));
    }

    public BoatEngineMenu(int id, Inventory inv, BlockPos pos, Container fuel, ContainerData data) {
        super(CreateHighSeas.BOAT_ENGINE_MENU.get(), id);
        this.pos = pos;
        this.fuel = fuel;
        this.data = data;
        addDataSlots(data);

        addSlot(new Slot(fuel, 0, 72, 45) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getBurnTime(null) > 0;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 15 + col * 18, 131 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 15 + col * 18, 189));
        }
    }

    public int getLitProgress() {
        int total = data.get(1);
        if (total == 0) {
            total = 200;
        }
        return data.get(0) * 13 / total;
    }

    public boolean isLit() {
        return data.get(0) > 0;
    }

    public boolean isEngineEnabled() {
        return data.get(2) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return moved;
        }
        ItemStack stack = slot.getItem();
        moved = stack.copy();

        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getBurnTime(null) > 0) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return fuel.stillValid(player);
    }
}
