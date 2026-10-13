package com.maxenonyme.highseas.gui;

import com.maxenonyme.highseas.CreateHighSeas;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class BoatEngineScreen extends AbstractContainerScreen<BoatEngineMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation
            .fromNamespaceAndPath(CreateHighSeas.MOD_ID, "textures/gui/boat_engine_gui.png");
    private static final ResourceLocation OFF = ResourceLocation
            .fromNamespaceAndPath(CreateHighSeas.MOD_ID, "textures/gui/engine_off.png");
    private static final ResourceLocation OFF_OVER = ResourceLocation
            .fromNamespaceAndPath(CreateHighSeas.MOD_ID, "textures/gui/engine_off_over.png");
    private static final ResourceLocation ON = ResourceLocation
            .fromNamespaceAndPath(CreateHighSeas.MOD_ID, "textures/gui/engine_on.png");
    private static final ResourceLocation ON_OVER = ResourceLocation
            .fromNamespaceAndPath(CreateHighSeas.MOD_ID, "textures/gui/engine_on_over.png");

    private static final int W = 192;
    private static final int H = 216;

    private static final int FLAME_U = 200;
    private static final int FLAME_V = 0;
    private static final int FLAME_SIZE = 14;
    private static final int FLAME_X = 97;
    private static final int FLAME_Y = 46;

    private static final int BTN_W = 14;
    private static final int BTN_H = 15;
    private static final int BTN_STATE_X = 10;
    private static final int BTN_STATE_Y = 102;
    private static final int BTN_CLOSE_X = 159;
    private static final int BTN_CLOSE_Y = 99;

    public BoatEngineScreen(BoatEngineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new StateButton(leftPos + BTN_STATE_X, topPos + BTN_STATE_Y, false, OFF, OFF_OVER));
        addRenderableWidget(new StateButton(leftPos + BTN_STATE_X + BTN_W, topPos + BTN_STATE_Y, true, ON, ON_OVER));

        IconButton close = new IconButton(leftPos + BTN_CLOSE_X, topPos + BTN_CLOSE_Y, AllIcons.I_CONFIRM);
        close.withCallback(this::onClose);
        addRenderableWidget(close);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        int lit = menu.getLitProgress();
        if (lit > 0) {
            graphics.blit(TEXTURE, leftPos + FLAME_X, topPos + FLAME_Y + FLAME_SIZE - lit,
                    FLAME_U, FLAME_V + FLAME_SIZE - lit, FLAME_SIZE, lit);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private class StateButton extends AbstractButton {
        private final ResourceLocation normal;
        private final ResourceLocation over;
        private final boolean isOnButton;

        StateButton(int x, int y, boolean isOnButton, ResourceLocation normal, ResourceLocation over) {
            super(x, y, BTN_W, BTN_H, Component.empty());
            this.isOnButton = isOnButton;
            this.normal = normal;
            this.over = over;
        }

        @Override
        public void onPress() {
            if (isOnButton != menu.isEngineEnabled()) {
                PacketDistributor.sendToServer(new EngineTogglePayload(menu.pos));
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean active = isOnButton == menu.isEngineEnabled();
            graphics.blit(active ? over : normal, getX(), getY(), 0.0f, 0.0f, BTN_W, BTN_H, BTN_W, BTN_H);
            if (isHovered) {
                graphics.fill(getX(), getY(), getX() + BTN_W, getY() + BTN_H, 0x33FFFFFF);
            }
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
        }
    }
}
