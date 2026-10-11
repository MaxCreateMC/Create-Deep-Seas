package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.entity.renderer.SonarVisual;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.maxenonyme.createsubmarine.submarine.client.VeilLights;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class SonarBlockEntity extends BlockEntity {
    public static final Set<SonarBlockEntity> LOADED_ON_CLIENT = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<SonarBlockEntity> LOADED_ON_SERVER = Collections.newSetFromMap(new WeakHashMap<>());
    public static final int TURN_TICKS = 20;
    public static final int HALF_TURN = TURN_TICKS / 2;
    private static final ResourceLocation PING = ResourceLocation.fromNamespaceAndPath("aeronautics", "fluid.levitite_blend.crystallize");

    private static final float LIGHT_REACH = 0.45f;
    private static final int PING_MIN = 600;
    private static final int PING_SPREAD = 600;

    public boolean visualized;
    private int lastHalf = -1;
    private int pingIn = -1;
    private Object greenLight;
    private Object pinkLight;

    public SonarBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.SONAR_BE.get(), pos, state);
    }

    public void tickClient() {
        if (level instanceof PonderLevel)
            return;
        if (VeilLights.usable()) {
            float angle = SonarVisual.angle(level);
            greenLight = light(greenLight, angle, LIGHT_REACH, 0.2f, 1.0f, 0.55f);
            pinkLight = light(pinkLight, angle, -LIGHT_REACH, 1.0f, 0.45f, 0.75f);
        } else if (greenLight != null || pinkLight != null) {
            freeLights();
        }
        if (pingIn < 0)
            pingIn = PING_MIN + level.random.nextInt(PING_SPREAD);
        int half = AnimationTickHolder.getTicks(level) / HALF_TURN;
        boolean turned = half != lastHalf;
        lastHalf = half;
        if (pingIn > 0)
            pingIn--;
        if (pingIn > 0 || !turned)
            return;
        pingIn = PING_MIN + level.random.nextInt(PING_SPREAD);
        SoundEvent ping = BuiltInRegistries.SOUND_EVENT.get(PING);
        if (ping == null)
            return;
        Vec3 world = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(worldPosition));
        float pitch = 0.9f + level.random.nextFloat() * 0.7f;
        level.playLocalSound(world.x, world.y, world.z, ping, SoundSource.BLOCKS, 0.35f, pitch, false);
    }

    private Object light(Object current, float angle, float reach, float r, float g, float b) {
        BlockState state = getBlockState();
        Vector3f local = new Vector3f(0, reach, 0).rotateX(angle)
                .add(0, SonarVisual.PIVOT_Y - 0.5f, SonarVisual.PIVOT_Z - 0.5f);
        local.rotateX(SonarVisual.tilt(state));
        local.rotateY(SonarVisual.yaw(state));
        Vec3 world = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(worldPosition).add(local.x, local.y, local.z));
        if (current instanceof LightRenderHandle<?> handle && handle.getLightData() instanceof PointLightData light) {
            light.setPosition(world.x, world.y, world.z);
            handle.markDirty();
            return current;
        }
        PointLightData light = new PointLightData();
        light.setPosition(world.x, world.y, world.z);
        light.setBrightness(1.6f);
        light.setColor(r, g, b);
        light.setRadius(4.5f);
        light.setOcclusionEnabled(false);
        return VeilLights.add(light);
    }

    private void freeLights() {
        VeilLights.free(greenLight);
        VeilLights.free(pinkLight);
        greenLight = pinkLight = null;
    }

    private void forget() {
        if (level != null)
            (level.isClientSide ? LOADED_ON_CLIENT : LOADED_ON_SERVER).remove(this);
    }

    public static SonarBlockEntity onSameSub(BlockEntity console) {
        SubLevel sub = Sable.HELPER.getContaining(console);
        if (sub == null)
            return null;
        SonarBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        Set<SonarBlockEntity> loaded = console.getLevel() != null && console.getLevel().isClientSide ? LOADED_ON_CLIENT
                : LOADED_ON_SERVER;
        for (SonarBlockEntity sonar : loaded) {
            if (sonar.isRemoved() || sonar.getLevel() != console.getLevel() || Sable.HELPER.getContaining(sonar) != sub)
                continue;
            double d = sonar.getBlockPos().distSqr(console.getBlockPos());
            if (d < bestDist) {
                bestDist = d;
                best = sonar;
            }
        }
        return best;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null)
            (level.isClientSide ? LOADED_ON_CLIENT : LOADED_ON_SERVER).add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        forget();
        if (level != null && level.isClientSide)
            freeLights();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        forget();
        if (level != null && level.isClientSide)
            freeLights();
    }
}
