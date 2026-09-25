package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.item.SeaglideDrainPayload;
import com.maxenonyme.highseas.item.SeaglideImpactPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

import java.util.Set;
import com.maxenonyme.highseas.config.HighSeasConfig;

public final class SeaglideClientHandler {
    private SeaglideClientHandler() {
    }

    private static final float MAX_SPEED = 42.0f;
    private static final float SPIN_UP = 0.055f;
    private static final float SPIN_DOWN = 0.022f;
    private static final float WRAP = 360000.0f;

    private static final float ACTIVE_SPEED = 4.0f;
    private static final double LEAP_RISE = 0.10;
    private static final double LEAP_BOOST = 1.05;
    private static final double LEAP_MIN_UP = 0.55;
    private static final float LEAP_POWER = 0.5f;
    private static final int LEAP_TICKS = 80;
    private static final double LEAP_GRAVITY = 0.08;
    private static final double LEAP_HANG = 0.30;
    private static final double LEAP_FALL = 0.45;
    private static final double BOUND_BOOST = 0.70;
    private static final double BOUND_MIN_UP = 0.50;
    private static final int LEAP_COOLDOWN = 10;
    private static final double ENTRY_BLEED = 0.88;
    private static final double ARC_REFERENCE = 0.60;
    private static final float ARC_SMOOTH = 0.16f;
    private static final float BLEND_SMOOTH = 0.13f;
    private static final int WATER_SCAN = 9;
    private static final float RUSH_START = 0.70f;
    private static final float RUSH_SMOOTH = 0.10f;
    private static final int DRAIN_INTERVAL = 20;
    private static final long NOZZLE_STALE = 2L;
    private static final Set<String> MUTED_STROKES = Set.of("entity.player.swim", "entity.player.splash.high_speed");

    private static Vec3 nozzle;
    private static long nozzleTick = Long.MIN_VALUE;
    private static boolean wasWet;
    private static int leap;
    private static int cooldown;
    private static boolean wasGround;
    private static boolean locked;
    private static boolean posed;
    private static int drainTicks;
    private static float rush;
    private static float arc;
    private static float blend;
    private static SeaglideSoundInstance[] voices;
    private static int voiceTicks;

