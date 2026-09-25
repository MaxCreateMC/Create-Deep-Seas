package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.block.BoatEngineBlock;
import com.maxenonyme.highseas.block.entity.BoatEngineBlockEntity;
import com.maxenonyme.highseas.helm.HelmClient;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public class BoatEngineSoundInstance extends AbstractTickableSoundInstance {
    private final BoatEngineBlockEntity blockEntity;
    private final boolean running;

    public BoatEngineSoundInstance(BoatEngineBlockEntity blockEntity, SoundEvent event, boolean running) {
        super(event, SoundSource.BLOCKS, RandomSource.create());
        this.blockEntity = blockEntity;
        this.running = running;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.pitch = running ? 0.55f : 0.65f;
        updatePosition();
    }

    private void updatePosition() {
        Vec3 p = BoatEngineSoundHandler.worldPos(blockEntity);
        this.x = p.x;
        this.y = p.y;
        this.z = p.z;
    }

    @Override
    public void tick() {
        if (blockEntity.isRemoved() || !blockEntity.getBlockState().getValue(BoatEngineBlock.POWERED)) {
            stop();
            return;
        }
        updatePosition();

        float raw = 0.0f;
        if (blockEntity.getLevel() != null)
            raw = HelmClient.throttleFor(blockEntity.getBlockPos(), blockEntity.getLevel().getGameTime());
        float throttle = Mth.clamp(Math.abs(raw), 0.0f, 1.0f);

        float targetVolume;
        float targetPitch;
        if (running) {
            targetVolume = throttle;
            targetPitch = 0.55f + throttle * 0.65f;
        } else {
            targetVolume = (1.0f - throttle) * 0.8f;
            targetPitch = 0.65f + throttle * 0.15f;
        }
        if (raw < 0.0f)
            targetPitch -= 0.08f;

        this.volume = Mth.lerp(0.1f, this.volume, targetVolume);
        this.pitch = Mth.lerp(0.1f, this.pitch, targetPitch);
    }
}
