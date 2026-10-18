package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.vortexengine.light.PointLight;
import com.maxenonyme.vortexengine.light.VortexLights;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public class SubmarineStaffClientHandler {
    private static boolean wasHolding = false;
    private static int ambientCooldown = 0;

    private static PointLight conduitLight;

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
            freeLights();
        }

        if (holding) {
            updateLights(player, mc);

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
        freeLights();
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

    private static void updateLights(Player player, Minecraft mc) {
        if (!VortexLights.usable()) {
            freeLights();
            return;
        }

        Vec3 look = player.getLookAngle();

        float f = player.level().getGameTime() + mc.getTimer().getGameTimeDeltaTicks();
        float bob = Mth.sin(f * 0.1F) / 2.0F + 0.5F;
        bob = bob * bob + bob;
        double bobOffset = 0.1 * (bob - 1.0);

        double conduitX = player.getX() + look.x * 0.5;
        double conduitY = player.getEyeY() - 0.2 + look.y * 0.5 + bobOffset;
        double conduitZ = player.getZ() + look.z * 0.5;
        if (conduitLight == null)
            conduitLight = new PointLight().setColor(0.3f, 0.6f, 1.0f).setIntensity(0.8f).setRadius(5.0f);

        VortexLights.add(conduitLight).setPosition(conduitX, conduitY, conduitZ);
    }

    private static void freeLights() {
        if (conduitLight != null) {
            VortexLights.remove(conduitLight);
            conduitLight = null;
        }
    }
}
