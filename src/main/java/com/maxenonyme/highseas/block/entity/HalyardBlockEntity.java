package com.maxenonyme.highseas.block.entity;

import com.maxenonyme.highseas.block.BoatSailBlock;
import com.maxenonyme.highseas.sail.FurlState;
import com.maxenonyme.highseas.sail.Halyard;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public class HalyardBlockEntity extends SmartBlockEntity implements RopeStrandHolderBlockEntity {

    private static final double CLEAR = 0.25;

    private RopeStrandHolderBehavior ropeBehavior;
    private int side = 1;

    public boolean halyard;
    public double rest = -1.0;
    public double headY;
    public double footY;
    public double edge;
    public double plane;
    public long group;
    public float reef;
    public float applied = -1.0f;
    public long synced;
    public UUID ship;
    public double lastLength = -1.0;

    public HalyardBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        ropeBehavior = new RopeStrandHolderBehavior(this);
        behaviours.add(ropeBehavior);
    }

    @Override
    public RopeStrandHolderBehavior getBehavior() {
        return ropeBehavior;
    }

    public void setSide(int side) {
        this.side = side < 0 ? -1 : 1;
        setChanged();
    }

    public void makeHalyard(double headY, double footY, double edge, double plane, long group) {
        this.halyard = true;
        this.headY = headY;
        this.footY = footY;
        this.edge = edge;
        this.plane = plane;
        this.group = group;
        this.rest = -1.0;
        this.reef = 0.0f;
        this.applied = -1.0f;
        setChanged();
    }

    public float shownReef() {
        if (level == null || !level.isClientSide)
            return reef;
        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        return ship == null ? reef : FurlState.amount(ship.getUniqueId(), group);
    }

    public Vec3 halyardPoint(BlockState state, float reef) {
        return along(state, headY - reef * (headY - footY) * Halyard.FOLD);
    }

    public Vec3 halyardFoot(BlockState state) {
        return along(state, footY);
    }

    public Vec3 halyardAnchor(BlockState state, float reef) {
        double n = Math.floor(plane);
        double lean = plane - (n + 0.5);
        int free = lean > 0.1 ? -1 : lean < -0.1 ? 1 : side;
        double out = free > 0 ? n + 0.9 + CLEAR : n + 0.1 - CLEAR;
        return at(state, out, headY - reef * (headY - footY) * Halyard.FOLD);
    }

    private boolean alongX() {
        BlockState state = getBlockState();
        return state.hasProperty(BoatSailBlock.AXIS) && state.getValue(BoatSailBlock.AXIS) == Direction.Axis.X;
    }

    private int across() {
        return alongX() ? worldPosition.getZ() : worldPosition.getX();
    }

    private int normal() {
        return alongX() ? worldPosition.getX() : worldPosition.getZ();
    }

    private Vec3 along(BlockState state, double y) {
        return at(state, plane, y);
    }

    private Vec3 at(BlockState state, double n, double y) {
        if (state.hasProperty(BoatSailBlock.AXIS) && state.getValue(BoatSailBlock.AXIS) == Direction.Axis.X)
            return new Vec3(n, y, edge);
        return new Vec3(edge, y, n);
    }

    @Override
    public Vec3 getAttachmentPoint(BlockPos pos, BlockState state) {
        return halyard ? halyardAnchor(state, reef) : Vec3.atCenterOf(pos);
    }

    public ServerRopeStrand strand() {
        if (ropeBehavior == null)
            return null;
        ServerRopeStrand owned = ropeBehavior.getOwnedStrand();
        return owned != null ? owned : ropeBehavior.getAttachedStrand();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || ropeBehavior == null)
            return;
        if (halyard)
            Halyard.tick(this, ropeBehavior.isAttached());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putByte("Side", (byte) side);
        if (halyard) {
            tag.putBoolean("Halyard", true);
            tag.putDouble("Rest", rest);
            tag.putDouble("HeadOffset", headY - worldPosition.getY());
            tag.putDouble("FootOffset", footY - worldPosition.getY());
            tag.putDouble("EdgeOffset", edge - across());
            tag.putDouble("PlaneOffset", plane - normal());
            tag.putLong("Group", group);
            if (ship != null)
                tag.putUUID("Ship", ship);
            tag.putFloat("Reef", reef);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Side"))
            side = tag.getByte("Side") < 0 ? -1 : 1;
        halyard = tag.getBoolean("Halyard");
        if (halyard) {
            rest = tag.getDouble("Rest");
            if (tag.contains("HeadOffset")) {
                headY = tag.getDouble("HeadOffset") + worldPosition.getY();
                footY = tag.getDouble("FootOffset") + worldPosition.getY();
                edge = tag.getDouble("EdgeOffset") + across();
                plane = tag.getDouble("PlaneOffset") + normal();
            } else {
                headY = tag.getDouble("Head");
                footY = tag.getDouble("Foot");
                edge = tag.getDouble("Edge");
                plane = tag.getDouble("Plane");
            }
            group = tag.getLong("Group");
            ship = tag.hasUUID("Ship") ? tag.getUUID("Ship") : null;
            reef = tag.getFloat("Reef");
        }
    }
}
