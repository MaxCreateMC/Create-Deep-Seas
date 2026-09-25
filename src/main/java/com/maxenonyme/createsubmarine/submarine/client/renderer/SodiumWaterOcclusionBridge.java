package com.maxenonyme.createsubmarine.submarine.client.renderer;

import com.maxenonyme.createsubmarine.submarine.mixin.compat.WaterOcclusionRendererAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.AdvancedFboTextureAttachment;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;


public final class SodiumWaterOcclusionBridge {
    public static final int CLOSE_TEXTURE_UNIT = 6;
    public static final int FAR_TEXTURE_UNIT = 7;

    private static final String UNIFORM_ENABLED = "SableWaterOcclusionEnabled";
    private static final String UNIFORM_CLOSE_SAMPLER = "SableCloseSampler";
    private static final String UNIFORM_FAR_SAMPLER = "SableFarSampler";

    private static final Map<Integer, ProgramUniforms> UNIFORM_CACHE = new HashMap<>();

    public static volatile boolean PIXEL_PERFECT_ACTIVE = false;

    private record ProgramUniforms(int enabled, int closeSampler, int farSampler) {
        boolean hasOcclusion() {
            return enabled >= 0;
        }
    }

    private SodiumWaterOcclusionBridge() {
    }

    private static ProgramUniforms locations(int programHandle) {
        ProgramUniforms cached = UNIFORM_CACHE.get(programHandle);
        if (cached != null)
            return cached;
        ProgramUniforms u = new ProgramUniforms(
                GL20.glGetUniformLocation(programHandle, UNIFORM_ENABLED),
                GL20.glGetUniformLocation(programHandle, UNIFORM_CLOSE_SAMPLER),
                GL20.glGetUniformLocation(programHandle, UNIFORM_FAR_SAMPLER));
        UNIFORM_CACHE.put(programHandle, u);
        return u;
    }

    public static void applyToProgram(int programHandle, boolean translucentPass) {
        if (programHandle <= 0)
            return;
        ProgramUniforms u = locations(programHandle);

        if (!u.hasOcclusion()) {
            if (translucentPass)
                setPixelPerfect(false);
            return;
        }

        if (!translucentPass) {
            GL20.glUniform1f(u.enabled, 0.0f);
            return;
        }

        if (!WaterOcclusionRenderer.isEnabled()) {
            GL20.glUniform1f(u.enabled, 0.0f);
            setPixelPerfect(false);
            return;
        }

        try {
            WaterOcclusionRendererAccessor acc = (WaterOcclusionRendererAccessor) (Object) SableClient.WATER_OCCLUSION_RENDERER;
            AdvancedFbo close = acc.createsubmarine$getCloseBuffer();
            AdvancedFbo far = acc.createsubmarine$getFarBuffer();
            if (close == null || far == null) {
                GL20.glUniform1f(u.enabled, 0.0f);
                setPixelPerfect(false);
                return;
            }
            AdvancedFboTextureAttachment closeDepth = close.getDepthTextureAttachment();
            AdvancedFboTextureAttachment farDepth = far.getDepthTextureAttachment();
            if (closeDepth == null || farDepth == null) {
                GL20.glUniform1f(u.enabled, 0.0f);
                setPixelPerfect(false);
                return;
            }

            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + CLOSE_TEXTURE_UNIT);
            RenderSystem.bindTexture(closeDepth.getId());
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + FAR_TEXTURE_UNIT);
            RenderSystem.bindTexture(farDepth.getId());
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);

            if (u.closeSampler >= 0)
                GL20.glUniform1i(u.closeSampler, CLOSE_TEXTURE_UNIT);
            if (u.farSampler >= 0)
                GL20.glUniform1i(u.farSampler, FAR_TEXTURE_UNIT);
            GL20.glUniform1f(u.enabled, 1.0f);
            setPixelPerfect(true);
        } catch (Throwable t) {
            GL20.glUniform1f(u.enabled, 0.0f);
            setPixelPerfect(false);
        }
    }

    private static void setPixelPerfect(boolean active) {
        if (active == PIXEL_PERFECT_ACTIVE)
            return;
        PIXEL_PERFECT_ACTIVE = active;
        SubmarineWaterCullBuffer.invalidateAllPoseCaches();
    }
}
