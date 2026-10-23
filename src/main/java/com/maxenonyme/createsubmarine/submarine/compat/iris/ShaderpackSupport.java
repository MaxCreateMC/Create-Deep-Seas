package com.maxenonyme.createsubmarine.submarine.compat.iris;

import com.maxenonyme.createsubmarine.submarine.client.SubmarineFogHandler;
import com.maxenonyme.createsubmarine.submarine.compat.sodium.SodiumWaterOcclusionBridge;
import com.maxenonyme.createsubmarine.submarine.compat.sodium.WaterOcclusionPatch;
import com.maxenonyme.createsubmarine.submarine.mixin.compat.sable.WaterOcclusionRendererAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ryanhcode.sable.SableClient;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.AdvancedFboTextureAttachment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


//Sa sera ici que se feront le support des Shaderpacks, meme si certains on a un fonctionnement trés different,
//surtout avec photon et le TAA Upscaling



public class ShaderpackSupport {
    public static final String HULL_SAMPLER = "CdsHullFar";
    public static final String HULL_READY = "CdsHullReady";

    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(void)?\\s*\\)");

    private static final String WATER_BEHIND_HULL = """
            float cdsWaterBehindHull(vec2 cdsUV, float sceneDepth) {
                float hull = CdsHullReady > 0.5 ? texture2D(CdsHullFar, cdsUV).r : 1.0;
                if (hull >= 1.0 || sceneDepth <= hull)
                    return -1.0;
                if (sceneDepth >= 1.0)
                    return 4096.0;
                vec4 hullView = gbufferProjectionInverse * vec4(vec3(cdsUV, hull) * 2.0 - 1.0, 1.0);
                vec4 sceneView = gbufferProjectionInverse * vec4(vec3(cdsUV, sceneDepth) * 2.0 - 1.0, 1.0);
                return max(length(sceneView.xyz / sceneView.w) - length(hullView.xyz / hullView.w), 0.01);
            }

            """;

    private static boolean patched;
    private static boolean photon;

    public static String patchFragment(String source) {
        if (source == null || source.contains(HULL_SAMPLER))
            return source;

        String result = PhotonSupport.patch(source);
        if (result.equals(source)) {
            result = ComplementarySupport.patch(source);
            if (result.equals(source))
                result = BslSupport.patch(source);
            if (result.equals(source))
                result = BlissSupport.patch(source);
            if (!result.equals(source))
                result = beforeMain(result, WATER_BEHIND_HULL);
        } else {
            photon = true;
        }

        if (result == null || result.equals(source))
            return source;

        patched = true;
        return WaterOcclusionPatch.declare(result,
                "uniform sampler2D " + HULL_SAMPLER + ";\nuniform float " + HULL_READY + ";\n");
    }

    public static boolean lyingAboutWater() {
        return photon && SubmarineFogHandler.shouldFog() && packName().toLowerCase(Locale.ROOT).contains("photon");
    }

    public static void bindHull() {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (!patched || !SodiumWaterOcclusionBridge.holdsHull(program))
            return;

        int texture = SubmarineFogHandler.shouldFog() ? hullDepth() : 0;
        SodiumWaterOcclusionBridge.setHullReady(program, texture != 0);
        if (texture == 0)
            return;

        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + SodiumWaterOcclusionBridge.FAR_TEXTURE_UNIT);
        RenderSystem.bindTexture(texture);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
    }

    static boolean uses(String source, String... names) {
        for (String name : names) {
            if (!Pattern.compile("\\b" + name + "\\b").matcher(source).find())
                return false;
        }

        return true;
    }

    private static String beforeMain(String source, String function) {
        Matcher main = MAIN.matcher(source);
        if (!main.find())
            return null;

        return source.substring(0, main.start()) + function + source.substring(main.start());
    }

    private static String packName() {
        try {
            Object name = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName").invoke(null);
            return name == null ? "" : name.toString();
        } catch (ReflectiveOperationException e) {
            return "";
        }
    }

    private static int hullDepth() {
        AdvancedFbo far = ((WaterOcclusionRendererAccessor) (Object) SableClient.WATER_OCCLUSION_RENDERER)
                .createsubmarine$getFarBuffer();
        if (far == null)
            return 0;

        AdvancedFboTextureAttachment depth = far.getDepthTextureAttachment();
        return depth == null ? 0 : depth.getId();
    }
}
