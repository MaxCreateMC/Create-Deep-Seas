package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.List;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineClientState;

public class DeepSeasWelcomeScreen extends Screen {
    private static final int PANEL_BG = 0xE6101A22;
    private static final int PANEL_ACCENT = 0xFF3FB6E0;
    private static final int PANEL_BORDER = 0xFF2C5566;
    private static final int TITLE_COLOR = 0xFF8FE0FF;
    private static final int BODY_COLOR = 0xFFCEDDE4;

    private final Screen titleScreen;

    private List<FormattedCharSequence> messageLines = List.of();
    private int panelX, panelY, panelW, panelH;

    public DeepSeasWelcomeScreen(Screen titleScreen) {
        super(Component.translatable("create_submarine.welcome.title"));
        this.titleScreen = titleScreen;
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof TitleScreen menu)) {
            return;
        }
        if (!SubmarineConfig.CLIENT_SPEC.isLoaded() || SubmarineClientState.hasSeenWelcomeScreen()) {
            return;
        }
        if (SubmarineConfig.DISABLE_STARTUP_SCREENS.get()) {
            return;
        }
        event.setNewScreen(new DeepSeasWelcomeScreen(menu));
    }

    @Override
    protected void init() {
        panelW = Math.min(360, this.width - 40);
        messageLines = this.font.split(Component.translatable("create_submarine.welcome.message"), panelW - 28);

        int titleBlock = 12 + this.font.lineHeight + 10;
        int bodyBlock = messageLines.size() * (this.font.lineHeight + 2);
        panelH = titleBlock + bodyBlock + 12;

        int buttonW = Math.min(170, (panelW - 8) / 2);
        int gap = 8;
        int blockH = panelH + 14 + 20;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - blockH) / 2;

        int centerX = this.width / 2;
        int buttonsY = panelY + panelH + 14;

        addRenderableWidget(Button.builder(
                        Component.translatable("create_submarine.welcome.configure"),
                        b -> { acknowledge(); openConfig(); })
                .bounds(centerX - gap / 2 - buttonW, buttonsY, buttonW, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("create_submarine.welcome.dismiss"),
                        b -> { acknowledge(); this.minecraft.setScreen(titleScreen); })
                .bounds(centerX + gap / 2, buttonsY, buttonW, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, PANEL_ACCENT);
        g.renderOutline(panelX, panelY, panelW, panelH, PANEL_BORDER);

        int centerX = this.width / 2;
        g.drawCenteredString(this.font, this.title, centerX, panelY + 12, TITLE_COLOR);

        int ty = panelY + 12 + this.font.lineHeight + 10;
        for (FormattedCharSequence line : messageLines) {
            g.drawCenteredString(this.font, line, centerX, ty, BODY_COLOR);
            ty += this.font.lineHeight + 2;
        }
    }

    private void acknowledge() {
        SubmarineClientState.setWelcomeScreenSeen(true);
    }

    private void openConfig() {
        ModList.get().getModContainerById(CreateSubmarine.MOD_ID).ifPresentOrElse(
                mc -> mc.getCustomExtension(IConfigScreenFactory.class).ifPresentOrElse(
                        factory -> this.minecraft.setScreen(factory.createScreen(mc, titleScreen)),
                        () -> this.minecraft.setScreen(titleScreen)),
                () -> this.minecraft.setScreen(titleScreen));
    }

    @Override
    public void onClose() {
        acknowledge();
        this.minecraft.setScreen(titleScreen);
    }
}
