package com.maxenonyme.createsubmarine.submarine.block.entity.renderer;

import com.maxenonyme.createsubmarine.submarine.block.CommandSubBlock;
import com.maxenonyme.createsubmarine.submarine.block.entity.CommandSubBlockEntity;
import com.maxenonyme.createsubmarine.submarine.client.CommandSubClientHandler;
import com.maxenonyme.createsubmarine.submarine.client.CommandSubDiagram;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

import java.util.Locale;

public class CommandSubRenderer implements BlockEntityRenderer<CommandSubBlockEntity> {

    private static final float PIVOT_Y = 10.5f / 16f;
    private static final float PIVOT_Z = -3 / 16f;
    private static final float TILT = 45f;
    private static final float PAPER_LEFT = 16 / 16f;
    private static final float PAPER_TOP = 16 / 16f;
    private static final float UNITS_PER_BLOCK = 128f;

    public static final float W = 128;
    public static final float H = 112;

    public static final float[] FIELD = { 4, 13, 40, 29 };
    public static final float[][] SPEEDS = { { 4, 44, 40, 56 }, { 4, 59, 40, 71 }, { 4, 74, 40, 86 } };
    public static final float[] DIAGRAM = { 81, 40, 125, 72 };

    private static final int INK = 0xFF4F5257;
    private static final int LINE = 0xFF2E3032;
    private static final int BUTTON = 0xFF6D7177;
    private static final int DULL = 0xFFB5B1A8;
    private static final int PAPER = 0xFFF7F0DD;
    private static final int DANGER = 0xFFA8433C;
    private static final int DANGER_WASH = 0x33A8433C;

    private static final float AXIS_X = 78;
    private static final float TAPE_TOP = 6;
    private static final float TAPE_BOTTOM = 106;
    private static final float CENTER_Y = 56;
    private static final float PER_BLOCK = 2.5f;
    private static final String[] SPEED_NAMES = { "slow", "cruise", "fast" };
    private static final String[] PUMP_STATES = { "drain", "hold", "fill" };

    public CommandSubRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static Matrix4f canvasToBlock(BlockState state) {
        Direction facing = state.getValue(CommandSubBlock.FACING);
        float yRot = facing.toYRot() + 180;
        return new Matrix4f()
                .translate(0.5f, 0, 0.5f)
                .rotateY((float) Math.toRadians(-yRot))
                .translate(-0.5f, 0, -0.5f)
                .translate(0, PIVOT_Y, PIVOT_Z)
                .rotateX((float) Math.toRadians(TILT))
                .translate(PAPER_LEFT, PAPER_TOP, -0.002f)
                .rotateY((float) Math.PI)
                .scale(1 / UNITS_PER_BLOCK, -1 / UNITS_PER_BLOCK, 1 / UNITS_PER_BLOCK);
    }

    @Override
    public void render(CommandSubBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (CommandSubDiagram.drawing)
            return;
        Font font = Minecraft.getInstance().font;

        double subY = be.getBlockPos().getY() + 0.5;
        SubLevel sub = Sable.HELPER.getContaining(be);
        if (sub instanceof ClientSubLevel csl)
            subY = csl.renderPose().position().y();
        float depth = be.surfaceY == Integer.MIN_VALUE ? 0 : Math.max(0f, (float) (be.surfaceY + 1 - subY));
        trackVerticalSpeed(be, subY);

        int hovered = CommandSubClientHandler.hoveredWidget(be);

        ms.pushPose();
        ms.mulPose(canvasToBlock(be.getBlockState()));

        fill(ms, buffer, 44, 4, 44.6f, 108, 0.5f, DULL, light);

        text(ms, buffer, font, Component.translatable("create_submarine.command_sub.target").getString(),
                5, 5, 0.6f, INK, light, false);
        String typed = CommandSubClientHandler.typedFor(be);
        boolean fieldHover = typed == null && hovered == CommandSubClientHandler.FIELD;
        box(ms, buffer, FIELD, fieldHover ? INK : typed != null ? DULL : PAPER, fieldHover || typed != null ? INK : LINE, light);
        String shown = typed == null ? be.targetDepth + " m"
                : typed + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : " ") + " m";
        text(ms, buffer, font, shown, (FIELD[0] + FIELD[2]) / 2, FIELD[1] + 4, 1f,
                fieldHover ? PAPER : INK, light, true);

        text(ms, buffer, font, Component.translatable("create_submarine.command_sub.speed").getString(),
                5, 36, 0.6f, INK, light, false);
        for (int i = 0; i < 3; i++) {
            float[] r = SPEEDS[i];
            boolean hover = hovered == CommandSubClientHandler.SPEED_FIRST + i;
            boolean selected = be.speedMode == i;
            int back = hover ? INK : selected ? DULL : PAPER;
            box(ms, buffer, r, back, selected || hover ? LINE : BUTTON, light);
            String label = Component.translatable("create_submarine.command_sub.speed." + SPEED_NAMES[i]).getString();
            float scale = Math.min(0.75f, (r[2] - r[0] - 4) / font.width(label));
            text(ms, buffer, font, label, (r[0] + r[2]) / 2, (r[1] + r[3]) / 2 - 4.5f * scale, scale,
                    hover ? PAPER : INK, light, true);
        }

