package com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller;

import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.simulated_team.simulated.util.SimMathUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Consumer;

import static dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlock.REVERSED;

public class SubmarinePropellerVisual extends OrientedRotatingVisual<SubmarinePropellerBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance propeller;
    private final TransformedInstance contraPropeller;
    private final Direction facing;
    private final Vector3f rotationAxis;
    private final Quaternionf blockOrientation;
    private final Vector3f origin;
    private final Quaternionf spin = new Quaternionf();

    public SubmarinePropellerVisual(VisualizationContext context, SubmarinePropellerBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick, Direction.SOUTH,
                blockEntity.getBlockState().getValue(BlockStateProperties.FACING).getOpposite(),
                Models.partial(com.simibubi.create.AllPartialModels.SHAFT_HALF));
        facing = blockState.getValue(BlockStateProperties.FACING);
        rotationAxis = Direction.get(Direction.AxisDirection.POSITIVE, facing.getAxis()).step();
        blockOrientation = SimMathUtils.getBlockStateOrientation(facing);
        BlockPos visualPos = getVisualPosition();
        origin = new Vector3f(visualPos.getX(), visualPos.getY(), visualPos.getZ())
                .add(facing.step().mul(3 / 16f));

        propeller = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(getModel(blockState))).createInstance();
        contraPropeller = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(getContraModel(blockState))).createInstance();
        place(0);
    }

    public PartialModel getModel(BlockState state) {
        return state.getValue(REVERSED) ? AllPartialModels.SUBMARINE_PROPELLER_REVERSED : AllPartialModels.SUBMARINE_PROPELLER;
    }

    public PartialModel getContraModel(BlockState state) {
        return state.getValue(REVERSED) ? AllPartialModels.SUBMARINE_PROPELLER_REVERSED_CONTRA : AllPartialModels.SUBMARINE_PROPELLER_CONTRA;
    }

    private float getAngle(float partialTicks) {
        float angle = 2 * Mth.lerp(partialTicks, blockEntity.getPreviousAngle(), blockEntity.getAngle());
        return angle + rotationOffset(blockState, facing.getAxis(), blockEntity.getBlockPos());
    }

    @Override
    public void beginFrame(Context context) {
        place(context.partialTick());
    }

    private void place(float partialTicks) {
        if (blockEntity.isGroupMember()) {
            propeller.setZeroTransform().setChanged();
            contraPropeller.setZeroTransform().setChanged();
            return;
        }
        float angle = getAngle(partialTicks);
        pose(propeller, angle);
        pose(contraPropeller, -angle);
    }

    private void pose(TransformedInstance instance, float angle) {
        boolean big = blockEntity.isGroupMaster();
        float grow = big ? 2 : 1;
        Direction[] plane = SubmarinePropellerBlockEntity.planeAxes(facing);
        float shift = big ? 0.5f : 0;
        spin.identity()
                .rotateAxis(Mth.DEG_TO_RAD * angle, rotationAxis.x, rotationAxis.y, rotationAxis.z)
                .mul(blockOrientation);
        instance.setIdentityTransform()
                .translate(origin.x + (plane[0].getStepX() + plane[1].getStepX()) * shift,
                        origin.y + (plane[0].getStepY() + plane[1].getStepY()) * shift,
                        origin.z + (plane[0].getStepZ() + plane[1].getStepZ()) * shift)
                .translate(0.5f, 0.5f, 0.5f)
                .scale(facing.getAxis() == Direction.Axis.X ? 1 : grow,
                        facing.getAxis() == Direction.Axis.Y ? 1 : grow,
                        facing.getAxis() == Direction.Axis.Z ? 1 : grow)
                .rotate(spin)
                .translate(-0.5f, -0.5f, -0.5f)
                .setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        super.updateLight(partialTick);
        relight(pos, propeller, contraPropeller);
    }

    @Override
    protected void _delete() {
        super._delete();
        propeller.delete();
        contraPropeller.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        super.collectCrumblingInstances(consumer);
        consumer.accept(propeller);
        consumer.accept(contraPropeller);
    }
}
