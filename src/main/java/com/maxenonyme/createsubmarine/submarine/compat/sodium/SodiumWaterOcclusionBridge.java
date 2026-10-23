package com.maxenonyme.createsubmarine.submarine.compat.sodium;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compat.iris.ShaderpackSupport;
import com.maxenonyme.createsubmarine.submarine.mixin.compat.sable.WaterOcclusionRendererAccessor;
import com.maxenonyme.createsubmarine.submarine.client.renderer.SubmarineWaterCullBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.AdvancedFboTextureAttachment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL41;
import org.lwjgl.system.MemoryStack;

import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class SodiumWaterOcclusionBridge {
    public static final int CLOSE_TEXTURE_UNIT = 8;
    public static final int FAR_TEXTURE_UNIT = 9;

    private static final String UNIFORM_ENABLED = "SableWaterOcclusionEnabled";
    private static final String UNIFORM_CLOSE_SAMPLER = "SableCloseSampler";
    private static final String UNIFORM_FAR_SAMPLER = "SableFarSampler";

    private static final Map<Integer, ProgramUniforms> UNIFORM_CACHE = new HashMap<>();
    private static final Set<Integer> STARVED = ConcurrentHashMap.newKeySet();
    private static int maxTextureUnits;

    private static final Map<Integer, Integer> HULL_READY = new ConcurrentHashMap<>();

    private static Object irisApi;
    private static Method shadowPass;
    private static boolean irisResolved;

    public static volatile boolean PIXEL_PERFECT_ACTIVE = false;
    public static final Set<Long> FALLBACK_HOLES = ConcurrentHashMap.newKeySet();

    private record ProgramUniforms(int enabled, int closeSampler, int farSampler) {
        boolean hasOcclusion() {
            return enabled >= 0;
        }
    }

    private static ProgramUniforms locations(int programHandle) {
        ProgramUniforms cached = UNIFORM_CACHE.get(programHandle);
        if (cached != null)
            return cached;

        ProgramUniforms u = new ProgramUniforms(-1, -1, -1);
        if (!STARVED.contains(programHandle))
            u = new ProgramUniforms(
                    GL20.glGetUniformLocation(programHandle, UNIFORM_ENABLED),
                    GL20.glGetUniformLocation(programHandle, UNIFORM_CLOSE_SAMPLER),
                    GL20.glGetUniformLocation(programHandle, UNIFORM_FAR_SAMPLER));

        UNIFORM_CACHE.put(programHandle, u);
        return u;
    }

    public static Set<Integer> reserveUnits(int program, Set<Integer> reserved) {
        UNIFORM_CACHE.remove(program);
        STARVED.remove(program);
        HULL_READY.remove(program);

        int close = GL20.glGetUniformLocation(program, UNIFORM_CLOSE_SAMPLER);
        int far = GL20.glGetUniformLocation(program, UNIFORM_FAR_SAMPLER);
        int hull = GL20.glGetUniformLocation(program, ShaderpackSupport.HULL_SAMPLER);
        int ready = GL20.glGetUniformLocation(program, ShaderpackSupport.HULL_READY);
        if (close < 0 && far < 0 && hull < 0)
            return reserved;

        Set<Integer> units = new HashSet<>(reserved);
        if (close >= 0)
            units.add(CLOSE_TEXTURE_UNIT);
        if (far >= 0 || hull >= 0)
            units.add(FAR_TEXTURE_UNIT);

        int ours = 0;
        for (int location : new int[]{close, far, hull})
            if (location >= 0)
                ours++;

        if (samplerUnits(program) - ours + units.size() > maxTextureUnits()) {
            STARVED.add(program);
            CreateSubmarine.LOGGER.warn("Shader program {} uses too many textures to fit the hull culling on this "
                    + "GPU ({} units), leaving it out", program, maxTextureUnits());
            return reserved;
        }

        if (hull >= 0)
            GL41.glProgramUniform1i(program, hull, FAR_TEXTURE_UNIT);
        if (ready >= 0)
            HULL_READY.put(program, ready);

        return units;
    }

    public static void setHullReady(boolean ready) {
        HULL_READY.forEach((program, location) -> GL41.glProgramUniform1f(program, location, ready ? 1f : 0f));
    }

    private static int samplerUnits(int program) {
        int count = 0;
        int uniforms = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer size = stack.mallocInt(1);
            IntBuffer type = stack.mallocInt(1);
            for (int i = 0; i < uniforms; i++) {
                GL20.glGetActiveUniform(program, i, size, type);
                if (sampler(type.get(0)))
                    count += size.get(0);
            }
        }

        return count;
    }

    private static boolean sampler(int type) {
        return type >= 0x8B5D && type <= 0x8B64 || type >= 0x8DC0 && type <= 0x8DC5
                || type >= 0x8DC9 && type <= 0x8DD8 || type >= 0x900C && type <= 0x900F
                || type >= 0x9108 && type <= 0x910D;
    }

    private static int maxTextureUnits() {
        if (maxTextureUnits == 0)
            maxTextureUnits = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS);
        return maxTextureUnits;
    }

    public static void applyToProgram(int programHandle, boolean translucentPass) {
        if (programHandle <= 0)
            return;

        ProgramUniforms u = locations(programHandle);

        if (!u.hasOcclusion()) {
            if (translucentPass && !renderingShadows())
                setPixelPerfect(false);
            return;
        }

        if (renderingShadows()) {
            GL20.glUniform1f(u.enabled, 0.0f);
            return;
        }

        if (!translucentPass) {
            GL20.glUniform1f(u.enabled, PIXEL_PERFECT_ACTIVE && bind(u) ? 1.0f : 0.0f);
            return;
        }

        boolean bound = WaterOcclusionRenderer.isEnabled() && bind(u);
        GL20.glUniform1f(u.enabled, bound ? 1.0f : 0.0f);
        setPixelPerfect(bound);
    }

