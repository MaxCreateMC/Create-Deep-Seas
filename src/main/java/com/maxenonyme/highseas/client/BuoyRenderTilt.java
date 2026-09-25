package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.block.entity.BuoySeatEntity;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Vector3f;

public final class BuoyRenderTilt {
    private BuoyRenderTilt() {
    }

    private static final float RECLINE = 40.0f;
    private static final double PIVOT = 0.7;
    private static final double BUMP = 0.28;

    public static void onPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player.getVehicle() instanceof BuoySeatEntity)) {
            return;
        }
        float yaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vector3f lateral = new Vector3f(-Mth.cos(yaw), 0.0f, -Mth.sin(yaw));

        event.getPoseStack().pushPose();
        event.getPoseStack().translate(0.0, PIVOT + BUMP * BuoyRideEffects.jolt(), 0.0);
        event.getPoseStack().mulPose(Axis.of(lateral).rotationDegrees(RECLINE));
        event.getPoseStack().translate(0.0, -PIVOT, 0.0);
    }

    public static void onPost(RenderPlayerEvent.Post event) {
        if (event.getEntity().getVehicle() instanceof BuoySeatEntity) {
            event.getPoseStack().popPose();
        }
    }
}
