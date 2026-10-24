package com.maxenonyme.theabyss.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import java.util.List;

public class PDAManager {

    private enum State {
        HIDDEN,
        SLIDE_IN,
        TYPING,
        WAITING,
        SLIDE_OUT
    }

    private static final ResourceLocation PDA_TEXTURE = ResourceLocation.fromNamespaceAndPath("create_abyss",
            "textures/gui/pda.png");
    private static final int BOX_WIDTH = 240;
    private static final int BOX_HEIGHT = 60;
    private static final int TRANSITION_TICKS = 15;

    private static State state = State.HIDDEN;
    private static String activeText = "";
    private static int typedCharsCount = 0;
    private static int slideTicks = 0;
    private static int ticksSinceFinished = 0;
    private static int waitDurationTicks = 60;
    private static SimpleSoundInstance activeSoundInstance = null;
    private static long transitionStartTime = 0L;
    private static long typingStartTime = 0L;
    private static int typingDurationMs = 2000;

    private static int sceneDelayTicks = -1;
    private static String pendingText = "";
    private static ResourceLocation pendingSound = null;
    private static int pendingAudioDurationMs = 0;
    private static int pendingOverrideWaitTicks = 0;

    private static boolean isFlickering = false;
    private static boolean currentLightsOn = true;
    private static int flickerTickCounter = 0;
    private static int nextFlickerToggle = 0;
    private static int roarDelayTicks = -1;

    public static boolean isFlickering() {
        return isFlickering;
    }

    public static boolean isLightsOn() {
        return currentLightsOn;
    }

    public static void queuePDACommand(String text, ResourceLocation sound, int audioDurationMs,
            int overrideWaitTicks) {
        if (text == null) {
            text = "";
        }
        pendingText = text;
        pendingSound = sound;
        pendingAudioDurationMs = audioDurationMs;
        pendingOverrideWaitTicks = overrideWaitTicks;
        sceneDelayTicks = 100;
    }

    public static void startDialogue(String text, ResourceLocation sound, int audioDurationMs, int overrideWaitTicks) {
        if (text == null) {
            text = "";
        }
        activeText = text;
        typedCharsCount = 0;
        ticksSinceFinished = 0;
        waitDurationTicks = overrideWaitTicks > 0 ? overrideWaitTicks : 60;
        state = State.SLIDE_IN;
        transitionStartTime = System.currentTimeMillis();
        typingStartTime = 0L;
        typingDurationMs = audioDurationMs > 0 ? audioDurationMs : (text.length() * 50);

        if (activeSoundInstance != null) {
            Minecraft.getInstance().getSoundManager().stop(activeSoundInstance);
            activeSoundInstance = null;
        }

        if (sound != null) {
            activeSoundInstance = new SimpleSoundInstance(
                    sound,
                    SoundSource.MASTER,
                    8.0F,
                    1.0F,
                    RandomSource.create(),
                    false,
                    0,
                    SoundInstance.Attenuation.NONE,
                    0.0D,
                    0.0D,
                    0.0D,
                    true);
            Minecraft.getInstance().getSoundManager().play(activeSoundInstance);
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            state = State.HIDDEN;
            activeSoundInstance = null;
            sceneDelayTicks = -1;
            stopFlickering();
            return;
        }

        if (sceneDelayTicks > 0) {
            sceneDelayTicks--;
            if (sceneDelayTicks == 0) {
                startDialogue(pendingText, pendingSound, pendingAudioDurationMs, pendingOverrideWaitTicks);
                startFlickering();
            }
        }

        if (isFlickering) {
            tickFlicker(mc);
        }

        long now = System.currentTimeMillis();
        long elapsed = now - transitionStartTime;

        if (state == State.SLIDE_IN) {
            if (elapsed >= 500) {
                state = State.TYPING;
                typingStartTime = now;
            }
        } else if (state == State.TYPING) {
            long typingElapsed = now - typingStartTime;
            float typingProgress = Math.min((float) typingElapsed / typingDurationMs, 1.0F);
            typedCharsCount = (int) (typingProgress * activeText.length());
            if (typedCharsCount >= activeText.length()) {
                typedCharsCount = activeText.length();
                state = State.WAITING;
            }
        } else if (state == State.WAITING) {
            boolean isPlaying = activeSoundInstance != null && mc.getSoundManager().isActive(activeSoundInstance);
            if (!isPlaying) {
                if (isFlickering) {
                    stopFlickering();
                }
                ticksSinceFinished++;
                if (ticksSinceFinished >= waitDurationTicks) {
                    state = State.SLIDE_OUT;
                    transitionStartTime = now;
                }
            }
        } else if (state == State.SLIDE_OUT) {
            if (elapsed >= 500) {
                state = State.HIDDEN;
            }
        }
    }

