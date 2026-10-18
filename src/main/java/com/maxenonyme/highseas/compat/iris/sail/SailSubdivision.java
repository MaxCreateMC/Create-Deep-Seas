package com.maxenonyme.highseas.compat.iris.sail;

import net.minecraft.client.renderer.block.model.BakedQuad;

import java.util.ArrayList;
import java.util.List;

public class SailSubdivision {

    private static final int STRIDE = 8;

    public static List<BakedQuad> split(List<BakedQuad> quads, int cells) {
        List<BakedQuad> out = new ArrayList<>(quads.size() * cells * cells);

        for (BakedQuad quad : quads) {
            int[] v = quad.getVertices();
            if (v.length < STRIDE * 4) {
                out.add(quad);
                continue;
            }

            for (int i = 0; i < cells; i++) {
                for (int j = 0; j < cells; j++) {
                    float s0 = i / (float) cells, s1 = (i + 1) / (float) cells;
                    float t0 = j / (float) cells, t1 = (j + 1) / (float) cells;
                    int[] part = new int[STRIDE * 4];
                    corner(v, part, 0, s0, t0);
                    corner(v, part, 1, s1, t0);
                    corner(v, part, 2, s1, t1);
                    corner(v, part, 3, s0, t1);
                    out.add(new BakedQuad(part, quad.getTintIndex(), quad.getDirection(), quad.getSprite(),
                            quad.isShade(), quad.hasAmbientOcclusion()));
                }
            }
        }

        return out;
    }

    private static void corner(int[] v, int[] part, int index, float s, float t) {
        int at = index * STRIDE;
        System.arraycopy(v, at, part, at, STRIDE);

        for (int k : new int[] { 0, 1, 2, 4, 5 }) {
            float a = Float.intBitsToFloat(v[k]), b = Float.intBitsToFloat(v[STRIDE + k]);
            float c = Float.intBitsToFloat(v[2 * STRIDE + k]), d = Float.intBitsToFloat(v[3 * STRIDE + k]);
            float bottom = a + (b - a) * s, top = d + (c - d) * s;
            part[at + k] = Float.floatToRawIntBits(bottom + (top - bottom) * t);
        }
    }
}
