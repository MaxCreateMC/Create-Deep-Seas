package com.maxenonyme.createsubmarine.submarine.stress;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3d;
import java.util.*;

public class StressForceFeedSystem {
    private static final Map<UUID, Long> stressLastGameTick = new HashMap<>();
    private static final Map<UUID, Integer> stressSubstepIdx = new HashMap<>();
    private static final int SOLVE_INTERVAL = 4;
    private static final Map<UUID, Long> lastSolvedGameTick = new HashMap<>();

    

    public static final ResourceLocation STRESS_ID = ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "stress");
    public static final ResourceLocation INTERNAL_STRESS_ID = ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "internal_stresses");
    public static final ResourceLocation BUOYANCY_ID = ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "buoyancy");

    public static ForceGroup getStressForceGroup() {
        return ForceGroups.REGISTRY.get(STRESS_ID);
    }

    public static ForceGroup getInternalStressForceGroup() {
        return ForceGroups.REGISTRY.get(INTERNAL_STRESS_ID);
    }

    public static ForceGroup getBuoyancyForceGroup() {
        return ForceGroups.REGISTRY.get(BUOYANCY_ID);
    }

    public static void register() {
        ForceGroup fg = getStressForceGroup();
        if (fg != null) {
            fg.defaultDisplayed();
        }
    }

    public static void registerListeners() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, ForgeSablePostPhysicsTickEvent.class,
            StressForceFeedSystem::onPostPhysicsTick);
    }

    public static void onPostPhysicsTick(final ForgeSablePostPhysicsTickEvent event) {
        final var level = event.getPhysicsSystem().getLevel();
        final var container = SubLevelContainer.getContainer(level);
        if (!(container instanceof final ServerSubLevelContainer sslc)) return;

        final double timeStep = event.getTimeStep();
        final long gameTime = level.getGameTime();
        final int substepsPerTick = Math.max(1, (int) Math.round(0.05 / timeStep));

        for (final ServerSubLevel ssl : sslc.getAllSubLevels()) {
            if (ssl.isRemoved()) continue;

            final UUID id = ssl.getUniqueId();
            if (stressLastGameTick.getOrDefault(id, -1L) != gameTime) {
                stressLastGameTick.put(id, gameTime);
                stressSubstepIdx.put(id, 0);
            }
            final int idx = stressSubstepIdx.getOrDefault(id, 0);
            stressSubstepIdx.put(id, idx + 1);

            recordForSubLevel(ssl, timeStep, idx, idx + 1 >= substepsPerTick);
        }
    }

    public static void recordForSubLevel(final ServerSubLevel ssl, final double timeStep) {
        recordForSubLevel(ssl, timeStep, 0, true);
    }

    private static void recordForSubLevel(final ServerSubLevel ssl, final double timeStep,
                                           final int substepIdx, final boolean isLastSubstep) {
        final ForceGroup stressGroup = getStressForceGroup();
        final ForceGroup internalGroup = getInternalStressForceGroup();
        final ForceGroup buoyancyGroup = getBuoyancyForceGroup();
        if (stressGroup == null) return;

        final SubLevelStressAnalyzer analyzer = SubLevelStressAnalyzer.INSTANCES.get(ssl.getLevel());
        if (analyzer == null) return;

        final LatticeStressSolver solver = analyzer.getSolver(ssl);
        if (solver == null || solver.blockCount() == 0) return;

        final UUID subId = ssl.getUniqueId();
        final long tick = ssl.getLevel().getGameTime();
        final boolean shouldSolve = isLastSubstep && (
            !lastSolvedGameTick.containsKey(subId) ||
            tick - lastSolvedGameTick.get(subId) >= SOLVE_INTERVAL
        );
        if (shouldSolve) {
            lastSolvedGameTick.put(subId, tick);
            final double freshWaterSurface = SubLevelStressAnalyzer.getWaterSurfaceWorldY(ssl);
            solver.refreshWaterDepths(freshWaterSurface, ssl.logicalPose());
            solver.resolve();
        }

        if (!ssl.isTrackingIndividualQueuedForces()) {
            ssl.enableIndividualQueuedForcesTracking(true);
        }
        final QueuedForceGroup queued = ssl.getOrCreateQueuedForceGroup(stressGroup);
        final QueuedForceGroup internalQueued = internalGroup != null
            ? ssl.getOrCreateQueuedForceGroup(internalGroup) : queued;
        if (queued == null) return;

        final double[] stressDist = solver.getStressDistribution();
        final int n = solver.blockCount();

        // Only set crush depths, broadcast, and check failure on the LAST substep per game tick.
        // Forces are NOT recorded here: the unconditional call below already runs on every
        // substep including the last one, and recording in both places doubled every
        // queued force on the final substep.
        if (isLastSubstep) {
            analyzer.setCrushDepths(ssl, solver.computeCrushDepth());

            checkStructuralFailure(ssl, solver, analyzer);
        }

        // Always record forces on every substep (drive the physics)
        recordFaceForces(solver, queued, stressDist, n, timeStep);
        recordInternalForces(solver, internalQueued, n, stressDist, timeStep);

        if (buoyancyGroup != null) {
            recordBuoyancyForce(ssl, buoyancyGroup, solver, timeStep);
        }

    }

    private static void checkStructuralFailure(final ServerSubLevel ssl, final LatticeStressSolver solver,
                                                final SubLevelStressAnalyzer analyzer) {
        final int n = solver.blockCount();
        if (n <= 1) return;

        final Level plotLevel = ssl.getLevel();
        boolean brokeAny = false;
        int brokeCount = 0;

        for (int i = 0; i < n; i++) {
            final double ratio = solver.getStressRatio(i);
            if (ratio < 1.0) continue;

            final BlockPos pos = solver.getPosition(i);
            final BlockState state = plotLevel.getBlockState(pos);
            if (state.isAir()) continue;

            plotLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            brokeAny = true;
            brokeCount++;
        }

        if (brokeAny) {
            analyzer.markDirty(ssl);
        }
    }

    private static void recordBuoyancyForce(final ServerSubLevel ssl, final ForceGroup group, final LatticeStressSolver solver, final double timeStep) {
        // buoyancy constant derived from the effective hydrostatic scale:
        //  F = rho * g * V, with rho*g = solver rhoG (10 000 Pa/m for water,
        //  31 000 for lava) -> per-volume force ~ rhoG/1000 [N per block-volume].
        final double SABLE_BUOYANCY_CONST = solver.getEffectiveRhoG() / 1000.0;

        double totalBuoyancy = 0;
        double sumX = 0, sumY = 0, sumZ = 0;
        final int n = solver.blockCount();
        final int hullCount = solver.hullBlockCount();
        int counted = 0;

        for (int i = 0; i < n; i++) {
            final double waterDepth = solver.getBlockWaterDepth(i);
            if (waterDepth <= 0) continue;

            if (!solver.isHullBlock(i)) continue;

            final double submergedFrac = Math.min(Math.max(waterDepth, 0), 1);

            final BlockPos pos = solver.getPosition(i);
            final BlockState state = ssl.getLevel().getBlockState(pos);
            final double blockVolume = PhysicsBlockPropertyHelper.getVolume(state);

            final double b = SABLE_BUOYANCY_CONST * blockVolume * submergedFrac;
            if (b <= 0) continue;

            totalBuoyancy += b;
            sumX += b * (pos.getX() + 0.5);
            sumY += b * (pos.getY() + 0.5);
            sumZ += b * (pos.getZ() + 0.5);
            counted++;
        }

        if (totalBuoyancy <= 0) {
            return;
        }

        // Multiply by timeStep (impulse convention) so ForceTrackingDispatcher's division recovers correct force
        final double recordedBuoyancy = totalBuoyancy * timeStep;

        final Vector3d localUp = new Vector3d(0, recordedBuoyancy, 0);
        ssl.logicalPose().orientation().transformInverse(localUp);

        final QueuedForceGroup queued = ssl.getOrCreateQueuedForceGroup(group);
        queued.recordPointForce(
            new Vector3d(sumX / totalBuoyancy, sumY / totalBuoyancy, sumZ / totalBuoyancy),
            localUp
        );
    }

    private static int recordFaceForces(final LatticeStressSolver solver, final QueuedForceGroup queued,
                                                                                                  final double[] stressDist, final int n, final double timeStep) {
        final double[][] faceOff = { {0,0.5,0.5}, {1,0.5,0.5}, {0.5,0,0.5}, {0.5,1,0.5}, {0.5,0.5,0}, {0.5,0.5,1} };
        final double[][] dirVec  = { {-1,0,0}, {1,0,0}, {0,-1,0}, {0,1,0}, {0,0,-1}, {0,0,1} };

        final Map<BlockPos, double[]> faceForce = new HashMap<>();
        for (int i = 0; i < n; i++) {
            final double frac = stressDist[i];
            if (frac <= 0) continue;

            final BlockPos pos = solver.getPosition(i);
            final double[] ff = new double[6];
            int any = 0;
            for (int d = 0; d < 6; d++) {
                if (solver.isFaceExposed(i, d)) {
                    ff[d] = frac * timeStep * com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.STRESS_FORCE_MAX.get();
                    any++;
                }
            }
            if (any > 0) faceForce.put(pos, ff);
        }

        final int[][][] planeNeighbours = {
            {{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}},
            {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1}},
            {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0}},
        };

        int totalArrows = 0;

        for (int dir = 0; dir < 6; dir++) {
            final Set<BlockPos> candidates = new HashSet<>();
            for (final Map.Entry<BlockPos, double[]> e : faceForce.entrySet()) {
                if (e.getValue()[dir] > 0) candidates.add(e.getKey());
            }
            if (candidates.isEmpty()) continue;

            final Set<BlockPos> visited = new HashSet<>();
            final int plane = dir / 2;
            final int[][] neighbourDeltas = planeNeighbours[plane];

            for (final BlockPos start : candidates) {
                if (visited.contains(start)) continue;

                final Deque<BlockPos> stack = new ArrayDeque<>();
                stack.push(start);
                double compForce = 0;
                double compWx = 0, compWy = 0, compWz = 0;
                int compCount = 0;

                while (!stack.isEmpty()) {
                    final BlockPos cur = stack.pop();
                    if (!visited.add(cur)) continue;
                    if (!candidates.contains(cur)) continue;

                    final double mag = faceForce.get(cur)[dir];
                    final double[] fo = faceOff[dir];
                    final double cx = cur.getX() + fo[0];
                    final double cy = cur.getY() + fo[1];
                    final double cz = cur.getZ() + fo[2];

                    compForce += mag;
                    compWx += mag * cx;
                    compWy += mag * cy;
                    compWz += mag * cz;
                    compCount++;

                    for (final int[] nd : neighbourDeltas) {
                        stack.push(new BlockPos(
                            cur.getX() + nd[0],
                            cur.getY() + nd[1],
                            cur.getZ() + nd[2]
                        ));
                    }
                }

                if (compForce <= 0) continue;

                final double arrowMag = Math.min(compForce, 1.0e6);
                final Vector3d centroid = new Vector3d(
                    compWx / compForce,
                    compWy / compForce,
                    compWz / compForce
                );
                final double[] dv = dirVec[dir];
                queued.recordPointForce(centroid,
                    new Vector3d(dv[0] * arrowMag, dv[1] * arrowMag, dv[2] * arrowMag));
                totalArrows++;
            }
        }
        return totalArrows;
    }

    private static int recordInternalForces(final LatticeStressSolver solver, final QueuedForceGroup queued,
                                             final int n, final double[] stressDist, final double timeStep) {
        final double[] u = solver.getU();
        final int[] dx = LatticeStressSolver.getDX();
        final int[] dy = LatticeStressSolver.getDY();
        final int[] dz = LatticeStressSolver.getDZ();
        final double[] invDist = LatticeStressSolver.getInvDist();
        final int maxDir = LatticeStressSolver.getMaxNeighbors();

        final double maxForce = com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.STRESS_FORCE_MAX.get();
        int arrows = 0;

        for (int i = 0; i < n; i++) {
            final double frac = stressDist[i];
            if (frac <= 0) continue;

            final BlockPos pi = solver.getPosition(i);
            final int i3 = 6 * i;
            final double uix = u[i3], uiy = u[i3 + 1], uiz = u[i3 + 2];

            double netX = 0, netY = 0, netZ = 0;

            for (int dir = 0; dir < maxDir; dir++) {
                final int j = solver.getNeighbor(i, dir);
                if (j < 0) continue;

                final double Kij = solver.getSpringK(i, dir);
                if (Math.abs(Kij) < 1e-15) continue;

                final int j3 = 6 * j;
                final double dux = u[j3] - uix;
                final double duy = u[j3 + 1] - uiy;
                final double duz = u[j3 + 2] - uiz;

                if (dir < 6) {
                    final int comp = dir / 2;
                    final double sign = (dir % 2 == 0) ? 1.0 : -1.0;
                    final double du = sign * (u[j3 + comp] - (comp == 0 ? uix : comp == 1 ? uiy : uiz));
                    final double fComp = Kij * du * sign;
                    if (comp == 0) netX += fComp;
                    else if (comp == 1) netY += fComp;
                    else netZ += fComp;
                } else {
                    final double cosX = dx[dir] * invDist[dir];
                    final double cosY = dy[dir] * invDist[dir];
                    final double cosZ = dz[dir] * invDist[dir];
                    final double duProj = cosX * dux + cosY * duy + cosZ * duz;
                    final double force = Kij * duProj;
                    netX += force * cosX;
                    netY += force * cosY;
                    netZ += force * cosZ;
                }
            }

            final double netMag = Math.sqrt(netX * netX + netY * netY + netZ * netZ);
            if (netMag < 1e-6) continue;

            // Uniform scale preserves Newton's 3rd law cancellation; 1e6 N Ã¢â€ â€™ ~8000 displayed
            final double UNIFORM_SCALE = 0.008;
            final double recordedMag = netMag * UNIFORM_SCALE * timeStep;
            if (recordedMag < 0.01) continue;

            final double cx = pi.getX() + 0.5;
            final double cy = pi.getY() + 0.5;
            final double cz = pi.getZ() + 0.5;

            final double scale = recordedMag / netMag;
            queued.recordPointForce(new Vector3d(cx, cy, cz),
                new Vector3d(netX * scale, netY * scale, netZ * scale));
            arrows++;
        }
        return arrows;
    }

}
