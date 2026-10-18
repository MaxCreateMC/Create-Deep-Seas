package com.maxenonyme.createsubmarine.submarine.block.entity.renderer;

import com.maxenonyme.createsubmarine.submarine.block.SonarBlock;
import com.maxenonyme.createsubmarine.submarine.block.entity.SonarBlockEntity;
import com.maxenonyme.createsubmarine.submarine.client.DeviceGlitch;
import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.joml.Quaternionf;

import java.util.function.Consumer;

public class SonarVisual extends AbstractBlockEntityVisual<SonarBlockEntity> implements SimpleDynamicVisual {
    public static final float PIVOT_Y = 8 / 16f;
    public static final float PIVOT_Z = 7.95f / 16f;
    private static final float SPIN = Mth.TWO_PI / SonarBlockEntity.TURN_TICKS;

    private final TransformedInstance turn;
    private final TransformedInstance glow;
    private final Quaternionf spin = new Quaternionf();

    public SonarVisual(VisualizationContext ctx, SonarBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        turn = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.SONAR_TURN))
                .createInstance();
        glow = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.SONAR_TURN_EMISSIVE))
                .createInstance();
        glow.light = LightTexture.FULL_BRIGHT;
        blockEntity.visualized = true;
        animate(partialTick);
        relight(turn);
    }

    public static float yaw(BlockState state) {
        int degrees = switch (state.getValue(SonarBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        if (state.getValue(SonarBlock.FACE) == AttachFace.CEILING)
            degrees += 180;
        return -Mth.DEG_TO_RAD * degrees;
    }

    public static float tilt(BlockState state) {
        return switch (state.getValue(SonarBlock.FACE)) {
            case CEILING -> Mth.PI;
            case WALL -> -Mth.HALF_PI;
            default -> 0;
        };
    }

    public static float angle(Level level) {
        return (AnimationTickHolder.getRenderTime(level) % SonarBlockEntity.TURN_TICKS) * SPIN
                + DeviceGlitch.spinDegrees(AnimationTickHolder.getPartialTicks()) * 2f * Mth.DEG_TO_RAD;
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        animate(ctx.partialTick());
    }

    private void animate(float partialTick) {
        place(turn, partialTick);
        place(glow, partialTick);
    }

    private void place(TransformedInstance instance, float partialTick) {
        BlockPos pos = getVisualPosition();
        BlockState state = blockEntity.getBlockState();
        instance.setIdentityTransform()
                .translate(pos.getX(), pos.getY(), pos.getZ())
                .rotateYCentered(yaw(state))
                .rotateXCentered(tilt(state))
                .translate(0, PIVOT_Y, PIVOT_Z)
                .rotate(spin.rotationX(angle(blockEntity.getLevel())))
                .translate(0, -PIVOT_Y, -PIVOT_Z)
                .setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(turn);
    }

    @Override
    protected void _delete() {
        blockEntity.visualized = false;
        turn.delete();
        glow.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(turn);
    }
}