    private static float speed;
    private static float angle;
    private static float prevAngle;

    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            locked = false;
            posed = false;
            return;
        }
        boolean streamlined = isHolding(mc.player) && (leap > 0 || (isSpinning(mc.player) && mc.player.isUnderWater()));
        if (streamlined) {
            mc.player.setForcedPose(Pose.SWIMMING);
            posed = true;
        } else if (posed) {
            mc.player.setForcedPose(null);
            posed = false;
        }

        boolean lock = mc.screen == null && isSpinning(mc.player) && mc.player.isInWater();
        if (lock) {
            mc.options.keyUp.setDown(true);
            mc.options.keySprint.setDown(true);
            mc.options.keyDown.setDown(false);
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
            mc.options.keyJump.setDown(false);
            mc.player.setSprinting(true);
        } else if (locked) {
            for (KeyMapping mapping : strokes(mc)) {
                mapping.setDown(rawPressed(mapping));
            }
        }
        locked = lock;
    }

    private static KeyMapping[] strokes(Minecraft mc) {
        return new KeyMapping[] { mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight,
                mc.options.keyJump, mc.options.keySprint };
    }

    private static boolean rawPressed(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        int value = key.getValue();
        if (value == InputConstants.UNKNOWN.getValue()) {
            return false;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, value) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, value);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        prevAngle = angle;

        boolean active = mc.player != null && mc.level != null && mc.screen == null
                && isHolding(mc.player) && mc.options.keyAttack.isDown()
                && mc.player.getFoodData().getFoodLevel() > 0;

        if (active) {
            drainTicks++;
            if (drainTicks >= DRAIN_INTERVAL) {
                PacketDistributor.sendToServer(new SeaglideDrainPayload((byte) drainTicks));
                drainTicks = 0;
            }
        } else {
            drainTicks = 0;
        }

        float target = active ? MAX_SPEED : 0.0f;
        speed += (target - speed) * (target > speed ? SPIN_UP : SPIN_DOWN);
        if (speed < 0.01f) {
            speed = 0.0f;
        }

        angle += speed;
        if (angle > WRAP) {
            angle -= WRAP;
            prevAngle -= WRAP;
        }

        if (mc.player == null || mc.level == null) {
            return;
        }

        if (!isHolding(mc.player)) {
            speed = 0.0f;
        }

        if (speed > ACTIVE_SPEED) {
            drive(mc.player);
            reverb(mc);
        } else {
            voices = null;
            voiceTicks = 0;
        }

        float rushTarget = 0.0f;
        if (speed > ACTIVE_SPEED && isHolding(mc.player) && mc.player.isUnderWater()) {
            float cruise = (float) Mth.clamp(mc.player.getDeltaMovement().length() / HighSeasConfig.seaglideMaxSwim, 0.0, 1.0);
            rushTarget = Mth.clamp((cruise - RUSH_START) / (1.0f - RUSH_START), 0.0f, 1.0f);
        }
        rush = Mth.lerp(RUSH_SMOOTH, rush, rushTarget);

        if (isHolding(mc.player)) {
            breach(mc.player, speed / MAX_SPEED, mc.player.isInWater());
        } else {
            wasWet = false;
            leap = 0;
            cooldown = 0;
            wasGround = false;
            arc = 0.0f;
            blend = 0.0f;
        }
    }

    private static void reverb(Minecraft mc) {
        if (voices == null || voices[0] == null || voices[0].isStopped()) {
            voices = new SeaglideSoundInstance[SeaglideSoundInstance.VOICE_START.length];
            voiceTicks = 0;
        }
        for (int i = 0; i < voices.length; i++) {
            if (voices[i] == null && voiceTicks >= SeaglideSoundInstance.VOICE_START[i]) {
                voices[i] = new SeaglideSoundInstance(mc.player, i);
                mc.getSoundManager().play(voices[i]);
            }
        }
        voiceTicks++;
    }

    private static void drive(LocalPlayer player) {
        float power = speed / MAX_SPEED;
        Vec3 look = player.getLookAngle();
        boolean wet = player.isInWater();

        if (wet) {
            Vec3 next = player.getDeltaMovement().add(look.scale(HighSeasConfig.seaglideThrust * power));
            double len = next.length();
            if (len > HighSeasConfig.seaglideMaxSwim) {
                next = next.scale(Math.max(HighSeasConfig.seaglideMaxSwim, len * ENTRY_BLEED) / len);
            }
            player.setDeltaMovement(next);
        }

        Vec3 at = nozzle(player);
        if (at != null) {
            wash(player, at, look, power, wet);
        }
    }

    private static void breach(LocalPlayer player, float power, boolean wet) {
        if (cooldown > 0) {
            cooldown--;
        }

        if (leap > 0) {
            if (wet) {
                leap = 0;
            } else if (player.onGround()) {
                leap = 0;
                PacketDistributor.sendToServer(SeaglideImpactPayload.INSTANCE);
            } else {
                leap--;

                Vec3 v = player.getDeltaMovement();
                if (v.y < 0.0) {
                    player.setDeltaMovement(v.x, Math.max(v.y + LEAP_GRAVITY * LEAP_HANG, -LEAP_FALL), v.z);
                }
            }
        }

        if (leap == 0 && cooldown == 0) {
            if (power > LEAP_POWER && wasWet && !wet && player.getDeltaMovement().y > LEAP_RISE) {
                launch(player, LEAP_BOOST, LEAP_MIN_UP, power);
            } else if (!wet && wasGround && !player.onGround() && player.isSprinting()
                    && player.getDeltaMovement().y > 0.0 && waterAhead(player)) {
                launch(player, BOUND_BOOST, BOUND_MIN_UP, 1.0f);
            }
        }

        float target = leap > 0 ? (float) Mth.clamp(player.getDeltaMovement().y / ARC_REFERENCE, -1.0, 1.0) : 0.0f;
        arc = Mth.lerp(ARC_SMOOTH, arc, target);
        blend = Mth.lerp(BLEND_SMOOTH, blend, leap > 0 ? 1.0f : 0.0f);

        wasGround = player.onGround();
        wasWet = wet;
    }

    public static float rush() {
        return HighSeasConfig.seaglideScreenEffects ? rush : 0.0f;
    }

    public static float arcPhase() {
        return arc;
    }

    public static float arcBlend() {
        return blend;
    }

    private static boolean waterAhead(LocalPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            return false;
        }
        flat = flat.normalize();

        Vec3 from = player.position();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int step = 2; step <= WATER_SCAN; step++) {
            Vec3 at = from.add(flat.scale(step));
            for (int dy = 1; dy >= -3; dy--) {
                probe.set(Mth.floor(at.x), Mth.floor(at.y) + dy, Mth.floor(at.z));
                if (player.level().getFluidState(probe).is(FluidTags.WATER)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void launch(LocalPlayer player, double boost, double rise, float power) {
        leap = LEAP_TICKS;
        cooldown = LEAP_COOLDOWN;
        Vec3 v = player.getDeltaMovement().add(player.getLookAngle().scale(boost * power));
        player.setDeltaMovement(v.x, Math.max(v.y, rise * power), v.z);
    }

    public static boolean isLeaping(Player player) {
        return leap > 0 && player == Minecraft.getInstance().player;
    }

    public static void captureNozzle(Vector3f cameraRelative) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 at = new Vec3(cam.x + cameraRelative.x, cam.y + cameraRelative.y, cam.z + cameraRelative.z);
        if (at.distanceToSqr(mc.player.getEyePosition()) > 4.0) {
            return;
        }
        nozzle = at;
        nozzleTick = mc.level.getGameTime();
    }

    private static Vec3 nozzle(LocalPlayer player) {
        if (nozzle == null || player.level().getGameTime() - nozzleTick > NOZZLE_STALE) {
            return null;
        }
        return nozzle;
    }

    private static void wash(LocalPlayer player, Vec3 at, Vec3 look, float power, boolean water) {
        int count = 1 + (int) (power * 3.0f);
        for (int i = 0; i < count; i++) {
            double spread = 0.10;
            double x = at.x + (player.getRandom().nextDouble() - 0.5) * spread;
            double y = at.y + (player.getRandom().nextDouble() - 0.5) * spread;
            double z = at.z + (player.getRandom().nextDouble() - 0.5) * spread;
            Vec3 drift = look.scale(water ? -0.12 * power : -0.05 * power);
            player.level().addParticle(water ? ParticleTypes.BUBBLE : ParticleTypes.WHITE_SMOKE,
                    x, y, z, drift.x, drift.y + (water ? 0.02 : 0.01), drift.z);
        }
    }

    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound.getSource() != SoundSource.PLAYERS
                || !MUTED_STROKES.contains(sound.getLocation().getPath())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !isSpinning(mc.player)) {
            return;
        }
        double dx = sound.getX() - mc.player.getX();
        double dy = sound.getY() - mc.player.getY();
        double dz = sound.getZ() - mc.player.getZ();
        if (dx * dx + dy * dy + dz * dz > 4.0) {
            return;
        }
        event.setSound(null);
    }

    public static void onAttackInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && isHolding(mc.player)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    public static float angle(float partialTicks) {
        return Mth.lerp(partialTicks, prevAngle, angle);
    }

    public static float speed() {
        return speed;
    }

    public static float power() {
        return speed / MAX_SPEED;
    }

    public static Vec3 emitter(LocalPlayer player) {
        Vec3 at = nozzle(player);
        return at != null ? at : player.getEyePosition();
    }

    public static boolean isActive() {
        return speed > ACTIVE_SPEED;
    }

    public static boolean isSpinning(Player player) {
        return player == Minecraft.getInstance().player && speed > ACTIVE_SPEED && isHolding(player);
    }

    public static boolean isHolding(Player player) {
        return player.getMainHandItem().is(CreateHighSeas.SEAGLIDE.get())
                || player.getOffhandItem().is(CreateHighSeas.SEAGLIDE.get());
    }
}
