package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class BallastTankCT {
    private BallastTankCT() {
    }

    private static final int SCAN_LIMIT = 512;

    private static final CTSpriteShiftEntry SIDE = shift("ballast_all");
    private static final CTSpriteShiftEntry CAP = shift("below");
    private static final CTSpriteShiftEntry INNER = shift("above");

    private static CTSpriteShiftEntry shift(String name) {
        return CTSpriteShifter.getCT(AllCTTypes.RECTANGLE,
                ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/ballast/" + name),
                ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID,
                        "block/ballast/" + name + "_connected"));
    }

    private static final class Behaviour extends ConnectedTextureBehaviour.Base {
        @Override
        public CTSpriteShiftEntry getShift(BlockState state, Direction direction, TextureAtlasSprite sprite) {
            if (direction.getAxis().isHorizontal())
                return SIDE;
            return sprite != null && sprite == INNER.getOriginal() ? INNER : CAP;
        }

        @Override
        public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter world,
                BlockPos pos, BlockPos otherPos, Direction face) {
            if (state.getBlock() != other.getBlock())
                return false;
            if (face.getAxis().isHorizontal())
                return true;
            return fullRectangle(world, pos, state.getBlock());
        }

        private static boolean fullRectangle(BlockAndTintGetter world, BlockPos pos, Block block) {
            Set<Long> seen = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            int minX = pos.getX(), maxX = pos.getX();
            int minZ = pos.getZ(), maxZ = pos.getZ();

            seen.add(pos.asLong());
            queue.add(pos);
            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                minX = Math.min(minX, current.getX());
                maxX = Math.max(maxX, current.getX());
                minZ = Math.min(minZ, current.getZ());
                maxZ = Math.max(maxZ, current.getZ());
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos next = current.relative(dir);
                    if (world.getBlockState(next).getBlock() != block)
                        continue;
                    if (!seen.add(next.asLong()))
                        continue;
                    if (seen.size() > SCAN_LIMIT)
                        return true;
                    queue.add(next);
                }
            }
            int width = maxX - minX + 1;
            int depth = maxZ - minZ + 1;
            if (width < 2 || depth < 2)
                return false;
            return seen.size() == (long) width * depth;
        }
    }

    public static void register() {
        try {
            Behaviour behaviour = new Behaviour();
            Class<?> fnClass = Class.forName("com.tterrag.registrate.util.nullness.NonNullFunction");
            Object wrapper = Proxy.newProxyInstance(
                    BallastTankCT.class.getClassLoader(), new Class<?>[] { fnClass },
                    (proxy, method, args) -> switch (method.getName()) {
                        case "apply" -> new CTModel((BakedModel) args[0], behaviour);
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "BallastTankCT";
                        default -> null;
                    });

            Object models = CreateClient.MODEL_SWAPPER.getCustomBlockModels();
            models.getClass()
                    .getMethod("register", ResourceLocation.class, fnClass)
                    .invoke(models, ResourceLocation.fromNamespaceAndPath(
                            CreateSubmarine.MOD_ID, "ballast_tank"), wrapper);
        } catch (Exception e) {
            CreateSubmarine.LOGGER.warn("Ballast tank connected textures unavailable", e);
        }
    }
}
