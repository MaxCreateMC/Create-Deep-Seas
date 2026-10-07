package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.item.PressureGogglesItem;
import com.maxenonyme.createsubmarine.submarine.stress.HullShapeAnalyzer;
import com.maxenonyme.createsubmarine.submarine.stress.SolverCore;
import com.maxenonyme.createsubmarine.submarine.system.GirderSupport;
import com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = CreateSubmarine.MOD_ID, value = Dist.CLIENT)
public final class HullPressureRenderer {
    private HullPressureRenderer() {
    }

    private static final int REFRESH_TICKS = 10;
    private static final int SETTLE_TICKS = 10;
    private static final int NEAR_SETTLE_TICKS = 2;
    private static final double NEAR = 10.0;
    private static final int RESCAN_TICKS = 200;
    private static final double RANGE = 96.0;
    private static final int MAX_VOLUME = 1_000_000;
    private static final int ALPHA = 0x70;

    private record Shape(int edits, long bounds, int tick, int ax, int ay, int az, Long2FloatOpenHashMap factors) {
    }

    private static final class Mesh {
        final Shape shape;
        final int n;
        final int[] lx, ly, lz;
        final float[] base, factor;
        final float[] quads;
        final int[] quadCell;
        final int[] colors;
        long key = Long.MIN_VALUE;
        int edits;

        Mesh(Shape shape, int n, int[] lx, int[] ly, int[] lz, float[] base, float[] factor, float[] quads,
                int[] quadCell) {
            this.shape = shape;
            this.n = n;
            this.lx = lx;
            this.ly = ly;
            this.lz = lz;
            this.base = base;
            this.factor = factor;
            this.quads = quads;
            this.quadCell = quadCell;
            this.colors = new int[n];
        }
    }

    private record Visible(SubLevel sub, Mesh mesh) {
    }

