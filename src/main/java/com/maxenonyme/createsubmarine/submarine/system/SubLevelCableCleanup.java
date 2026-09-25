package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.CreateSubmarine;

import com.maxenonyme.createsubmarine.submarine.network.CableStrandRemovePayload;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SubLevelCableCleanup implements SubLevelObserver {

    public static final SubLevelCableCleanup INSTANCE = new SubLevelCableCleanup();

    private SubLevelCableCleanup() {
    }

    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level))
            return;
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null)
            container.addObserver(INSTANCE);
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        if (reason != SubLevelRemovalReason.REMOVED)
            return;
        if (!(subLevel.getLevel() instanceof ServerLevel level))
            return;
        UUID id = subLevel.getUniqueId();
        ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(level);
        if (manager == null)
            return;

        List<ServerRopeStrand> doomed = new ArrayList<>();
        for (ServerRopeStrand strand : manager.getAllStrands()) {
            if (attachedTo(strand, id))
                doomed.add(strand);
        }
        for (ServerRopeStrand strand : doomed) {
            if (!destroyViaOwner(level, strand))
                rawRemove(level, manager, strand);
        }
    }

    private static boolean attachedTo(ServerRopeStrand strand, UUID id) {
        RopeAttachment start = strand.getAttachment(RopeAttachmentPoint.START);
        RopeAttachment end = strand.getAttachment(RopeAttachmentPoint.END);
        return (start != null && id.equals(start.subLevelID()))
                || (end != null && id.equals(end.subLevelID()));
    }

    private static boolean destroyViaOwner(ServerLevel level, ServerRopeStrand strand) {
        for (RopeAttachmentPoint point : new RopeAttachmentPoint[] { RopeAttachmentPoint.START, RopeAttachmentPoint.END }) {
            RopeAttachment att = strand.getAttachment(point);
            if (att == null || att.subLevelID() != null)
                continue;
            BlockEntity be = level.getBlockEntity(att.blockAttachment());
            if (!(be instanceof SmartBlockEntity sbe))
                continue;
            RopeStrandHolderBehavior behavior = sbe.getBehaviour(RopeStrandHolderBehavior.TYPE);
            if (behavior == null || behavior.getOwnedStrand() != strand)
                continue;
            try {
                behavior.destroyRope(null, att.blockAttachment().getCenter(), true);
                return true;
            } catch (RuntimeException e) {
                CreateSubmarine.LOGGER.warn("Could not break the cable held at {}", att.blockAttachment(), e);
                return false;
            }
        }
        return false;
    }

    private static void rawRemove(ServerLevel level, ServerLevelRopeManager manager, ServerRopeStrand strand) {
        strand.removeConstraints();
        manager.removeStrand(strand.getUUID());
        PacketDistributor.sendToPlayersInDimension(level, new CableStrandRemovePayload(strand.getUUID()));
    }
}
