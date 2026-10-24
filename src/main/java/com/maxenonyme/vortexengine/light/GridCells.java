package com.maxenonyme.vortexengine.light;

import java.util.Arrays;

public class GridCells {

    private static final int SIZE = ShadowGrid.SIZE;

    static void fill(byte[] cells, long[] bits, int baseX, int baseY, int baseZ) {
        for (int i = 0; i < 4096; i++) {
            if ((bits[i >> 6] >>> (i & 63) & 1) == 0)
                continue;

            int cx = baseX + (i & 15), cy = baseY + (i >> 8), cz = baseZ + (i >> 4 & 15);
            block(cells, (cx + cy * SIZE + cz * SIZE * SIZE) * 4);
        }
    }

    static void tint(byte[] cells, int[] tints, int baseX, int baseY, int baseZ) {
        for (int k = 0; k < tints.length; k += 2) {
            int local = tints[k];
            int cx = baseX + (local & 15), cy = baseY + (local >> 8), cz = baseZ + (local >> 4 & 15);
            filter(cells, (cx + cy * SIZE + cz * SIZE * SIZE) * 4, tints[k + 1]);
        }
    }

    static void shape(byte[] cells, int[] shapes, int baseX, int baseY, int baseZ) {
        for (int k = 0; k < shapes.length; k += 2) {
            int local = shapes[k];
            int cx = baseX + (local & 15), cy = baseY + (local >> 8), cz = baseZ + (local >> 4 & 15);
            cover(cells, (cx + cy * SIZE + cz * SIZE * SIZE) * 4, shapes[k + 1]);
        }
    }

    static void block(byte[] cells, int index) {
        Arrays.fill(cells, index, index + 4, (byte) 255);
    }

    static void filter(byte[] cells, int index, int absorbed) {
        if (cells[index + 3] != 0)
            return;

        cells[index] = (byte) (absorbed >> 16);
        cells[index + 1] = (byte) (absorbed >> 8);
        cells[index + 2] = (byte) absorbed;
    }

    static void cover(byte[] cells, int index, int mask) {
        if (cells[index + 3] == (byte) 255)
            return;

        if (cells[index + 3] == 1)
            mask |= cells[index] & 255;
        if (mask == 255) {
            block(cells, index);
            return;
        }

        byte share = (byte) (Integer.bitCount(mask) * 32);
        cells[index] = (byte) mask;
        cells[index + 1] = share;
        cells[index + 2] = share;
        cells[index + 3] = 1;
    }
}
