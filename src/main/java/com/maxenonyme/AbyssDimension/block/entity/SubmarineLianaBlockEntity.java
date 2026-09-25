package com.maxenonyme.AbyssDimension.block.entity;

import com.maxenonyme.AbyssDimension.LianaRegistry;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import java.util.EnumSet;
import java.util.UUID;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.world.entity.player.Player;

public class SubmarineLianaBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    private UUID parentId;
    private UUID seedId;
    private boolean isController;
    private int worldLightLevel = 10;
    private int segmentLen = 3;
    private Vector3d groundAnchor = null;
    private double seedLocalY = -1.0;
    private Vector3d seedLocalAnchor = null;
    private Vector3d seedOffset = null;
    private int parentPlotX = -1;
    private int parentPlotZ = -1;
    private int childPlotX = -1;
    private int childPlotZ = -1;
    private int seedPlotX = -1;
    private int seedPlotZ = -1;

    private transient PhysicsConstraintHandle groundJoint = null;
    private transient PhysicsConstraintHandle parentJoint = null;
    private transient PhysicsConstraintHandle seedJoint = null;

    private transient Vector3d restPos = null;
    private transient Quaterniond restRot = null;

    private final ForceTotal forceTotal = new ForceTotal();
    private final ForceTotal parentForceTotal = new ForceTotal();
    private final Vector3d totalForce = new Vector3d();
    private final Vector3d totalTorque = new Vector3d();
    private final Vector3d linearVelocity = new Vector3d();
    private final Vector3d angularVelocity = new Vector3d();
    private final Vector3d tempVec = new Vector3d();
    private final Vector3d tempVec2 = new Vector3d();
    private final Vector3d upA = new Vector3d();
    private final Vector3d upB = new Vector3d();

    public SubmarineLianaBlockEntity(BlockPos pos, BlockState state) {
        super(LianaRegistry.LIANA_BE.get(), pos, state);
    }

    public void setParent(UUID parentId) {
        this.parentId = parentId;
        this.setChanged();
    }

    public void setController(boolean controller) {
        this.isController = controller;
        this.setChanged();
    }

    public void setSegmentLen(int segmentLen) {
        this.segmentLen = segmentLen;
        this.setChanged();
    }

    public void setGroundAnchor(Vector3d groundAnchor) {
        this.groundAnchor = groundAnchor;
        this.setChanged();
    }

    public void setSeed(UUID seedId, double localY) {
        this.seedId = seedId;
        this.seedLocalY = localY;
        this.setChanged();
    }

    public void setSeed(UUID seedId, Vector3d seedLocalAnchor) {
        this.seedId = seedId;
        this.seedLocalAnchor = seedLocalAnchor;
        this.setChanged();
    }

    public void setGroundJoint(PhysicsConstraintHandle groundJoint) {
        this.groundJoint = groundJoint;
    }

    public void setParentJoint(PhysicsConstraintHandle parentJoint) {
        this.parentJoint = parentJoint;
    }

    public void setSeedJoint(PhysicsConstraintHandle seedJoint) {
        this.seedJoint = seedJoint;
    }

    public void setParentPlot(int x, int z) {
        this.parentPlotX = x;
        this.parentPlotZ = z;
        this.setChanged();
    }

    public void setChildPlot(int x, int z) {
        this.childPlotX = x;
        this.childPlotZ = z;
        this.setChanged();
    }

    public void setSeedPlot(int x, int z) {
        this.seedPlotX = x;
        this.seedPlotZ = z;
        this.setChanged();
    }

    public boolean isController() {
        return isController;
    }

    public boolean isGroundJointValid() {
        return groundJoint != null && groundJoint.isValid();
    }

    public boolean isParentJointValid() {
        return parentJoint != null && parentJoint.isValid();
    }

    public boolean hasParentPlot() {
        return parentPlotX != -1 || parentId != null;
    }

    public int getSeedPlotX() {
        return seedPlotX;
    }

    public int getSeedPlotZ() {
        return seedPlotZ;
    }

    public boolean isSeedJointValid() {
        return seedJoint != null && seedJoint.isValid();
    }

    private ServerSubLevel findSubLevelByPlot(ServerSubLevelContainer container, int plotX, int plotZ) {
        if (plotX == -1)
            return null;
        for (ServerSubLevel sub : container.getAllSubLevels()) {
            if (sub.getPlot() != null && sub.getPlot().plotPos.x == plotX && sub.getPlot().plotPos.z == plotZ) {
                return sub;
            }
        }
        return null;
    }

    public int getWorldLightLevel() {
        return worldLightLevel;
    }

    public void setWorldLightLevel(int level) {
        if (this.worldLightLevel != level) {
            this.worldLightLevel = level;
            this.setChanged();
            if (this.level != null && !this.level.isClientSide) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("WorldLight", worldLightLevel);
        return tag;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (subLevel.getPlot() == null) {
            return;
        }
        if (!worldPosition.equals(subLevel.getPlot().getCenterBlock())) {
            return;
        }

        if (this.parentPlotX == -1 && !this.isController && this.groundAnchor == null) {
            com.maxenonyme.AbyssDimension.system.PlantPhysicsRegistry registry = com.maxenonyme.AbyssDimension.system.PlantPhysicsRegistry
                    .get(subLevel.getLevel());
            com.maxenonyme.AbyssDimension.system.PlantPhysicsRegistry.SegmentData data = registry
                    .getSegment(subLevel.getPlot().plotPos.x, subLevel.getPlot().plotPos.z);
            if (data != null) {
                this.isController = data.root();
                this.segmentLen = data.segmentLen();
                if (data.hasGroundAnchor()) {
                    this.groundAnchor = new Vector3d(data.groundX(), data.groundY(), data.groundZ());
                }
                this.parentPlotX = data.parentPlotX();
                this.parentPlotZ = data.parentPlotZ();
                this.childPlotX = data.childPlotX();
                this.childPlotZ = data.childPlotZ();
                this.seedPlotX = data.attachmentPlotX();
                this.seedPlotZ = data.attachmentPlotZ();
                if (data.hasAttachment()) {
                    this.seedLocalAnchor = new Vector3d(data.attachmentLocalX(), data.attachmentLocalY(),
                            data.attachmentLocalZ());
                    this.seedOffset = new Vector3d(data.attachmentOffsetX(), data.attachmentOffsetY(),
                            data.attachmentOffsetZ());
                }
            }
        }
        handle.getLinearVelocity(linearVelocity);
        handle.getAngularVelocity(angularVelocity);

        totalForce.set(0, 0, 0);
        totalTorque.set(0, 0, 0);

        if (isController && groundAnchor != null && (groundJoint == null || !groundJoint.isValid())) {
            ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer
                    .getContainer(subLevel.getLevel());
            if (container != null) {
                Vector3d localAnchor0 = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY(),
                        worldPosition.getZ() + 0.5);
                groundJoint = container.physicsSystem().getPipeline().addConstraint(
                        subLevel,
                        null,
                        new GenericConstraintConfiguration(
                                localAnchor0,
                                new Vector3d(groundAnchor),
                                new Quaterniond(),
                                new Quaterniond(),
                                EnumSet.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y,
                                        ConstraintJointAxis.LINEAR_Z, ConstraintJointAxis.ANGULAR_Y)));
                if (groundJoint != null) {
                    groundJoint.setContactsEnabled(false);
                    container.physicsSystem().getPipeline().wakeUp(subLevel);
                }
            }
        }

        if ((parentId != null || parentPlotX != -1) && (parentJoint == null || !parentJoint.isValid())) {
            ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer
                    .getContainer(subLevel.getLevel());
            if (container != null) {
                ServerSubLevel parentSub = null;
                if (parentPlotX != -1) {
                    parentSub = findSubLevelByPlot(container, parentPlotX, parentPlotZ);
                }
                if (parentSub == null && parentId != null) {
                    parentSub = (ServerSubLevel) container.getSubLevel(parentId);
                }
                if (parentSub != null && parentSub.getPlot() != null) {
                    BlockPos parentPlotAnchor = parentSub.getPlot().getCenterBlock();
                    BlockPos currentPlotAnchor = subLevel.getPlot().getCenterBlock();
                    BoundingBox3ic parentBounds = parentSub.getPlot()
                            .getBoundingBox();
                    int parentLenVal = parentBounds.maxY() - parentBounds.minY() + 1;
                    Vector3d localAnchorCurrent = new Vector3d(parentPlotAnchor.getX() + 0.5,
                            parentPlotAnchor.getY() + parentLenVal - 0.05, parentPlotAnchor.getZ() + 0.5);
                    Vector3d localAnchorNext = new Vector3d(currentPlotAnchor.getX() + 0.5,
                            currentPlotAnchor.getY() + 0.05, currentPlotAnchor.getZ() + 0.5);

                    parentJoint = container.physicsSystem().getPipeline().addConstraint(
                            parentSub,
                            subLevel,
                            new GenericConstraintConfiguration(
                                    localAnchorCurrent,
                                    localAnchorNext,
                                    new Quaterniond(),
                                    new Quaterniond(),
                                    EnumSet.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y,
                                            ConstraintJointAxis.LINEAR_Z, ConstraintJointAxis.ANGULAR_Y)));
                    if (parentJoint != null) {
                        parentJoint.setContactsEnabled(false);
                        container.physicsSystem().getPipeline().wakeUp(subLevel);
                        container.physicsSystem().getPipeline().wakeUp(parentSub);
                    }
                }
            }
        }

        if ((seedId != null || seedPlotX != -1) && (seedJoint == null || !seedJoint.isValid())) {
            Vector3d localAnchorCurrent = null;
            if (seedLocalAnchor != null) {
                BlockPos segmentPlotAnchor = subLevel.getPlot().getCenterBlock();
                localAnchorCurrent = new Vector3d(
                        segmentPlotAnchor.getX() + seedLocalAnchor.x,
                        segmentPlotAnchor.getY() + seedLocalAnchor.y,
                        segmentPlotAnchor.getZ() + seedLocalAnchor.z);
            } else if (seedLocalY >= 0) {
                BlockPos segmentPlotAnchor = subLevel.getPlot().getCenterBlock();
                localAnchorCurrent = new Vector3d(segmentPlotAnchor.getX() + 0.9,
                        segmentPlotAnchor.getY() + seedLocalY + 0.5, segmentPlotAnchor.getZ() + 0.5);
            }

            if (localAnchorCurrent != null) {
                ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer
                        .getContainer(subLevel.getLevel());
                if (container != null) {
                    ServerSubLevel seedSub = null;
                    if (seedPlotX != -1) {
                        seedSub = findSubLevelByPlot(container, seedPlotX, seedPlotZ);
                    }
                    if (seedSub == null && seedId != null) {
                        seedSub = (ServerSubLevel) container.getSubLevel(seedId);
                    }
                    if (seedSub != null && seedSub.getPlot() != null) {




                        if (seedOffset != null) {
                            Vector3d seedTarget = new Vector3d(subLevel.logicalPose().position()).add(seedOffset);
                            RigidBodyHandle seedHandle = RigidBodyHandle.of(seedSub);
                            if (seedHandle != null) {
                                Vector3d sv = new Vector3d();
                                Vector3d sa = new Vector3d();
                                seedHandle.getLinearVelocity(sv);
                                seedHandle.getAngularVelocity(sa);
                                seedHandle.addLinearAndAngularVelocity(sv.negate(), sa.negate());
                            }
                            seedSub.logicalPose().position().set(seedTarget);
                            seedSub.updateLastPose();
                            container.physicsSystem().getPipeline().teleport(seedSub, seedTarget, new Quaterniond());
                        }

                        BlockPos seedPlotAnchor = seedSub.getPlot().getCenterBlock();
                        Vector3d localAnchorNext = new Vector3d(seedPlotAnchor.getX() + 0.5,
                                seedPlotAnchor.getY() + 0.9, seedPlotAnchor.getZ() + 0.5);

                        seedJoint = container.physicsSystem().getPipeline().addConstraint(
                                subLevel,
                                seedSub,
                                new GenericConstraintConfiguration(
                                        localAnchorCurrent,
                                        localAnchorNext,
                                        new Quaterniond(),
                                        new Quaterniond(),
                                        EnumSet.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y,
                                                ConstraintJointAxis.LINEAR_Z)));
                        if (seedJoint != null) {
                            seedJoint.setContactsEnabled(false);
                            container.physicsSystem().getPipeline().wakeUp(subLevel);
                            container.physicsSystem().getPipeline().wakeUp(seedSub);
                        }
                    }
                }
            }
        }

        if (restPos == null) {
            restPos = new Vector3d(subLevel.logicalPose().position());
            restRot = new Quaterniond(subLevel.logicalPose().orientation());
        }

        boolean anchored;
        if (isController) {
            anchored = isGroundJointValid();
        } else if (hasParentPlot()) {
            anchored = isParentJointValid();
        } else {
            anchored = true;
        }

        if (!anchored) {
            handle.addLinearAndAngularVelocity(tempVec.set(linearVelocity).negate(),
                    tempVec2.set(angularVelocity).negate());
            subLevel.logicalPose().position().set(restPos);
            subLevel.logicalPose().orientation().set(restRot);
            subLevel.updateLastPose();
            ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer
                    .getContainer(subLevel.getLevel());
            if (container != null) {
                container.physicsSystem().getPipeline().teleport(subLevel, restPos, restRot);
            }
            return;
        }

        double mass = subLevel.getMassTracker().getMass();
        if (mass < 1.0) {
            mass = segmentLen * 10.0;
        }
        double lianaBuoyancy = mass * (9.81 + 2.0);
        totalForce.add(0, lianaBuoyancy, 0);

        double waterDragCoeff = 2.0;
        tempVec.set(linearVelocity).mul(-waterDragCoeff);
        totalForce.add(tempVec);

        double swayFreq = 1.0;
        double swayAmp = 1.5;
        double time = subLevel.getLevel().getGameTime() * timeStep;
        double waveSway = Math.sin(time * swayFreq + worldPosition.getX() * 0.15 + worldPosition.getZ() * 0.15)
                * swayAmp;
        totalForce.add(waveSway, 0, 0);

        Vector3dc subPos = subLevel.logicalPose().position();
        for (Player player : subLevel.getLevel().players()) {
            double dx = subPos.x() - player.getX();
            double dy = subPos.y() - (player.getY() + player.getEyeHeight() * 0.5);
            double dz = subPos.z() - player.getZ();
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr < 3.0) {
                double dist = Math.sqrt(distSqr);
                if (dist > 0.01) {
                    double forceScale = (2.4 - dist) * 50.0;
                    double playerSpeed = player.getDeltaMovement().length();
                    double speedBonus = Math.min(playerSpeed * 100.0, 120.0);
                    tempVec.set(dx, dy, dz).normalize().mul((forceScale + speedBonus) * mass);
                    totalForce.add(tempVec);
                }
            }
        }

        if (parentId != null) {
            ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer
                    .getContainer(subLevel.getLevel());
            if (container != null) {
                ServerSubLevel parentSubLevel = (ServerSubLevel) container.getSubLevel(parentId);
                if (parentSubLevel != null) {
                    Quaterniond orientA = subLevel.logicalPose().orientation();
                    Quaterniond orientB = parentSubLevel.logicalPose().orientation();

                    upA.set(0, 1, 0);
                    orientA.transform(upA);

                    upB.set(0, 1, 0);
                    orientB.transform(upB);

                    double stiffness = 45.0;
                    upA.cross(upB, totalTorque).mul(stiffness);

                    RigidBodyHandle parentHandle = RigidBodyHandle.of(parentSubLevel);
                    if (parentHandle != null) {
                        parentForceTotal.reset();
                        tempVec2.set(totalTorque).negate();
                        parentForceTotal.applyLinearAndAngularImpulse(JOMLConversion.ZERO,
                                parentSubLevel.logicalPose().transformNormalInverse(tempVec2, tempVec2).mul(timeStep));
                        parentHandle.applyForcesAndReset(parentForceTotal);
                    }
                }
            }
        }

        Quaterniond orient = subLevel.logicalPose().orientation();

        upA.set(0, 1, 0);
        orient.transform(upA);

        Vector3d worldVertical = tempVec2.set(0, 1, 0);
        double cosTheta = upA.y;
        double theta = Math.acos(Math.max(-1.0, Math.min(1.0, cosTheta)));

        double verticalRestorationStiffness = 30.0;
        if (theta > 0.5236) {
            verticalRestorationStiffness += (theta - 0.5236) * 500.0;
        }

        upA.cross(worldVertical, tempVec).mul(verticalRestorationStiffness);
        totalTorque.add(tempVec);

        forceTotal.reset();
        forceTotal.applyImpulseAtPoint(
                subLevel,
                JOMLConversion.atCenterOf(worldPosition),
                subLevel.logicalPose().transformNormalInverse(totalForce, tempVec).mul(timeStep));
        forceTotal.applyLinearAndAngularImpulse(JOMLConversion.ZERO,
                subLevel.logicalPose().transformNormalInverse(totalTorque, tempVec).mul(timeStep));
        handle.applyForcesAndReset(forceTotal);
    }








    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("WorldLight", worldLightLevel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("WorldLight"))
            worldLightLevel = tag.getInt("WorldLight");
    }

    public Vector3d getGroundAnchor() {
        return groundAnchor;
    }

    public int getParentPlotX() {
        return parentPlotX;
    }

    public int getParentPlotZ() {
        return parentPlotZ;
    }

    public int getChildPlotX() {
        return childPlotX;
    }

    public int getChildPlotZ() {
        return childPlotZ;
    }

    public int getSegmentLen() {
        return segmentLen;
    }
}
