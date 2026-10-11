package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.mixin.ChannelAccessor;
import com.maxenonyme.createsubmarine.submarine.mixin.SoundEngineAccessor;
import com.maxenonyme.createsubmarine.submarine.mixin.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

import java.util.Map;

public class EarPressure {
    private static volatile int filter = -1;
    private static volatile boolean unsupported;
    private static boolean engaged;

    public static void apply(Minecraft mc, float muffle) {
        if (unsupported)
            return;
        boolean off = muffle < 0.01f;
        if (off && !engaged)
            return;
        engaged = !off;
        float gain = 1f - 0.25f * muffle;
        float gainHf = 1f - 0.93f * muffle;
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
                int lowpass = lowpass();
                if (lowpass <= 0)
                    return;
                EXTEfx.alFilterf(lowpass, EXTEfx.AL_LOWPASS_GAIN, gain);
                EXTEfx.alFilterf(lowpass, EXTEfx.AL_LOWPASS_GAINHF, gainHf);
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, lowpass);
            });
        }
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
