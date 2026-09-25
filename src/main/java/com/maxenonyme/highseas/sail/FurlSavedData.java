package com.maxenonyme.highseas.sail;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class FurlSavedData extends SavedData {
    private static final String NAME = "create_high_seas_furl";

    private static FurlSavedData instance;

    public static FurlSavedData get(ServerLevel level) {
        instance = level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FurlSavedData::new, FurlSavedData::load), NAME);
        return instance;
    }

    public static void markDirty() {
        if (instance != null) {
            instance.setDirty();
        }
    }

    public static FurlSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        FurlState.loadFromTag(tag);
        return new FurlSavedData();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        FurlState.saveToTag(tag);
        return tag;
    }
}
