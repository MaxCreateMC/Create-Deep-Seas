package com.maxenonyme.vortexengine.light;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.vortexengine.VortexConfig;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GlowDefinitions extends SimpleJsonResourceReloadListener {

    private static final float[] NEUTRAL = { 1, 0.92f, 0.8f };
    private static final int HUES = 8;
    private static final Direction[] SIDES = { null, Direction.DOWN, Direction.UP, Direction.NORTH,
            Direction.SOUTH, Direction.WEST, Direction.EAST };
    private static final Set<String> OWN = Set.of("create_submarine", "create_high_seas", "create_abyss");

    private static volatile Map<BlockState, Glow> defined = Map.of();
    private static volatile Map<BlockState, Glow> combined = Map.of();
    private static volatile Map<BlockState, Glow> ownDefined = Map.of();
    private static volatile Map<BlockState, Glow> ownCombined = Map.of();

    private GlowDefinitions() {
        super(new Gson(), "vortex/glow");
    }

    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new GlowDefinitions());
    }

    public static Map<BlockState, Glow> all() {
        if (VortexConfig.ALL_BLOCKS.get())
            return VortexConfig.AUTO_GLOW.get() ? combined : defined;
        return VortexConfig.AUTO_GLOW.get() ? ownCombined : ownDefined;
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
        ownDefined = Collections.unmodifiableMap(onlyOwn(blocks));
        ownCombined = Collections.unmodifiableMap(onlyOwn(everything));
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
        Map<List<TextureAtlasSprite>, float[]> colors = new HashMap<>();

        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            if (state.getBlock() instanceof LiquidBlock)
                continue;

            int emission = state.getLightEmission();
            if (emission <= 0 && !emissive(state))
                continue;

            float[] color = colors.computeIfAbsent(sprites(models, state), GlowDefinitions::colorOf);
            if (state.getRenderShape() == RenderShape.INVISIBLE)
                color = NEUTRAL;

            Block block = state.getBlock();
            boolean fire = block instanceof BaseFireBlock || block instanceof CampfireBlock
                    || block instanceof AbstractCandleBlock || block instanceof BaseTorchBlock;
            float level = emission > 0 ? emission / 15f : 0.3f;

            glows.put(state, new Glow(color[0], color[1], color[2], 1 + 5 * level, 0.125f + 0.25f * level, 0,
                    fire ? 0.35f : 0));
        }
    }

    private static Map<BlockState, Glow> onlyOwn(Map<BlockState, Glow> glows) {
        Map<BlockState, Glow> own = new IdentityHashMap<>(glows);
        own.keySet().removeIf(state -> !own(state));
        return own;
    }

    private static boolean own(BlockState state) {
        return OWN.contains(state.getBlock().builtInRegistryHolder().key().location().getNamespace());
    }

    private static boolean emissive(BlockState state) {
        try {
            return state.emissiveRendering(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static List<TextureAtlasSprite> sprites(BlockModelShaper models, BlockState state) {
        BakedModel model = models.getBlockModel(state);
        Set<TextureAtlasSprite> found = new LinkedHashSet<>();

        try {
            for (Direction side : SIDES) {
                for (BakedQuad quad : model.getQuads(state, side, RandomSource.create(42)))
                    found.add(quad.getSprite());
            }
        } catch (RuntimeException e) {
            found.clear();
        }

        found.removeIf(sprite -> MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name()));
        if (found.isEmpty())
            found.add(model.getParticleIcon());
        return List.copyOf(found);
    }

    private static float[] colorOf(List<TextureAtlasSprite> sprites) {
        IntArrayList pixels = new IntArrayList();

        for (TextureAtlasSprite sprite : sprites) {
            if (MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name()))
                continue;

            try {
                collect(sprite.contents(), pixels);
            } catch (RuntimeException e) {
                CreateSubmarine.LOGGER.debug("Couldn't read the colours of {}", sprite.contents().name(), e);
            }
        }

        int peak = 0;
        for (int i = 0; i < pixels.size(); i++)
            peak = Math.max(peak, brightness(pixels.getInt(i)));

        if (peak == 0)
            return NEUTRAL;

        float[] weights = new float[HUES * HUES * HUES];
        float[][] sums = new float[weights.length][3];

        for (int i = 0; i < pixels.size(); i++) {
            int pixel = pixels.getInt(i);
            int light = brightness(pixel);
            if (light * 4 < peak * 3)
                continue;

            float red = (pixel & 255) / 255f, green = (pixel >> 8 & 255) / 255f, blue = (pixel >> 16 & 255) / 255f;
            float top = light / 255f;
            int hue = shade(red / top) * HUES * HUES + shade(green / top) * HUES + shade(blue / top);
            float weight = top * top;

            weights[hue] += weight;
            sums[hue][0] += red * weight;
            sums[hue][1] += green * weight;
            sums[hue][2] += blue * weight;
        }

        int best = -1;
        float bestScore = 0;

        for (int hue = 0; hue < weights.length; hue++) {
            float score = around(weights, hue);
            if (score > bestScore) {
                bestScore = score;
                best = hue;
            }
        }

        if (best < 0)
            return NEUTRAL;

        float[] color = blend(sums, best);
        return new float[] { color[0] / bestScore, color[1] / bestScore, color[2] / bestScore };
    }

    private static float around(float[] weights, int hue) {
        int red = hue / (HUES * HUES), green = hue / HUES % HUES, blue = hue % HUES;
        float total = 0;

        for (int r = Math.max(0, red - 1); r <= Math.min(HUES - 1, red + 1); r++) {
            for (int g = Math.max(0, green - 1); g <= Math.min(HUES - 1, green + 1); g++) {
                for (int b = Math.max(0, blue - 1); b <= Math.min(HUES - 1, blue + 1); b++)
                    total += weights[r * HUES * HUES + g * HUES + b];
            }
        }

        return total;
    }

    private static float[] blend(float[][] sums, int hue) {
        int red = hue / (HUES * HUES), green = hue / HUES % HUES, blue = hue % HUES;
        float[] color = new float[3];

        for (int r = Math.max(0, red - 1); r <= Math.min(HUES - 1, red + 1); r++) {
            for (int g = Math.max(0, green - 1); g <= Math.min(HUES - 1, green + 1); g++) {
                for (int b = Math.max(0, blue - 1); b <= Math.min(HUES - 1, blue + 1); b++) {
                    float[] sum = sums[r * HUES * HUES + g * HUES + b];
                    color[0] += sum[0];
                    color[1] += sum[1];
                    color[2] += sum[2];
                }
            }
        }

        return color;
    }

    private static int shade(float channel) {
        return Math.min(HUES - 1, (int) (channel * HUES));
    }

    private static void collect(SpriteContents contents, IntArrayList pixels) {
        NativeImage image = contents.getOriginalImage();
        int width = Math.min(contents.width(), image.getWidth());
        int height = Math.min(contents.height(), image.getHeight());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getPixelRGBA(x, y);
                if (pixel >>> 24 > 0)
                    pixels.add(pixel);
            }
        }
    }

    private static int brightness(int pixel) {
        return Math.max(pixel & 255, Math.max(pixel >> 8 & 255, pixel >> 16 & 255));
    }
}
