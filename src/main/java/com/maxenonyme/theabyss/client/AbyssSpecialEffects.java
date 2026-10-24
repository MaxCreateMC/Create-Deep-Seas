package com.maxenonyme.theabyss.client;

import com.maxenonyme.theabyss.dimension.AbyssLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class AbyssSpecialEffects extends DimensionSpecialEffects {

    private static final float DEPTH_FALLOFF = 180;
    private static final float NIGHT = 0.25f;
    private static final float STRENGTH = 0.7f;

    public AbyssSpecialEffects() {
        super(Float.NaN, false, DimensionSpecialEffects.SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
        return biomeFogColor;
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTick, float skyDarken, float flicker,
            float skyLight, int blockLight, int skyLevel, Vector3f colors) {
        float ambient = STRENGTH * daylight(level, partialTick);
        colors.set(Math.min(1, colors.x + ambient * 0.55f), Math.min(1, colors.y + ambient * 0.8f),
                Math.min(1, colors.z + ambient));
    }

    public static float daylight(ClientLevel level, float partialTick) {
        double eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().y;
        double below = Math.max(0, AbyssLayout.SUNLIGHT_SURFACE - eye);
        float depth = (float) Math.exp(-below / DEPTH_FALLOFF);

        float sun = Mth.cos(level.getTimeOfDay(partialTick) * Mth.TWO_PI);
        float day = Mth.clamp(sun * 2 + 0.5f, NIGHT, 1);
        return depth * day;
    }
}
