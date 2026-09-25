package com.maxenonyme.highseas.helm;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import dev.ryanhcode.sable.mixinterface.camera.camera_zoom.CameraZoomExtension;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import com.maxenonyme.highseas.client.BoatEngineSoundHandler;

public final class HelmClientHandler {
    private HelmClientHandler() {
    }

    private static final int RESEND_INTERVAL = 5;
    private static final int RELEASE_LOCKOUT = 10;
    private static final int CAMERA_ATTEMPTS = 40;

    private static boolean engaged;
    private static BlockPos enginePos;
    private static CameraType stashedCamera;
    private static CameraType appliedCamera;
    private static byte lastMask;
    private static int resendCooldown;
    private static int orientCountdown;
    private static int cameraAttempts;
    private static boolean cameraSettled;
    private static int releaseLockout;

    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (releaseLockout > 0)
            releaseLockout--;
        if (mc.player == null || mc.level == null) {
            if (engaged)
                disengage(mc);
            return;
        }

        boolean seated = mc.player.getVehicle() instanceof HelmSeatEntity;
        if (!seated) {
            if (engaged)
                disengage(mc);
            return;
        }
        HelmSeatEntity seat = (HelmSeatEntity) mc.player.getVehicle();
        if (!engaged) {
            if (releaseLockout > 0)
                return;
            engage(mc, seat);
        }
        tickCamera(mc, seat);

        if (mc.screen != null) {
            send((byte) 0);
            suppress();
            return;
        }
        if (rawPressed(mc.options.keyShift)) {
            send((byte) 0);
            suppress();
            requestRelease(mc);
            return;
        }

        byte mask = 0;
        if (rawPressed(mc.options.keyUp))
            mask |= HelmInput.FORWARD;
        if (rawPressed(mc.options.keyDown))
            mask |= HelmInput.BACKWARD;
        if (rawPressed(mc.options.keyLeft))
            mask |= HelmInput.LEFT;
        if (rawPressed(mc.options.keyRight))
            mask |= HelmInput.RIGHT;
        send(mask);
        suppress();
    }

    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;

        if (engaged && orientCountdown > 0 && --orientCountdown == 0 && mc.player != null
                && mc.player.getVehicle() instanceof HelmSeatEntity seat) {
            float yaw = seat.getFacing().toYRot();
            mc.player.setYRot(yaw);
            mc.player.yRotO = yaw;
            mc.player.setYHeadRot(yaw);
            mc.player.yHeadRotO = yaw;
            mc.player.setXRot(8.0f);
            mc.player.xRotO = 8.0f;
        }
    }

    public static void onRenderHand(RenderHandEvent event) {
        if (engaged)
            event.setCanceled(true);
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        HelmClient.clear();
        BoatEngineSoundHandler.clear();
        Minecraft mc = Minecraft.getInstance();
        if (engaged)
            disengage(mc);
    }

    private static void engage(Minecraft mc, HelmSeatEntity seat) {
        engaged = true;
        enginePos = seat.getEnginePos();
        lastMask = 0;
        resendCooldown = 0;
        cameraAttempts = 0;
        cameraSettled = false;
        orientCountdown = 2;
        stashedCamera = mc.options.getCameraType();
        appliedCamera = null;
    }

    private static void tickCamera(Minecraft mc, HelmSeatEntity seat) {
        if (cameraSettled)
            return;
        if (appliedCamera != null && mc.options.getCameraType() == appliedCamera) {
            cameraSettled = true;
            return;
        }
        if (++cameraAttempts > CAMERA_ATTEMPTS) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            appliedCamera = CameraType.THIRD_PERSON_BACK;
            cameraSettled = true;
            return;
        }
        if (!gateOpen(mc, seat))
            return;
        applyCamera(mc);
    }

    private static boolean gateOpen(Minecraft mc, HelmSeatEntity seat) {
        return mc.level != null && SableCompanion.INSTANCE.getContaining(mc.level, seat.position()) != null;
    }

    private static void disengage(Minecraft mc) {
        engaged = false;
        enginePos = null;
        orientCountdown = 0;
        cameraSettled = false;
        cameraAttempts = 0;
        restoreKeys();
        if (appliedCamera != null && mc.options.getCameraType() == appliedCamera)
            mc.options.setCameraType(stashedCamera == null ? CameraType.FIRST_PERSON : stashedCamera);
        setZoom(mc, 0.0f);
        appliedCamera = null;
        stashedCamera = null;
    }

    private static void requestRelease(Minecraft mc) {
        PacketDistributor.sendToServer(HelmReleasePayload.INSTANCE);
        releaseLockout = RELEASE_LOCKOUT;
        disengage(mc);
    }

    private static void applyCamera(Minecraft mc) {
        try {
            mc.options.setCameraType(SableCameraTypes.SUB_LEVEL_VIEW);
            appliedCamera = SableCameraTypes.SUB_LEVEL_VIEW;
            setZoom(mc, 0.6f);
        } catch (LinkageError e) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            appliedCamera = CameraType.THIRD_PERSON_BACK;
        }
    }

    private static void setZoom(Minecraft mc, float amount) {
        if (mc.gameRenderer.getMainCamera() instanceof CameraZoomExtension zoom)
            zoom.sable$setZoomAmount(amount);
    }

    private static void send(byte mask) {
        if (enginePos == null)
            return;
        if (resendCooldown > 0)
            resendCooldown--;
        if (mask == lastMask && resendCooldown > 0)
            return;
        PacketDistributor.sendToServer(new HelmInputPayload(enginePos, mask));
        lastMask = mask;
        resendCooldown = RESEND_INTERVAL;
    }

    private static void suppress() {
        for (KeyMapping mapping : controls())
            mapping.setDown(false);
    }

    private static void restoreKeys() {
        for (KeyMapping mapping : controls())
            mapping.setDown(rawPressed(mapping));
    }

    private static KeyMapping[] controls;

    private static KeyMapping[] controls() {
        if (controls == null) {
            Minecraft mc = Minecraft.getInstance();
            controls = new KeyMapping[] { mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight,
                    mc.options.keyJump, mc.options.keyShift, mc.options.keySprint };
        }
        return controls;
    }

    private static boolean rawPressed(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        int value = key.getValue();
        if (value == InputConstants.UNKNOWN.getValue())
            return false;
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE)
            return GLFW.glfwGetMouseButton(window, value) == GLFW.GLFW_PRESS;
        return InputConstants.isKeyDown(window, value);
    }
}
