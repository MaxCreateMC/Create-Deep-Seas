package com.maxenonyme.createsubmarine.submarine.client.alarm;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.alarm.AlarmSettings;
import com.maxenonyme.createsubmarine.submarine.alarm.AlarmSounds;
import com.maxenonyme.createsubmarine.submarine.network.AlarmConfigurePayload;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AlarmScreen extends AbstractSimiScreen {

    private static final int ROW = 22;
    private static final int W = 200;
    private static final int H = 158;
    private static final int FIELD_X = 124;
    private static final int FIELD_W = 68;
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID,
            "textures/gui/industrial_alarm.png");
    private static final int MAX_NAME = 30;
    private static final String KEY = "create_submarine.gui.alarm.";

    private final ItemStack icon = new ItemStack(CreateSubmarine.INDUSTRIAL_ALARM_ITEM.get());
    private final BlockPos pos;
    private final List<String> files;

    private int sound;
    private int on;
    private int off;
    private int volume;
    private boolean follow;

    public AlarmScreen(BlockPos pos, AlarmSettings settings, List<String> files) {
        super(Component.translatable(KEY + "title"));
        this.pos = pos;
        this.files = files;
        this.sound = settings.sound().isEmpty() ? 0 : files.indexOf(settings.sound()) + 1;
        this.on = settings.onTicks();
        this.off = settings.offTicks();
        this.volume = settings.volume();
        this.follow = settings.followDanger();
    }

    private static String shorten(String name) {
        String bare = name.toLowerCase(Locale.ROOT).endsWith(".ogg") ? name.substring(0, name.length() - 4) : name;
        return bare.length() > MAX_NAME ? bare.substring(0, MAX_NAME - 1) + "…" : bare;
    }

    private static Component seconds(int ticks) {
        return Component.literal(String.format(Locale.ROOT, "%.2fs", ticks / 20.0));
    }

    @Override
    protected void init() {
        setWindowSize(W, H);
        setWindowOffset(-20, 0);
        super.init();
        int x = guiLeft;
        int y = guiTop;

        List<Component> options = new ArrayList<>();
        options.add(Component.translatable(KEY + "default"));
        for (String file : files)
            options.add(Component.literal(shorten(file)));
        Label soundLabel = new Label(x + 13, y + 25, Component.empty()).withShadow();
        ScrollInput soundInput = new SelectionScrollInput(x + 8, y + 20, W - 16, 18).forOptions(options)
                .titled(Component.translatable(KEY + "sound"))
                .writingTo(soundLabel)
                .calling(i -> sound = i)
                .setState(sound);
        if (files.isEmpty())
            soundInput.addHint(Component.translatable(KEY + "empty"));
        soundInput.onChanged();
        addRenderableWidget(soundInput);
        addRenderableWidget(soundLabel);

        addRenderableWidgets(time(x, y + 20 + ROW, 1, on, v -> on = v, "on"));
        addRenderableWidgets(time(x, y + 20 + ROW * 2, 0, off, v -> off = v, "off"));

        Label volumeLabel = new Label(x + FIELD_X + 5, y + 25 + ROW * 3, Component.empty()).withShadow();
        ScrollInput volumeInput = new ScrollInput(x + FIELD_X, y + 20 + ROW * 3, FIELD_W, 18).withRange(10, 301)
                .withShiftStep(25)
                .withStepFunction(ctx -> ctx.shift ? 25 : 5)
                .format(v -> Component.literal(v + "%"))
                .titled(Component.translatable(KEY + "volume"))
                .writingTo(volumeLabel)
                .calling(v -> volume = v)
                .setState(volume);
        volumeInput.onChanged();
        addRenderableWidget(volumeInput);
        addRenderableWidget(volumeLabel);

        Label followLabel = new Label(x + FIELD_X + 5, y + 25 + ROW * 4, Component.empty()).withShadow();
        ScrollInput followInput = new SelectionScrollInput(x + FIELD_X, y + 20 + ROW * 4, FIELD_W, 18)
                .forOptions(List.of(Component.translatable(KEY + "yes"), Component.translatable(KEY + "no")))
                .titled(Component.translatable(KEY + "follow"))
                .addHint(Component.translatable(KEY + "follow.hint"))
                .writingTo(followLabel)
                .calling(i -> follow = i == 0)
                .setState(follow ? 0 : 1);
        followInput.onChanged();
        addRenderableWidget(followInput);
        addRenderableWidget(followLabel);

        IconButton folder = new IconButton(x + 6, y + H - 22, AllIcons.I_OPEN_FOLDER);
        folder.withCallback(() -> Util.getPlatform().openFile(AlarmSounds.folder().toFile()));
        folder.setToolTip(Component.translatable(KEY + "folder"));
        addRenderableWidget(folder);

        IconButton confirm = new IconButton(x + W - 24, y + H - 22, AllIcons.I_CONFIRM);
        confirm.withCallback(this::onClose);
        addRenderableWidget(confirm);
    }

    private List<Object> time(int x, int y, int min, int value, java.util.function.Consumer<Integer> sink, String key) {
        Label label = new Label(x + FIELD_X + 5, y + 5, Component.empty()).withShadow();
        ScrollInput input = new ScrollInput(x + FIELD_X, y, FIELD_W, 18).withRange(min, 601)
                .withShiftStep(20)
                .withStepFunction(ctx -> ctx.shift ? 20 : ctx.currentValue < 20 || !ctx.forward && ctx.currentValue <= 20 ? 1 : 5)
                .format(AlarmScreen::seconds)
                .titled(Component.translatable(KEY + key))
                .addHint(Component.translatable(KEY + key + ".hint"))
                .writingTo(label)
                .calling(sink)
                .setState(value);
        input.onChanged();
        return List.of(input, label);
    }

    private void addRenderableWidgets(List<Object> widgets) {
        for (Object widget : widgets) {
            if (widget instanceof ScrollInput input)
                addRenderableWidget(input);
            else if (widget instanceof Label label)
                addRenderableWidget(label);
        }
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;
        graphics.blit(BACKGROUND, x, y, 0, 0, W, H);
        String[] names = { "on", "off", "volume", "follow" };
        for (int row = 1; row < 5; row++)
            graphics.drawString(font, Component.translatable(KEY + names[row - 1]), x + 10, y + 25 + ROW * row, 0xFFFFEE);
        graphics.drawString(font, title, x + W / 2 - font.width(title) / 2, y + 4, 0x592424, false);
        GuiGameElement.of(icon).<GuiGameElement.GuiRenderBuilder>at(x + W + 6, y + H - 56, -100)
                .scale(5)
                .render(graphics);
    }

    @Override
    public void removed() {
        String name = sound <= 0 || sound > files.size() ? "" : files.get(sound - 1);
        PacketDistributor.sendToServer(new AlarmConfigurePayload(pos, new AlarmSettings(name, 0L, on, off, volume, follow)));
    }
}