    private static void startFlickering() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return;

        isFlickering = true;
        flickerTickCounter = 0;
        currentLightsOn = false;
        nextFlickerToggle = 2 + mc.level.random.nextInt(4);
        CameraShake.shake(2.8f, 99999);
        roarDelayTicks = 20;
    }

    private static void tickFlicker(Minecraft mc) {
        if (!isFlickering) {
            currentLightsOn = true;
            return;
        }
        flickerTickCounter++;

        if (roarDelayTicks > 0) {
            roarDelayTicks--;
            if (roarDelayTicks == 0) {
                mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        SoundEvent.createVariableRangeEvent(
                                ResourceLocation.fromNamespaceAndPath("create_abyss", "reaper_leviathan_roars")),
                        SoundSource.HOSTILE, 0.5F, 1.0F, false);
            }
        }

        if (flickerTickCounter >= nextFlickerToggle) {
            currentLightsOn = !currentLightsOn;
            int delay = currentLightsOn ? (3 + mc.level.random.nextInt(12)) : (2 + mc.level.random.nextInt(5));
            nextFlickerToggle = flickerTickCounter + delay;
        }
    }

    private static void stopFlickering() {
        isFlickering = false;
        currentLightsOn = true;
        CameraShake.stop();
    }

    public static void renderOverlay(GuiGraphics guiGraphics, float partialTick) {
        if (state == State.HIDDEN)
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui)
            return;

        long now = System.currentTimeMillis();
        long elapsed = now - transitionStartTime;
        float progress = 0.0F;

        if (state == State.SLIDE_IN) {
            progress = easeOutCubic(Math.min((float) elapsed / 500.0F, 1.0F));
        } else if (state == State.SLIDE_OUT) {
            progress = easeOutCubic(Math.max(1.0F - (float) elapsed / 500.0F, 0.0F));
        } else {
            progress = 1.0F;
        }

        int x = (guiGraphics.guiWidth() - BOX_WIDTH) / 2;
        int startY = -BOX_HEIGHT - 10;
        int targetY = 15;
        int y = (int) (startY + (targetY - startY) * progress);

        guiGraphics.blit(PDA_TEXTURE, x, y, 0, 0, BOX_WIDTH, BOX_HEIGHT, BOX_WIDTH, BOX_HEIGHT);

        String currentString = activeText.substring(0, Math.min(typedCharsCount, activeText.length()));

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x + 20, y + 25, 0.0F);
        guiGraphics.pose().scale(0.80F, 0.80F, 1.0F);

        int wrapWidth = (int) (200 / 0.80F);
        List<FormattedCharSequence> lines = mc.font.split(Component.literal(currentString), wrapWidth);

        int lineY = 0;
        for (FormattedCharSequence line : lines) {
            guiGraphics.drawString(mc.font, line, 0, lineY, 0xFF491E1E, false);
            lineY += mc.font.lineHeight + 1;
        }

        guiGraphics.pose().popPose();
    }

    private static float easeOutCubic(float t) {
        return 1.0F - (float) Math.pow(1.0F - t, 3);
    }

    public static class ModEvents {
        @SubscribeEvent
        public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(
                    VanillaGuiLayers.HOTBAR,
                    ResourceLocation.fromNamespaceAndPath("create_abyss", "pda"),
                    (guiGraphics, partialTick) -> PDAManager.renderOverlay(guiGraphics,
                            partialTick.getGameTimeDeltaTicks()));
        }
    }

    public static class GameEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            PDAManager.tick();
        }

        @SubscribeEvent
        public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(
                    Commands.literal("pda")
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        String message = StringArgumentType.getString(context, "message");
                                        PDAManager.queuePDACommand(message, ResourceLocation.fromNamespaceAndPath(
                                                "create_abyss", "leviathan_class_detected"), 6800, 100);
                                        return 1;
                                    }))
                            .executes(context -> {
                                PDAManager.queuePDACommand(
                                        "Detecting multiple leviathan class organisms in the region. Are you certain whatever you're doing is worth it?",
                                        ResourceLocation.fromNamespaceAndPath("create_abyss",
                                                "leviathan_class_detected"),
                                        6800, 100);
                                return 1;
                            }));
        }
    }
}
