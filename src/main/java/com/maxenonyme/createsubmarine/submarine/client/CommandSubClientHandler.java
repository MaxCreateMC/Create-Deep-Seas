package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.submarine.block.entity.CommandSubBlockEntity;
import com.maxenonyme.createsubmarine.submarine.block.entity.renderer.CommandSubRenderer;
import com.maxenonyme.createsubmarine.submarine.network.CommandSubPayload;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public final class CommandSubClientHandler {
    private CommandSubClientHandler() {
    }

    public static final int FIELD = 0;
    public static final int SPEED_FIRST = 1;

    private static final int MAX_DIGITS = 4;

    private static CommandSubBlockEntity hoveredConsole;
    private static int hoveredWidget = -1;

    private static boolean clickLatched;

    private static CommandSubBlockEntity typingConsole;
    private static final StringBuilder typed = new StringBuilder();

    public static int hoveredWidget(CommandSubBlockEntity be) {
        return be == hoveredConsole ? hoveredWidget : -1;
    }

    public static String typedFor(CommandSubBlockEntity be) {
        return be == typingConsole ? typed.toString() : null;
    }

    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (typingConsole != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (!event.isAttack())
            return;
        Minecraft mc = Minecraft.getInstance();
        boolean onConsole = mc.hitResult instanceof BlockHitResult bhr && mc.hitResult.getType() == HitResult.Type.BLOCK
                && mc.level.getBlockEntity(bhr.getBlockPos()) instanceof CommandSubBlockEntity;
        if (onConsole && mc.player.isShiftKeyDown())
            return;

        if (hoveredConsole == null) {
            if (onConsole) {
                event.setCanceled(true);
                event.setSwingHand(false);
                warnSneakToBreak(mc);
            }
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (hoveredWidget < 0) {
            if (!mc.player.isShiftKeyDown())
                warnSneakToBreak(mc);
            return;
        }
        if (clickLatched)
            return;
        clickLatched = true;

        mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25f, 1.4f);
        if (hoveredWidget == FIELD) {
            typingConsole = hoveredConsole;
            typed.setLength(0);
            KeyMapping.releaseAll();
            mc.player.displayClientMessage(Component.translatable("create_submarine.command_sub.typing"), true);
            return;
        }
        PacketDistributor.sendToServer(new CommandSubPayload(hoveredConsole.getBlockPos(), CommandSubPayload.SPEED,
                hoveredWidget - SPEED_FIRST));
    }

    private static void warnSneakToBreak(Minecraft mc) {
        mc.player.displayClientMessage(Component.literal("✖ ")
                .append(Component.translatable("create_submarine.command_sub.sneak_to_break"))
                .withStyle(ChatFormatting.RED), true);
    }

    public static void onKey(InputEvent.Key event) {
        if (typingConsole == null)
            return;
        Minecraft mc = Minecraft.getInstance();
        int key = event.getKey();
        int action = event.getAction();

        if (action != GLFW.GLFW_RELEASE) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
                    || mc.options.keyShift.matches(key, event.getScanCode())) {
                confirm();
                if (key == GLFW.GLFW_KEY_ESCAPE)
                    mc.setScreen(null);
            } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!typed.isEmpty())
                    typed.setLength(typed.length() - 1);
            } else if (action == GLFW.GLFW_PRESS && typed.length() < MAX_DIGITS) {
                int digit = key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9 ? key - GLFW.GLFW_KEY_0
                        : key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9 ? key - GLFW.GLFW_KEY_KP_0 : -1;
                if (digit >= 0) {
                    typed.append(digit);
                    mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.15f, 1.8f);
                }
            }
        }

        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping.matches(key, event.getScanCode())) {
                while (mapping.consumeClick()) {
                }
                mapping.setDown(false);
            }
        }
    }

    private static void confirm() {
        CommandSubBlockEntity be = typingConsole;
        typingConsole = null;
        if (be == null || be.isRemoved() || typed.isEmpty())
            return;
        int value = Integer.parseInt(typed.toString());
        typed.setLength(0);
        PacketDistributor.sendToServer(new CommandSubPayload(be.getBlockPos(), CommandSubPayload.DEPTH, value));
        Minecraft.getInstance().player.displayClientMessage(
                Component.translatable("create_submarine.command_sub.depth_set", value), true);
    }

    private static void cancelTyping() {
        typingConsole = null;
        typed.setLength(0);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        hoveredConsole = null;
        hoveredWidget = -1;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyAttack.isDown())
            clickLatched = false;
        if (typingConsole != null && (mc.player == null || typingConsole.isRemoved() || mc.screen != null
                || Sable.HELPER.distanceSquaredWithSubLevels(mc.level, mc.player.getEyePosition(),
                        Vec3.atCenterOf(typingConsole.getBlockPos())) > 64))
            cancelTyping();
        if (mc.player == null || mc.level == null || mc.screen != null)
            return;

        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1f);
        double reach = mc.player.blockInteractionRange();
        HitResult hit = mc.hitResult;
        BlockPos hitPos = hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK ? bhr.getBlockPos() : null;

        double best = Double.MAX_VALUE;
        for (CommandSubBlockEntity be : CommandSubBlockEntity.LOADED_ON_CLIENT) {
            if (be.isRemoved() || be.getLevel() != mc.level)
                continue;
            BlockPos pos = be.getBlockPos();
            Vec3 from = eye;
            Vec3 dir = look;
            SubLevel sub = Sable.HELPER.getContaining(be);
            if (sub instanceof ClientSubLevel csl) {
                from = csl.renderPose().transformPositionInverse(eye);
                dir = csl.renderPose().transformNormalInverse(look);
            }
            if (from.distanceToSqr(Vec3.atCenterOf(pos)) > 64)
                continue;

            double limit = reach;
            if (hitPos != null && !hitPos.equals(pos) && sub == null)
                limit = Math.min(limit, hit.getLocation().distanceTo(eye) + 0.05);

            Matrix4f toCanvas = CommandSubRenderer.canvasToBlock(be.getBlockState()).invert();
            Vector3f a = toCanvas.transformPosition(new Vector3f(
                    (float) (from.x - pos.getX()), (float) (from.y - pos.getY()), (float) (from.z - pos.getZ())));
            Vector3f b = toCanvas.transformPosition(new Vector3f(
                    (float) (from.x + dir.x * limit - pos.getX()),
                    (float) (from.y + dir.y * limit - pos.getY()),
                    (float) (from.z + dir.z * limit - pos.getZ())));
            if (a.z <= 0 || b.z >= a.z)
                continue;
            float t = a.z / (a.z - b.z);
            if (t > 1)
                continue;
            float u = a.x + (b.x - a.x) * t;
            float v = a.y + (b.y - a.y) * t;
            if (u < 0 || v < 0 || u > CommandSubRenderer.W || v > CommandSubRenderer.H)
                continue;
            if (t * limit < best) {
                best = t * limit;
                hoveredConsole = be;
                hoveredWidget = widgetAt(u, v);
            }
        }
    }

    private static int widgetAt(float u, float v) {
        if (inside(CommandSubRenderer.FIELD, u, v))
            return FIELD;
        for (int i = 0; i < CommandSubRenderer.SPEEDS.length; i++) {
            if (inside(CommandSubRenderer.SPEEDS[i], u, v))
                return SPEED_FIRST + i;
        }
        return -1;
    }

    private static boolean inside(float[] r, float u, float v) {
        return u >= r[0] && u <= r[2] && v >= r[1] && v <= r[3];
    }
}
