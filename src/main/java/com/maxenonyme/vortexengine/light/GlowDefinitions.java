package com.maxenonyme.vortexengine.light;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.vortexengine.VortexConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class GlowDefinitions extends SimpleJsonResourceReloadListener {

    private static volatile Map<BlockState, Glow> defined = Map.of();
    private static volatile Map<BlockState, Glow> combined = Map.of();

    private GlowDefinitions() {
        super(new Gson(), "vortex/glow");
    }

    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new GlowDefinitions());
    }

    public static Map<BlockState, Glow> all() {
        return VortexConfig.AUTO_GLOW.get() ? combined : defined;
    }

    public static boolean glows(BlockState state) {
        return all().containsKey(state);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<BlockState, Glow> blocks = new IdentityHashMap<>();
        Set<BlockState> muted = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
            try {
                for (JsonElement element : GsonHelper.getAsJsonArray(file.getValue().getAsJsonObject(), "glows"))
                    read(element.getAsJsonObject(), blocks, muted);
            } catch (RuntimeException e) {
                CreateSubmarine.LOGGER.warn("Couldn't read the glow definitions in {}", file.getKey(), e);
            }
        }

        Map<BlockState, Glow> everything = new IdentityHashMap<>();
        automatic(everything);
        everything.keySet().removeAll(muted);
        everything.putAll(blocks);

        defined = Collections.unmodifiableMap(blocks);
        combined = Collections.unmodifiableMap(everything);
        LightIndex.rescanAll();
    }

    private static void read(JsonObject json, Map<BlockState, Glow> blocks, Set<BlockState> muted) {
        int color = Integer.parseInt(GsonHelper.getAsString(json, "color", "#FFFFFF").replace("#", ""), 16);
        Glow glow = new Glow((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f,
                GsonHelper.getAsFloat(json, "radius", 5),
                GsonHelper.getAsFloat(json, "intensity", 1),
                Mth.clamp(GsonHelper.getAsFloat(json, "pulse", 0), 0, 1),
                Mth.clamp(GsonHelper.getAsFloat(json, "flicker", 0), 0, 1));

        Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(GsonHelper.getAsString(json, "block")))
                .orElse(null);
        if (block == null)
            return;

        boolean scaled = GsonHelper.getAsBoolean(json, "scale_with_light", false);

        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            float scale = scaled ? state.getLightEmission() / 15f : 1;
            if (glow.intensity() <= 0 || scale <= 0)
                muted.add(state);
            else
                blocks.put(state, glow.scaled(scale));
        }
    }

    private static void automatic(Map<BlockState, Glow> glows) {
        BlockModelShaper models = Minecraft.getInstance().getModelManager().getBlockModelShaper();
        Map<TextureAtlasSprite, float[]> colors = new IdentityHashMap<>();

        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            if (state.getBlock() instanceof LiquidBlock)
                continue;

            int emission = state.getLightEmission();
            if (emission <= 0 && !emissive(state))
                continue;

            float[] color = colors.computeIfAbsent(models.getParticleIcon(state), GlowDefinitions::brightest);
            Block block = state.getBlock();
            boolean fire = block instanceof BaseFireBlock || block instanceof CampfireBlock
                    || block instanceof AbstractCandleBlock || block instanceof BaseTorchBlock;
            float level = emission > 0 ? emission / 15f : 0.3f;

            glows.put(state, new Glow(color[0], color[1], color[2], 1 + 5 * level, 0.125f + 0.25f * level, 0,
                    fire ? 0.35f : 0));
        }
    }

    private static boolean emissive(BlockState state) {
        try {
            return state.emissiveRendering(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static float[] brightest(TextureAtlasSprite sprite) {
        try {
            return sample(sprite.contents());
        } catch (RuntimeException e) {
            return new float[] { 1, 1, 1 };
        }
    }

    private static float[] sample(SpriteContents contents) {
        NativeImage image = contents.getOriginalImage();
        int width = Math.min(contents.width(), image.getWidth());
        int height = Math.min(contents.height(), image.getHeight());
        int peak = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getPixelRGBA(x, y);
                if (pixel >>> 24 > 0)
                    peak = Math.max(peak, brightness(pixel));
            }
        }

        float red = 0, green = 0, blue = 0;
        int count = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getPixelRGBA(x, y);
                if (pixel >>> 24 == 0 || brightness(pixel) * 4 < peak * 3)
                    continue;

                red += (pixel & 255) / 255f;
                green += (pixel >> 8 & 255) / 255f;
                blue += (pixel >> 16 & 255) / 255f;
                count++;
            }
        }

        if (count == 0)
            return new float[] { 1, 1, 1 };

        return new float[] { red / count, green / count, blue / count };
    }

    private static int brightness(int pixel) {
        return Math.max(pixel & 255, Math.max(pixel >> 8 & 255, pixel >> 16 & 255));
    }
}
