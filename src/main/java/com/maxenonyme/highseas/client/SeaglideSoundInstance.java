package com.maxenonyme.highseas.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public class SeaglideSoundInstance extends AbstractTickableSoundInstance {

    private static final ResourceLocation LOOP = ResourceLocation
            .fromNamespaceAndPath("aeronautics", "block.propeller_bearing.large_loop");

    public static final int[] VOICE_START = { 0, 3, 7, 12 };
    private static final float[] VOICE_GAIN = { 1.0f, 0.45f, 0.26f, 0.14f };
    private static final float[] VOICE_DETUNE = { 1.0f, 0.988f, 0.974f, 0.96f };
    private static final double[] VOICE_SPREAD = { 0.0, 0.7, -1.1, 1.5 };
    private static final double VOICE_BEHIND = -0.4;

    private static final float PITCH_IDLE = 0.68f;
    private static final float PITCH_MAX = 1.55f;
    private static final float WET_PITCH = 0.80f;
    private static final float WET_VOLUME = 0.90f;
    private static final float TAIL_DRY = 0.35f;
    private static final float TAIL_WET = 1.0f;
    private static final float VOLUME_MAX = 1.0f;
    private static final float FOLLOW = 0.15f;
    private static final float SOAK = 0.10f;

    private final LocalPlayer player;
    private final int voice;
    private float wet;

    public SeaglideSoundInstance(LocalPlayer player, int voice) {
        super(loop(), SoundSource.PLAYERS, RandomSource.create());
        this.player = player;
        this.voice = voice;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.pitch = PITCH_IDLE;
        this.wet = player.isUnderWater() ? 1.0f : 0.0f;
        follow();
    }

    private static SoundEvent loop() {
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(LOOP);
        return event != null ? event : SoundEvent.createVariableRangeEvent(LOOP);
    }

    private void follow() {
        Vec3 at = SeaglideClientHandler.emitter(player);
        if (voice > 0) {
            Vec3 look = player.getLookAngle();
            Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
            if (side.lengthSqr() > 1.0E-4) {
                at = at.add(side.normalize().scale(VOICE_SPREAD[voice])).add(look.scale(VOICE_BEHIND));
            }
        }
        this.x = at.x;
        this.y = at.y;
        this.z = at.z;
    }

    @Override
    public void tick() {
        if (player.isRemoved() || !SeaglideClientHandler.isSpinning(player)) {
            stop();
            return;
        }
        follow();
        wet = Mth.lerp(SOAK, wet, player.isUnderWater() ? 1.0f : 0.0f);

        float power = SeaglideClientHandler.power();
        float tail = voice == 0 ? 1.0f : VOICE_GAIN[voice] * Mth.lerp(wet, TAIL_DRY, TAIL_WET);

        float targetPitch = Mth.lerp(power, PITCH_IDLE, PITCH_MAX)
                * Mth.lerp(wet, 1.0f, WET_PITCH) * VOICE_DETUNE[voice];
        float targetVolume = power * VOLUME_MAX * Mth.lerp(wet, 1.0f, WET_VOLUME) * tail;

        this.pitch = Mth.lerp(FOLLOW, this.pitch, targetPitch);
        this.volume = Mth.lerp(FOLLOW, this.volume, targetVolume);
    }
}
