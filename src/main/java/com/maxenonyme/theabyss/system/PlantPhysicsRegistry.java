package com.maxenonyme.theabyss.system;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;


public class PlantPhysicsRegistry extends SavedData {
    private static final String NAME = "plant_physics_registry";

    public record PlotKey(int x, int z) {}


    public record SegmentData(
            int plotX, int plotZ,
            boolean root,
            boolean hasGroundAnchor,
            double groundX, double groundY, double groundZ,
            int parentPlotX, int parentPlotZ,
            int childPlotX, int childPlotZ,
            int attachmentPlotX, int attachmentPlotZ,
            double attachmentLocalX, double attachmentLocalY, double attachmentLocalZ,
            double attachmentOffsetX, double attachmentOffsetY, double attachmentOffsetZ,
            int segmentLen
    ) {
        public boolean hasAttachment() {
            return attachmentPlotX != -1;
        }
    }

    private final Map<PlotKey, SegmentData> segments = new HashMap<>();

    public PlantPhysicsRegistry() {}

    public static PlantPhysicsRegistry get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(new SavedData.Factory<>(
                PlantPhysicsRegistry::new,
                PlantPhysicsRegistry::load,
                null
        ), NAME);
    }

    public static PlantPhysicsRegistry load(CompoundTag tag, HolderLookup.Provider provider) {
        PlantPhysicsRegistry registry = new PlantPhysicsRegistry();
        ListTag list = tag.getList("Segments", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag s = list.getCompound(i);
            SegmentData data = new SegmentData(
                    s.getInt("plotX"), s.getInt("plotZ"),
                    s.getBoolean("root"),
                    s.getBoolean("hasGround"),
                    s.getDouble("groundX"), s.getDouble("groundY"), s.getDouble("groundZ"),
                    s.getInt("parentX"), s.getInt("parentZ"),
                    s.getInt("childX"), s.getInt("childZ"),
                    s.getInt("attachX"), s.getInt("attachZ"),
                    s.getDouble("attachLocalX"), s.getDouble("attachLocalY"), s.getDouble("attachLocalZ"),
                    s.getDouble("attachOffX"), s.getDouble("attachOffY"), s.getDouble("attachOffZ"),
                    s.getInt("segmentLen")
            );
            registry.segments.put(new PlotKey(data.plotX(), data.plotZ()), data);
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (SegmentData d : segments.values()) {
            CompoundTag s = new CompoundTag();
            s.putInt("plotX", d.plotX());
            s.putInt("plotZ", d.plotZ());
            s.putBoolean("root", d.root());
            s.putBoolean("hasGround", d.hasGroundAnchor());
            s.putDouble("groundX", d.groundX());
            s.putDouble("groundY", d.groundY());
            s.putDouble("groundZ", d.groundZ());
            s.putInt("parentX", d.parentPlotX());
            s.putInt("parentZ", d.parentPlotZ());
            s.putInt("childX", d.childPlotX());
            s.putInt("childZ", d.childPlotZ());
            s.putInt("attachX", d.attachmentPlotX());
            s.putInt("attachZ", d.attachmentPlotZ());
            s.putDouble("attachLocalX", d.attachmentLocalX());
            s.putDouble("attachLocalY", d.attachmentLocalY());
            s.putDouble("attachLocalZ", d.attachmentLocalZ());
            s.putDouble("attachOffX", d.attachmentOffsetX());
            s.putDouble("attachOffY", d.attachmentOffsetY());
            s.putDouble("attachOffZ", d.attachmentOffsetZ());
            s.putInt("segmentLen", d.segmentLen());
            list.add(s);
        }
        tag.put("Segments", list);
        return tag;
    }

    public void putSegment(SegmentData data) {
        segments.put(new PlotKey(data.plotX(), data.plotZ()), data);
        setDirty();
    }

    public SegmentData getSegment(int plotX, int plotZ) {
        return segments.get(new PlotKey(plotX, plotZ));
    }

    public void removeSegment(int plotX, int plotZ) {
        if (segments.remove(new PlotKey(plotX, plotZ)) != null) {
            setDirty();
        }
    }
}
