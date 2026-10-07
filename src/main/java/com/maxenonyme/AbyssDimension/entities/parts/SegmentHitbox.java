package com.maxenonyme.AbyssDimension.entities.parts;

import com.maxenonyme.AbyssDimension.entities.EntityRegistry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SegmentHitbox extends Entity {
    private HasSegments segmentParent;
    private int index;
    private SegmentDefinition definition;
    private boolean parentResolved;
    private int pendingParentId = -1;
    private int pendingIndex = -1;
    private Vec3 direction;

    public SegmentHitbox(HasSegments parent, int index, SegmentDefinition definition, Level level) {
        super(EntityRegistry.SEGMENT_HITBOX.get(), level);
        this.segmentParent = parent;
        this.index = index;
        this.definition = definition;
        this.parentResolved = true;
        Entity parentEntity = (Entity) parent;
        this.setPos(parentEntity.getX(), parentEntity.getY(), parentEntity.getZ());
    }

    public SegmentHitbox(Level level) {
        super(EntityRegistry.SEGMENT_HITBOX.get(), level);
        this.segmentParent = null;
        this.index = -1;
        this.definition = null;
        this.parentResolved = false;
    }

    @Override
    public void tick() {
        if (!parentResolved) {
            resolveParent();
            if (!parentResolved) return;
        }
        Entity parent = (Entity) segmentParent;
        if (parent == null || !parent.isAlive()) {
            this.discard();
            return;
        }
        this.setDeltaMovement(Vec3.ZERO);
    }

    public void setChainPosition(Vec3 pos, Vec3 dir, AABB box) {
        this.setPos(pos.x, pos.y, pos.z);
        this.direction = dir;
        this.setBoundingBox(box);
    }

    private void resolveParent() {
        if (pendingParentId < 0 || pendingIndex < 0) return;
        Entity found = this.level().getEntity(pendingParentId);
        if (found instanceof HasSegments hs) {
            List<SegmentDefinition> defs = hs.segmentDefinitions();
            if (pendingIndex >= 0 && pendingIndex < defs.size()) {
                this.segmentParent = hs;
                this.index = pendingIndex;
                this.definition = defs.get(pendingIndex);
                this.parentResolved = true;
                List<SegmentHitbox> parentSegs = hs.segments();
                while (parentSegs.size() <= pendingIndex) {
                    parentSegs.add(null);
                }
                parentSegs.set(pendingIndex, this);
            }
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public void push(Entity entity) {
        double dx = this.getX() - entity.getX();
        double dz = this.getZ() - entity.getZ();
        double d = Math.max(Math.abs(dx), Math.abs(dz));
        if (d >= 0.01) {
            d = Math.sqrt(d);
            double s = 0.6 / d;
            entity.setDeltaMovement(entity.getDeltaMovement().add(dx * s, 0, dz * s));
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (segmentParent instanceof Entity parent) {
            return parent.hurt(source, amount);
        }
        return false;
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || segmentParent == entity;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        Entity parent = (Entity) segmentParent;
        int parentId = parent == null ? 0 : parent.getId();
        int data = (parentId << 16) | (index & 0xFFFF);
        return new ClientboundAddEntityPacket(this, serverEntity, data);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        int data = packet.getData();
        this.pendingParentId = data >> 16;
        this.pendingIndex = data & 0xFFFF;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (definition == null) return EntityDimensions.fixed(0.01F, 0.01F);
        return EntityDimensions.fixed((float) definition.width(), (float) definition.height());
    }

    public int getSegmentIndex() {
        return index;
    }

    public SegmentDefinition getDefinition() {
        return definition;
    }
}
