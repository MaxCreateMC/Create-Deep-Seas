package com.maxenonyme.createsubmarine.submarine.compartment;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompartmentDetector {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Deep Seas compartments");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private static int maxBlocks() {
        var spec = SubmarineConfig.SERVER_SPEC;
        return spec.isLoaded()
                ? SubmarineConfig.OXYGEN_MAX_FILL_BLOCKS.get()
                : 500_000;
    }

    public record Component(Set<BlockPos> internal, Set<BlockPos> hull, boolean sealed, BlockPos anchor) {
    }

    public record Result(List<Component> components, int totalScanned, Set<BlockPos> solidBlocks,
            Set<BlockPos> sealed, Set<BlockPos> visual) {

        public Result(List<Component> components, int totalScanned, Set<BlockPos> solidBlocks) {
            this(components, totalScanned, solidBlocks, sealedCells(components), visualCells(components, solidBlocks));
        }
    }

    private static Set<BlockPos> sealedCells(List<Component> components) {
        Set<BlockPos> sealed = new HashSet<>();

        for (Component compartment : components) {
            if (compartment.sealed())
                sealed.addAll(compartment.internal());
        }

        return Collections.unmodifiableSet(sealed);
    }

    private static Set<BlockPos> visualCells(List<Component> components, Set<BlockPos> solidBlocks) {
        Set<BlockPos> visual = new HashSet<>();
        boolean anySealed = false;

        for (Component compartment : components) {
            if (!compartment.sealed())
                continue;

            anySealed = true;
            visual.addAll(compartment.internal());
            visual.addAll(compartment.hull());
        }

        if (anySealed)
            visual.addAll(solidBlocks);

        return Collections.unmodifiableSet(visual);
    }

    public static class Scan {
        private final PlotSnapshot plot;
        private final Map<BlockState, HullSeal.Shape> shapes = new IdentityHashMap<>();
        private final Set<BlockState> partial = Collections.newSetFromMap(new IdentityHashMap<>());
        private final LongOpenHashSet plugs = new LongOpenHashSet();
        private final int max;

        private final LongOpenHashSet visited = new LongOpenHashSet();
        private final Set<BlockPos> solid = new HashSet<>();
        private final List<Component> components = new ArrayList<>();
        private int total;
        private boolean chunksMissing;
        private Result result;

        Scan(LevelPlot plot, Set<BlockPos> plugs, int max) {
            this.plot = new PlotSnapshot(plot);
            this.max = max;

            for (BlockPos plug : plugs)
                this.plugs.add(plug.asLong());

            this.plot.palette(this::remember);
        }

        public boolean chunksMissing() {
            return chunksMissing;
        }

        public Result result() {
            return result;
        }

        private void remember(BlockState state) {
            if (shapes.containsKey(state))
                return;

            HullSeal.Shape shape = HullSeal.of(state);
            shapes.put(state, shape);
            if (HullSeal.partial(shape))
                partial.add(state);
        }

        private Scan run() {
            for (int x = plot.minX; x <= plot.maxX; x++) {
                for (int y = plot.minY; y <= plot.maxY; y++) {
                    for (int z = plot.minZ; z <= plot.maxZ; z++) {
                        if (total >= max)
                            return finish();

                        long key = BlockPos.asLong(x, y, z);
                        if (!visited.add(key))
                            continue;

                        total++;

                        BlockState state = plot.get(x, y, z);
                        if (state == null) {
                            chunksMissing = true;
                            continue;
                        }

                        HullSeal.Shape shape = shapes.get(state);
                        if (shape.passable() && !plugs.contains(key))
                            flood(key, state);
                        else
                            solid.add(new BlockPos(x, y, z));
                    }
                }
            }

            return finish();
        }

        private void flood(long start, BlockState startState) {
            LongOpenHashSet inside = new LongOpenHashSet();
            Set<BlockPos> hull = new HashSet<>();
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            boolean sealed = true;
            long anchor = start;

            inside.add(start);
            queue.enqueue(start);

            if (partial.contains(startState)) {
                BlockPos pos = BlockPos.of(start);
                hull.add(pos);
                solid.add(pos);
            }

            while (!queue.isEmpty() && total < max) {
                long current = queue.dequeueLong();
                int x = BlockPos.getX(current), y = BlockPos.getY(current), z = BlockPos.getZ(current);
                HullSeal.Shape shape = shapes.get(plot.get(x, y, z));

                for (Direction dir : Direction.values()) {
                    int nx = x + dir.getStepX(), ny = y + dir.getStepY(), nz = z + dir.getStepZ();
                    long next = BlockPos.asLong(nx, ny, nz);
                    if (inside.contains(next) || !shape.opens(dir))
                        continue;

                    if (nx < plot.minX || nx > plot.maxX || ny < plot.minY || ny > plot.maxY
                            || nz < plot.minZ || nz > plot.maxZ) {
                        sealed = false;
                        continue;
                    }

                    BlockState state = plot.get(nx, ny, nz);
                    if (state == null) {
                        chunksMissing = true;
                        sealed = false;
                        continue;
                    }

                    HullSeal.Shape nextShape = shapes.get(state);
                    boolean open = nextShape.passable() && !plugs.contains(next);
                    if (open && HullSeal.connects(shape, dir, nextShape)) {
                        if (!visited.add(next))
                            continue;

                        total++;
                        inside.add(next);
                        queue.enqueue(next);

                        if (partial.contains(state)) {
                            BlockPos pos = new BlockPos(nx, ny, nz);
                            hull.add(pos);
                            solid.add(pos);
                        }

                        if (before(next, anchor))
                            anchor = next;
                        if (total >= max)
                            break;
                    } else if (open) {
                        BlockPos pos = new BlockPos(nx, ny, nz);
                        hull.add(pos);
                        solid.add(pos);
                    } else {
                        BlockPos pos = new BlockPos(nx, ny, nz);
                        hull.add(pos);

                        if (visited.add(next)) {
                            total++;
                            solid.add(pos);
                        }
                    }
                }
            }

            Set<BlockPos> internal = new HashSet<>(inside.size() * 2);

            for (LongIterator it = inside.iterator(); it.hasNext();)
                internal.add(BlockPos.of(it.nextLong()));

            components.add(new Component(Collections.unmodifiableSet(internal), Collections.unmodifiableSet(hull),
                    sealed, BlockPos.of(anchor)));
        }

        private Scan finish() {
            result = new Result(Collections.unmodifiableList(components), total, Collections.unmodifiableSet(solid));
            return this;
        }
    }

    public static CompletableFuture<Scan> start(SubLevelAccess subAccess, Set<BlockPos> plugs) {
        if (!(subAccess instanceof SubLevel sub))
            return null;

        LevelPlot plot = sub.getPlot();
        if (plot == null)
            return null;

        Scan scan = new Scan(plot, plugs, maxBlocks());
        UUID id = sub.getUniqueId();

        return CompletableFuture.supplyAsync(() -> {
            try {
                return scan.run();
            } catch (RuntimeException e) {
                CreateSubmarine.LOGGER.warn("Couldn't scan the compartments of {}", id, e);
                return null;
            }
        }, WORKER);
    }

    public static boolean isPermeable(BlockState state) {
        return HullSeal.of(state) == HullSeal.OPEN;
    }

    private static boolean before(long a, long b) {
        int dx = Integer.compare(BlockPos.getX(a), BlockPos.getX(b));
        if (dx != 0)
            return dx < 0;

        int dy = Integer.compare(BlockPos.getY(a), BlockPos.getY(b));
        if (dy != 0)
            return dy < 0;

        return BlockPos.getZ(a) < BlockPos.getZ(b);
    }
}
