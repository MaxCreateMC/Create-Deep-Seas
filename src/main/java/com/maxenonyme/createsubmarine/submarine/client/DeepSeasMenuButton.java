package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.simibubi.create.infrastructure.gui.OpenCreateMenuButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = CreateSubmarine.MOD_ID, value = Dist.CLIENT)
public class DeepSeasMenuButton extends Button {
    private static final int ROW = 24;

    private final Screen parent;

    public DeepSeasMenuButton(int x, int y, Screen parent) {
        super(x, y, 20, 20, CommonComponents.EMPTY, DeepSeasMenuButton::open, DEFAULT_NARRATION);
        this.parent = parent;
        setTooltip(Tooltip.create(Component.translatable("create_submarine.menu.config")));
    }

    private static void open(Button button) {
        Minecraft mc = Minecraft.getInstance();
        Screen parent = button instanceof DeepSeasMenuButton b ? b.parent : mc.screen;
        ModList.get().getModContainerById(CreateSubmarine.MOD_ID)
                .ifPresent(container -> mc.setScreen(new HullStrengthConfigScreen(container, parent)));
    }

    @Override
    public void renderString(GuiGraphics graphics, Font font, int color) {
        graphics.renderItem(new ItemStack(CreateSubmarine.PRESSURE_GOGGLES.get()), getX() + 2, getY() + 2);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof PauseScreen) && !(screen instanceof TitleScreen))
            return;
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof OpenCreateMenuButton create) {
                event.addListener(new DeepSeasMenuButton(create.getX(), create.getY() + ROW, screen));
                return;
            }
        }
    }
}
