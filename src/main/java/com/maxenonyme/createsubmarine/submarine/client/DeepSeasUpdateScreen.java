package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.system.UpdateChecker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineClientState;
import net.minecraft.Util;

public class DeepSeasUpdateScreen extends Screen {
    private static final int PANEL_BG = 0xE6101A22;
    private static final int PANEL_ACCENT = 0xFF3FB6E0;
    private static final int PANEL_BORDER = 0xFF2C5566;
    private static final int TITLE_COLOR = 0xFF8FE0FF;
    private static final int BODY_COLOR = 0xFFCEDDE4;
    private static final int CHANGELOG_COLOR = 0xFFAAAAAA;
    private static final int HEADER_COLOR = 0xFFFFFFFF;

    public static boolean UPDATE_SCREEN_SHOWN = false;

    private final Screen titleScreen;

    private List<FormattedCharSequence> messageLines = List.of();
    private List<FormattedCharSequence> changelogLines = List.of();
    private int panelX, panelY, panelW, panelH;
    private int changelogBlockHeight;
    private int clYStart;

    private double scrollAmount = 0.0;
    private int maxScroll = 0;

    public DeepSeasUpdateScreen(Screen titleScreen) {
        super(Component.translatable("create_submarine.update.title"));
        this.titleScreen = titleScreen;
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof TitleScreen menu)) {
            return;
        }
        if (!SubmarineConfig.CLIENT_SPEC.isLoaded() || !SubmarineClientState.hasSeenWelcomeScreen()) {
            return;
        }
        if (SubmarineConfig.DISABLE_STARTUP_SCREENS.get()) {
            return;
        }
        if (UPDATE_SCREEN_SHOWN || !UpdateChecker.isUpdateAvailable()) {
            return;
        }
        if (UpdateChecker.getLatestVersion() != null && UpdateChecker.getLatestVersion().equals(SubmarineClientState.getIgnoredUpdateVersion())) {
            return;
        }
        UPDATE_SCREEN_SHOWN = true;
        event.setNewScreen(new DeepSeasUpdateScreen(menu));
    }

    private List<Component> parseMarkdown(String text) {
        List<Component> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        String[] rawLines = text.split("\n");
        for (String line : rawLines) {
            line = line.trim();
            if (line.isEmpty()) {
                lines.add(Component.empty());
                continue;
            }
            if (line.startsWith("#")) {
                String clean = line.replaceAll("^#+\\s*", "");
                lines.add(Component.literal(clean).withStyle(style -> style.withColor(HEADER_COLOR).withBold(true)));
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                lines.add(parseInlineMarkdown("• " + line.substring(2)));
            } else {
                lines.add(parseInlineMarkdown(line));
            }
        }
        return lines;
    }

    private Component parseInlineMarkdown(String line) {
        MutableComponent root = Component.empty();
        String[] boldParts = line.split("\\*\\*");
        for (int i = 0; i < boldParts.length; i++) {
            MutableComponent part = Component.literal(boldParts[i]).withStyle(style -> style.withColor(CHANGELOG_COLOR));
            if (i % 2 == 1) {
                part.withStyle(style -> style.withBold(true));
            }
            root.append(part);
        }
        return root;
    }

    @Override
    protected void init() {
        panelW = Math.min(360, this.width - 40);
        messageLines = this.font.split(Component.translatable("create_submarine.update.message_screen", UpdateChecker.getLatestVersion()), panelW - 28);

        String rawChangelog = UpdateChecker.getLatestChangelog();
        changelogLines = new ArrayList<>();
        if (rawChangelog != null && !rawChangelog.isEmpty()) {
            List<Component> mdLines = parseMarkdown(rawChangelog);
            for (Component comp : mdLines) {
                changelogLines.addAll(this.font.split(comp, panelW - 32));
            }
        }

        int titleBlock = 12 + this.font.lineHeight + 10;
        int bodyBlock = messageLines.size() * (this.font.lineHeight + 2);

        int totalChangelogHeight = changelogLines.size() * (this.font.lineHeight + 2);
        int maxChangelogDisplay = 120;
        changelogBlockHeight = Math.min(maxChangelogDisplay, totalChangelogHeight);
        maxScroll = Math.max(0, totalChangelogHeight - changelogBlockHeight);

        panelH = titleBlock + bodyBlock + (changelogLines.isEmpty() ? 0 : 10 + changelogBlockHeight) + 12;

        int buttonW = Math.min(170, (panelW - 8) / 2);
        int gap = 8;
        int blockH = panelH + 14 + 20;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - blockH) / 2;

        int centerX = this.width / 2;
        int buttonsY = panelY + panelH + 14;

        addRenderableWidget(Button.builder(
                        Component.translatable("create_submarine.update.button.modrinth"),
                        b -> { markAsSeenAndClose(); Util.getPlatform().openUri("https://modrinth.com/project/mva5q4qZ"); })
                .bounds(centerX - gap / 2 - buttonW, buttonsY, buttonW, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("create_submarine.welcome.dismiss"),
                        b -> { markAsSeenAndClose(); })
                .bounds(centerX + gap / 2, buttonsY, buttonW, 20)
                .build());

        clYStart = panelY + titleBlock + bodyBlock + 10;
    }

    private void markAsSeenAndClose() {
        if (UpdateChecker.getLatestVersion() != null) {
            SubmarineClientState.setIgnoredUpdateVersion(UpdateChecker.getLatestVersion());
        }
        this.minecraft.setScreen(titleScreen);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            this.scrollAmount -= scrollY * (this.font.lineHeight + 2) * 2;
            this.scrollAmount = Mth.clamp(this.scrollAmount, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

        if (!changelogLines.isEmpty()) {
            g.enableScissor(panelX + 10, clYStart, panelX + panelW - 10, clYStart + changelogBlockHeight);
            int yText = clYStart - (int) this.scrollAmount;
            for (FormattedCharSequence line : changelogLines) {
                if (yText + this.font.lineHeight >= clYStart && yText <= clYStart + changelogBlockHeight) {
                    g.drawString(this.font, line, panelX + 14, yText, 0xFFFFFFFF);
                }
                yText += this.font.lineHeight + 2;
            }
            g.disableScissor();

            if (maxScroll > 0) {
                int barX = panelX + panelW - 8;
                int barY = clYStart;
                int barH = changelogBlockHeight;
                g.fill(barX, barY, barX + 2, barY + barH, 0x80000000);

                int thumbH = Math.max(10, (int) ((changelogBlockHeight / (float) (changelogBlockHeight + maxScroll)) * barH));
                int thumbY = barY + (int) ((scrollAmount / maxScroll) * (barH - thumbH));
                g.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public void onClose() {
        markAsSeenAndClose();
    }
}
