package com.maxenonyme.vortexengine.light;

import com.maxenonyme.createsubmarine.submarine.client.VeilUploadGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EntityShadows {

    private static Map<Integer, Integer> stamped = Map.of();
    private static int stampedGeneration = -1;

    static void update(Vec3 camera, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !ShadowGrid.ready())
            return;

        Map<Integer, Integer> masks = new HashMap<>();

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || entity.isInvisible() || entity.isSpectator())
                continue;

            Vec3 lerped = entity.getPosition(partialTick);
            AABB box = entity.getBoundingBox().move(lerped.subtract(entity.position()));
            if (box.distanceToSqr(camera) > 48 * 48)
                continue;

            stamp(masks, box);
        }

        boolean fresh = stampedGeneration != ShadowGrid.generation();
        if (!fresh && masks.equals(stamped))
            return;

        Map<Integer, Integer> changed = new HashMap<>();
        if (!fresh) {
            for (Integer index : stamped.keySet())
                changed.put(index, 0);
        }

        for (Map.Entry<Integer, Integer> entry : masks.entrySet()) {
            if (fresh || !Objects.equals(stamped.get(entry.getKey()), entry.getValue()))
                changed.put(entry.getKey(), entry.getValue());
            else
                changed.remove(entry.getKey());
        }

        patch(changed);
        stamped = masks;
        stampedGeneration = ShadowGrid.generation();
    }

    private static void patch(Map<Integer, Integer> changed) {
        byte[] cells = ShadowGrid.cells();
        byte[] cell = new byte[4];

        VeilUploadGuard.reset();
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, ShadowGrid.texture());

        for (Map.Entry<Integer, Integer> entry : changed.entrySet()) {
            int index = entry.getKey();
            System.arraycopy(cells, index * 4, cell, 0, 4);
            if (entry.getValue() != 0)
                GridCells.cover(cell, 0, entry.getValue());

            ShadowGrid.write(index, (cell[0] & 255) << 24 | (cell[1] & 255) << 16 | (cell[2] & 255) << 8
                    | cell[3] & 255);
        }

        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
    }

    private static void stamp(Map<Integer, Integer> masks, AABB box) {
        int size = ShadowGrid.SIZE;
        int originX = ShadowGrid.originX(), originY = ShadowGrid.originY(), originZ = ShadowGrid.originZ();

        for (int x = Mth.floor(box.minX); x < Mth.ceil(box.maxX); x++) {
            for (int y = Mth.floor(box.minY); y < Mth.ceil(box.maxY); y++) {
                for (int z = Mth.floor(box.minZ); z < Mth.ceil(box.maxZ); z++) {
                    int cx = x - originX, cy = y - originY, cz = z - originZ;
                    if (cx < 0 || cy < 0 || cz < 0 || cx >= size || cy >= size || cz >= size)
                        continue;

                    int mask = LightIndex.octants(box.minX - x, box.minY - y, box.minZ - z, box.maxX - x,
                            box.maxY - y, box.maxZ - z);
                    if (mask != 0)
                        masks.merge(cx + cy * size + cz * size * size, mask, (a, b) -> a | b);
                }
            }
        }
    }
}
