package com.maxenonyme.highseas.block;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.block.entity.BuoyBlockEntity;
import com.maxenonyme.highseas.block.entity.BuoySeatEntity;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import java.util.Set;
import net.minecraft.world.level.block.state.StateDefinition;

public class BuoyBlock extends Block implements EntityBlock {

    public static final MapCodec<BuoyBlock> CODEC = simpleCodec(BuoyBlock::new);

    public static final net.minecraft.world.level.block.state.properties.BooleanProperty ROPED =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("roped");

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(7.5, 0.25, -9.75, 10.5, 10.25, -6.75),
            Block.box(0.5, 0.25, -9.75, 3.5, 10.25, -6.75),
            Block.box(0.5, 3.25, -13.75, 3.5, 7.25, -9.75),
            Block.box(3.5, 3.25, -13.75, 7.5, 7.25, -9.75),
            Block.box(7.5, 3.25, -13.75, 10.5, 7.25, -9.75),
            Block.box(-0.5, 0, -6.7, 11.5, 11, 0),
            Block.box(-0.5, 0, 15, 11.5, 11, 21.7),
            Block.box(-7.4, 0, 0, -0.5, 11, 15),
            Block.box(11.5, 0, 0, 18.4, 11, 15),
            Block.box(8.289, 0.1, 16.929, 13.026, 10.9, 21.667),
            Block.box(9.632, 0.1, 15.586, 14.37, 10.9, 20.323),
            Block.box(10.976, 0.1, 14.242, 15.713, 10.9, 18.98),
            Block.box(12.319, 0.1, 12.899, 17.057, 10.9, 17.636),
            Block.box(13.663, 0.1, 11.555, 18.4, 10.9, 16.293),
            Block.box(-7.382, 0.1, -1.331, -2.63, 10.9, 3.42),
            Block.box(-6.024, 0.1, -2.689, -1.273, 10.9, 2.063),
            Block.box(-4.667, 0.1, -4.047, 0.085, 10.9, 0.705),
            Block.box(-3.309, 0.1, -5.404, 1.443, 10.9, -0.652),
            Block.box(-1.951, 0.1, -6.762, 2.8, 10.9, -2.01),
            Block.box(-7.337, 0.1, 11.619, -2.585, 10.9, 16.371),
            Block.box(-5.979, 0.1, 12.976, -1.228, 10.9, 17.728),
            Block.box(-4.622, 0.1, 14.334, 0.13, 10.9, 19.086),
            Block.box(-3.264, 0.1, 15.692, 1.488, 10.9, 20.443),
            Block.box(-1.907, 0.1, 17.049, 2.845, 10.9, 21.801),
            Block.box(8.192, 0.1, -6.711, 12.944, 10.9, -1.959),
            Block.box(9.55, 0.1, -5.353, 14.302, 10.9, -0.601),
            Block.box(10.907, 0.1, -3.995, 15.659, 10.9, 0.757),
            Block.box(12.265, 0.1, -2.638, 17.017, 10.9, 2.114),
            Block.box(13.623, 0.1, -1.28, 18.374, 10.9, 3.472));

    private static final VoxelShape COLLISION = Block.box(0.0, 0.0, 0.0, 16.0, 11.0, 16.0);

    private static final double SEAT_DROP = -0.35;
    private static final double SEAT_X = 0.3375;
    private static final double SEAT_Z = 0.46875;

    public BuoyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ROPED, false));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROPED);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BuoyBlockEntity be) {
            be.destroy();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BuoyBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (type != CreateHighSeas.BUOY_BE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((BuoyBlockEntity) be).tick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isPassenger() || player.isShiftKeyDown() || holdsRope(player)) {
            return InteractionResult.PASS;
        }
        BuoySeatEntity seat = new BuoySeatEntity(level);
        seat.setBuoy(pos);
        seat.setPos(pos.getX() + SEAT_X, pos.getY() + SEAT_DROP, pos.getZ() + SEAT_Z);
        seat.setYRot(Direction.NORTH.toYRot());
        if (!level.addFreshEntity(seat)) {
            return InteractionResult.PASS;
        }
        if (!player.startRiding(seat, true)) {
            seat.discard();
            return InteractionResult.PASS;
        }
        return InteractionResult.CONSUME;
    }

    private static BlockState wool(RandomSource random) {
        return random.nextBoolean() ? Blocks.RED_WOOL.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState();
    }

    @Override
    public boolean addLandingEffects(BlockState state, ServerLevel level, BlockPos pos, BlockState state2,
            LivingEntity entity, int count) {
        int red = count / 2;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RED_WOOL.defaultBlockState()).setPos(pos),
                entity.getX(), entity.getY(), entity.getZ(), red, 0.0, 0.0, 0.0, 0.15);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WHITE_WOOL.defaultBlockState()).setPos(pos),
                entity.getX(), entity.getY(), entity.getZ(), count - red, 0.0, 0.0, 0.0, 0.15);
        return true;
    }

    @Override
    public boolean addRunningEffects(BlockState state, Level level, BlockPos pos, Entity entity) {
        RandomSource random = entity.getRandom();
        Vec3 motion = entity.getDeltaMovement();
        double x = entity.getX() + (random.nextDouble() - 0.5) * entity.getBbWidth();
        double z = entity.getZ() + (random.nextDouble() - 0.5) * entity.getBbWidth();
        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, wool(random)).setPos(pos),
                x, entity.getY() + 0.1, z, motion.x * -4.0, 1.5, motion.z * -4.0);
        return true;
    }

    @Override
    public void initializeClient(Consumer<IClientBlockExtensions> consumer) {
        consumer.accept(new IClientBlockExtensions() {
            @Override
            public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
                if (!(target instanceof BlockHitResult hit) || !(level instanceof ClientLevel client)) {
                    return true;
                }
                BlockPos pos = hit.getBlockPos();
                AABB box = COLLISION.bounds();
                RandomSource random = level.random;
                double x = pos.getX() + box.minX + 0.1 + random.nextDouble() * (box.maxX - box.minX - 0.2);
                double y = pos.getY() + box.minY + 0.1 + random.nextDouble() * (box.maxY - box.minY - 0.2);
                double z = pos.getZ() + box.minZ + 0.1 + random.nextDouble() * (box.maxZ - box.minZ - 0.2);
                switch (hit.getDirection()) {
                    case DOWN -> y = pos.getY() + box.minY - 0.1;
                    case UP -> y = pos.getY() + box.maxY + 0.1;
                    case NORTH -> z = pos.getZ() + box.minZ - 0.1;
                    case SOUTH -> z = pos.getZ() + box.maxZ + 0.1;
                    case WEST -> x = pos.getX() + box.minX - 0.1;
                    case EAST -> x = pos.getX() + box.maxX + 0.1;
                }
                BlockState wool = wool(random);
                manager.add(new TerrainParticle(client, x, y, z, 0.0, 0.0, 0.0, wool, pos)
                        .updateSprite(wool, pos).setPower(0.2F).scale(0.6F));
                return true;
            }

            @Override
            public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
                if (!(level instanceof ClientLevel client)) {
                    return true;
                }
                AABB box = COLLISION.bounds();
                int nx = Math.max(2, Mth.ceil(box.getXsize() / 0.25));
                int ny = Math.max(2, Mth.ceil(box.getYsize() / 0.25));
                int nz = Math.max(2, Mth.ceil(box.getZsize() / 0.25));
                for (int i = 0; i < nx; i++) {
                    for (int j = 0; j < ny; j++) {
                        for (int k = 0; k < nz; k++) {
                            double fx = (i + 0.5) / nx;
                            double fy = (j + 0.5) / ny;
                            double fz = (k + 0.5) / nz;
                            BlockState wool = wool(level.random);
                            manager.add(new TerrainParticle(client,
                                    pos.getX() + box.minX + fx * box.getXsize(),
                                    pos.getY() + box.minY + fy * box.getYsize(),
                                    pos.getZ() + box.minZ + fz * box.getZsize(),
                                    fx - 0.5, fy - 0.5, fz - 0.5, wool, pos).updateSprite(wool, pos));
                        }
                    }
                }
                return true;
            }
        });
    }

    private static boolean holdsRope(Player player) {
        return player.getMainHandItem()
                .getItem() instanceof dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem
                || player.getOffhandItem()
                        .getItem() instanceof dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        setAfloat(level, pos);
    }

    public static void setAfloat(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockState(pos).getBlock() instanceof BuoyBlock)) {
            return;
        }
        SubLevelAccess current =
                SableCompanion.INSTANCE.getContaining(level, pos);
        if (current instanceof ServerSubLevel already) {
            BoundingBox3ic bb = already.getPlot().getBoundingBox();
            if (bb != null && bb.minX() == bb.maxX() && bb.minY() == bb.maxY() && bb.minZ() == bb.maxZ()) {
                return;
            }
        }

        try {
            BoundingBox3i bounds =
                    new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX(), pos.getY(), pos.getZ());

            SubLevelAssemblyHelper.assembleBlocks(level, pos, Set.of(pos), bounds);
        } catch (Exception e) {
            CreateHighSeas.LOGGER.warn("Buoy at {} could not be floated", pos, e);
        }
    }
}
