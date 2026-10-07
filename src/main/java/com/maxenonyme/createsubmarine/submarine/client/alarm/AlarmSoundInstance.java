package com.maxenonyme.createsubmarine.submarine.client.alarm;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class AlarmSoundInstance extends AbstractSoundInstance {

    private final byte[] data;

    public AlarmSoundInstance(ResourceLocation id, byte[] data, double x, double y, double z, float volume) {
        super(id, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.data = data;
        this.x = x;
        this.y = y;
        this.z = z;
        this.volume = volume;
        this.pitch = 1.0f;
        this.attenuation = Attenuation.LINEAR;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        this.sound = new Sound(location, ConstantFloat.of(1.0f), ConstantFloat.of(1.0f), 1, Sound.Type.FILE, true, false, 16);
        return new WeighedSoundEvents(location, null);
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
        try {
            return CompletableFuture.completedFuture(new JOrbisAudioStream(new ByteArrayInputStream(data)));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
