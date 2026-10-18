package com.maxenonyme.AbyssDimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BurningSandBlock extends Block {
    public BurningSandBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        BlockPos above = pos.above();
        if (level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN))
            return;
        if (random.nextInt(5) != 0)
            return;
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() + 1.02;
        double z = pos.getZ() + random.nextDouble();
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.03, 0.0);
    }
}
