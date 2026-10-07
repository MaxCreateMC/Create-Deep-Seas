package com.maxenonyme.createsubmarine.submarine.block.entity.renderer;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.locale.Language;
import com.maxenonyme.createsubmarine.submarine.block.CommandSubBlock;
import com.maxenonyme.createsubmarine.submarine.block.entity.CommandSubBlockEntity;
import com.maxenonyme.createsubmarine.submarine.block.entity.SonarBlockEntity;
import com.maxenonyme.createsubmarine.submarine.client.CommandSubClientHandler;
import com.maxenonyme.createsubmarine.submarine.client.CommandSubDiagram;
import com.maxenonyme.createsubmarine.submarine.client.SonarContact;
import com.maxenonyme.createsubmarine.submarine.client.SonarScan;
import com.maxenonyme.createsubmarine.submarine.client.SonarView;
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
import com.maxenonyme.createsubmarine.submarine.network.DiagnosticPayload;
import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
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
    public static final float[] PREV = { 92, 101.5f, 107, 108.5f };
    public static final float[] NEXT = { 109, 101.5f, 124, 108.5f };
    public static final float[] AUTO = { 6, 14, 74, 30 };
    public static final float[] SONAR = { 3, 3, 125, 99 };
    public static final float[] EXPAND = { 113, 5, 123, 15 };
    public static final float[] DIAG_RUN = { 24, 50, 104, 66 };
    public static final float[] DIAG_RERUN = { 94, 3, 124, 11 };
    public static final long SCAN_MILLIS = 2600;
    private static final long SCAN_TIMEOUT = 8000;
    private static final float[] SCAN_FRAME = { 30, 16, 98, 64 };
    private static final float[] SCAN_BAR = { 14, 72, 114, 77 };
    private static final String[] SCAN_STEPS = { "hull", "pressure", "seal", "report" };
    private static final float[] TANK = { 24, 14, 104, 46 };
    private static final float FISH_PIXEL = 1.4f;
    private static final long FISH_LAP = 9000;
    private static final float[] WEEDS = { 9, 12, 61, 70, 73 };
    private static final int COD = 0xFFB9956A;
    private static final int COD_BELLY = 0xFFE3CFA6;
    private static final int COD_FIN = 0xFF8D6C45;
    private static final int SAND = 0xFFE2D3AE;
    private static final int WEED = 0xFF6F8A4F;
    private static final int BUBBLE = 0xFF8FA7B5;
    private static final String[] FISH = {
            "....ddd.......",
            "..#######...##",
            ".#########.###",
            "#e###########.",
            ".bbbbbbbbb.###",
            "..bbbbbbb...##",
            "....ddd.......",
    };
    private static final String[] FISH_FLICK = {
            "..#######....#",
            "..bbbbbbb....#",
    };

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

        drawArrow(PREV, "<", hovered == CommandSubClientHandler.PREV, ms, buffer, font, light);
        drawArrow(NEXT, ">", hovered == CommandSubClientHandler.NEXT, ms, buffer, font, light);
        if (be.page == 1) {
            drawSonar(be, hovered == CommandSubClientHandler.EXPAND, ms, buffer, font, light);
            ms.popPose();
            return;
        }
        if (be.page == 2) {
            drawAutopilot(be, hovered, ms, buffer, font, light);
            ms.popPose();
            return;
        }
        if (be.page == 3) {
            drawDiagnostic(be, hovered == CommandSubClientHandler.DIAGNOSE, ms, buffer, font, light);
            ms.popPose();
            return;
        }

        fill(ms, buffer, 44, 4, 44.6f, 108, 0.5f, DULL, light);

        text(ms, buffer, font, tr("create_submarine.command_sub.target"),
                5, 5, 0.6f, INK, light, false);
        String typed = CommandSubClientHandler.typedFor(be);
        boolean fieldHover = typed == null && hovered == CommandSubClientHandler.FIELD;
        box(ms, buffer, FIELD, fieldHover ? INK : typed != null ? DULL : PAPER, fieldHover || typed != null ? INK : LINE, light);
        String shown = typed != null ? typed + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : " ") + " m"
                : autoSteering(be) ? "AUTO " + be.syncedAutoTarget + " m" : be.targetDepth + " m";
        text(ms, buffer, font, shown, (FIELD[0] + FIELD[2]) / 2, FIELD[1] + 4, 1f,
                fieldHover ? PAPER : INK, light, true);

        text(ms, buffer, font, tr("create_submarine.command_sub.speed"),
                5, 36, 0.6f, INK, light, false);
        for (int i = 0; i < 3; i++) {
            float[] r = SPEEDS[i];
            boolean hover = hovered == CommandSubClientHandler.SPEED_FIRST + i;
            boolean selected = be.speedMode == i;
            int back = hover ? INK : selected ? DULL : PAPER;
            box(ms, buffer, r, back, selected || hover ? LINE : BUTTON, light);
            String label = tr("create_submarine.command_sub.speed." + SPEED_NAMES[i]);
            float scale = Math.min(0.75f, (r[2] - r[0] - 4) / font.width(label));
            text(ms, buffer, font, label, (r[0] + r[2]) / 2, (r[1] + r[3]) / 2 - 4.5f * scale, scale,
                    hover ? PAPER : INK, light, true);
        }

        text(ms, buffer, font, tr("create_submarine.command_sub.vertical"),
                5, 93, 0.6f, INK, light, false);
        text(ms, buffer, font, String.format(Locale.ROOT, "%+.1f m/s", be.shownVs), 5, 100, 0.75f, INK, light, false);

        drawTape(be, depth, ms, buffer, font, light);

        String ballast = be.syncedFill < 0
                ? tr("create_submarine.command_sub.no_ballast")
                : Component.translatable("create_submarine.command_sub.ballast", be.syncedFill).getString();
        text(ms, buffer, font, ballast, 123, 7, 0.5f, INK, light, false, true);
        String pumps = be.syncedPumps == 0
                ? tr("create_submarine.command_sub.pump.none")
                : Component.translatable("create_submarine.command_sub.pump." + PUMP_STATES[Math.max(0, Math.min(2, be.syncedStatus))],
                        be.syncedPumps).getString();
        text(ms, buffer, font, pumps, 123, 12.5f, 0.5f, be.syncedPumps == 0 ? DANGER : INK, light, false, true);

        ms.popPose();
    }

    public static boolean autoSteering(CommandSubBlockEntity be) {
        return be.autopilot && be.syncedAutoTarget >= 0;
    }

    private static void drawArrow(float[] r, String arrow, boolean hover, PoseStack ms, MultiBufferSource buffer, Font font,
            int light) {
        box(ms, buffer, r, hover ? INK : PAPER, hover ? INK : LINE, light, 0.6f);
        text(ms, buffer, font, arrow, (r[0] + r[2]) / 2, (r[1] + r[3]) / 2 - 3.6f, 0.8f, hover ? PAPER : INK, light, true);
    }

    private static void drawAutopilot(CommandSubBlockEntity be, int hovered, PoseStack ms, MultiBufferSource buffer,
            Font font, int light) {
        text(ms, buffer, font, tr("create_submarine.command_sub.tab.autopilot"),
                5, 5, 0.6f, INK, light, false);
        boolean sonar = SonarBlockEntity.onSameSub(be) != null;
        boolean hover = sonar && hovered == CommandSubClientHandler.AUTOPILOT;
        int back = !sonar ? PAPER : hover ? INK : be.autopilot ? DULL : PAPER;
        int border = !sonar ? DULL : hover || be.autopilot ? INK : LINE;
        int ink = !sonar ? DULL : hover ? PAPER : INK;
        box(ms, buffer, AUTO, back, border, light);
        String label = tr(be.autopilot && sonar ? "create_submarine.command_sub.autopilot.on"
                : "create_submarine.command_sub.autopilot.off");
        float scale = Math.min(0.7f, (AUTO[2] - AUTO[0] - 4) / font.width(label));
        text(ms, buffer, font, label, (AUTO[0] + AUTO[2]) / 2, (AUTO[1] + AUTO[3]) / 2 - 4.5f * scale, scale, ink, light,
                true);

        String status;
        int color = INK;
        if (!sonar) {
            status = tr("create_submarine.command_sub.no_sonar");
            color = DANGER;
        } else if (!be.autopilot) {
            status = tr("create_submarine.command_sub.autopilot.idle");
        } else if (be.syncedAutoTarget < 0) {
            status = tr("create_submarine.command_sub.autopilot.no_floor");
        } else {
            status = Component.translatable("create_submarine.command_sub.autopilot.following", be.syncedAutoTarget)
                    .getString();
        }
        text(ms, buffer, font, status, 6, 36, 0.6f, color, light, false);
    }

    public static boolean scanning(CommandSubBlockEntity be) {
        if (be.scanStarted < 0)
            return false;
        long elapsed = System.currentTimeMillis() - be.scanStarted;
        if (be.report == null && elapsed > SCAN_TIMEOUT) {
            be.scanStarted = -1;
            return false;
        }
        return be.report == null || elapsed < SCAN_MILLIS;
    }

    private static void drawDiagnostic(CommandSubBlockEntity be, boolean hover, PoseStack ms, MultiBufferSource buffer,
            Font font, int light) {
        text(ms, buffer, font, tr("create_submarine.command_sub.tab.diagnostic"),
                5, 5, 0.6f, INK, light, false);
        if (scanning(be)) {
            drawScan(be, ms, buffer, font, light);
            return;
        }
        DiagnosticPayload report = be.report;
        if (report == null) {
            box(ms, buffer, DIAG_RUN, hover ? INK : PAPER, hover ? INK : LINE, light, 0.6f);
            box(ms, buffer, new float[] { DIAG_RUN[0] + 1.4f, DIAG_RUN[1] + 1.4f, DIAG_RUN[2] - 1.4f, DIAG_RUN[3] - 1.4f },
                    hover ? INK : PAPER, hover ? PAPER : BUTTON, light, 0.9f);
            String run = tr("create_submarine.command_sub.diag.run");
            float scale = Math.min(0.7f, (DIAG_RUN[2] - DIAG_RUN[0] - 8) / font.width(run));
            text(ms, buffer, font, run, (DIAG_RUN[0] + DIAG_RUN[2]) / 2, (DIAG_RUN[1] + DIAG_RUN[3]) / 2 - 4.5f * scale,
                    scale, hover ? PAPER : INK, light, true);
            String[] hint = tr("create_submarine.command_sub.diag.hint").split("\n");
            for (int i = 0; i < hint.length; i++)
                text(ms, buffer, font, hint[i], 64, 72 + i * 5, 0.45f, BUTTON, light, true);
            drawTank(ms, buffer, light);
            return;
        }

        box(ms, buffer, DIAG_RERUN, hover ? INK : PAPER, hover ? INK : LINE, light, 0.6f);
        text(ms, buffer, font, tr("create_submarine.command_sub.diag.rerun"),
                (DIAG_RERUN[0] + DIAG_RERUN[2]) / 2, DIAG_RERUN[1] + 2.2f, 0.5f, hover ? PAPER : INK, light, true);
        if (!report.onVessel()) {
            text(ms, buffer, font, tr("create_submarine.command_sub.diag.no_vessel"),
                    64, 52, 0.7f, DANGER, light, true);
            return;
        }
        drawReport(report, ms, buffer, font, light);
    }

    private static void drawTank(PoseStack ms, MultiBufferSource buffer, int light) {
        box(ms, buffer, TANK, PAPER, LINE, light, 0.3f);
        long now = System.currentTimeMillis();
        for (float x = TANK[0] + 3; x < TANK[2] - 3; x += 6) {
            float wave = (float) Math.sin(now / 500.0 + x * 0.4) * 0.5f;
            fill(ms, buffer, x, TANK[1] + 3 + wave, x + 3, TANK[1] + 3.4f + wave, 0.5f, DULL, light);
        }
        fill(ms, buffer, TANK[0] + 0.6f, TANK[3] - 3, TANK[2] - 0.6f, TANK[3] - 0.6f, 0.5f, SAND, light);
        for (int i = 0; i < WEEDS.length; i++) {
            float x = TANK[0] + WEEDS[i];
            float sway = (float) Math.sin(now / 700.0 + i) * 0.6f;
            for (int k = 0; k < 4; k++) {
                float y = TANK[3] - 3 - (k + 1) * 1.6f;
                fill(ms, buffer, x + sway * k * 0.4f, y, x + 1 + sway * k * 0.4f, y + 1.6f, 0.6f, WEED, light);
            }
        }

        float span = TANK[2] - TANK[0] - FISH_PIXEL * FISH[0].length() - 6;
        double lap = (now % FISH_LAP) / (double) FISH_LAP;
        boolean left = lap >= 0.5;
        double along = left ? 2 - lap * 2 : lap * 2;
        float ease = (float) (0.5 - Math.cos(along * Math.PI) / 2);
        float fx = TANK[0] + 3 + span * ease;
        float fy = (TANK[1] + TANK[3]) / 2 - FISH_PIXEL * FISH.length / 2f - 1 + (float) Math.sin(now / 420.0) * 1.2f;
        boolean flick = (now / 180) % 2 == 0;
        int width = FISH[0].length();
        for (int row = 0; row < FISH.length; row++) {
            String line = flick && (row == 1 || row == FISH.length - 2) ? FISH_FLICK[row == 1 ? 0 : 1] : FISH[row];
            for (int col = 0; col < width; col++) {
                int color = fishColor(line.charAt(col));
                if (color == 0)
                    continue;
                int drawn = left ? col : width - 1 - col;
                float px = fx + drawn * FISH_PIXEL;
                float py = fy + row * FISH_PIXEL;
                fill(ms, buffer, px, py, px + FISH_PIXEL, py + FISH_PIXEL, 0.8f, color, light);
            }
        }

        float mouth = left ? fx : fx + width * FISH_PIXEL;
        for (int i = 0; i < 3; i++) {
            float t = ((now + i * 700) % 2100) / 2100f;
            float by = fy + FISH_PIXEL * 2 - t * (fy - TANK[1] - 2);
            float bx = mouth + (left ? -1.5f : 1.5f) + (float) Math.sin(t * 9 + i) * 0.6f;
            if (by > TANK[1] + 4)
                box(ms, buffer, new float[] { bx, by, bx + 1.4f, by + 1.4f }, PAPER, BUBBLE, light, 0.7f);
        }
    }

    private static int fishColor(char c) {
        return switch (c) {
            case '#' -> COD;
            case 'b' -> COD_BELLY;
            case 'd' -> COD_FIN;
            case 'e' -> LINE;
            default -> 0;
        };
    }

    private static void drawScan(CommandSubBlockEntity be, PoseStack ms, MultiBufferSource buffer, Font font, int light) {
        long elapsed = System.currentTimeMillis() - be.scanStarted;
        float progress = Math.min(elapsed / (float) SCAN_MILLIS, be.report == null ? 0.95f : 1f);

        box(ms, buffer, SCAN_FRAME, PAPER, LINE, light, 0.3f);
        RenderType diagram = CommandSubDiagram.textureFor(be);
        float[] inner = { SCAN_FRAME[0] + 1, SCAN_FRAME[1] + 1, SCAN_FRAME[2] - 1, SCAN_FRAME[3] - 1 };
        if (diagram != null) {
            picture(ms, buffer.getBuffer(diagram), inner, 0.6f, light);
        } else {
            float cy = (SCAN_FRAME[1] + SCAN_FRAME[3]) / 2;
            fill(ms, buffer, 40, cy - 4, 88, cy + 4, 0.6f, LINE, light);
            fill(ms, buffer, 58, cy - 9, 68, cy - 4, 0.6f, LINE, light);
            fill(ms, buffer, 34, cy - 2, 40, cy + 2, 0.6f, BUTTON, light);
        }
        float sweep = (elapsed % 1100) / 1100f;
        float x = inner[0] + (inner[2] - inner[0]) * sweep;
        fill(ms, buffer, inner[0], inner[1], x, inner[3], 0.9f, 0x224F5257, light);
        fill(ms, buffer, x - 0.4f, inner[1], x + 0.4f, inner[3], 1.0f, DANGER, light);
        for (float y = inner[1] + 2; y < inner[3]; y += 4)
            fill(ms, buffer, x - 1.6f, y, x - 0.4f, y + 0.4f, 1.0f, DANGER, light);

        box(ms, buffer, SCAN_BAR, PAPER, LINE, light, 0.3f);
        float barEnd = SCAN_BAR[0] + 0.8f + (SCAN_BAR[2] - SCAN_BAR[0] - 1.6f) * progress;
        fill(ms, buffer, SCAN_BAR[0] + 0.8f, SCAN_BAR[1] + 0.8f, barEnd, SCAN_BAR[3] - 0.8f, 0.8f, INK, light);
        for (float tick = SCAN_BAR[0] + 10; tick < SCAN_BAR[2]; tick += 10)
            fill(ms, buffer, tick - 0.2f, SCAN_BAR[3], tick + 0.2f, SCAN_BAR[3] + 1.2f, 0.5f, BUTTON, light);
        text(ms, buffer, font, (int) (progress * 100) + "%", SCAN_BAR[2], SCAN_BAR[3] + 2.5f, 0.5f, INK, light, false,
                true);

        int step = Math.min(SCAN_STEPS.length - 1, (int) (progress * SCAN_STEPS.length));
        String dots = ".".repeat(1 + (int) ((elapsed / 300) % 3));
        text(ms, buffer, font, tr("create_submarine.command_sub.diag.step." + SCAN_STEPS[step]) + dots, SCAN_BAR[0], SCAN_BAR[3] + 2.5f, 0.5f, INK, light, false);
        for (int i = 0; i < SCAN_STEPS.length; i++) {
            float cx = 50 + i * 9;
            int color = i < step ? INK : i == step && (elapsed / 250) % 2 == 0 ? DANGER : DULL;
            fill(ms, buffer, cx - 1.2f, 88, cx + 1.2f, 90.4f, 0.8f, color, light);
        }
    }

    private static void drawReport(DiagnosticPayload report, PoseStack ms, MultiBufferSource buffer, Font font, int light) {
        text(ms, buffer, font, diag("max_depth"), 5, 14, 0.5f, BUTTON, light, false);
        String max = report.maxDepth() < 0 ? "--" : report.maxDepth() + " m";
        text(ms, buffer, font, max, 5, 19, 1.3f, report.maxDepth() < 0 ? DULL : INK, light, false);
        if (report.maxDepth() >= 0 && !report.weakest().isEmpty()) {
            String name = Component.translatable(report.weakest()).getString();
            text(ms, buffer, font, diag("weakest", name), 5, 32, 0.45f, INK, light, false);
            if (report.weakestAt() != null)
                text(ms, buffer, font, coords(report.weakestAt()), 5, 37, 0.45f, BUTTON, light, false);
        } else {
            text(ms, buffer, font, diag("unknown"), 5, 32, 0.45f, BUTTON, light, false);
        }

        boolean sealed = report.hermetic() && report.breachCount() == 0;
        stamp(ms, buffer, font, diag(sealed ? "sealed" : "breached"), 103, 22, sealed ? INK : DANGER, light);

        if (report.maxDepth() > 0) {
            float used = Math.min(1f, report.depth() / (float) report.maxDepth());
            int margin = Math.max(0, 100 - Math.round(used * 100));
            boolean tight = used >= 0.8f;
            text(ms, buffer, font, diag("margin", report.depth(), margin), 5, 46, 0.5f, tight ? DANGER : INK, light,
                    false);
            float end = 5 + 119 * used;
            fill(ms, buffer, 5, 53, 124, 53.5f, 0.5f, DULL, light);
            fill(ms, buffer, 5, 52.5f, end, 54, 0.6f, tight ? DANGER : INK, light);
            float warn = 5 + 119 * 0.8f;
            fill(ms, buffer, warn - 0.3f, 51.5f, warn + 0.3f, 55, 0.9f, DANGER, light);
        } else {
            text(ms, buffer, font, diag("depth", report.depth()), 5, 46, 0.5f, INK, light, false);
        }

        if (report.cracks() == 0)
            text(ms, buffer, font, diag("no_cracks"), 5, 59, 0.5f, INK, light, false);
        else
            text(ms, buffer, font, diag("cracks", report.cracks()), 5, 59, 0.5f, DANGER, light, false);

        if (report.breachCount() == 0) {
            text(ms, buffer, font, diag(report.hermetic() ? "no_breach" : "open_room"), 5, 68, 0.5f,
                    report.hermetic() ? INK : DANGER, light, false);
            return;
        }
        text(ms, buffer, font, diag("breaches", report.breachCount()), 5, 68, 0.5f, DANGER, light, false);
        int shown = Math.min(4, report.breaches().size());
        boolean more = report.breachCount() > shown;
        if (more && shown == 4)
            shown = 3;
        for (int i = 0; i < shown; i++)
            text(ms, buffer, font, "- " + coords(report.breaches().get(i)), 7, 74 + i * 5, 0.45f, INK, light, false);
        if (more)
            text(ms, buffer, font, diag("more", report.breachCount() - shown), 7, 74 + shown * 5, 0.45f, BUTTON,
                    light, false);
    }

    private static String diag(String key, Object... args) {
        return Component.translatable("create_submarine.command_sub.diag." + key, args).getString();
    }

    private static void stamp(PoseStack ms, MultiBufferSource buffer, Font font, String label, float cx, float cy,
            int color, int light) {
        float half = font.width(label) * 0.35f + 4;
        ms.pushPose();
        ms.translate(cx, cy, 0);
        ms.mulPose(Axis.ZP.rotationDegrees(-7));
        box(ms, buffer, new float[] { -half, -6, half, 6 }, PAPER, color, light, 0.6f);
        box(ms, buffer, new float[] { -half + 1.4f, -4.6f, half - 1.4f, 4.6f }, PAPER, color, light, 0.8f);
        text(ms, buffer, font, label, 0, -2.6f, 0.7f, color, light, true);
        ms.popPose();
    }

    private static String coords(BlockPos pos) {
        return "X " + pos.getX() + "  Y " + pos.getY() + "  Z " + pos.getZ();
    }

    private static void drawSonar(CommandSubBlockEntity be, boolean hover, PoseStack ms, MultiBufferSource buffer,
            Font font, int light) {
        box(ms, buffer, SONAR, PAPER, LINE, light, 0.3f);
        text(ms, buffer, font, tr("create_submarine.command_sub.tab.sonar"),
                SONAR[0] + 2, SONAR[1] + 2, 0.55f, INK, light, false);
        if (SonarBlockEntity.onSameSub(be) == null) {
            text(ms, buffer, font, tr("create_submarine.command_sub.no_sonar"),
                    (SONAR[0] + SONAR[2]) / 2, (SONAR[1] + SONAR[3]) / 2 - 3, 0.7f, DANGER, light, true);
            return;
        }
        SonarScan scan = SonarView.preview(be);
        RenderType picture = SonarView.previewTexture(scan);
        if (picture != null)
            picture(ms, buffer.getBuffer(picture), new float[] { SONAR[0] + 0.6f, SONAR[1] + 0.6f, SONAR[2] - 0.6f,
                    SONAR[3] - 0.6f }, 0.9f, light, scan.u0());
        drawExpand(hover, ms, buffer, light);
        String mineLabel = tr("create_submarine.command_sub.mine");
        for (SonarContact contact : scan.contacts()) {
            float cx = SONAR[0] + (SONAR[2] - SONAR[0]) * contact.u();
            float cy = SONAR[1] + (SONAR[3] - SONAR[1]) * contact.v();
            float r = contact.kind() == SonarContact.VESSEL ? 1.4f : 1f;
            fill(ms, buffer, cx - r, cy - r, cx + r, cy + r, 1.3f, contact.color(), light);
            if (contact.kind() == SonarContact.MINE)
                text(ms, buffer, font, mineLabel, cx + 1.8f, cy - 1.6f, 0.4f, DANGER, light, false);
        }
        float[] cursor = CommandSubClientHandler.cursor(be);
        if (cursor != null && cursor[0] >= SONAR[0] && cursor[0] <= SONAR[2] && cursor[1] >= SONAR[1]
                && cursor[1] <= SONAR[3]) {
            float w = SONAR[2] - SONAR[0];
            float h = SONAR[3] - SONAR[1];
            SonarContact hovered = SonarContact.nearest(scan.contacts(), (cursor[0] - SONAR[0]) / w,
                    (cursor[1] - SONAR[1]) / h, 3f / w, 3f / h);
            if (hovered != null) {
                String name = hovered.name().getString();
                float cx = SONAR[0] + w * hovered.u();
                float cy = SONAR[1] + h * hovered.v();
                float width = font.width(name) * 0.5f;
                float x = Math.min(cx + 3, SONAR[2] - width - 3);
                float y = Math.max(SONAR[1] + 2, cy - 8);
                box(ms, buffer, new float[] { x - 1.5f, y - 1.2f, x + width + 1.5f, y + 5.2f }, PAPER,
                        hovered.color(), light, 1.6f);
                text(ms, buffer, font, name, x, y, 0.5f, INK, light, false);
            }
        }
        String floor = SonarView.floorLabel(scan.floor()).getString();
        fill(ms, buffer, SONAR[0] + 1, SONAR[3] - 7.5f, SONAR[0] + 4 + font.width(floor) * 0.55f, SONAR[3] - 1, 1.2f,
                PAPER, light);
        text(ms, buffer, font, floor, SONAR[0] + 2.5f, SONAR[3] - 6.2f, 0.55f, INK, light, false);
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
                    text(ms, buffer, font, tr("create_submarine.command_sub.limit"),
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
            text(ms, buffer, font, tr("create_submarine.command_sub.surface"),
                    123, surfaceY - 5, 0.5f, INK, light, false, true);
        }

        float targetY = tapeY(be.activeTarget(), depth);
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

    private static void drawExpand(boolean hover, PoseStack ms, MultiBufferSource buffer, int light) {
        float x = EXPAND[0];
        float y = EXPAND[1];
        int ink = hover ? PAPER : INK;
        box(ms, buffer, EXPAND, hover ? INK : PAPER, hover ? INK : LINE, light, 1.1f);
        fill(ms, buffer, x + 2, y + 2, x + 6.5f, y + 2.7f, 1.5f, ink, light);
        fill(ms, buffer, x + 2, y + 5.8f, x + 6.5f, y + 6.5f, 1.5f, ink, light);
        fill(ms, buffer, x + 2, y + 2, x + 2.7f, y + 6.5f, 1.5f, ink, light);
        fill(ms, buffer, x + 5.8f, y + 2, x + 6.5f, y + 6.5f, 1.5f, ink, light);
        fill(ms, buffer, x + 6.3f, y + 6.3f, x + 7.4f, y + 7.4f, 1.5f, ink, light);
        fill(ms, buffer, x + 7.2f, y + 7.2f, x + 8.3f, y + 8.3f, 1.5f, ink, light);
    }

    private static void picture(PoseStack ms, VertexConsumer vc, float[] r, float z, int light) {
        picture(ms, vc, r, z, light, 0);
    }

    private static void picture(PoseStack ms, VertexConsumer vc, float[] r, float z, int light, float u0) {
        Matrix4f pose = ms.last().pose();
        vc.addVertex(pose, r[0], r[1], z).setColor(0xFFFFFFFF).setUv(u0, 1).setLight(light);
        vc.addVertex(pose, r[0], r[3], z).setColor(0xFFFFFFFF).setUv(u0, 0).setLight(light);
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

    private static final Map<String, String> LABELS = new HashMap<>();
    private static Language labelsFor;

    private static String tr(String key) {
        Language language = Language.getInstance();
        if (language != labelsFor) {
            LABELS.clear();
            labelsFor = language;
        }
        return LABELS.computeIfAbsent(key, k -> Component.translatable(k).getString());
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
