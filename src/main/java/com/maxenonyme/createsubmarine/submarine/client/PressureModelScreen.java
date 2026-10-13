package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineClientState;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PressureModelScreen extends Screen {
    private static final int PANEL_BG = 0xE6101A22;
    private static final int PANEL_ACCENT = 0xFF3FB6E0;
    private static final int PANEL_BORDER = 0xFF2C5566;
    private static final int INFO_BG = 0xF2050A0E;
    private static final int INFO_BORDER = 0xFF1C3540;
    private static final int TITLE_COLOR = 0xFF8FE0FF;
    private static final int LABEL_COLOR = 0xFF7E9099;
    private static final int SUMMARY_COLOR = 0xFFCEDDE4;
    private static final int PRO_COLOR = 0xFF6FE08A;
    private static final int CON_COLOR = 0xFFFF7A66;
    private static final int DEPTH_COLOR = 0xFFFFD54A;
    private static final int LIST_W = 128;
    private static final int ROW_H = 18;
    private static final int HOVER_BG = 0xFF13252E;
    private static final int HOVER_BORDER = 0xFF3FB6E0;
    private static final int GAP = 10;
    private static final int PAD = 10;
    private static final Path SERVER_CONFIG = FMLPaths.CONFIGDIR.get().resolve("create_submarine-server.toml");
    private static final Pattern MODEL_LINE = Pattern.compile("(?m)^(\\s*pressureModel\\s*=\\s*\")([A-Z]+)(\")");

    private record Line(FormattedCharSequence text, int color) {
    }

    private final Screen titleScreen;
    private final SubmarineConfig.PressureModel[] models = SubmarineConfig.PressureModel.values();
    private SubmarineConfig.PressureModel picked;
    private boolean open;
    private final List<Line> info = new ArrayList<>();
    private int panelX, panelY, panelW, panelH, infoX, infoY, infoW, infoH, listX, headerY;

    public PressureModelScreen(Screen titleScreen) {
        super(Component.translatable("create_submarine.pressure_choice.title"));
        this.titleScreen = titleScreen;
        this.picked = current();
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof TitleScreen menu))
            return;
        if (!SubmarineConfig.CLIENT_SPEC.isLoaded() || SubmarineClientState.hasSeenPressureChoice())
            return;
        if (SubmarineConfig.DISABLE_STARTUP_SCREENS.get())
            return;
        event.setNewScreen(new PressureModelScreen(menu));
    }

    @Override
    protected void init() {
        clearWidgets();
        panelW = Math.min(440, this.width - 20);
        panelH = Math.min(190, this.height - 40);
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(4, (this.height - panelH - 28) / 2);
        int top = panelY + PAD + this.font.lineHeight + 8;
        listX = panelX + PAD;
        infoX = listX + LIST_W + GAP;
        infoY = top;
        infoW = panelX + panelW - PAD - infoX;
        infoH = panelY + panelH - PAD - infoY;
        headerY = top + this.font.lineHeight + 4;

        int buttonW = Math.min(160, panelW);
        addRenderableWidget(Button.builder(Component.translatable("create_submarine.pressure_choice.confirm"),
                        b -> onClose())
                .bounds((this.width - buttonW) / 2, panelY + panelH + 6, buttonW, 20)
                .build());
        layoutInfo();
    }

    private Component name(SubmarineConfig.PressureModel model) {
        Component name = model.getTranslatedName();
        return model == SubmarineConfig.PressureModel.REALISTIC
                ? Component.translatable("create_submarine.pressure_choice.recommended", name)
                : name;
    }

    private void layoutInfo() {
        info.clear();
        String key = "create_submarine.pressure_choice." + picked.name().toLowerCase();
        int w = infoW - 2 * 8;
        add(Component.translatable(key + ".summary"), w, SUMMARY_COLOR, "");
        add(Component.translatable(key + ".pro1"), w, PRO_COLOR, "+ ");
        add(Component.translatable(key + ".pro2"), w, PRO_COLOR, "+ ");
        add(Component.translatable(key + ".con"), w, CON_COLOR, "- ");
        add(Component.translatable(key + ".depth"), w, DEPTH_COLOR, "\u25C6 ");
    }

    private void add(Component text, int width, int color, String bullet) {
        Component full = bullet.isEmpty() ? text : Component.literal(bullet).append(text);
        if (!info.isEmpty())
            info.add(new Line(FormattedCharSequence.EMPTY, color));
        for (FormattedCharSequence line : this.font.split(full, width))
            info.add(new Line(line, color));
    }

    private static int color(SubmarineConfig.PressureModel model) {
        ChatFormatting tint = model == SubmarineConfig.PressureModel.CLASSIC ? ChatFormatting.GREEN
                : model == SubmarineConfig.PressureModel.HYBRID ? ChatFormatting.AQUA : ChatFormatting.LIGHT_PURPLE;
        return 0xFF000000 | tint.getColor();
    }

    private boolean overHeader(double mx, double my) {
        return mx >= listX && mx < listX + LIST_W && my >= headerY && my < headerY + ROW_H + 2;
    }

    private int rowAt(double mx, double my) {
        if (!open || mx < listX || mx >= listX + LIST_W)
            return -1;
        int first = headerY + ROW_H + 4;
        int row = (int) Math.floor((my - first) / ROW_H);
        return my >= first && row >= 0 && row < models.length ? row : -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        boolean hover = overHeader(mouseX, mouseY);
        boolean lit = hover || open;
        int h = ROW_H + 2;
        int textY = headerY + (h - this.font.lineHeight) / 2 + 1;
        g.fill(listX, headerY, listX + LIST_W, headerY + h, lit ? HOVER_BG : INFO_BG);
        g.renderOutline(listX, headerY, LIST_W, h, lit ? HOVER_BORDER : INFO_BORDER);
        g.fill(listX, headerY, listX + 2, headerY + h, color(picked));
        g.drawString(this.font, name(picked), listX + 7, textY, color(picked));
        g.drawString(this.font, open ? "\u25B2" : "\u25BC", listX + LIST_W - 11, textY, lit ? 0xFFFFFFFF : LABEL_COLOR);
        if (!open)
            return;
        int first = headerY + ROW_H + 4;
        int hovered = rowAt(mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(listX, first, listX + LIST_W, first + models.length * ROW_H, INFO_BG);
        g.renderOutline(listX, first, LIST_W, models.length * ROW_H, HOVER_BORDER);
        for (int i = 0; i < models.length; i++) {
            SubmarineConfig.PressureModel model = models[i];
            int y = first + i * ROW_H;
            if (i == hovered)
                g.fill(listX + 1, y + 1, listX + LIST_W - 1, y + ROW_H - 1, HOVER_BG);
            if (model == picked)
                g.fill(listX + 1, y + 1, listX + 3, y + ROW_H - 1, color(model));
            g.drawString(this.font, name(model), listX + 7, y + (ROW_H - this.font.lineHeight) / 2 + 1,
                    model == picked || i == hovered ? color(model) : SUMMARY_COLOR);
        }
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && overHeader(mx, my)) {
            open = !open;
            return true;
        }
        if (open) {
            int row = rowAt(mx, my);
            open = false;
            if (row >= 0) {
                picked = models[row];
                layoutInfo();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, PANEL_ACCENT);
        g.renderOutline(panelX, panelY, panelW, panelH, PANEL_BORDER);
        g.drawCenteredString(this.font, this.title, this.width / 2, panelY + PAD, TITLE_COLOR);
        g.drawString(this.font, Component.translatable("create_submarine.pressure_choice.label"), panelX + PAD, infoY,
                LABEL_COLOR);

        g.fill(infoX, infoY, infoX + infoW, infoY + infoH, INFO_BG);
        g.renderOutline(infoX, infoY, infoW, infoH, INFO_BORDER);
        g.fill(infoX, infoY, infoX + 2, infoY + infoH, color(picked));
        g.drawString(this.font, picked.getTranslatedName(), infoX + 8, infoY + 7, 0xFFFFFFFF);
        int y = infoY + 7 + this.font.lineHeight + 6;
        int bottom = infoY + infoH - this.font.lineHeight - 4;
        for (Line line : info) {
            if (y > bottom)
                break;
            g.drawString(this.font, line.text(), infoX + 8, y, line.color());
            y += line.text() == FormattedCharSequence.EMPTY ? 4 : this.font.lineHeight + 1;
        }
    }

    @Override
    public void onClose() {
        apply(picked);
        SubmarineClientState.setPressureChoiceSeen(true);
        this.minecraft.setScreen(titleScreen);
    }

    private static SubmarineConfig.PressureModel current() {
        if (SubmarineConfig.SERVER_SPEC.isLoaded())
            return SubmarineConfig.PRESSURE_MODEL.get();
        try {
            if (Files.exists(SERVER_CONFIG)) {
                Matcher matcher = MODEL_LINE.matcher(Files.readString(SERVER_CONFIG, StandardCharsets.UTF_8));
                if (matcher.find())
                    return SubmarineConfig.PressureModel.valueOf(matcher.group(2));
            }
        } catch (IOException | IllegalArgumentException ignored) {
        }
        return SubmarineConfig.PressureModel.REALISTIC;
    }

    private static void apply(SubmarineConfig.PressureModel model) {
        if (SubmarineConfig.SERVER_SPEC.isLoaded()) {
            SubmarineConfig.PRESSURE_MODEL.set(model);
            SubmarineConfig.PRESSURE_MODEL.save();
            return;
        }
        String entry = "[hullStrength]" + System.lineSeparator() + "\tpressureModel = \"" + model.name() + "\""
                + System.lineSeparator();
        try {
            if (!Files.exists(SERVER_CONFIG)) {
                Files.writeString(SERVER_CONFIG, entry, StandardCharsets.UTF_8);
                return;
            }
            String text = Files.readString(SERVER_CONFIG, StandardCharsets.UTF_8);
            Matcher matcher = MODEL_LINE.matcher(text);
            Files.writeString(SERVER_CONFIG, matcher.find() ? matcher.replaceFirst("$1" + model.name() + "$3")
                    : text + System.lineSeparator() + entry, StandardCharsets.UTF_8);
        } catch (IOException e) {
            CreateSubmarine.LOGGER.warn("Could not save the pressure model choice", e);
        }
    }
}
