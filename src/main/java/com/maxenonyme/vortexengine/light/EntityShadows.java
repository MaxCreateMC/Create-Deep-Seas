package com.maxenonyme.vortexengine.light;

import com.maxenonyme.createsubmarine.submarine.client.VeilUploadGuard;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class EntityShadows {

    private static final int BOXES = 16;

    private static Map<Integer, Integer> stamped = Map.of();
    private static int stampedGeneration = -1;
    private static int boxTexture;
    private static int boxCount;
    private static FloatBuffer boxData;

    static void update(Vec3 camera, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null)
            return;

        List<AABB> boxes = new ArrayList<>();

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || entity.isInvisible() || entity.isSpectator())
                continue;

            Vec3 lerped = entity.getPosition(partialTick);
            AABB box = entity.getBoundingBox().move(lerped.subtract(entity.position()));
            if (box.distanceToSqr(camera) <= 48 * 48)
                boxes.add(box);
        }

        boxes.sort(Comparator.comparingDouble(box -> box.distanceToSqr(camera)));
        upload(boxes, camera);
        if (ShadowGrid.ready())
            stampGrid(boxes);
    }

    static void bind(ShaderInstance shader) {
        shader.setSampler("Boxes", boxTexture);
        shader.safeGetUniform("BoxCount").set(boxCount);
    }

    private static void upload(List<AABB> boxes, Vec3 camera) {
        if (boxTexture == 0) {
            boxTexture = GlStateManager._genTexture();
            boxData = MemoryUtil.memAllocFloat(BOXES * 2 * 4);
            GlStateManager._bindTexture(boxTexture);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, BOXES, 2, 0, GL11.GL_RGBA, GL11.GL_FLOAT,
                    (FloatBuffer) null);
        }

        boxCount = Math.min(boxes.size(), BOXES);
        boxData.clear();

        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < BOXES; i++) {
                AABB box = i < boxCount ? boxes.get(i) : null;
                if (box == null) {
                    boxData.put(0).put(0).put(0).put(0);
                    continue;
                }

                boxData.put((float) ((row == 0 ? box.minX : box.maxX) - camera.x))
                        .put((float) ((row == 0 ? box.minY : box.maxY) - camera.y))
                        .put((float) ((row == 0 ? box.minZ : box.maxZ) - camera.z))
                        .put(0);
            }
        }

        boxData.flip();
        VeilUploadGuard.reset();
        GlStateManager._bindTexture(boxTexture);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, BOXES, 2, GL11.GL_RGBA, GL11.GL_FLOAT, boxData);
        GlStateManager._bindTexture(0);
    }

    private static void stampGrid(List<AABB> boxes) {
        Map<Integer, Integer> masks = new HashMap<>();
        for (AABB box : boxes)
            stamp(masks, box);

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

        VeilUploadGuard.reset();
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, ShadowGrid.texture());

        for (Map.Entry<Integer, Integer> entry : changed.entrySet()) {
            int index = entry.getKey();
            int base = index * 4;
            int packed = (cells[base] & 255) << 24 | (cells[base + 1] & 255) << 16 | (cells[base + 2] & 255) << 8
                    | cells[base + 3] & 255;

            int mask = entry.getValue();
            if (mask != 0 && cells[base + 3] != (byte) 255) {
                if (cells[base + 3] == 1)
                    mask |= cells[base] & 255;

                int share = Math.min(Integer.bitCount(mask) * 32, 255);
                packed = mask << 24 | share << 16 | share << 8 | 2;
            }

            ShadowGrid.write(index, packed);
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
