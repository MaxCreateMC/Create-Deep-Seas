package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public class SubmarineStaffClientHandler {
    private static boolean wasHolding = false;
    private static int ambientCooldown = 0;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;

        if (player == null || level == null) {
            reset();
            return;
        }

        boolean holding = isHoldingStaff(player);

        if (holding && !wasHolding) {
            playSound(player, level, SoundEvents.CONDUIT_ACTIVATE, 0.7f);
            ambientCooldown = 30;
        } else if (!holding && wasHolding) {
            playSound(player, level, SoundEvents.CONDUIT_DEACTIVATE, 0.7f);
        }

        if (holding) {
            if (ambientCooldown <= 0) {
                boolean shortClip = level.random.nextBoolean();
                playSound(player, level, shortClip ? SoundEvents.CONDUIT_AMBIENT_SHORT : SoundEvents.CONDUIT_AMBIENT,
                        0.5f);
                ambientCooldown = (shortClip ? 45 : 80) + level.random.nextInt(40);
            } else {
                ambientCooldown--;
            }
        }

        wasHolding = holding;
    }

    public static void reset() {
        wasHolding = false;
        ambientCooldown = 0;
    }

    private static boolean isHoldingStaff(Player player) {
        return player.getMainHandItem().is(CreateSubmarine.SUBMARINE_STAFF.get())
                || player.getOffhandItem().is(CreateSubmarine.SUBMARINE_STAFF.get());
    }

    private static void playSound(Player player, Level level, SoundEvent sound, float volume) {
        level.playLocalSound(player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, 1.0f,
                false);
    }
}
