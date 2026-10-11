package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig;
import com.maxenonyme.createsubmarine.submarine.network.HullConfigEditPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class HullStrengthConfigScreen extends Screen {
    private final ModContainer modContainer;
    private final Screen parentScreen;
    private final Map<String, HullStrengthConfig.HullProperty> editedValues = new TreeMap<>();
    private final Set<String> dirtyKeys = new HashSet<>();

    private EditBox searchBox;
    private BlockList blockList;
    private BlockEntry selectedBlock;

    private EditBox maxDepthBox;
    private EditBox implosionBox;
    private Button btnApply;

    public HullStrengthConfigScreen(ModContainer modContainer, Screen parentScreen) {
        super(Component.translatable("create_submarine.screen.hull_strength_config.title"));
        this.modContainer = modContainer;
        this.parentScreen = parentScreen;
        this.editedValues.putAll(HullStrengthConfig.getValues());
    }

    @Override
    protected void init() {
        super.init();

        int leftWidth = (int) (this.width * 0.4);

        this.searchBox = new EditBox(this.font, 10, 35, leftWidth - 20, 20, Component.literal("Search"));
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);

        this.blockList = new BlockList(this.minecraft, leftWidth - 20, this.height - 110, 65, 24);
        this.blockList.setPosition(10, 65);
        this.addRenderableWidget(this.blockList);

        int rightX = leftWidth + 20;
        int rightWidth = this.width - rightX - 20;

        this.maxDepthBox = new EditBox(this.font, rightX, 100, rightWidth, 20, Component.literal("Max Water Depth"));
        this.maxDepthBox.visible = false;
        this.addRenderableWidget(this.maxDepthBox);

        this.implosionBox = new EditBox(this.font, rightX, 150, rightWidth, 20, Component.literal("Implosion Chance"));
        this.implosionBox.visible = false;
        this.addRenderableWidget(this.implosionBox);

        this.btnApply = Button.builder(Component.translatable("create_submarine.ui.button.apply_changes"), this::onApply)
                .bounds(rightX, 185, rightWidth, 20)
                .build();
        this.btnApply.visible = false;
        this.addRenderableWidget(this.btnApply);

        this.addRenderableWidget(Button.builder(Component.translatable("create_submarine.ui.button.global_settings"), btn -> this.minecraft.setScreen(new ConfigurationScreen(this.modContainer, this)))
                .bounds(10, this.height - 30, leftWidth - 20, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("create_submarine.ui.button.save_exit"), btn -> this.onSave())
                .bounds(this.width - 210, this.height - 30, 95, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("create_submarine.ui.button.cancel"), btn -> this.onCancel())
                .bounds(this.width - 105, this.height - 30, 95, 20)
                .build());

        this.populateList("");
    }

    private void onSearchChanged(String query) {
        this.populateList(query);
    }

    private void populateList(String query) {
        String lowerQuery = query.toLowerCase().trim();
        BlockEntry previouslySelected = this.selectedBlock;
        this.blockList.clearList();

        for (Map.Entry<String, HullStrengthConfig.HullProperty> entry : this.editedValues.entrySet()) {
            String key = entry.getKey();
            ResourceLocation id = ResourceLocation.parse(key);
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block != null) {
                String translatedName = block.getName().getString().toLowerCase();
                if (lowerQuery.isEmpty() || key.contains(lowerQuery) || translatedName.contains(lowerQuery)) {
                    BlockEntry listEntry = new BlockEntry(key, block);
                    this.blockList.addListEntry(listEntry);
                    if (previouslySelected != null && key.equals(previouslySelected.key)) {
                        this.blockList.setSelected(listEntry);
                        this.selectedBlock = listEntry;
                    }
                }
            }
        }
    }

    private void setSelectedBlock(BlockEntry entry) {
        this.selectedBlock = entry;
        if (entry == null) {
            this.maxDepthBox.visible = false;
            this.implosionBox.visible = false;
            this.btnApply.visible = false;
        } else {
            this.maxDepthBox.visible = true;
            this.implosionBox.visible = true;
            this.btnApply.visible = true;
            HullStrengthConfig.HullProperty prop = this.editedValues.get(entry.key);
            if (prop != null) {
                this.maxDepthBox.setValue(String.valueOf(prop.maxWaterDepth()));
                this.implosionBox.setValue(String.valueOf(prop.implosionChance()));
            }
        }
    }

    private void onApply(Button button) {
        if (this.selectedBlock == null)
            return;
        try {
            int depth = Integer.parseInt(this.maxDepthBox.getValue());
            float chance = Float.parseFloat(this.implosionBox.getValue());
            this.editedValues.put(this.selectedBlock.key, new HullStrengthConfig.HullProperty(depth, chance));
            this.dirtyKeys.add(this.selectedBlock.key);
        } catch (NumberFormatException ignored) {}
    }

    private void onSave() {
        if (this.minecraft.getConnection() != null) {
            Map<String, HullStrengthConfig.HullProperty> changed = new HashMap<>();
            for (String key : this.dirtyKeys) {
                HullStrengthConfig.HullProperty prop = this.editedValues.get(key);
                if (prop != null)
                    changed.put(key, prop);
            }
            if (!changed.isEmpty()) {
                PacketDistributor.sendToServer(
                        new HullConfigEditPayload(changed));
            }
        } else {
            this.editedValues.forEach((key, prop) ->
                    HullStrengthConfig.update(key, prop.maxWaterDepth(), prop.implosionChance()));
            HullStrengthConfig.save();
        }
        this.minecraft.setScreen(this.parentScreen);
    }

    private void onCancel() {
        this.minecraft.setScreen(this.parentScreen);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.drawString(this.font, this.title, 10, 15, 0xFFFFFFFF, false);

        int leftWidth = (int) (this.width * 0.4);
        g.fill(leftWidth, 30, leftWidth + 1, this.height - 40, 0x44FFFFFF);

        if (this.selectedBlock == null) {
            String text = Component.translatable("create_submarine.ui.text.select_block_tip").getString();
            int tw = this.font.width(text);
            g.drawString(this.font, text, leftWidth + (this.width - leftWidth - tw) / 2, this.height / 2, 0x88888888, false);
        } else {
            int rightX = leftWidth + 20;
            renderBlockIcon(g, this.selectedBlock.block, rightX, 40);
            g.drawString(this.font, this.selectedBlock.name, rightX + 25, 44, 0xFFFFFFFF, false);
            g.drawString(this.font, Component.literal(this.selectedBlock.key), rightX, 65, 0x88888888, false);

            g.drawString(this.font, Component.translatable("create_submarine.ui.text.max_water_depth"), rightX, 85, 0xFFAAAAAA, false);
            g.drawString(this.font, Component.translatable("create_submarine.ui.text.implosion_chance"), rightX, 135, 0xFFAAAAAA, false);
        }
    }

    private void renderBlockIcon(GuiGraphics g, Block block, int x, int y) {
        try {
            g.renderFakeItem(new ItemStack(block), x, y);
        } catch (RuntimeException e) {
        }
    }

    public class BlockList extends ObjectSelectionList<BlockEntry> {
        public BlockList(Minecraft mc, int width, int height, int y0, int itemHeight) {
            super(mc, width, height, y0, itemHeight);
        }

        public void clearList() {
            this.clearEntries();
        }

        public void addListEntry(BlockEntry entry) {
            this.addEntry(entry);
        }

        @Override
        public int getRowWidth() {
            return this.width - 10;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getX() + this.width - 6;
        }
    }

    public class BlockEntry extends ObjectSelectionList.Entry<BlockEntry> {
        public final String key;
        public final Block block;
        public final Component name;

        public BlockEntry(String key, Block block) {
            this.key = key;
            this.block = block;
            this.name = block.getName();
        }

        @Override
        public void render(@NotNull GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
            boolean selected = selectedBlock != null && selectedBlock.key.equals(this.key);
            if (selected) {
                g.fill(left - 2, top - 2, left + width + 2, top + height + 2, 0x33FFFFFF);
            } else if (isMouseOver) {
                g.fill(left - 2, top - 2, left + width + 2, top + height + 2, 0x11FFFFFF);
            }
            renderBlockIcon(g, this.block, left + 2, top + 2);
            g.drawString(font, this.name, left + 24, top + 6, 0xFFFFFFFF, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            setSelectedBlock(this);
            blockList.setSelected(this);
            return true;
        }

        @Override
        public @NotNull Component getNarration() {
            return this.name;
        }
    }
}