//ne fonctionne pas sur la version alpha 0.9.3 :(


    private static boolean bind(ProgramUniforms u) {
        try {

            WaterOcclusionRendererAccessor acc = (WaterOcclusionRendererAccessor) (Object) SableClient.WATER_OCCLUSION_RENDERER;
            AdvancedFbo close = acc.createsubmarine$getCloseBuffer();
            AdvancedFbo far = acc.createsubmarine$getFarBuffer();
            if (close == null || far == null)
                return false;

            AdvancedFboTextureAttachment closeDepth = close.getDepthTextureAttachment();
            AdvancedFboTextureAttachment farDepth = far.getDepthTextureAttachment();
            if (closeDepth == null || farDepth == null)
                return false;

            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + CLOSE_TEXTURE_UNIT);
            RenderSystem.bindTexture(closeDepth.getId());
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + FAR_TEXTURE_UNIT);
            RenderSystem.bindTexture(farDepth.getId());
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);

            if (u.closeSampler >= 0)
                GL20.glUniform1i(u.closeSampler, CLOSE_TEXTURE_UNIT);
            if (u.farSampler >= 0)
                GL20.glUniform1i(u.farSampler, FAR_TEXTURE_UNIT);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean renderingShadows() {
        if (!irisResolved) {
            irisResolved = true;

            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                shadowPass = api.getMethod("isRenderingShadowPass");
            } catch (ReflectiveOperationException e) {
                irisApi = null;
            }
        }

        if (irisApi == null)
            return false;

        try {
            return (boolean) shadowPass.invoke(irisApi);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static void setPixelPerfect(boolean active) {
        if (active == PIXEL_PERFECT_ACTIVE)
            return;

        PIXEL_PERFECT_ACTIVE = active;
        SubmarineWaterCullBuffer.invalidateAllPoseCaches();
    }
}
