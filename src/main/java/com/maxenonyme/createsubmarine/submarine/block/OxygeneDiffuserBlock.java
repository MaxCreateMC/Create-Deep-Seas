package com.maxenonyme.createsubmarine.submarine.block;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.entity.OxygeneDiffuserBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;
import com.maxenonyme.createsubmarine.submarine.system.DiffuserZoneProtection;
import net.minecraft.world.item.context.BlockPlaceContext;
public class OxygeneDiffuserBlock extends Block implements EntityBlock {
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return DiffuserZoneProtection.canPlaceMachine(context)
                ? super.getStateForPlacement(context)
                : null;
    }

    protected static final VoxelShape SHAPE = Shapes.or(
        Block.box(0.0, 0.0, 0.0, 16.0, 13.0, 16.0),
        Block.box(1.0, 13.0, 1.0, 15.0, 15.0, 15.0),
        Block.box(2.0, 13.0, 2.0, 14.0, 27.0, 14.0)
    );
    public OxygeneDiffuserBlock(Properties properties) {
        super(properties);
    }
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OxygeneDiffuserBlockEntity(pos, state);
    }
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == CreateSubmarine.OXYGENE_DIFFUSER_BE.get() ? (lvl, p, st, be) -> OxygeneDiffuserBlockEntity.tick(lvl, p, st, (OxygeneDiffuserBlockEntity) be) : null;
    }
}
