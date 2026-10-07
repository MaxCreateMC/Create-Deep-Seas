package com.maxenonyme.AbyssDimension.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

import java.util.function.Supplier;

public class IsopodSpawnEggItem extends DeferredSpawnEggItem {
    private final int size;

    public IsopodSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor, int size, Properties properties) {
        super(type, backgroundColor, highlightColor, properties);
        this.size = size;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState state = level.getBlockState(pos);

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Spawner spawner) {
            EntityType<?> type = getType(stack);
            if (type != null) {
                spawner.setEntityId(type, level.getRandom());
                level.sendBlockUpdated(pos, state, state, 3);
                level.gameEvent(context.getPlayer(), GameEvent.BLOCK_CHANGE, pos);
                stack.shrink(1);
                return InteractionResult.CONSUME;
            }
        }

        BlockPos spawnPos;
        if (state.getCollisionShape(level, pos).isEmpty()) {
            spawnPos = pos;
        } else {
            spawnPos = pos.relative(face);
        }

        EntityType<?> entityType = getType(stack);
        if (!(entityType instanceof EntityType<?>)) return InteractionResult.FAIL;

        @SuppressWarnings("unchecked")
        IsopodEntity entity = ((EntityType<IsopodEntity>) entityType).create(serverLevel, null, spawnPos, MobSpawnType.SPAWN_EGG, true, !pos.equals(spawnPos) && face == Direction.UP);
        if (entity == null) return InteractionResult.FAIL;

        entity.setSize(this.size, true);
        entity.setYRot(context.getPlayer() != null ? context.getPlayer().getYRot() : 0.0F);
        entity.setXRot(0.0F);
        serverLevel.addFreshEntityWithPassengers(entity);
        stack.shrink(1);
        level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, spawnPos);
        return InteractionResult.CONSUME;
    }
}