    private static final Map<UUID, Shape> SHAPES = new ConcurrentHashMap<>();
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Mesh> MESHES = new HashMap<>();
    private static final Map<UUID, int[]> EDIT_SEEN = new HashMap<>();
    private static final Map<UUID, Visible> VISIBLE = new HashMap<>();
    private static ClientLevel lastLevel;
    private static int ticks;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            SHAPES.clear();
            MESHES.clear();
            EDIT_SEEN.clear();
            VISIBLE.clear();
        }
        if (mc.level == null || mc.player == null || !PressureGogglesItem.isWearing(mc.player)
                || PressureGogglesItem.mode(mc.player.getItemBySlot(EquipmentSlot.HEAD)) == PressureGogglesItem.Mode.NONE
                || !SubmarineConfig.SERVER_SPEC.isLoaded()) {
            VISIBLE.clear();
            return;
        }
        ticks++;
        boolean full = ticks % REFRESH_TICKS == 0;
        SubLevelContainer container = SubLevelContainer.getContainer(mc.level);
        if (container == null) {
            VISIBLE.clear();
            return;
        }
        Set<UUID> alive = full ? new HashSet<>() : null;
        Vec3 eye = mc.player.position();
        PressureGogglesItem.Mode mode = PressureGogglesItem.mode(mc.player.getItemBySlot(EquipmentSlot.HEAD));
        boolean hybrid = SubmarineConfig.hybridPressure();
        boolean scanned = false;
        for (SubLevel sub : container.getAllSubLevels()) {
            if (sub.isRemoved() || sub.getPlot() == null)
                continue;
            UUID id = sub.getUniqueId();
            BoundingBox3ic b = sub.getPlot().getBoundingBox();
            boolean near = near(sub.logicalPose(), b, eye);
            if (!full && !near)
                continue;
            Vector3dc center = sub.logicalPose().position();
            if (eye.distanceToSqr(center.x(), center.y(), center.z()) > RANGE * RANGE) {
                VISIBLE.remove(id);
                continue;
            }
            if (alive != null)
                alive.add(id);
            int edits = CompartmentTracker.clientEdits(id);
            long bounds = boundsKey(b);
            int[] seen = EDIT_SEEN.computeIfAbsent(id, k -> new int[] { edits, ticks });
            if (seen[0] != edits) {
                seen[0] = edits;
                seen[1] = ticks;
            }
            Shape shape = SHAPES.get(id);
            boolean stale = shape == null || shape.edits() != edits || shape.bounds() != bounds
                    || ticks - shape.tick() > RESCAN_TICKS;
            boolean settled = shape == null || ticks - seen[1] >= (near ? NEAR_SETTLE_TICKS : SETTLE_TICKS);
            if (stale && settled && (near || !scanned) && !PENDING.contains(id)) {
                scan(sub.getPlot(), id, b, edits, bounds);
                scanned |= !near;
            }
            if (shape == null || shape.factors().isEmpty()) {
                VISIBLE.remove(id);
                continue;
            }
            Mesh mesh = MESHES.get(id);
            if (mesh == null || mesh.shape != shape || near && mesh.edits != edits) {
                GirderSupport.Layout supports = GirderSupport.layout(mc.level, id, edits, b.minX(), b.minY(), b.minZ(),
                        b.maxX(), b.maxY(), b.maxZ());
                mesh = build(mc.level, shape, supports);
                mesh.edits = edits;
                MESHES.put(id, mesh);
            }
            if (mesh.n == 0) {
                VISIBLE.remove(id);
                continue;
            }
            colour(mc.level, sub, mesh, mode, hybrid);
            VISIBLE.put(id, new Visible(sub, mesh));
        }
        if (alive != null) {
            VISIBLE.keySet().retainAll(alive);
            MESHES.keySet().removeIf(id -> !SHAPES.containsKey(id));
        }
    }

    private static boolean near(Pose3dc pose, BoundingBox3ic b, Vec3 eye) {
        Vector3d p = pose.transformPositionInverse(new Vector3d(eye.x, eye.y, eye.z));
        double dx = Math.max(Math.max(b.minX() - p.x, p.x - (b.maxX() + 1)), 0.0);
        double dy = Math.max(Math.max(b.minY() - p.y, p.y - (b.maxY() + 1)), 0.0);
        double dz = Math.max(Math.max(b.minZ() - p.z, p.z - (b.maxZ() + 1)), 0.0);
        return dx * dx + dy * dy + dz * dz <= NEAR * NEAR;
    }

    private static long boundsKey(BoundingBox3ic b) {
        long h = b.minX();
        h = h * 31 + b.minY();
        h = h * 31 + b.minZ();
        h = h * 31 + b.maxX();
        h = h * 31 + b.maxY();
        return h * 31 + b.maxZ();
    }

    private static void scan(LevelPlot plot, UUID id, BoundingBox3ic b, int edits, long bounds) {
        int minX = b.minX(), minY = b.minY(), minZ = b.minZ();
        int maxX = b.maxX(), maxY = b.maxY(), maxZ = b.maxZ();
        int sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;
        if ((long) sx * sy * sz > MAX_VOLUME) {
            SHAPES.put(id, new Shape(edits, bounds, ticks, minX, minY, minZ, new Long2FloatOpenHashMap()));
            return;
        }
        LongArrayList solid = new LongArrayList();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk chunk = plot.getChunk(plot.toLocal(new ChunkPos(cx, cz)));
                if (chunk == null)
                    continue;
                for (int cy = minY >> 4; cy <= maxY >> 4; cy++) {
                    int si = chunk.getSectionIndexFromSectionY(cy);
                    if (si < 0 || si >= chunk.getSections().length)
                        continue;
                    LevelChunkSection section = chunk.getSection(si);
                    if (section == null || section.hasOnlyAir())
                        continue;
                    int x0 = Math.max(minX, cx << 4), x1 = Math.min(maxX, (cx << 4) + 15);
                    int y0 = Math.max(minY, cy << 4), y1 = Math.min(maxY, (cy << 4) + 15);
                    int z0 = Math.max(minZ, cz << 4), z1 = Math.min(maxZ, (cz << 4) + 15);
                    for (int x = x0; x <= x1; x++)
                        for (int y = y0; y <= y1; y++)
                            for (int z = z0; z <= z1; z++) {
                                if (CompartmentDetector.isPermeable(section.getBlockState(x & 15, y & 15, z & 15)))
                                    continue;
                                solid.add(m.set(x, y, z).asLong());
                            }
                }
            }
        }
        int span = SubmarineConfig.SHAPE_REFERENCE_SPAN.get();
        double min = SubmarineConfig.SHAPE_FACTOR_MIN.get();
        double max = SubmarineConfig.SHAPE_FACTOR_MAX.get();
        int tick = ticks;
        PENDING.add(id);
        HullShapeAnalyzer.submit(() -> {
            try {
                Long2FloatOpenHashMap factors = analyse(solid, minX, minY, minZ, sx, sy, sz, span, min, max);
                SHAPES.put(id, new Shape(edits, bounds, tick, minX, minY, minZ, factors));
            } catch (RuntimeException e) {
                CreateSubmarine.LOGGER.warn("Pressure scan failed for {}", id, e);
            } finally {
                PENDING.remove(id);
            }
        });
    }

    private static Long2FloatOpenHashMap analyse(LongArrayList solid, int minX, int minY, int minZ,
            int sx, int sy, int sz, int span, double min, double max) {
        int n = solid.size();
        int gx = sx + 2, gy = sy + 2, gz = sz + 2;
        boolean[] filled = new boolean[gx * gy * gz];
        int[] x = new int[n], y = new int[n], z = new int[n];
        Long2IntOpenHashMap index = new Long2IntOpenHashMap(n);
        index.defaultReturnValue(-1);
        for (int i = 0; i < n; i++) {
            long p = solid.getLong(i);
            x[i] = BlockPos.getX(p);
            y[i] = BlockPos.getY(p);
            z[i] = BlockPos.getZ(p);
            index.put(p, i);
            filled[cell(x[i] - minX + 1, y[i] - minY + 1, z[i] - minZ + 1, gy, gz)] = true;
        }

        boolean[] outside = new boolean[filled.length];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        outside[0] = true;
        queue.add(new int[] { 0, 0, 0 });
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (Direction d : Direction.values()) {
                int cx = c[0] + d.getStepX(), cy = c[1] + d.getStepY(), cz = c[2] + d.getStepZ();
                if (cx < 0 || cy < 0 || cz < 0 || cx >= gx || cy >= gy || cz >= gz)
                    continue;
                int k = cell(cx, cy, cz, gy, gz);
                if (outside[k] || filled[k])
                    continue;
                outside[k] = true;
                queue.add(new int[] { cx, cy, cz });
            }
        }

        boolean[][] exterior = new boolean[n][6];
        for (int i = 0; i < n; i++) {
            for (int d = 0; d < 6; d++) {
                int cx = x[i] - minX + 1 + SolverCore.DX[d];
                int cy = y[i] - minY + 1 + SolverCore.DY[d];
                int cz = z[i] - minZ + 1 + SolverCore.DZ[d];
                exterior[i][d] = outside[cell(cx, cy, cz, gy, gz)];
            }
        }
        return HullShapeAnalyzer.factors(n, x, y, z, index, exterior, span, min, max);
    }

    private static int cell(int x, int y, int z, int gy, int gz) {
        return (x * gy + y) * gz + z;
    }

    private static Mesh build(ClientLevel level, Shape shape, GirderSupport.Layout supports) {
        Long2FloatOpenHashMap factors = shape.factors();
        int cap = factors.size();
        int[] lx = new int[cap], ly = new int[cap], lz = new int[cap];
        float[] base = new float[cap], factor = new float[cap];
        LongOpenHashSet present = new LongOpenHashSet(cap);
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Long2FloatMap.Entry e : factors.long2FloatEntrySet()) {
            m.set(e.getLongKey());
            BlockState state = level.getBlockState(m);
            if (state.isAir())
                continue;
            Optional<HullStrengthConfig.HullProperty> prop = HullStrengthConfig
                    .getFor(SubmarinePressureSystem.getActualBlockState(level, m, state));
            if (prop.isEmpty())
                continue;
            lx[n] = m.getX() - shape.ax();
            ly[n] = m.getY() - shape.ay();
            lz[n] = m.getZ() - shape.az();
            base[n] = (float) (prop.get().maxWaterDepth() * GirderSupport.factor(supports, m));
            factor[n] = e.getFloatValue();
            present.add(e.getLongKey());
            n++;
        }

        FloatArrayList quads = new FloatArrayList();
        IntArrayList quadCell = new IntArrayList();
        for (int i = 0; i < n; i++) {
            int wx = lx[i] + shape.ax(), wy = ly[i] + shape.ay(), wz = lz[i] + shape.az();
            for (Direction d : Direction.values()) {
                if (present.contains(BlockPos.asLong(wx + d.getStepX(), wy + d.getStepY(), wz + d.getStepZ())))
                    continue;
                face(quads, lx[i], ly[i], lz[i], d);
                quadCell.add(i);
            }
        }
        return new Mesh(shape, n, lx, ly, lz, base, factor, quads.toFloatArray(), quadCell.toIntArray());
    }

    private static final float LO = -0.005f, HI = 1.005f;

    private static void face(FloatArrayList q, float x, float y, float z, Direction d) {
        float a = x + LO, b = x + HI, c = y + LO, e = y + HI, f = z + LO, g = z + HI;
        switch (d) {
            case DOWN -> q.addElements(q.size(), new float[] { a, c, f, b, c, f, b, c, g, a, c, g });
            case UP -> q.addElements(q.size(), new float[] { a, e, f, a, e, g, b, e, g, b, e, f });
            case NORTH -> q.addElements(q.size(), new float[] { a, c, f, a, e, f, b, e, f, b, c, f });
            case SOUTH -> q.addElements(q.size(), new float[] { a, c, g, b, c, g, b, e, g, a, e, g });
            case WEST -> q.addElements(q.size(), new float[] { a, c, f, a, c, g, a, e, g, a, e, f });
            case EAST -> q.addElements(q.size(), new float[] { b, c, f, b, e, f, b, e, g, b, c, g });
        }
    }

    private static void colour(ClientLevel level, SubLevel sub, Mesh mesh, PressureGogglesItem.Mode mode,
            boolean hybrid) {
        Pose3dc pose = sub.logicalPose();
        SubmarinePressureSystem.LiquidColumn column = SubmarinePressureSystem.measureColumn(level, pose.position());
        int surface = column.surfaceY();
        double density = column.density();
        long key = poseKey(pose);
        key = key * 31 + surface;
        key = key * 31 + Math.round(density * 100);
        key = key * 31 + mode.ordinal();
        key = key * 31 + (hybrid ? 1 : 0);
        if (key == mesh.key)
            return;
        mesh.key = key;

        int n = mesh.n;
        double[] worldY = new double[n];
        double[] limit = new double[n];
        double top = Double.NEGATIVE_INFINITY;
        Vector3d w = new Vector3d();
        Shape s = mesh.shape;
        for (int i = 0; i < n; i++) {
            w.set(s.ax() + mesh.lx[i] + 0.5, s.ay() + mesh.ly[i] + 0.5, s.az() + mesh.lz[i] + 0.5);
            pose.transformPosition(w);
            worldY[i] = w.y;
            limit[i] = Math.max(1.0, mesh.base[i] * (hybrid ? mesh.factor[i] : 1.0) / density);
            top = Math.max(top, w.y);
        }

        if (mode == PressureGogglesItem.Mode.DISTRIBUTION) {
            double reference = surface == Integer.MIN_VALUE ? top + 1.0 : surface;
            double max = 0;
            double[] load = new double[n];
            for (int i = 0; i < n; i++) {
                load[i] = Math.max(0.0, reference - worldY[i]) / limit[i];
                max = Math.max(max, load[i]);
            }
            for (int i = 0; i < n; i++)
                mesh.colors[i] = shade(max > 0 ? load[i] / max : 0.0);
            return;
        }

        double crush = Double.POSITIVE_INFINITY;
        if (surface == Integer.MIN_VALUE) {
            for (int i = 0; i < n; i++)
                crush = Math.min(crush, limit[i] - (top - worldY[i]));
        }
        for (int i = 0; i < n; i++) {
            double depth = surface == Integer.MIN_VALUE ? crush + top - worldY[i] : surface - Math.floor(worldY[i]);
            mesh.colors[i] = shade(Math.max(0.0, depth) / limit[i]);
        }
    }

    private static long poseKey(Pose3dc pose) {
        Vector3dc p = pose.position();
        Quaterniondc q = pose.orientation();
        long h = Math.round(p.x() * 10);
        h = h * 31 + Math.round(p.y() * 10);
        h = h * 31 + Math.round(p.z() * 10);
        h = h * 31 + Math.round(q.x() * 1000);
        h = h * 31 + Math.round(q.y() * 1000);
        h = h * 31 + Math.round(q.z() * 1000);
        return h * 31 + Math.round(q.w() * 1000);
    }

    private static final float[][] RAMP = {
            { 0.05f, 0.05f, 0.45f },
            { 0.10f, 0.35f, 1.00f },
            { 0.00f, 0.85f, 0.85f },
            { 0.20f, 0.90f, 0.20f },
            { 1.00f, 0.90f, 0.10f },
            { 1.00f, 0.45f, 0.05f },
            { 0.95f, 0.05f, 0.05f },
    };

    private static int shade(double h) {
        double t = Math.max(0.0, Math.min(1.0, h)) * (RAMP.length - 1);
        int i = Math.min(RAMP.length - 2, (int) t);
        float f = (float) (t - i);
        float r = RAMP[i][0] + (RAMP[i + 1][0] - RAMP[i][0]) * f;
        float g = RAMP[i][1] + (RAMP[i + 1][1] - RAMP[i][1]) * f;
        float b = RAMP[i][2] + (RAMP[i + 1][2] - RAMP[i][2]) * f;
        return ALPHA << 24 | (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || VISIBLE.isEmpty())
            return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        Vector3d anchor = new Vector3d();
        float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        for (Map.Entry<UUID, Visible> e : VISIBLE.entrySet()) {
            SubLevel sub = e.getValue().sub();
            if (sub.isRemoved())
                continue;
            Mesh mesh = e.getValue().mesh();
            Pose3dc pose = sub instanceof ClientSubLevel client ? client.renderPose(partialTicks) : sub.logicalPose();
            anchor.set(mesh.shape.ax(), mesh.shape.ay(), mesh.shape.az());
            pose.transformPosition(anchor);
            Quaterniondc q = pose.orientation();
            poseStack.pushPose();
            poseStack.translate(anchor.x - cam.x, anchor.y - cam.y, anchor.z - cam.z);
            poseStack.mulPose(new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()));
            Matrix4f mat = poseStack.last().pose();
            float[] v = mesh.quads;
            int[] cells = mesh.quadCell;
            int[] colors = mesh.colors;
            for (int k = 0, o = 0; k < cells.length; k++) {
                int c = colors[cells[k]];
                for (int j = 0; j < 4; j++, o += 3)
                    consumer.addVertex(mat, v[o], v[o + 1], v[o + 2]).setColor(c);
            }
            poseStack.popPose();
        }
        buffers.endBatch(RenderType.debugQuads());
    }
}
