package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.block.BoatSailBlock;
import com.maxenonyme.highseas.block.SailCorner;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SailTriangleModel extends BakedModelWrapper<BakedModel> {
    private static final int STRIDE = 8;
    private static final float EPS = 1.0e-3f;

    private record Key(BlockState state, @Nullable Direction side, @Nullable RenderType type) {
    }

    private final Map<Key, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    public SailTriangleModel(BakedModel original) {
        super(original);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        if (state == null || !state.hasProperty(BoatSailBlock.CORNER) || state.getValue(BoatSailBlock.CORNER) == SailCorner.NONE
                || state.getValue(BoatSailBlock.AXIS) == Direction.Axis.Y)
            return super.getQuads(state, side, rand, data, renderType);
        return cache.computeIfAbsent(new Key(state, side, renderType), k -> cut(state, side, rand, data, renderType));
    }

    private List<BakedQuad> cut(BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                @Nullable RenderType renderType) {
        Direction.Axis axis = state.getValue(BoatSailBlock.AXIS);
        SailCorner corner = state.getValue(BoatSailBlock.CORNER);
        int hi = axis == Direction.Axis.X ? 2 : 0;
        List<BakedQuad> out = new ArrayList<>();
        for (BakedQuad quad : super.getQuads(state, side, rand, data, renderType)) {
            BakedQuad shaped = shape(quad, corner, hi, side == null);
            if (shaped != null)
                out.add(shaped);
        }
        if (side == null) {
            for (Direction d : Direction.values()) {
                for (BakedQuad quad : super.getQuads(state, d, rand, data, renderType)) {
                    if (edge(quad, corner, hi) == 2)
                        out.add(diagonal(quad, corner, hi));
                }
            }
        }
        return out;
    }

    private static float a(int[] v, int i, SailCorner c, int hi) {
        float h = Float.intBitsToFloat(v[i * STRIDE + hi]);
        return c.h > 0 ? h : 1.0f - h;
    }

    private static float b(int[] v, int i, SailCorner c) {
        float y = Float.intBitsToFloat(v[i * STRIDE + 1]);
        return c.v > 0 ? y : 1.0f - y;
    }

    private static int edge(BakedQuad quad, SailCorner c, int hi) {
        int[] v = quad.getVertices();
        boolean onA = true, onB = true;
        for (int i = 0; i < 4; i++) {
            onA &= a(v, i, c, hi) > 1.0f - EPS;
            onB &= b(v, i, c) > 1.0f - EPS;
        }
        return onA ? 1 : onB ? 2 : 0;
    }

    @Nullable
    private static BakedQuad shape(BakedQuad quad, SailCorner c, int hi, boolean unculled) {
        int edge = edge(quad, c, hi);
        if (edge == 1)
            return null;
        if (edge == 2)
            return unculled ? diagonal(quad, c, hi) : null;
        int[] v = quad.getVertices().clone();
        int cut = -1;
        for (int i = 0; i < 4; i++) {
            if (a(v, i, c, hi) > 1.0f - EPS && b(v, i, c) > 1.0f - EPS)
                cut = i;
        }
        if (cut < 0)
            return quad;
        System.arraycopy(v, ((cut + 1) % 4) * STRIDE, v, cut * STRIDE, STRIDE);
        return new BakedQuad(v, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(),
                quad.hasAmbientOcclusion());
    }

    private static BakedQuad diagonal(BakedQuad quad, SailCorner c, int hi) {
        int[] v = quad.getVertices().clone();
        float y = c.v > 0 ? 0.0f : 1.0f;
        for (int i = 0; i < 4; i++) {
            if (a(v, i, c, hi) > 1.0f - EPS)
                v[i * STRIDE + 1] = Float.floatToRawIntBits(y);
        }
        float s = (float) (1.0 / Math.sqrt(2.0));
        float nh = c.h * s, nv = c.v * s;
        float nx = hi == 0 ? nh : 0.0f, nz = hi == 2 ? nh : 0.0f;
        int normal = ((int) (nx * 127.0f) & 0xFF) | (((int) (nv * 127.0f) & 0xFF) << 8) | (((int) (nz * 127.0f) & 0xFF) << 16);
        for (int i = 0; i < 4; i++)
            v[i * STRIDE + 7] = normal;
        return new BakedQuad(v, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(),
                quad.hasAmbientOcclusion());
    }
}
