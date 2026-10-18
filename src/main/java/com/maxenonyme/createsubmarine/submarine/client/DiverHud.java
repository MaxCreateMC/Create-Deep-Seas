package com.maxenonyme.createsubmarine.submarine.client;

import com.google.gson.JsonSyntaxException;
import com.maxenonyme.AbyssDimension.client.CameraShake;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.network.ImplosionFxPayload;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.io.IOException;

public class DiverHud {
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID,
            "shaders/post/thought_blur.json");
    private static final int THOUGHT_TICKS = 100;

    private static PostChain chain;
    private static boolean broken;
    private static int width = -1;
    private static int height = -1;
    private static Component thought;
    private static int thoughtLeft;
    private static int depth;
    private static int limit;
    private static int depthLeft;

    public static void accept(ImplosionFxPayload payload) {
        if (payload.kind() == ImplosionFxPayload.THOUGHT) {
            thought = Component.translatable("create_submarine.pressure.thought." + payload.ticks())
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            thoughtLeft = THOUGHT_TICKS;
        } else {
            depth = Math.round(payload.strength());
            limit = payload.ticks();
            depthLeft = 15;
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused())
            return;
        if (mc.player != null && mc.player.isDeadOrDying()) {
            thoughtLeft = 0;
            depthLeft = 0;
            CameraShake.stop();
            ImplosionCinematics.dropSqueeze();
            PressureVision.dropSqueeze();
            return;
        }
        if (thoughtLeft > 0)
            thoughtLeft--;
        if (depthLeft > 0)
            depthLeft--;
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null)
            return;
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;

        if (depthLeft > 0) {
            String text = Component.translatable("create_submarine.pressure.depth", depth, limit).getString();
            int color = 0xFFFFFF;
            if (depth > limit)
                color = 0xFF5555;
            else if (depth >= limit * 0.9)
                color = 0xFFAA00;
            graphics.drawString(font, text, graphics.guiWidth() - font.width(text) - 6, graphics.guiHeight() - 14,
                    color, true);
        }

        if (thoughtLeft <= 0 || thought == null)
            return;
        float left = thoughtLeft - event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float fade = Math.min(THOUGHT_TICKS - left, left) / 20f;
        int alpha = Mth.clamp((int) (fade * 255f), 0, 255);
        if (alpha <= 8)
            return;

        int textWidth = font.width(thought);
        int centerX = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 72;
        graphics.flush();
        blur(mc, centerX, y + 4, textWidth / 2f + 8, 9, alpha / 255f);
        graphics.drawString(font, thought, centerX - textWidth / 2, y, 0xFFFFFF | alpha << 24, true);
    }

    private static void blur(Minecraft mc, float centerX, float centerY, float halfWidth, float halfHeight,
            float strength) {
        PostChain effect = chain(mc);
        if (effect == null)
            return;
        double scale = mc.getWindow().getGuiScale();
        float guiHeight = mc.getWindow().getGuiScaledHeight();
        effect.setUniform("CenterX", (float) (centerX * scale));
        effect.setUniform("CenterY", (float) ((guiHeight - centerY) * scale));
        effect.setUniform("HalfWidth", (float) (halfWidth * scale));
        effect.setUniform("HalfHeight", (float) (halfHeight * scale));
        effect.setUniform("Strength", strength);
        effect.process(0f);
        mc.getMainRenderTarget().bindWrite(false);
    }

    private static PostChain chain(Minecraft mc) {
        if (broken)
            return null;
        RenderTarget main = mc.getMainRenderTarget();
        if (chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, CHAIN);
            } catch (IOException | JsonSyntaxException e) {
                CreateSubmarine.LOGGER.warn("Could not load the pressure thought blur", e);
                broken = true;
                return null;
            }
            width = -1;
        }
        if (main.width != width || main.height != height) {
            width = main.width;
            height = main.height;
            chain.resize(width, height);
        }
        return chain;
    }

    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            if (chain != null)
                chain.close();
            chain = null;
            broken = false;
        });
    }
}
