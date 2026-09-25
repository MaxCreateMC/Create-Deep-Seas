package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.helm.HelmClient;
import com.maxenonyme.highseas.helm.HelmSeatEntity;
import com.maxenonyme.highseas.helm.ThrottleCapPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class EngineThrottleHandler extends BlockHoldInteraction {

    private static final ResourceLocation TEXTURE = ResourceLocation
            .fromNamespaceAndPath(CreateHighSeas.MOD_ID, "textures/gui/engine_throttle.png");
    private static final int SHEET = 256;
    private static final int TEXT_COLOR = 0xFF442000;

    private static final int[] FRAME_TL = { 65, 9, 4, 4 };
    private static final int[] FRAME_TR = { 70, 9, 4, 4 };
    private static final int[] FRAME_BL = { 65, 19, 4, 4 };
    private static final int[] FRAME_BR = { 70, 19, 4, 4 };
    private static final int[] FRAME_LEFT = { 65, 14, 3, 4 };
    private static final int[] FRAME_RIGHT = { 71, 14, 3, 4 };
    private static final int[] FRAME_TOP = { 0, 24, 256, 3 };
    private static final int[] FRAME_BOTTOM = { 0, 27, 256, 3 };
    private static final int[] BAR = { 7, 0, 249, 8 };
    private static final int[] CURSOR_LEFT = { 0, 9, 3, 14 };
    private static final int[] CURSOR = { 4, 9, 56, 14 };
    private static final int[] CURSOR_RIGHT = { 61, 9, 3, 14 };
    private static final int[] LABEL = { 54, 44, 60, 20 };

    private static final int MARGIN_RIGHT = 70;
    private static final int LABEL_GAP = 2;

    private int cap = 15;
    private int lastSent = 15;
    private float value = 1.0f;
    private float animatedValue;
    private float lastAnimatedValue;

    @Override
    public void startHold(Level level, Player player, BlockPos pos) {
        this.cap = HelmClient.capFor(pos, level.getGameTime());
        this.lastSent = this.cap;
        this.value = this.cap / 15.0f;
        this.animatedValue = this.lastAnimatedValue = this.value;
        super.startHold(level, player, pos);
    }

    @Override
    public Result onUse(int modifiers, int action, KeyMapping rightKey) {
        if (action == GLFW.GLFW_PRESS && !isActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null && mc.player.getVehicle() instanceof HelmSeatEntity seat) {
                startHold(mc.level, mc.player, seat.getEnginePos());
                return new Result(true);
            }
        }
        return super.onUse(modifiers, action, rightKey);
    }

    @Override
    public boolean activeTick(Level level, LocalPlayer player) {
        if (!(player.getVehicle() instanceof HelmSeatEntity seat))
            return true;
        if (getInteractionPos() == null || !seat.getEnginePos().equals(getInteractionPos()))
            return true;
        float speed = 0.85f;
        lastAnimatedValue = animatedValue;
        animatedValue = animatedValue * (1.0f - speed) + cap / 15.0f * speed;
        return false;
    }

    @Override
    public boolean activeOnMouseMove(double yaw, double pitch) {
        value -= (float) (pitch / 180.0);
        value = Math.min(1.0f, Math.max(0.0f, value));
        cap = Math.min(15, Math.max(0, Math.round(value * 15.0f)));
        if (cap != lastSent) {
            lastSent = cap;
            BlockPos pos = getInteractionPos();
            if (pos != null)
                PacketDistributor.sendToServer(new ThrottleCapPayload(pos, (byte) cap));
        }
        return true;
    }

    @Override
    public void renderOverlay(GuiGraphics graphics, int width, int height, boolean hideGui) {
        if (hideGui)
            return;

        final int h = 14;
        final int w = 100;
        final int centreX = width - MARGIN_RIGHT;
        final int centreY = height / 2;
        final int x = centreX - w / 2;
        final int y = centreY - h / 2;
        final PoseStack ps = graphics.pose();

        ps.pushPose();
        ps.translate(x + w / 2, y + h / 2, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(90.0f));
        ps.translate(-x - w / 2, -y - h / 2, 0);

        sprite(graphics, x, y, FRAME_TL);
        sprite(graphics, x + w - 4, y, FRAME_TR);
        sprite(graphics, x, y + h - 4, FRAME_BL);
        sprite(graphics, x + w - 4, y + h - 4, FRAME_BR);

        stretched(graphics, x, y + 4, 3, h - 8, FRAME_LEFT);
        stretched(graphics, x + w - 3, y + 4, 3, h - 8, FRAME_RIGHT);
        cropped(graphics, x + 4, y, w - 8, 3, FRAME_TOP);
        cropped(graphics, x + 4, y + h - 3, w - 8, 3, FRAME_BOTTOM);

        final int valueBarX = x + 3;
        final int valueBarWidth = w - 6;
        for (int w1 = 0; w1 < valueBarWidth; w1 += BAR[2] - 1)
            cropped(graphics, valueBarX + w1, y + 3, Math.min(BAR[2] - 1, valueBarWidth - w1), 8, BAR);
        ps.popPose();

        sprite(graphics, centreX - LABEL[2] / 2, centreY - w / 2 - LABEL[3] - LABEL_GAP, LABEL);

        ps.pushPose();
        ps.translate(0.0, 0.0, 4.0);
        final float partialTick = AnimationTickHolder.getPartialTicks();
        final float currentValue = lastAnimatedValue * (1.0f - partialTick) + animatedValue * partialTick;

        final float cursorY = ((1.0f - 2.0f * currentValue) * 3.0f * h) + 2;
        final int cx = x + w / 2 - 7;
        final float cy = y + h / 2 - 9 + cursorY;
        final int cursorWidth = 14;

        ps.pushPose();
        ps.translate(0, cy, 0);
        sprite(graphics, cx - 3, 0, CURSOR_LEFT);
        cropped(graphics, cx, 0, cursorWidth, 14, CURSOR);
        sprite(graphics, cx + cursorWidth, 0, CURSOR_RIGHT);
        ps.translate(0.0, 0.0, 4.0);
        graphics.drawString(Minecraft.getInstance().font, String.valueOf(cap), cx + 1, 3, TEXT_COLOR, false);
        ps.popPose();

        ps.popPose();
    }

    private static void sprite(GuiGraphics graphics, int x, int y, int[] region) {
        graphics.blit(TEXTURE, x, y, region[0], region[1], region[2], region[3]);
    }

    private static void stretched(GuiGraphics graphics, int x, int y, int w, int h, int[] region) {
        graphics.blit(TEXTURE, x, y, w, h, region[0], region[1], region[2], region[3], SHEET, SHEET);
    }

    private static void cropped(GuiGraphics graphics, int x, int y, int w, int h, int[] region) {
        graphics.blit(TEXTURE, x, y, w, h, region[0], region[1], w, h, SHEET, SHEET);
    }
}
