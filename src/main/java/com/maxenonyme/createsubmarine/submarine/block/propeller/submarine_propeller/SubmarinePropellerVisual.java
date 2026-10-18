package com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller;

import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlock.REVERSED;

public class SubmarinePropellerVisual extends OrientedRotatingVisual<SubmarinePropellerBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance propeller;
    private final TransformedInstance contraPropeller;
    private final Direction facing;
    private final Vector3f rotationAxis;
    private final Quaternionf blockOrientation;
    private final Quaternionf facingRotation;
    private final Vector3f origin;
    private final Quaternionf spin = new Quaternionf();

    private int clusterSize;
    private final Vector3f clusterCenter = new Vector3f();
    private TransformedInstance clusterBase;
    private final List<TransformedInstance> clusterParts = new ArrayList<>();
    private boolean shaftVisible = true;

    public SubmarinePropellerVisual(VisualizationContext context, SubmarinePropellerBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick, Direction.SOUTH,
                blockEntity.getBlockState().getValue(BlockStateProperties.FACING).getOpposite(),
                Models.partial(com.simibubi.create.AllPartialModels.SHAFT_HALF));
        facing = blockState.getValue(BlockStateProperties.FACING);
        rotationAxis = Direction.get(Direction.AxisDirection.POSITIVE, facing.getAxis()).step();
        blockOrientation = SimMathUtils.getBlockStateOrientation(facing);
        facingRotation = facing.getRotation();
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
        SubmarinePropellerBlockEntity source = blockEntity.clusterDriver();
        float angle = 2 * Mth.lerp(partialTicks, source.getPreviousAngle(), source.getAngle());
        return angle + rotationOffset(blockState, facing.getAxis(), blockEntity.getBlockPos());
    }

    @Override
    public void beginFrame(Context context) {
        place(context.partialTick());
    }

    private void place(float partialTicks) {
        int size = blockEntity.getClusterSize();
        boolean master = blockEntity.isGroupMaster();
        boolean showShaft = size != 3 || master;
        if (shaftVisible != showShaft) {
            shaftVisible = showShaft;
            rotatingModel.setVisible(showShaft);
        }
        if (size == 0 || !master)
            deleteCluster();
        if (size != 0) {
            propeller.setZeroTransform().setChanged();
            contraPropeller.setZeroTransform().setChanged();
            if (master)
                placeCluster(size, getAngle(partialTicks));
            return;
        }
        float angle = getAngle(partialTicks);
        pose(propeller, angle);
        pose(contraPropeller, -angle);
    }

    private void pose(TransformedInstance instance, float angle) {
        spin.identity()
                .rotateAxis(Mth.DEG_TO_RAD * angle, rotationAxis.x, rotationAxis.y, rotationAxis.z)
                .mul(blockOrientation);
        instance.setIdentityTransform()
                .translate(origin)
                .translate(0.5f, 0.5f, 0.5f)
                .rotate(spin)
                .translate(-0.5f, -0.5f, -0.5f)
                .setChanged();
    }

    private void placeCluster(int size, float angle) {
        if (clusterSize != size) {
            deleteCluster();
            createCluster(size);
        }
        spin.identity()
                .rotateAxis(Mth.DEG_TO_RAD * angle, rotationAxis.x, rotationAxis.y, rotationAxis.z)
                .mul(facingRotation);
        for (TransformedInstance part : clusterParts) {
            part.setIdentityTransform()
                    .translate(clusterCenter)
                    .rotate(spin)
                    .translate(0, -0.5f, 0)
                    .setChanged();
        }
    }

    private void createCluster(int size) {
        clusterSize = size;
        BlockPos visualPos = getVisualPosition();
        clusterCenter.set(visualPos.getX() + 0.5f, visualPos.getY() + 0.5f, visualPos.getZ() + 0.5f);
        float halfWidth = 0.5f;
        if (size == 2) {
            Direction[] plane = SubmarinePropellerBlockEntity.planeAxes(facing);
            clusterCenter.add(plane[0].step().add(plane[1].step()).mul(0.5f));
            halfWidth = 1.0f;
            clusterParts.add(transformed(Models.partial(AllPartialModels.SUBMARINE_PROPELLER_2X2_BLADES)));
            clusterBase = transformed(Models.partial(AllPartialModels.SUBMARINE_PROPELLER_2X2_BASE));
        } else {
            clusterParts.add(transformed(Models.partial(AllPartialModels.SUBMARINE_PROPELLER_3X3_GEAR)));
            clusterParts.add(transformed(Models.partial(AllPartialModels.SUBMARINE_PROPELLER_3X3_BLADES)));
            clusterBase = transformed(Models.partial(AllPartialModels.SUBMARINE_PROPELLER_3X3_BASE));
        }
        clusterBase.setIdentityTransform()
                .translate(clusterCenter)
                .rotate(facingRotation)
                .translate(-halfWidth, -0.5f, -halfWidth)
                .setChanged();
        relightCluster();
    }

    private TransformedInstance transformed(Model model) {
        return instancerProvider().instancer(InstanceTypes.TRANSFORMED, model).createInstance();
    }

    private void relightCluster() {
        if (clusterBase == null)
            return;
        List<FlatLit> lit = new ArrayList<>(clusterParts);
        lit.add(clusterBase);
        relight(pos, lit);
    }

    private void deleteCluster() {
        if (clusterBase == null)
            return;
        clusterBase.delete();
        clusterParts.forEach(Instance::delete);
        clusterParts.clear();
        clusterBase = null;
        clusterSize = 0;
    }

    @Override
    public void updateLight(float partialTick) {
        super.updateLight(partialTick);
        relight(pos, propeller, contraPropeller);
        relightCluster();
    }

    @Override
    protected void _delete() {
        super._delete();
        propeller.delete();
        contraPropeller.delete();
        deleteCluster();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        super.collectCrumblingInstances(consumer);
        consumer.accept(propeller);
        consumer.accept(contraPropeller);
        if (clusterBase != null) {
            consumer.accept(clusterBase);
            clusterParts.forEach(consumer);
        }
    }
}
