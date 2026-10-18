package com.maxenonyme.createsubmarine.submarine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class PanicDrone extends AbstractTickableSoundInstance {
    private int quiet;

    public PanicDrone() {
        super(SoundEvents.AMBIENT_BASALT_DELTAS_LOOP.value(), SoundSource.MASTER, RandomSource.create());
        looping = true;
        delay = 0;
        relative = true;
        attenuation = SoundInstance.Attenuation.NONE;
        volume = 0.01f;
        pitch = 0.5f;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isDeadOrDying()) {
            stop();
            return;
        }
        float target = PressureVision.droneVolume();
        volume += (Math.max(0.01f, target) - volume) * 0.04f;
        pitch = 0.5f + 0.12f * target;
        quiet = target <= 0.01f ? quiet + 1 : 0;
        if (quiet > 60)
            stop();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
