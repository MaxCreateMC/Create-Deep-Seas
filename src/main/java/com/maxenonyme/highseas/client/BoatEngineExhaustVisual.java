package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.block.BoatEngineBlock;
import com.maxenonyme.highseas.block.entity.BoatEngineBlockEntity;
import com.maxenonyme.highseas.helm.HelmClient;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import java.util.function.Consumer;

public class BoatEngineExhaustVisual extends AbstractBlockEntityVisual<BoatEngineBlockEntity> implements SimpleDynamicVisual {

    private static final float PROP_IDLE_SPEED = 0.3f;
    private static final float PROP_MAX_SPEED = 3.5f;
    private static final float MAX_LEVER_RAD = (float) Math.toRadians(20.0);
    private static final float STEER_RESPONSE = 10.0f;

    private final TransformedInstance bodyOn;
    private final TransformedInstance bodyOff;
    private final TransformedInstance exhaust1;
    private final TransformedInstance exhaust2;
    private final TransformedInstance prop;
    private final TransformedInstance lever;
    private final TransformedInstance leverOff;
    private final TransformedInstance leg;
    private final TransformedInstance legOff;
    private final TransformedInstance bodyGlow;
    private final TransformedInstance exhaust1Glow;
    private final TransformedInstance exhaust2Glow;

    private final Quaternionf turn = new Quaternionf();
    private float propAngle;
    private float lastTick;
    private float renderSteer;
    private long lastNanos;

    public BoatEngineExhaustVisual(VisualizationContext ctx, BoatEngineBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        this.bodyOn = instance(AllHighSeasPartialModels.BOAT_ENGINE_BODY);
        this.bodyOff = instance(AllHighSeasPartialModels.BOAT_ENGINE_BODY_OFF);
        this.exhaust1 = instance(AllHighSeasPartialModels.BOAT_ENGINE_EXHAUST_1);
        this.exhaust2 = instance(AllHighSeasPartialModels.BOAT_ENGINE_EXHAUST_2);
        this.prop = instance(AllHighSeasPartialModels.BOAT_ENGINE_PROP);
        this.lever = instance(AllHighSeasPartialModels.BOAT_ENGINE_LEVER);
        this.leverOff = instance(AllHighSeasPartialModels.BOAT_ENGINE_LEVER_OFF);
        this.leg = instance(AllHighSeasPartialModels.BOAT_ENGINE_LEG);
        this.legOff = instance(AllHighSeasPartialModels.BOAT_ENGINE_LEG_OFF);
        this.bodyGlow = emissive(AllHighSeasPartialModels.BOAT_ENGINE_BODY_GLOW);
        this.exhaust1Glow = emissive(AllHighSeasPartialModels.BOAT_ENGINE_EXHAUST_1_GLOW);
        this.exhaust2Glow = emissive(AllHighSeasPartialModels.BOAT_ENGINE_EXHAUST_2_GLOW);
        this.lastTick = tick(partialTick);
        apply(partialTick);
        relight(bodyOn, bodyOff, exhaust1, exhaust2, prop, lever, leverOff, leg, legOff);
    }

    private TransformedInstance emissive(PartialModel model) {
        TransformedInstance inst = instance(model);
        inst.light = LightTexture.FULL_BRIGHT;
        return inst;
    }

