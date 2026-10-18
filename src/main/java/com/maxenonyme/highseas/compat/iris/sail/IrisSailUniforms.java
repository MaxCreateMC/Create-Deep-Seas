package com.maxenonyme.highseas.compat.iris.sail;

import com.maxenonyme.highseas.block.BoatSailBlock;
import com.maxenonyme.highseas.client.SailShaderState;
import com.maxenonyme.highseas.compat.iris.IrisCompat;
import com.maxenonyme.highseas.config.HighSeasConfig;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL41C;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class IrisSailUniforms {

    private static final Map<ShaderInstance, Object2IntOpenHashMap<String>> LOCATIONS = new WeakHashMap<>();
    private static List<Block> sails;
    private static BakedModel spriteSource;
    private static float[] sprites = new float[0];

    public static boolean wanted(RenderType layer) {
        return layer == RenderType.cutout() && HighSeasConfig.shaderpackSails && IrisCompat.isShaderPackActive();
    }

    public static int cells() {
        if (!HighSeasConfig.shaderpackSails || !IrisCompat.isShaderPackActive())
            return 1;
        if (IrisCompat.packName().toLowerCase(Locale.ROOT).contains("rethinking"))
            return 1;
        return HighSeasConfig.sailSubdivisions;
    }

    public static void push(ClientSubLevel sub, ShaderInstance shader, Pose3dc pose, double camX, double camY,
            double camZ) {
        int program = shader.getId();
        SailShaderState.Uniforms out = new SailShaderState.Uniforms() {
            @Override
            public void set(String name, float x) {
                GL41C.glProgramUniform1f(program, location(shader, name), x);
            }

            @Override
            public void set(String name, int x) {
                GL41C.glProgramUniform1i(program, location(shader, name), x);
            }

            @Override
            public void set(String name, float x, float y, float z) {
                GL41C.glProgramUniform3f(program, location(shader, name), x, y, z);
            }

            @Override
            public void set(String name, float x, float y, float z, float w) {
                GL41C.glProgramUniform4f(program, location(shader, name), x, y, z, w);
            }
        };

        SailShaderState.prepareForSublevel(sub, out, pose, camX, camY, camZ);

        float[] rects = sprites();
        int count = rects.length / 4;

        for (int i = 0; i < count; i++)
            out.set("sprites[" + i + "]", rects[i * 4], rects[i * 4 + 1], rects[i * 4 + 2], rects[i * 4 + 3]);
        out.set("spriteCount", count);
    }

    public static void reset(ShaderInstance shader) {
        GL41C.glProgramUniform1i(shader.getId(), location(shader, "sailCount"), 0);
    }

    private static int location(ShaderInstance shader, String name) {
        Object2IntOpenHashMap<String> known = LOCATIONS.computeIfAbsent(shader, k -> new Object2IntOpenHashMap<>());
        return known.computeIfAbsent(name, k -> GL20C.glGetUniformLocation(shader.getId(), "cds_" + name));
    }

    private static float[] sprites() {
        if (sails == null) {
            sails = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block instanceof BoatSailBlock)
                    sails.add(block);
            }
        }

        if (sails.isEmpty())
            return sprites;

        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(sails.get(0).defaultBlockState());
        if (model == spriteSource)
            return sprites;

        Set<TextureAtlasSprite> found = new LinkedHashSet<>();
        RandomSource random = RandomSource.create(42L);

        for (Block block : sails) {
            BakedModel sail = Minecraft.getInstance().getBlockRenderer().getBlockModel(block.defaultBlockState());
            for (BakedQuad quad : sail.getQuads(block.defaultBlockState(), null, random, ModelData.EMPTY, null))
                found.add(quad.getSprite());

            for (Direction side : Direction.values()) {
                for (BakedQuad quad : sail.getQuads(block.defaultBlockState(), side, random, ModelData.EMPTY, null))
                    found.add(quad.getSprite());
            }
        }

        float[] rects = new float[Math.min(found.size(), 32) * 4];
        int i = 0;

        for (TextureAtlasSprite sprite : found) {
            if (i >= rects.length)
                break;

            rects[i++] = sprite.getU0();
            rects[i++] = sprite.getV0();
            rects[i++] = sprite.getU1();
            rects[i++] = sprite.getV1();
        }

        spriteSource = model;
        return sprites = rects;
    }
}
