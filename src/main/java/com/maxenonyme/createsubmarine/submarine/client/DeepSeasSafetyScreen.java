package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.submarine.config.SubmarineClientState;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.fml.ModList;
import com.maxenonyme.createsubmarine.CreateSubmarine;

import java.util.List;

public class DeepSeasSafetyScreen extends Screen {
    private static final int PANEL_BG = 0xE6101A22;
    private static final int PANEL_ACCENT = 0xFF3FB6E0;
    private static final int PANEL_BORDER = 0xFF2C5566;
    private static final int TITLE_COLOR = 0xFF8FE0FF;
    private static final int BODY_COLOR = 0xFFCEDDE4;
    private static final int SIGN_YELLOW = 0xFFFFC61A;
    private static final int SIGN_BLACK = 0xFF111111;
    private static final int SIGN_H = 34;

    private final Screen titleScreen;

    private List<FormattedCharSequence> messageLines = List.of();
    private int panelX, panelY, panelW, panelH;
    private int signTop, titleY, bodyY;
    private Checkbox photosensitive;

    public DeepSeasSafetyScreen(Screen titleScreen) {
        super(Component.translatable("create_submarine.safety.title"));
        this.titleScreen = titleScreen;
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof TitleScreen menu)) {
            return;
        }
        if (!SubmarineConfig.CLIENT_SPEC.isLoaded() || SubmarineClientState.hasSeenSafetyScreen()) {
            return;
        }
        if (SubmarineConfig.DISABLE_STARTUP_SCREENS.get()) {
            return;
        }
        event.setNewScreen(new DeepSeasSafetyScreen(menu));
    }

    @Override
    protected void init() {
        panelW = Math.min(380, this.width - 40);
        messageLines = this.font.split(Component.translatable("create_submarine.safety.message"), panelW - 28);

        int bodyH = messageLines.size() * (this.font.lineHeight + 2);
        panelH = 14 + SIGN_H + 10 + this.font.lineHeight + 10 + bodyH + 12 + 20 + 12;

        int blockH = panelH + 14 + 20;
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(6, (this.height - blockH) / 2);

        signTop = panelY + 14;
        titleY = signTop + SIGN_H + 10;
        bodyY = titleY + this.font.lineHeight + 10;
        int checkboxY = bodyY + bodyH + 12;

        boolean current = SubmarineConfig.PHOTOSENSITIVE_MODE.get();
        photosensitive = Checkbox.builder(Component.translatable("create_submarine.safety.checkbox"), this.font)
                .pos(panelX, checkboxY)
                .selected(current)
                .build();
        photosensitive.setX((this.width - photosensitive.getWidth()) / 2);
        addRenderableWidget(photosensitive);

        int gap = 8;
        int buttonW = Math.min(170, (panelW - gap) / 2);
        int buttonsY = panelY + panelH + 14;
        int centerX = this.width / 2;
        addRenderableWidget(Button.builder(
                        Component.translatable("create_submarine.safety.continue"),
                        b -> onClose())
                .bounds(centerX - gap / 2 - buttonW, buttonsY, buttonW, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("create_submarine.safety.settings"),
                        b -> openSettings())
                .bounds(centerX + gap / 2, buttonsY, buttonW, 20)
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, PANEL_ACCENT);
        g.renderOutline(panelX, panelY, panelW, panelH, PANEL_BORDER);

        int centerX = this.width / 2;
        drawSign(g, centerX, signTop);
        g.drawCenteredString(this.font, this.title, centerX, titleY, TITLE_COLOR);

        int ty = bodyY;
        for (FormattedCharSequence line : messageLines) {
            g.drawCenteredString(this.font, line, centerX, ty, BODY_COLOR);
            ty += this.font.lineHeight + 2;
        }
    }

    private static void drawSign(GuiGraphics g, int centerX, int top) {
        triangle(g, centerX, top, SIGN_H, SIGN_BLACK);
        triangle(g, centerX, top + 5, SIGN_H - 8, SIGN_YELLOW);
        g.fill(centerX - 2, top + 12, centerX + 2, top + SIGN_H - 11, SIGN_BLACK);
        g.fill(centerX - 2, top + SIGN_H - 9, centerX + 2, top + SIGN_H - 5, SIGN_BLACK);
    }

    private static void triangle(GuiGraphics g, int centerX, int top, int height, int color) {
        for (int row = 0; row < height; row++) {
            int half = (row * 9 / 8 + 1) / 2 + 1;
            g.fill(centerX - half, top + row, centerX + half, top + row + 1, color);
        }
    }

    private void acknowledge() {
        SubmarineConfig.PHOTOSENSITIVE_MODE.set(photosensitive != null && photosensitive.selected());
        SubmarineConfig.PHOTOSENSITIVE_MODE.save();
        SubmarineClientState.setSafetyScreenSeen(true);
    }

    private void openSettings() {
        acknowledge();
        ModList.get().getModContainerById(CreateSubmarine.MOD_ID).ifPresentOrElse(
                container -> this.minecraft.setScreen(new ConfigurationScreen(container, titleScreen)),
                () -> this.minecraft.setScreen(titleScreen));
    }

    @Override
    public void onClose() {
        acknowledge();
        this.minecraft.setScreen(titleScreen);
    }
}
