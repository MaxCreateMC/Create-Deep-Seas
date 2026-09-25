package com.maxenonyme.createsubmarine.submarine.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.caffeinemc.mods.sodium.client.gl.shader.GlShader;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderConstants;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;

@Pseudo
@Mixin(value = ShaderLoader.class, remap = false, priority = 2000)
public abstract class SodiumShaderLoaderMixin {

    private static final String UNIFORM_BLOCK = "uniform sampler2D SableCloseSampler;\n" +
            "uniform sampler2D SableFarSampler;\n" +
            "uniform float SableWaterOcclusionEnabled;\n";

    private static final String DISCARD_BLOCK = "    if (SableWaterOcclusionEnabled > 0.0) {\n" +
            "        float _csubCloseDepth = texelFetch(SableCloseSampler, ivec2(gl_FragCoord.xy), 0).r;\n" +
            "        float _csubFarDepth = texelFetch(SableFarSampler, ivec2(gl_FragCoord.xy), 0).r;\n" +
            "        float _csubFragDepth = gl_FragCoord.z;\n" +
            "        if (_csubCloseDepth < 1.0 && _csubFragDepth > _csubCloseDepth && _csubFragDepth < _csubFarDepth) {\n" +
            "            discard;\n" +
            "        }\n" +
            "    }\n";

    private static final Pattern SAFE_DIRECTIVE = Pattern.compile("^\\s*#(version|extension|define|undef|pragma|line|error|if|ifdef|ifndef|else|elif|endif)\\b");
    private static final Pattern MAIN_SIGNATURE = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(void)?\\s*\\)\\s*\\{");

    private static final ThreadLocal<Boolean> createsubmarine$bypass = ThreadLocal.withInitial(() -> false);

    @WrapMethod(method = "loadShader", remap = false)
    private static GlShader createsubmarine$fallBackWithoutOcclusion(ShaderType type, ResourceLocation name,
            ShaderConstants constants, Operation<GlShader> original) {
        try {
            return original.call(type, name, constants);
        } catch (RuntimeException e) {
            if (createsubmarine$bypass.get() || !isTarget(name.getPath()))
                throw e;
            CreateSubmarine.LOGGER.warn("Sodium could not compile {} with the water occlusion patch, loading it untouched instead", name, e);
            createsubmarine$bypass.set(true);
            try {
                return original.call(type, name, constants);
            } finally {
                createsubmarine$bypass.set(false);
            }
        }
    }

    @ModifyReturnValue(method = "getShaderSource", at = @At("RETURN"), remap = false)
    private static String createsubmarine$injectOcclusion(String source, ResourceLocation location) {
        if (createsubmarine$bypass.get() || source == null || !isTarget(location.getPath())
                || source.contains("SableWaterOcclusionEnabled")) {
            return source;
        }

        int insertPos = findAfterPreprocessor(source);
        String head = source.substring(0, insertPos);
        String body = injectIntoMain(source.substring(insertPos));
        if (body == null)
            return source;
        return head + UNIFORM_BLOCK + body;
    }

    private static boolean isTarget(String path) {
        return path.endsWith(".fsh") && path.contains("block_layer");
    }

    private static int findAfterPreprocessor(String source) {
        int pos = 0;
        boolean inBlockComment = false;
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            boolean skip;
            if (inBlockComment) {
                skip = true;
                if (trimmed.contains("*/")) inBlockComment = false;
            } else if (trimmed.startsWith("/*")) {
                skip = true;
                if (!trimmed.contains("*/")) inBlockComment = true;
            } else {
                skip = trimmed.isEmpty() || trimmed.startsWith("//") || SAFE_DIRECTIVE.matcher(trimmed).find();
            }
            if (!skip) break;
            pos += line.length() + 1;
        }
        return Math.min(pos, source.length());
    }

    private static String injectIntoMain(String source) {
        Matcher main = MAIN_SIGNATURE.matcher(source);
        if (!main.find())
            return null;
        int open = main.end() - 1;
        int close = matchingBrace(source, open);
        if (close < 0)
            return null;
        return source.substring(0, close) + "\n" + DISCARD_BLOCK + source.substring(close);
    }

    private static int matchingBrace(String source, int open) {
        int depth = 0;
        int length = source.length();
        for (int i = open; i < length; i++) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < length) {
                char next = source.charAt(i + 1);
                if (next == '/') {
                    int eol = source.indexOf('\n', i);
                    if (eol < 0)
                        return -1;
                    i = eol;
                    continue;
                }
                if (next == '*') {
                    int end = source.indexOf("*/", i + 2);
                    if (end < 0)
                        return -1;
                    i = end + 1;
                    continue;
                }
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0)
                    return i;
            }
        }
        return -1;
    }
}
