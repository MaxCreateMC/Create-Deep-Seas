package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.mixin.ChannelAccessor;
import com.maxenonyme.createsubmarine.submarine.mixin.SoundEngineAccessor;
import com.maxenonyme.createsubmarine.submarine.mixin.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.client.event.sound.SoundEvent;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

import java.util.Map;

public class EarPressure {
    private static volatile int filter = -1;
    private static volatile boolean unsupported;
    private static volatile boolean engaged;

    public static void apply(Minecraft mc, float muffle) {
        if (unsupported)
            return;
        boolean off = muffle < 0.01f;
        if (off && !engaged)
            return;
        engaged = !off;
        int lowpass = off ? 0 : lowpass();
        if (lowpass > 0) {
            EXTEfx.alFilterf(lowpass, EXTEfx.AL_LOWPASS_GAIN, 1f - 0.25f * muffle);
            EXTEfx.alFilterf(lowpass, EXTEfx.AL_LOWPASS_GAINHF, 1f - 0.93f * muffle);
        }
        Map<SoundInstance, ChannelAccess.ChannelHandle> channels =
                ((SoundEngineAccessor) ((SoundManagerAccessor) mc.getSoundManager()).createsubmarine$engine()).createsubmarine$channels();
        for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : channels.entrySet()) {
            boolean clear = off || entry.getKey().getSource() == SoundSource.MASTER;
            entry.getValue().execute(channel -> {
                int source = ((ChannelAccessor) channel).createsubmarine$source();
                if (clear) {
                    if (filter > 0)
                        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                    return;
                }
                if (lowpass > 0)
                    AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, lowpass);
            });
        }
    }

    public static void onSoundStart(PlaySoundSourceEvent event) {
        muffleOnStart(event);
    }

    public static void onStreamStart(PlayStreamingSourceEvent event) {
        muffleOnStart(event);
    }

    private static void muffleOnStart(SoundEvent.SoundSourceEvent event) {
        if (!engaged || filter <= 0 || event.getSound().getSource() == SoundSource.MASTER)
            return;
        int source = ((ChannelAccessor) event.getChannel()).createsubmarine$source();
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
    }

    private static int lowpass() {
        if (filter != -1)
            return filter;
        try {
            int made = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(made, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                unsupported = true;
                filter = 0;
            } else {
                filter = made;
            }
        } catch (Throwable e) {
            CreateSubmarine.LOGGER.warn("OpenAL low-pass filter unavailable, pressure muffling disabled", e);
            unsupported = true;
            filter = 0;
        }
        return filter;
    }
}