    private TransformedInstance instance(PartialModel model) {
        return instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(model)).createInstance();
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        apply(ctx.partialTick());
    }

    private void apply(float partialTick) {
        if (IrisCompat.isShaderPackActive()) {
            hide(bodyOn);
            hide(bodyOff);
            hide(exhaust1);
            hide(exhaust2);
            hide(prop);
            hide(lever);
            hide(leverOff);
            hide(leg);
            hide(legOff);
            hide(bodyGlow);
            hide(exhaust1Glow);
            hide(exhaust2Glow);
            return;
        }

        boolean powered = blockEntity.getBlockState().getValue(BoatEngineBlock.POWERED);
        float now = tick(partialTick);
        float delta = now - lastTick;
        lastTick = now;
        if (delta < 0.0f || delta > 2.0f)
            delta = 0.0f;

        float angle = facingAngle();
        long gameTime = blockEntity.getLevel() == null ? 0L : blockEntity.getLevel().getGameTime();
        float throttle = HelmClient.throttleFor(blockEntity.getBlockPos(), gameTime);

        long nanos = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0f : Math.min((nanos - lastNanos) / 1.0e9f, 0.1f);
        lastNanos = nanos;
        float steerTarget = HelmClient.steerFor(blockEntity.getBlockPos(), gameTime);
        renderSteer += (steerTarget - renderSteer) * (1.0f - (float) Math.exp(-STEER_RESPONSE * dt));
        float leverAngle = MAX_LEVER_RAD * renderSteer;

        if (!powered) {
            placeStatic(bodyOff, angle);
            swingLever(leverOff, angle, leverAngle);
            swivelLeg(legOff, angle, leverAngle);
            hide(bodyOn);
            hide(lever);
            hide(leg);
            hide(exhaust1);
            hide(exhaust2);
            hide(prop);
            hide(bodyGlow);
            hide(exhaust1Glow);
            hide(exhaust2Glow);
            return;
        }

        placeStatic(bodyOn, angle);
        swingLever(lever, angle, leverAngle);
        swivelLeg(leg, angle, leverAngle);
        hide(bodyOff);
        hide(leverOff);
        hide(legOff);

        float scale = 1.05f - 0.05f * Mth.cos(Mth.PI * now / 20.0f);
        pulse(exhaust1, angle, scale, 0.25f, 0.6875f, 1.0625f);
        pulse(exhaust2, angle, scale, 0.75f, 0.6875f, 1.0625f);
        placeStatic(bodyGlow, angle);
        pulse(exhaust1Glow, angle, scale, 0.25f, 0.6875f, 1.0625f);
        pulse(exhaust2Glow, angle, scale, 0.75f, 0.6875f, 1.0625f);

        float speed = PROP_IDLE_SPEED + Math.abs(throttle) * (PROP_MAX_SPEED - PROP_IDLE_SPEED);
        propAngle += speed * delta * (throttle < -0.02f ? -1.0f : 1.0f);
        spinProp(angle, leverAngle, propAngle);
    }

    private void swingLever(TransformedInstance inst, float angle, float lever) {
        BlockPos p = getVisualPosition();
        inst.setIdentityTransform()
                .translate(p.getX(), p.getY(), p.getZ())
                .rotateYCentered(angle)
                .translate(0.5, 1.1875, 0.31875)
                .rotate(turn.rotationY(lever))
                .translate(-0.5, -1.1875, -0.31875)
                .setChanged();
    }

    private void placeStatic(TransformedInstance inst, float angle) {
        BlockPos p = getVisualPosition();
        inst.setIdentityTransform()
                .translate(p.getX(), p.getY(), p.getZ())
                .rotateYCentered(angle)
                .setChanged();
    }

    private float tick(float partialTick) {
        return blockEntity.getLevel() == null ? 0.0f : (blockEntity.getLevel().getGameTime() % 100000L) + partialTick;
    }

    private void hide(TransformedInstance inst) {
        inst.setIdentityTransform().scale(0.0f).setChanged();
    }

    private void pulse(TransformedInstance inst, float angle, float scale, float px, float py, float pz) {
        BlockPos p = getVisualPosition();
        inst.setIdentityTransform()
                .translate(p.getX(), p.getY(), p.getZ())
                .rotateYCentered(angle)
                .translate(px, py, pz)
                .scale(scale)
                .translate(-px, -py, -pz)
                .setChanged();
    }

    private void swivelLeg(TransformedInstance inst, float angle, float swivel) {
        BlockPos p = getVisualPosition();
        inst.setIdentityTransform()
                .translate(p.getX(), p.getY(), p.getZ())
                .rotateYCentered(angle)
                .translate(0.5, 0.0, 0.6875)
                .rotate(turn.rotationY(swivel))
                .translate(-0.5, 0.0, -0.6875)
                .setChanged();
    }

    private void spinProp(float angle, float swivel, float spin) {
        BlockPos p = getVisualPosition();
        prop.setIdentityTransform()
                .translate(p.getX(), p.getY(), p.getZ())
                .rotateYCentered(angle)
                .translate(0.5, 0.0, 0.6875)
                .rotate(turn.rotationY(swivel))
                .translate(-0.5, 0.0, -0.6875)
                .translate(0.5, -0.65625, 0.90625)
                .rotate(turn.rotationZ(spin))
                .translate(-0.5, 0.65625, -0.90625)
                .setChanged();
    }

    private float facingAngle() {
        Direction facing = blockEntity.getBlockState().getValue(BoatEngineBlock.FACING);
        int yaw = switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        return -Mth.DEG_TO_RAD * yaw;
    }

    @Override
    public void updateLight(float partialTick) {
        relight(bodyOn, bodyOff, exhaust1, exhaust2, prop, lever, leverOff, leg, legOff);
    }

    @Override
    protected void _delete() {
        bodyOn.delete();
        bodyOff.delete();
        exhaust1.delete();
        exhaust2.delete();
        prop.delete();
        lever.delete();
        leverOff.delete();
        leg.delete();
        legOff.delete();
        bodyGlow.delete();
        exhaust1Glow.delete();
        exhaust2Glow.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(bodyOn);
        consumer.accept(bodyOff);
        consumer.accept(exhaust1);
        consumer.accept(exhaust2);
        consumer.accept(prop);
        consumer.accept(lever);
        consumer.accept(leverOff);
        consumer.accept(leg);
        consumer.accept(legOff);
        consumer.accept(bodyGlow);
        consumer.accept(exhaust1Glow);
        consumer.accept(exhaust2Glow);
    }
}
