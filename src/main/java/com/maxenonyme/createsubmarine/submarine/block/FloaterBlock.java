package com.maxenonyme.createsubmarine.submarine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.entity.FloaterBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.HitResult;
import java.util.ArrayList;
import java.util.List;

public class FloaterBlock extends Block implements EntityBlock {
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

    public FloaterBlock(Properties properties) {
        super(properties.sound(SoundType.WOOL));
        this.registerDefaultState(this.stateDefinition.any().setValue(COLOR, DyeColor.WHITE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (stack.getItem() instanceof DyeItem dye) {
            DyeColor color = dye.getDyeColor();
            if (state.getValue(COLOR) != color) {
                level.setBlock(pos, state.setValue(COLOR, color), 3);
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FloaterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (l, p, s, be) -> {
            if (be instanceof FloaterBlockEntity fbe)
                FloaterBlockEntity.serverTick(l, p, fbe);
        };
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        DyeColor color = state.getValue(COLOR);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(COLOR, color));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(getColorIndex(color)));
        return stack;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>();
        drops.add(getCloneItemStack(state, null, null, null, null));
        return drops;
    }

    private int getColorIndex(DyeColor color) {
        return switch (color) {
            case WHITE -> 1;
            case ORANGE -> 2;
            case MAGENTA -> 3;
            case LIGHT_BLUE -> 4;
            case YELLOW -> 5;
            case LIME -> 6;
            case PINK -> 7;
            case GRAY -> 8;
            case LIGHT_GRAY -> 9;
            case CYAN -> 10;
            case PURPLE -> 11;
            case BLUE -> 12;
            case BROWN -> 13;
            case GREEN -> 14;
            case RED -> 15;
            case BLACK -> 16;
        };
    }
}

