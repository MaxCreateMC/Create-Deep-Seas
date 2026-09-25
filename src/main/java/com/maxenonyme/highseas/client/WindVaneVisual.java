package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.block.entity.WindVaneBlockEntity;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.BlockPos;

import java.util.function.Consumer;

public class WindVaneVisual extends AbstractBlockEntityVisual<WindVaneBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance arrow;

    public WindVaneVisual(VisualizationContext ctx, WindVaneBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        this.arrow = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(AllHighSeasPartialModels.WIND_VANE_ARROW))
                .createInstance();
        applyTransform(WindVaneAngle.update(blockEntity));
        relight(arrow);
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        applyTransform(WindVaneAngle.update(blockEntity));
    }

    private void applyTransform(float angle) {
        BlockPos p = getVisualPosition();
        arrow.setIdentityTransform()
                .translate(p.getX(), p.getY(), p.getZ())
                .rotateYCentered(angle)
                .setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(arrow);
    }

    @Override
    protected void _delete() {
        arrow.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(arrow);
    }
}
