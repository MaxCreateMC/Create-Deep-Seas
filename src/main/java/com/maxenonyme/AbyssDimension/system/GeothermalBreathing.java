package com.maxenonyme.AbyssDimension.system;

import com.maxenonyme.AbyssDimension.block.GeothermalVentBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class GeothermalBreathing {
    private GeothermalBreathing() {
    }

    private static final int PERIOD = 10;
    private static final int PLUME = 4;
    private static final int HALF_BUBBLE = 15;

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % PERIOD != 0)
            return;
        if (!player.isUnderWater() || player.getAirSupply() >= player.getMaxAirSupply())
            return;
        if (!inPlume(player.level(), BlockPos.containing(player.getX(), player.getEyeY(), player.getZ())))
            return;
        player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + HALF_BUBBLE));
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.4f, 0.9f + player.getRandom().nextFloat() * 0.3f);
    }

    private static boolean inPlume(Level level, BlockPos eye) {
        BlockPos.MutableBlockPos p = eye.mutable();
        for (int i = 0; i <= PLUME; i++) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof GeothermalVentBlock)
                return state.getValue(GeothermalVentBlock.WATERLOGGED);
            if (!state.getFluidState().is(FluidTags.WATER))
                return false;
            p.move(0, -1, 0);
        }
        return false;
    }
}
