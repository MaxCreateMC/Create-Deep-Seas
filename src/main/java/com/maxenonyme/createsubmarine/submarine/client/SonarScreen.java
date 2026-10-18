package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.submarine.block.entity.CommandSubBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class SonarScreen extends Screen {
    private static final int INK = 0xFF4F5257;
    private static final int LINE = 0xFF2E3032;
    private static final int PAPER = 0xFFF7F0DD;
    private static final int DULL = 0xFFB5B1A8;
    private static final int DANGER = 0xFFA8433C;
    private static final int MAX_TEXTURE = 1600;

    private final CommandSubBlockEntity console;
    private int left, top, right, bottom;
    private int mapLeft, mapTop, mapRight, mapBottom;
    private int recenterLeft, recenterRight;
    private int dragButton = -1;
    private double lastX, lastY;

    public SonarScreen(CommandSubBlockEntity console) {
        super(Component.translatable("create_submarine.command_sub.tab.sonar"));
        this.console = console;
    }

    @Override
    protected void init() {
        int w = Math.min(width - 20, 520);
        int h = Math.min(height - 20, 340);
        left = (width - w) / 2;
        top = (height - h) / 2;
        right = left + w;
        bottom = top + h;
        mapLeft = left + 8;
        mapTop = top + 18;
        mapRight = right - 8;
        mapBottom = bottom - 16;
        String recenter = Component.translatable("create_submarine.command_sub.recenter").getString();
        recenterRight = right - 8;
        recenterLeft = recenterRight - font.width(recenter) - 8;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (console.isRemoved() || mc.player == null
                || Sable.HELPER.distanceSquaredWithSubLevels(mc.level, mc.player.getEyePosition(),
                        Vec3.atCenterOf(console.getBlockPos())) > 64)
            onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        double scale = minecraft.getWindow().getGuiScale();
        int texWidth = Math.min(MAX_TEXTURE, (int) Math.round((mapRight - mapLeft) * scale));
        int texHeight = Math.min(MAX_TEXTURE, (int) Math.round((mapBottom - mapTop) * scale));
        followDrag((double) texWidth / Math.max(1, mapRight - mapLeft));

        graphics.fill(left, top, right, bottom, PAPER);
        graphics.renderOutline(left, top, right - left, bottom - top, LINE);
        graphics.drawString(font, title, left + 8, top + 6, INK, false);

        boolean recenterHover = mouseX >= recenterLeft && mouseX <= recenterRight && mouseY >= top + 4 && mouseY <= top + 15;
        graphics.fill(recenterLeft, top + 4, recenterRight, top + 15, recenterHover ? INK : PAPER);
        graphics.renderOutline(recenterLeft, top + 4, recenterRight - recenterLeft, 11, recenterHover ? INK : LINE);
        graphics.drawCenteredString(font, Component.translatable("create_submarine.command_sub.recenter"),
                (recenterLeft + recenterRight) / 2, top + 6, recenterHover ? PAPER : INK);

        graphics.renderOutline(mapLeft - 1, mapTop - 1, mapRight - mapLeft + 2, mapBottom - mapTop + 2, DULL);
        SonarScan scan = SonarView.detail(console, texWidth, texHeight);
        int texture = scan.textureId();
        if (texture >= 0) {
            graphics.flush();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, texture);
            Matrix4f pose = graphics.pose().last().pose();
            float u0 = scan.u0();
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(pose, mapLeft, mapTop, 0).setUv(u0, 1);
            builder.addVertex(pose, mapLeft, mapBottom, 0).setUv(u0, 0);
            builder.addVertex(pose, mapRight, mapBottom, 0).setUv(1, 0);
            builder.addVertex(pose, mapRight, mapTop, 0).setUv(1, 1);
            BufferUploader.drawWithShader(builder.buildOrThrow());
            RenderSystem.disableBlend();
        }

        Component mineLabel = Component.translatable("create_submarine.command_sub.mine");
        for (SonarContact contact : scan.contacts()) {
            int cx = mapLeft + Math.round((mapRight - mapLeft) * contact.u());
            int cy = mapTop + Math.round((mapBottom - mapTop) * contact.v());
            int r = contact.kind() == SonarContact.VESSEL ? 3 : 2;
            graphics.fill(cx - r, cy - r, cx + r + 1, cy + r + 1, contact.color());
            if (contact.kind() == SonarContact.MINE)
                graphics.drawString(font, mineLabel, cx + 5, cy - 4, DANGER, false);
        }

        if (mouseX >= mapLeft && mouseX <= mapRight && mouseY >= mapTop && mouseY <= mapBottom) {
            SonarContact hovered = SonarContact.nearest(scan.contacts(),
                    (mouseX - mapLeft) / (float) (mapRight - mapLeft), (mouseY - mapTop) / (float) (mapBottom - mapTop),
                    6f / (mapRight - mapLeft), 6f / (mapBottom - mapTop));
            if (hovered != null)
                graphics.renderTooltip(font, hovered.name().copy().withStyle(style -> style.withColor(hovered.color())),
                        mouseX, mouseY);
        }

        Component floor = SonarView.floorLabel(scan.floor());
        graphics.fill(mapLeft + 2, mapBottom - 14, mapLeft + 10 + font.width(floor), mapBottom - 2, PAPER);
        graphics.renderOutline(mapLeft + 2, mapBottom - 14, font.width(floor) + 8, 12, LINE);
        graphics.drawString(font, floor, mapLeft + 6, mapBottom - 12, INK, false);

        graphics.drawCenteredString(font, Component.translatable("create_submarine.command_sub.sonar_controls"),
                (left + right) / 2, bottom - 12, INK);
    }

    private void followDrag(double toTexture) {
        if (dragButton < 0)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), dragButton) != GLFW.GLFW_PRESS) {
            dragButton = -1;
            return;
        }
        double x = mc.mouseHandler.xpos() * width / mc.getWindow().getScreenWidth();
        double y = mc.mouseHandler.ypos() * height / mc.getWindow().getScreenHeight();
        if (dragButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            SonarView.orbit(console, x - lastX);
        else
            SonarView.drag(console, (x - lastX) * toTexture, (y - lastY) * toTexture);
        lastX = x;
        lastY = y;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= recenterLeft && mouseX <= recenterRight && mouseY >= top + 4 && mouseY <= top + 15) {
            console.sonarPanX = 0;
            console.sonarPanZ = 0;
            console.sonarOrbit = 0;
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25f, 1.4f);
            return true;
        }
        if ((button == 0 || button == 1) && mouseX >= mapLeft && mouseX <= mapRight && mouseY >= mapTop
                && mouseY <= mapBottom) {
            dragButton = button;
            lastX = mouseX;
            lastY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == dragButton)
            dragButton = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        SonarView.zoom(console, scrollY);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
