package com.maxenonyme.highseas.sail;

import com.maxenonyme.highseas.block.BoatSailBlock;
import com.maxenonyme.highseas.block.entity.HalyardBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class Halyard {
    private Halyard() {
    }

    public static final double FOLD = 0.55;
    private static final double SUPPORT_SHIFT = 0.375;
    private static final double SLACK = 0.02;
    private static final double SAG = 0.015;
    private static final double SAG_MIN = 0.05;
    private static final float STEP = 0.01f;
    private static final int SYNC = 4;
    private static final int REFRESH = 20;
    private static final double MOVED = 1.0E-4;
    private static final int MAX_GROUP = 1024;

    private record Geometry(double head, double foot, double edge, double plane, long group) {
    }

    private record Column(int h, int count, BlockPos top, BlockPos bottom) {
    }

    public static void prepare(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof HalyardBlockEntity bridle))
            return;
        Geometry g = geometry(level, pos);
        if (g == null)
            return;
        bridle.makeHalyard(g.head(), g.foot(), g.edge(), g.plane(), g.group());
        bridle.notifyUpdate();
    }

    private static Map<Integer, Column> columns(Level level, BlockPos pos, Direction.Axis axis) {
        return columns(level, pos, axis, new HashSet<>());
    }

    private static Map<Integer, Column> columns(Level level, BlockPos pos, Direction.Axis axis, Set<BlockPos> seen) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(pos);
        queue.add(pos);
        Map<Integer, Column> columns = new HashMap<>();
        while (!queue.isEmpty() && seen.size() < MAX_GROUP) {
            BlockPos p = queue.poll();
            int h = axis == Direction.Axis.X ? p.getZ() : p.getX();
            Column c = columns.get(h);
            if (c == null)
                columns.put(h, new Column(h, 1, p, p));
            else
                columns.put(h, new Column(h, c.count() + 1, p.getY() > c.top().getY() ? p : c.top(),
                        p.getY() < c.bottom().getY() ? p : c.bottom()));
            for (Direction d : Direction.values()) {
                if (d.getAxis() == axis)
                    continue;
                BlockPos q = p.relative(d);
                if (!seen.contains(q) && sail(level, q, axis)) {
                    seen.add(q);
                    queue.add(q);
                }
            }
        }
        return columns;
    }

    private static Geometry geometry(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BoatSailBlock))
            return null;
        Direction.Axis axis = state.getValue(BoatSailBlock.AXIS);
        BlockPos min = SailDetector.groupMin(level, pos);
        if (min == null)
            return null;
        Map<Integer, Column> columns = columns(level, pos, axis);
        int lo = columns.keySet().stream().min(Integer::compare).orElseThrow();
        int hi = columns.keySet().stream().max(Integer::compare).orElseThrow();
        Column left = columns.get(lo), right = columns.get(hi);
        int h = axis == Direction.Axis.X ? pos.getZ() : pos.getX();
        boolean useLeft = left.count() != right.count() ? left.count() > right.count() : h - lo <= hi - h;
        Column luff = useLeft ? left : right;
        double edge = useLeft ? lo : hi + 1;
        Direction plus = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        int support = 0;
        for (int y = luff.bottom().getY(); y <= luff.top().getY(); y++) {
            BlockPos cell = luff.top().atY(y);
            if (!sail(level, cell, axis))
                continue;
            if (holds(level, cell.relative(plus)))
                support++;
            if (holds(level, cell.relative(plus.getOpposite())))
                support--;
        }
        int n = axis == Direction.Axis.X ? pos.getX() : pos.getZ();
        double plane = n + 0.5 + Integer.signum(support) * SUPPORT_SHIFT;
        return new Geometry(luff.top().getY() + 1.0, luff.bottom().getY(), edge, plane, min.asLong());
    }

    public static BlockPos relocate(ServerLevel level, BlockPos sail, BlockPos other) {
        BlockState state = level.getBlockState(sail);
        if (!(state.getBlock() instanceof BoatSailBlock) || !RopeItem.isValidRopeAttachment(level, sail))
            return sail;
        Set<BlockPos> canvas = new HashSet<>();
        Map<Integer, Column> columns = columns(level, sail, state.getValue(BoatSailBlock.AXIS), canvas);
        for (BlockPos p : canvas) {
            if (!p.equals(sail) && level.getBlockEntity(p) instanceof HalyardBlockEntity stray && stray.halyard
                    && stray.getBehavior() != null && !stray.getBehavior().isAttached())
                level.setBlock(p, level.getBlockState(p).setValue(BoatSailBlock.RIGGED, false), 3);
        }
        Column left = columns.get(columns.keySet().stream().min(Integer::compare).orElseThrow());
        Column right = columns.get(columns.keySet().stream().max(Integer::compare).orElseThrow());
        Column luff;
        if (left.count() != right.count()) {
            luff = left.count() > right.count() ? left : right;
        } else {
            Vec3 target = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(other));
            double l = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(left.top())).distanceToSqr(target);
            double r = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(right.top())).distanceToSqr(target);
            luff = l <= r ? left : right;
        }
        BlockPos best = luff.top();
        if (best.equals(sail))
            return sail;
        BlockState moved = level.getBlockState(best);
        if (!moved.getValue(BoatSailBlock.RIGGED))
            level.setBlock(best, moved.setValue(BoatSailBlock.RIGGED, true), 3);
        if (!RopeItem.isValidRopeAttachment(level, best))
            return sail;
        prepare(level, best);
        level.setBlock(sail, level.getBlockState(sail).setValue(BoatSailBlock.RIGGED, false), 3);
        return best;
    }

    private static void refresh(HalyardBlockEntity bridle, UUID sub) {
        Geometry g = geometry(bridle.getLevel(), bridle.getBlockPos());
        if (g == null)
            return;
        if (g.head() == bridle.headY && g.foot() == bridle.footY && g.edge() == bridle.edge
                && g.plane() == bridle.plane && g.group() == bridle.group)
            return;
        if (sub != null && g.group() != bridle.group) {
            FurlState.dropReef(sub, bridle.group);
            ReefSyncPayload.broadcast(sub);
        }
        bridle.headY = g.head();
        bridle.footY = g.foot();
        bridle.edge = g.edge();
        bridle.plane = g.plane();
        bridle.group = g.group();
        bridle.applied = -1.0f;
        bridle.notifyUpdate();
    }

    private static boolean holds(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !s.isAir() && s.getFluidState().isEmpty() && !s.is(BoatSailBlock.SAILS);
    }

    private static boolean sail(Level level, BlockPos pos, Direction.Axis axis) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof BoatSailBlock && s.getValue(BoatSailBlock.AXIS) == axis;
    }

    public static boolean isHalyard(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof HalyardBlockEntity bridle && bridle.halyard;
    }

    public static void tick(HalyardBlockEntity bridle, boolean attached) {
        Level level = bridle.getLevel();
        if (!(level instanceof ServerLevel server))
            return;
        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, bridle.getBlockPos());
        UUID sub = ship != null ? ship.getUniqueId() : null;
        if (bridle.ship != null && !bridle.ship.equals(sub)) {
            FurlState.dropReef(bridle.ship, bridle.group);
            ReefSyncPayload.broadcast(bridle.ship);
            bridle.ship = null;
            bridle.applied = -1.0f;
            bridle.setChanged();
        }
        if (!attached) {
            if (bridle.rest >= 0.0 && sub != null) {
                FurlState.dropReef(sub, bridle.group);
                ReefSyncPayload.broadcast(sub);
            }
            bridle.reef = 0.0f;
            bridle.rest = -1.0;
            bridle.lastLength = -1.0;
            return;
        }
        ServerRopeStrand strand = bridle.strand();
        if (strand == null)
            return;
        if (bridle.ship == null && sub != null) {
            bridle.ship = sub;
            refresh(bridle, sub);
            bridle.setChanged();
        } else if (server.getGameTime() % REFRESH == 0) {
            refresh(bridle, sub);
        }
        double length = length(strand);
        boolean driven = bridle.lastLength >= 0.0 && Math.abs(length - bridle.lastLength) > MOVED;
        if (bridle.rest < 0.0) {
            bridle.rest = length;
            bridle.setChanged();
        }
        BlockState state = bridle.getBlockState();
        Vec3 winch = winch(server, bridle, strand, sub);
        if (winch != null) {
            double top = winch.distanceTo(bridle.halyardAnchor(state, 0.0f));
            double missing = top * (1.0 + SAG) + SAG_MIN - bridle.rest;
            if (missing > SLACK) {
                bridle.rest += missing;
                length += missing;
                resize(server, strand, length);
                bridle.setChanged();
            }
        }
        float low = 1.0f;
        if (winch != null) {
            double nearest = Double.MAX_VALUE;
            for (int i = 0; i <= 16; i++) {
                double d = winch.distanceTo(bridle.halyardAnchor(state, i / 16.0f));
                if (d < nearest) {
                    nearest = d;
                    low = i / 16.0f;
                }
            }
            if (nearest > winch.distanceTo(bridle.halyardAnchor(state, 0.0f)) - 0.25) {
                winch = null;
                low = 1.0f;
            }
        }
        double travel = Math.max(0.5, (bridle.headY - bridle.footY) * FOLD);
        double offset = winch == null ? 0.0 : bridle.rest - winch.distanceTo(bridle.halyardAnchor(state, 0.0f));
        double shortest = winch == null ? bridle.rest - travel : winch.distanceTo(bridle.halyardAnchor(state, low)) + offset;
        shortest = Math.max(0.5, shortest);
        if (length > bridle.rest + SLACK) {
            resize(server, strand, bridle.rest);
            length = bridle.rest;
        } else if (length < shortest - SLACK) {
            resize(server, strand, shortest);
            length = shortest;
        }
        if (winch == null) {
            bridle.reef = (float) Mth.clamp((bridle.rest - length) / travel, 0.0, 1.0);
        } else {
            double reach = length - offset;
            float a = 0.0f, b = low;
            for (int i = 0; i < 20; i++) {
                float mid = (a + b) * 0.5f;
                if (winch.distanceTo(bridle.halyardAnchor(state, mid)) > reach)
                    a = mid;
                else
                    b = mid;
            }
            bridle.reef = Mth.clamp((a + b) * 0.5f, 0.0f, 1.0f);
        }
        boolean shared = sub != null && FurlState.onHalyard(sub, BlockPos.of(bridle.group));
        if (shared && !driven) {
            float follow = Math.min(FurlState.amount(sub, bridle.group), low);
            if (Math.abs(follow - bridle.reef) >= STEP * 0.5f) {
                double target = winch == null ? bridle.rest - follow * travel
                        : winch.distanceTo(bridle.halyardAnchor(state, follow)) + offset;
                target = Mth.clamp(target, shortest, bridle.rest);
                resize(server, strand, target);
                length = target;
                bridle.reef = follow;
            }
        }
        bridle.lastLength = length;
        if (!shared)
            bridle.applied = -1.0f;
        if (Math.abs(bridle.reef - bridle.applied) < STEP)
            return;
        bridle.applied = bridle.reef;
        bridle.setChanged();
        for (RopeAttachment attachment : strand.getAttachments()) {
            if (attachment.blockAttachment().equals(bridle.getBlockPos())) {
                strand.addAttachment(server, attachment.point(), attachment);
                break;
            }
        }
        if (sub == null)
            return;
        FurlState.setReef(sub, bridle.group, bridle.reef);
        long now = server.getGameTime();
        if (now - bridle.synced >= SYNC || bridle.reef <= 0.0f || bridle.reef >= low) {
            bridle.synced = now;
            ReefSyncPayload.broadcast(sub);
        }
    }

    private static Vec3 winch(ServerLevel level, HalyardBlockEntity bridle, ServerRopeStrand strand, UUID sub) {
        if (sub == null)
            return null;
        for (RopeAttachment attachment : strand.getAttachments()) {
            BlockPos at = attachment.blockAttachment();
            if (at.equals(bridle.getBlockPos()))
                continue;
            SubLevelAccess other = SableCompanion.INSTANCE.getContaining(level, at);
            if (other == null || !sub.equals(other.getUniqueId()))
                return null;
            RopeStrandHolderBehavior holder = RopeItem.getRopeHolder(level, at);
            return holder == null ? null : holder.getAttachmentPoint();
        }
        return null;
    }

    private static double length(ServerRopeStrand strand) {
        return strand.getExtension() + (strand.getPoints().size() - 2) * ServerRopeStrand.SEGMENT_LENGTH;
    }

    private static void resize(ServerLevel level, ServerRopeStrand strand, double target) {
        int points = strand.getPoints().size();
        double extension = target - (points - 2) * ServerRopeStrand.SEGMENT_LENGTH;
        while (extension <= 0.05 && points > 2) {
            strand.removeFirstPoint();
            points--;
            extension += ServerRopeStrand.SEGMENT_LENGTH;
        }
        if (extension > ServerRopeStrand.SEGMENT_LENGTH) {
            RopeAttachment start = strand.getAttachment(RopeAttachmentPoint.START);
            RopeStrandHolderBehavior holder = start == null ? null : RopeItem.getRopeHolder(level, start.blockAttachment());
            if (holder != null) {
                Vector3d at = JOMLConversion.toJOML(Sable.HELPER.projectOutOfSubLevel(level, holder.getAttachmentPoint()));
                while (extension > ServerRopeStrand.SEGMENT_LENGTH) {
                    strand.addPoint(at);
                    extension -= ServerRopeStrand.SEGMENT_LENGTH;
                }
            }
        }
        strand.updateFirstSegmentExtension(Math.max(0.05, extension));
    }
}
