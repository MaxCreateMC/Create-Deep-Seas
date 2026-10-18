package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.simulated_team.simulated.mixin_interface.diagram.LightTextureExtension;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

import static com.maxenonyme.createsubmarine.submarine.client.SonarView.DOWN;
import static com.maxenonyme.createsubmarine.submarine.client.SonarView.RANGE;
import static com.maxenonyme.createsubmarine.submarine.client.SonarView.SHELLS;
import static com.maxenonyme.createsubmarine.submarine.client.SonarView.UP;

class SonarTerrain {

    private static final int SKIN = 4;
    private static final int SPAN = RANGE * 2 + 1;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Deep Seas sonar");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    record Bake(ByteBufferBuilder bytes, MeshData[] meshes) {
    }

    static CompletableFuture<Bake> ping(Minecraft mc, Level level, BlockPos origin, BooleanSupplier dropped) {
        BlockState[] states = new BlockState[SPAN * SPAN * (DOWN + UP + 1)];
        IntArrayList[] shells = sortIntoShells(level, origin, states);
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();

        return CompletableFuture.supplyAsync(() -> {
            if (dropped.getAsBoolean())
                return null;

            ByteBufferBuilder bytes = new ByteBufferBuilder(RenderType.cutout().bufferSize());

            try {
                return new Bake(bytes, bake(dispatcher, level, origin, states, shells, bytes));
            } catch (RuntimeException e) {
                bytes.close();
                CreateSubmarine.LOGGER.warn("Couldn't bake the sonar terrain at {}", origin, e);
                return null;
            }
        }, WORKER);
    }

    static VertexBuffer[] upload(Bake bake) {
        VertexBuffer[] shells = new VertexBuffer[SHELLS];

        for (int shell = 0; shell < SHELLS; shell++)
            shells[shell] = upload(bake.meshes()[shell]);

        bake.bytes().close();
        return shells;
    }

    static void discard(Bake bake) {
        if (bake == null)
            return;

        for (MeshData mesh : bake.meshes()) {
            if (mesh != null)
                mesh.close();
        }

        bake.bytes().close();
    }

    private static IntArrayList[] sortIntoShells(Level level, BlockPos origin, BlockState[] states) {
        IntArrayList[] shells = new IntArrayList[SHELLS];
        int y0 = Math.max(level.getMinBuildHeight(), origin.getY() - DOWN);
        int y1 = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + UP);

        for (int dx = -RANGE; dx <= RANGE; dx++) {
            for (int dz = -RANGE; dz <= RANGE; dz++) {
                if (dx * dx + dz * dz <= RANGE * RANGE)
                    scanColumn(level, origin, dx, dz, y0, y1, states, shells);
            }
        }

        return shells;
    }

    private static void scanColumn(Level level, BlockPos origin, int dx, int dz, int y0, int y1, BlockState[] states,
            IntArrayList[] shells) {
        int x = origin.getX() + dx;
        int z = origin.getZ() + dz;
        ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
        if (chunk == null)
            return;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int buried = 0;

        for (int y = y1; y >= y0; y--) {
            BlockState state = CompartmentTracker.realBlockState(chunk, cursor.set(x, y, z));
            if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
                buried = 0;
                continue;
            }

            if (!state.isSolidRender(level, cursor))
                buried = 0;
            else if (++buried > SKIN)
                continue;

            int dy = y - origin.getY();
            int cell = index(dx, dy, dz);
            states[cell] = state;
            int shell = Math.min(SHELLS - 1, (int) Math.sqrt(dx * dx + dy * dy + dz * dz));
            if (shells[shell] == null)
                shells[shell] = new IntArrayList();
            shells[shell].add(cell);
        }
    }

    private static MeshData[] bake(BlockRenderDispatcher dispatcher, Level level, BlockPos origin, BlockState[] states,
            IntArrayList[] cellsByShell, ByteBufferBuilder bytes) {
        Region region = new Region(level, origin, states);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        PoseStack ms = new PoseStack();

        MeshData[] shells = new MeshData[SHELLS];

        for (int shell = 0; shell < SHELLS; shell++) {
            IntArrayList cells = cellsByShell[shell];
            if (cells == null)
                continue;

            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

            for (int k = 0; k < cells.size(); k++) {
                int cell = cells.getInt(k);
                int dx = cell % SPAN - RANGE;
                int dz = (cell / SPAN) % SPAN - RANGE;
                int dy = cell / (SPAN * SPAN) - DOWN;
                cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                ms.pushPose();
                ms.translate(dx, dy, dz);
                dispatcher.renderBatched(states[cell], cursor, region, ms, builder, true, RANDOM);
                ms.popPose();
            }

            shells[shell] = builder.build();
        }

        return shells;
    }

    private static VertexBuffer upload(MeshData mesh) {
        if (mesh == null)
            return null;

        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(mesh);
        VertexBuffer.unbind();
        return buffer;
    }

    private static int index(int dx, int dy, int dz) {
        return ((dy + DOWN) * SPAN + (dz + RANGE)) * SPAN + (dx + RANGE);
    }

    static void draw(Minecraft mc, SonarScan scan, Matrix4f projection, Quaternionf view, Vector3d center,
            float partialTicks, float front) {
        if (scan.shells == null && scan.previous == null)
            return;

        LightTexture light = mc.gameRenderer.lightTexture();
        RenderType type = RenderType.cutout();
        ((LightTextureExtension) light).simulated$makeDiagramLightTexture(0.65f);

        try {
            type.setupRenderState();
            ShaderInstance shader = GameRenderer.getRendertypeCutoutShader();
            if (shader != null)
                drawShells(scan, shader, projection, view, center, front);
            type.clearRenderState();
        } finally {
            light.updateLightTexture(partialTicks);
        }
    }

    private static void drawShells(SonarScan scan, ShaderInstance shader, Matrix4f projection, Quaternionf view,
            Vector3d center, float front) {
        if (shader.CHUNK_OFFSET != null)
            shader.CHUNK_OFFSET.set(0.0f, 0.0f, 0.0f);
        Matrix4f fresh = shellView(view, scan.origin, center);
        Matrix4f stale = scan.previousOrigin == null ? null : shellView(view, scan.previousOrigin, center);

        for (int shell = 0; shell < SHELLS; shell++) {
            boolean reached = shell <= front;
            VertexBuffer buffer = reached ? at(scan.shells, shell) : at(scan.previous, shell);
            if (buffer == null)
                continue;

            buffer.bind();
            buffer.drawWithShader(reached ? fresh : stale, projection, shader);
        }

        VertexBuffer.unbind();
    }

    private static VertexBuffer at(VertexBuffer[] buffers, int shell) {
        return buffers == null ? null : buffers[shell];
    }

    private static Matrix4f shellView(Quaternionf view, BlockPos origin, Vector3d center) {
        return new Matrix4f().rotation(view).translate(
                (float) (origin.getX() - center.x),
                (float) (origin.getY() - center.y),
                (float) (origin.getZ() - center.z));
    }

    private static class Region implements BlockAndTintGetter {
        private final Level level;
        private final BlockPos origin;
        private final BlockState[] states;

        Region(Level level, BlockPos origin, BlockState[] states) {
            this.level = level;
            this.origin = origin;
            this.states = states;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            int dx = pos.getX() - origin.getX();
            int dy = pos.getY() - origin.getY();
            int dz = pos.getZ() - origin.getZ();
            if (dx < -RANGE || dx > RANGE || dz < -RANGE || dz > RANGE || dy < -DOWN || dy > UP)
                return Blocks.AIR.defaultBlockState();

            BlockState state = states[index(dx, dy, dz)];
            return state == null ? Blocks.AIR.defaultBlockState() : state;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return level.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return level.getBlockTint(pos, resolver);
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }
    }
}
