package com.maxenonyme.createsubmarine.submarine.gui;

import com.maxenonyme.createsubmarine.submarine.network.ElectrolyzerTogglePayload;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public class ElectrolyzerScreen extends AbstractSimiContainerScreen<ElectrolyzerMenu> {

    private static final int W = 192;
    private static final int H = 123;

    private static final ResourceLocation BG =
        ResourceLocation.fromNamespaceAndPath("create_submarine", "textures/gui/electrolysator_gui.png");
    private static final ResourceLocation OFF      = ResourceLocation.fromNamespaceAndPath("create_submarine", "textures/gui/off.png");
    private static final ResourceLocation OFF_OVER = ResourceLocation.fromNamespaceAndPath("create_submarine", "textures/gui/off_over.png");
    private static final ResourceLocation ON       = ResourceLocation.fromNamespaceAndPath("create_submarine", "textures/gui/on.png");
    private static final ResourceLocation ON_OVER  = ResourceLocation.fromNamespaceAndPath("create_submarine", "textures/gui/on_over.png");

    private static final ResourceLocation ELECTRON_TUBE = ResourceLocation.fromNamespaceAndPath("create", "electron_tube");

    private static final int BTN_W = 14;
    private static final int BTN_H = 15;

    private static final int GAUGE_Y       = 32;
    private static final int GAUGE_H       = 40;
    private static final int GAUGE_W       = 14;
    private static final int GAUGE_ENERGY  = 85;
    private static final int GAUGE_WATER   = 60;
    private static final int GAUGE_OXYGEN  = 110;
    private static final int BTN_STATE_X   = 10;
    private static final int BTN_STATE_Y   = 102;
    private static final int BTN_CLOSE_X   = 159;
    private static final int BTN_CLOSE_Y   = 99;

    private AbstractWidget stateOffBtn;
    private AbstractWidget stateOnBtn;
    private IconButton     btnClose;

    public ElectrolyzerScreen(ElectrolyzerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        setWindowSize(W, H + 4 + AllGuiTextures.PLAYER_INVENTORY.getHeight());
    }

    @Override
    protected void init() {
        super.init();

        stateOffBtn = new StateButton(leftPos + BTN_STATE_X,          topPos + BTN_STATE_Y, false, OFF, OFF_OVER);
        stateOnBtn  = new StateButton(leftPos + BTN_STATE_X + BTN_W,  topPos + BTN_STATE_Y, true,  ON,  ON_OVER);
        addRenderableWidget(stateOffBtn);
        addRenderableWidget(stateOnBtn);

        btnClose = new IconButton(leftPos + BTN_CLOSE_X, topPos + BTN_CLOSE_Y, AllIcons.I_CONFIRM);
        btnClose.withCallback(this::onClose);
        addRenderableWidget(btnClose);
    }

    private class StateButton extends AbstractButton {
        private final ResourceLocation normal, over;
        private final boolean          isOnButton;

        StateButton(int x, int y, boolean isOnButton, ResourceLocation normal, ResourceLocation over) {
            super(x, y, BTN_W, BTN_H, Component.empty());
            this.isOnButton = isOnButton;
            this.normal     = normal;
            this.over       = over;
        }

        @Override
        public void onPress() {
            if (isOnButton != menu.isMachineEnabled())
                PacketDistributor.sendToServer(
                    new ElectrolyzerTogglePayload(menu.pos));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean active = isOnButton == menu.isMachineEnabled();
            g.blit(active ? over : normal, getX(), getY(), 0.0f, 0.0f, BTN_W, BTN_H, BTN_W, BTN_H);
            if (isHovered)
                g.fill(getX(), getY(), getX() + BTN_W, getY() + BTN_H, 0x33FFFFFF);
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput out) {}
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;

        renderPlayerInventory(graphics, getLeftOfCentered(AllGuiTextures.PLAYER_INVENTORY.getWidth()), y + H + 4);
        graphics.blit(BG, x, y, 0.0f, 0.0f, W, H, W, H);
        drawModuleSlot(graphics, x + ElectrolyzerMenu.MODULE_X - 1, y + ElectrolyzerMenu.MODULE_Y - 1);

        int tw = font.width(title);
        graphics.drawString(font, title, x + (W - tw) / 2, y + 4, 0xFF000000, false);

        drawGauge(graphics, x + GAUGE_ENERGY, y + GAUGE_Y, menu.getEnergy(), menu.getMaxEnergy(), 0x55FFAA00);
        drawGauge(graphics, x + GAUGE_WATER,  y + GAUGE_Y, menu.getWater(),  menu.getMaxWater(),  0x553399FF);
        drawGauge(graphics, x + GAUGE_OXYGEN, y + GAUGE_Y, menu.getOxygen(), menu.getMaxOxygen(), 0x55DDDDDD);

        int labelY = y + GAUGE_Y + GAUGE_H + 7;
        graphics.drawString(font, "FE", x + GAUGE_ENERGY, labelY, 0xFFFFAA00, false);
        graphics.drawString(font, "mB", x + GAUGE_WATER,  labelY, 0xFF3399FF, false);
        graphics.drawString(font, "O2", x + GAUGE_OXYGEN, labelY, 0xFF000000, false);
    }

    private void drawModuleSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        if (!menu.getSlot(0).hasItem()) {
            graphics.renderFakeItem(new ItemStack(BuiltInRegistries.ITEM.get(ELECTRON_TUBE)), x + 1, y + 1);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xAA8B8B8B);
            graphics.pose().popPose();
        }
    }

    private void drawGauge(GuiGraphics graphics, int x, int y, int amount, int max, int tint) {
        graphics.fill(x - 1, y - 1, x + GAUGE_W + 1, y + GAUGE_H + 1, 0xFF444444);
        graphics.fill(x,     y,     x + GAUGE_W,     y + GAUGE_H,     0xFF111111);

        int filled = (max > 0) ? (int)(GAUGE_H * (amount / (float) max)) : 0;
        if (filled > 0)
            drawFluidFill(graphics, x + 1, y + GAUGE_H - filled, GAUGE_W - 2, filled - 1, tint);

        for (int i = 0; i <= 5; i++) {
            int lineY = y + GAUGE_H - (i * GAUGE_H / 5);
            graphics.fill(x, lineY, x + 3,        lineY + 1, 0x88FFFFFF);
            if (i % 2 == 0)
                graphics.fill(x, lineY, x + GAUGE_W, lineY + 1, 0x33FFFFFF);
        }

        graphics.fill(x + 2, y + 3, x + 4, y + GAUGE_H - 3, 0x44FFFFFF);
    }

    private void drawFluidFill(GuiGraphics g, int x, int y, int w, int h, int tint) {
        if (w <= 0 || h <= 0) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(ResourceLocation.withDefaultNamespace("block/water_still"));

        float dU = sprite.getU1() - sprite.getU0();
        float dV = sprite.getV1() - sprite.getV0();
        int texW = Math.round(16f / dU);
        int texH = Math.round(16f / dV);
        float u0 = sprite.getU0() * texW;
        float v0 = sprite.getV0() * texH;

        g.enableScissor(x, y, x + w, y + h);
        for (int ty = y; ty < y + h + 16; ty += 16)
            for (int tx = x; tx < x + w + 16; tx += 16)
                g.blit(InventoryMenu.BLOCK_ATLAS, tx, ty, u0, v0, 16, 16, texW, texH);
        g.disableScissor();

        g.fill(x, y, x + w, y + h, tint);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
        if (isHovering(ElectrolyzerMenu.MODULE_X, ElectrolyzerMenu.MODULE_Y, 16, 16, mouseX, mouseY) && !menu.getSlot(0).hasItem())
            graphics.renderTooltip(font, font.split(Component.translatable("create_submarine.electrolyzer.alternator_hint"), 160), mouseX, mouseY);
        else if (isHovering(GAUGE_ENERGY, GAUGE_Y, GAUGE_W, GAUGE_H, mouseX, mouseY) && menu.getSlot(0).hasItem())
            graphics.renderComponentTooltip(font, List.of(
                    Component.literal("Energy: " + menu.getEnergy() + " / " + menu.getMaxEnergy() + " FE"),
                    Component.translatable("create_submarine.electrolyzer.generating", menu.getGenerated())), mouseX, mouseY);
        else if (isHovering(GAUGE_ENERGY, GAUGE_Y, GAUGE_W, GAUGE_H, mouseX, mouseY))
            graphics.renderTooltip(font, Component.literal("Energy: " + menu.getEnergy() + " / " + menu.getMaxEnergy() + " FE"), mouseX, mouseY);
        else if (isHovering(GAUGE_WATER, GAUGE_Y, GAUGE_W, GAUGE_H, mouseX, mouseY))
            graphics.renderTooltip(font, Component.literal("Water: " + menu.getWater() + " / " + menu.getMaxWater() + " mB"), mouseX, mouseY);
        else if (isHovering(GAUGE_OXYGEN, GAUGE_Y, GAUGE_W, GAUGE_H, mouseX, mouseY))
            graphics.renderTooltip(font, Component.literal("Oxygen: " + menu.getOxygen() + " / " + menu.getMaxOxygen() + " mB"), mouseX, mouseY);
    }
}