        text(ms, buffer, font, Component.translatable("create_submarine.command_sub.vertical").getString(),
                5, 93, 0.6f, INK, light, false);
        text(ms, buffer, font, String.format(Locale.ROOT, "%+.1f m/s", be.shownVs), 5, 100, 0.75f, INK, light, false);

        drawTape(be, depth, ms, buffer, font, light);

        String ballast = be.syncedFill < 0
                ? Component.translatable("create_submarine.command_sub.no_ballast").getString()
                : Component.translatable("create_submarine.command_sub.ballast", be.syncedFill).getString();
        text(ms, buffer, font, ballast, 123, 7, 0.5f, INK, light, false, true);
        String pumps = be.syncedPumps == 0
                ? Component.translatable("create_submarine.command_sub.pump.none").getString()
                : Component.translatable("create_submarine.command_sub.pump." + PUMP_STATES[Math.max(0, Math.min(2, be.syncedStatus))],
                        be.syncedPumps).getString();
        text(ms, buffer, font, pumps, 123, 12.5f, 0.5f, be.syncedPumps == 0 ? DANGER : INK, light, false, true);

        ms.popPose();
    }

    private void drawTape(CommandSubBlockEntity be, float depth, PoseStack ms, MultiBufferSource buffer,
            Font font, int light) {
        if (be.syncedWeakest > 0) {
            float limitY = tapeY(be.syncedWeakest, depth);
            if (limitY < TAPE_BOTTOM) {
                float top = Math.max(TAPE_TOP, limitY);
                fill(ms, buffer, 48, top, 124, TAPE_BOTTOM, 0.2f, DANGER_WASH, light);
                if (limitY >= TAPE_TOP) {
                    fill(ms, buffer, 48, limitY, 124, limitY + 0.6f, 0.5f, DANGER, light);
                    text(ms, buffer, font, Component.translatable("create_submarine.command_sub.limit").getString(),
                            123, limitY + 1.5f, 0.5f, DANGER, light, false, true);
                }
            }
        }

        fill(ms, buffer, AXIS_X, TAPE_TOP, AXIS_X + 0.6f, TAPE_BOTTOM, 0.5f, LINE, light);
        int from = (int) Math.floor(depth - (CENTER_Y - TAPE_TOP) / PER_BLOCK);
        int to = (int) Math.ceil(depth + (TAPE_BOTTOM - CENTER_Y) / PER_BLOCK);
        for (int d = from; d <= to; d++) {
            float y = tapeY(d, depth);
            if (y < TAPE_TOP || y > TAPE_BOTTOM)
                continue;
            float len = d % 10 == 0 ? 7 : d % 5 == 0 ? 4.5f : 2;
            fill(ms, buffer, AXIS_X - len, y - 0.3f, AXIS_X, y + 0.3f, 0.5f, d % 5 == 0 ? LINE : BUTTON, light);
            if (d % 10 == 0 && y > TAPE_TOP + 3 && y < TAPE_BOTTOM - 3)
                text(ms, buffer, font, Integer.toString(d), AXIS_X - 9, y - 2.2f, 0.55f, INK, light, false, true);
        }

        float surfaceY = tapeY(0, depth);
        if (surfaceY >= TAPE_TOP && surfaceY <= TAPE_BOTTOM) {
            for (float x = 48; x < 124; x += 4)
                fill(ms, buffer, x, surfaceY - 0.3f, x + 2, surfaceY + 0.3f, 0.6f, LINE, light);
            text(ms, buffer, font, Component.translatable("create_submarine.command_sub.surface").getString(),
                    123, surfaceY - 5, 0.5f, INK, light, false, true);
        }

        float targetY = tapeY(be.targetDepth, depth);
        if (targetY >= TAPE_TOP && targetY <= TAPE_BOTTOM) {
            for (float x = AXIS_X + 2; x < 124; x += 3)
                fill(ms, buffer, x, targetY - 0.25f, x + 1.5f, targetY + 0.25f, 0.6f, BUTTON, light);
            fill(ms, buffer, AXIS_X + 0.6f, targetY - 2.5f, AXIS_X + 4, targetY + 2.5f, 0.7f, INK, light);
        }

        RenderType diagram = CommandSubDiagram.textureFor(be);
        if (diagram != null) {
            fill(ms, buffer, AXIS_X + 4, CENTER_Y - 0.4f, DIAGRAM[0], CENTER_Y + 0.4f, 0.8f, LINE, light);
            picture(ms, buffer.getBuffer(diagram), DIAGRAM, 0.9f, light);
        } else {
            fill(ms, buffer, AXIS_X + 4, CENTER_Y - 0.4f, 90, CENTER_Y + 0.4f, 0.8f, LINE, light);
            fill(ms, buffer, 94, CENTER_Y - 2.5f, 116, CENTER_Y + 2.5f, 0.8f, LINE, light);
            fill(ms, buffer, 102, CENTER_Y - 5.5f, 108, CENTER_Y - 2.5f, 0.8f, LINE, light);
            fill(ms, buffer, 90, CENTER_Y - 1.5f, 94, CENTER_Y + 1.5f, 0.8f, BUTTON, light);
            fill(ms, buffer, 116, CENTER_Y - 1f, 118, CENTER_Y + 1f, 0.8f, LINE, light);
        }

        box(ms, buffer, new float[] { 50, CENTER_Y - 6, AXIS_X - 1, CENTER_Y + 6 }, PAPER, LINE, light, 1.0f);
        text(ms, buffer, font, Integer.toString((int) Math.floor(depth)), AXIS_X - 3, CENTER_Y - 3.5f, 0.85f, INK, light,
                false, true);
    }

    private static float tapeY(float d, float depth) {
        return CENTER_Y + (d - depth) * PER_BLOCK;
    }

    private static void trackVerticalSpeed(CommandSubBlockEntity be, double y) {
        long now = System.nanoTime();
        if (Double.isNaN(be.lastY)) {
            be.lastY = y;
            be.lastNanos = now;
            return;
        }
        double dt = (now - be.lastNanos) / 1.0e9;
        if (dt < 0.05)
            return;
        float raw = (float) ((y - be.lastY) / dt);
        be.shownVs += (raw - be.shownVs) * (float) Math.min(1.0, dt * 3.0);
        be.lastY = y;
        be.lastNanos = now;
    }

    private static void box(PoseStack ms, MultiBufferSource buffer, float[] r, int back, int border, int light) {
        box(ms, buffer, r, back, border, light, 0.3f);
    }

    private static void box(PoseStack ms, MultiBufferSource buffer, float[] r, int back, int border, int light, float z) {
        fill(ms, buffer, r[0], r[1], r[2], r[3], z, back, light);
        fill(ms, buffer, r[0], r[1], r[2], r[1] + 0.6f, z + 0.2f, border, light);
        fill(ms, buffer, r[0], r[3] - 0.6f, r[2], r[3], z + 0.2f, border, light);
        fill(ms, buffer, r[0], r[1], r[0] + 0.6f, r[3], z + 0.2f, border, light);
        fill(ms, buffer, r[2] - 0.6f, r[1], r[2], r[3], z + 0.2f, border, light);
    }

    private static void picture(PoseStack ms, VertexConsumer vc, float[] r, float z, int light) {
        Matrix4f pose = ms.last().pose();
        vc.addVertex(pose, r[0], r[1], z).setColor(0xFFFFFFFF).setUv(0, 1).setLight(light);
        vc.addVertex(pose, r[0], r[3], z).setColor(0xFFFFFFFF).setUv(0, 0).setLight(light);
        vc.addVertex(pose, r[2], r[3], z).setColor(0xFFFFFFFF).setUv(1, 0).setLight(light);
        vc.addVertex(pose, r[2], r[1], z).setColor(0xFFFFFFFF).setUv(1, 1).setLight(light);
    }

    private static void fill(PoseStack ms, MultiBufferSource buffer, float x0, float y0, float x1, float y1, float z, int argb,
            int light) {
        Matrix4f pose = ms.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.textBackground());
        vc.addVertex(pose, x0, y0, z).setColor(argb).setLight(light);
        vc.addVertex(pose, x0, y1, z).setColor(argb).setLight(light);
        vc.addVertex(pose, x1, y1, z).setColor(argb).setLight(light);
        vc.addVertex(pose, x1, y0, z).setColor(argb).setLight(light);
    }

    private static void text(PoseStack ms, MultiBufferSource buffer, Font font, String s, float x, float y, float scale,
            int color, int light, boolean centered) {
        text(ms, buffer, font, s, x, y, scale, color, light, centered, false);
    }

    private static void text(PoseStack ms, MultiBufferSource buffer, Font font, String s, float x, float y, float scale,
            int color, int light, boolean centered, boolean rightAligned) {
        float width = font.width(s) * scale;
        float left = centered ? x - width / 2 : rightAligned ? x - width : x;
        ms.pushPose();
        ms.translate(left, y, 1.5f);
        ms.scale(scale, scale, 1);
        font.drawInBatch(s, 0, 0, color, false, ms.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET, 0, light);
        ms.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CommandSubBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(CommandSubBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.5);
    }
}
