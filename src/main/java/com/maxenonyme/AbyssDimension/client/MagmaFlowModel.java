package com.maxenonyme.AbyssDimension.client;

import com.maxenonyme.AbyssDimension.CreateAbyss;
import com.maxenonyme.AbyssDimension.block.MagmaFlowBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import javax.annotation.Nullable;
import java.util.List;

public class MagmaFlowModel extends BakedModelWrapper<BakedModel> {
    public static final int TILES = 8;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final ChunkRenderTypeSet LAYERS = ChunkRenderTypeSet.of(RenderType.cutout());
    private static final ModelProperty<BlockPos> POS = new ModelProperty<>();

    private static final float[][][] CORNERS = {
            {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
            {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
            {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
            {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}
    };

    private final BakedQuad[] quads = new BakedQuad[2 * 4 * 6 * TILES];
    private volatile TextureAtlasSprite[] sprites;
    private volatile TextureAtlasSprite[] glows;

    public MagmaFlowModel(BakedModel original) {
        super(original);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        return data.derive().with(POS, pos.immutable()).build();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return LAYERS;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        BlockPos pos = data.get(POS);
        if (state == null || pos == null || !state.hasProperty(MagmaFlowBlock.FACING))
            return super.getQuads(state, side, rand, data, renderType);
        if (side == null)
            return List.of();
        Direction flow = state.getValue(MagmaFlowBlock.FACING);
        Direction u = across(side, flow);
        int along = u.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? u.getAxis().choose(pos.getX(), pos.getY(), pos.getZ())
                : -u.getAxis().choose(pos.getX(), pos.getY(), pos.getZ()) - 1;
        int tile = Math.floorMod(along, TILES);
        int key = (flow.get2DDataValue() * 6 + side.get3DDataValue()) * TILES + tile;
        BakedQuad base = quads[key * 2];
        BakedQuad glow = quads[key * 2 + 1];
        if (base == null || glow == null) {
            Direction v = down(side, flow);
            loadSprites();
            base = bake(side, u, v, sprites[tile], false);
            glow = bake(side, u, v, glows[tile], true);
            quads[key * 2] = base;
            quads[key * 2 + 1] = glow;
        }
        return List.of(base, glow);
    }

    private static Direction across(Direction side, Direction flow) {
        if (side.getAxis() == flow.getAxis())
            return side.getCounterClockWise();
        return flow;
    }

    private static Direction down(Direction side, Direction flow) {
        if (side.getAxis() == Direction.Axis.Y)
            return flow.getClockWise();
        return Direction.DOWN;
    }

    private void loadSprites() {
        if (sprites != null)
            return;
        glows = strip("block/magma_glow_");
        sprites = strip("block/magma_flow_");
    }

    private static TextureAtlasSprite[] strip(String prefix) {
        TextureAtlasSprite[] found = new TextureAtlasSprite[TILES];
        var atlas = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        for (int i = 0; i < TILES; i++)
            found[i] = atlas.getSprite(ResourceLocation.fromNamespaceAndPath(CreateAbyss.MOD_ID, prefix + i));
        return found;
    }

    private static BakedQuad bake(Direction side, Direction u, Direction v, TextureAtlasSprite sprite, boolean glow) {
        int[] vertices = new int[32];
        float[][] corners = CORNERS[side.get3DDataValue()];
        int normal = (side.getStepX() * 127 & 0xFF) | ((side.getStepY() * 127 & 0xFF) << 8)
                | ((side.getStepZ() * 127 & 0xFF) << 16);
        for (int i = 0; i < 4; i++) {
            float[] c = corners[i];
            int o = i * 8;
            vertices[o] = Float.floatToRawIntBits(c[0]);
            vertices[o + 1] = Float.floatToRawIntBits(c[1]);
            vertices[o + 2] = Float.floatToRawIntBits(c[2]);
            vertices[o + 3] = -1;
            vertices[o + 4] = Float.floatToRawIntBits(sprite.getU(local(c, u)));
            vertices[o + 5] = Float.floatToRawIntBits(sprite.getV(local(c, v)));
            vertices[o + 6] = glow ? FULL_BRIGHT : 0;
            vertices[o + 7] = normal;
        }
        return new BakedQuad(vertices, -1, side, sprite, !glow, !glow);
    }

    private static float local(float[] corner, Direction axis) {
        float p = corner[axis.getAxis().ordinal()];
        return axis.getAxisDirection() == Direction.AxisDirection.POSITIVE ? p : 1.0f - p;
    }
}
