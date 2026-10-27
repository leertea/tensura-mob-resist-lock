package com.example.mobresistlock;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-wide setting, stored in the world save. true = mobs may learn resistances (default). */
public class LockState extends SavedData {
    private static final String NAME = "mobresistlock_state";
    private static final String KEY = "mobsCanLearn";

    private boolean mobsCanLearn = true;

    public static LockState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(LockState::new, LockState::load, null), NAME);
    }

    private static LockState load(CompoundTag tag, HolderLookup.Provider registries) {
        LockState state = new LockState();
        if (tag.contains(KEY)) {
            state.mobsCanLearn = tag.getBoolean(KEY);
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(KEY, mobsCanLearn);
        return tag;
    }

    public boolean mobsCanLearn() {
        return mobsCanLearn;
    }

    public void setMobsCanLearn(boolean value) {
        this.mobsCanLearn = value;
        setDirty();
    }
}
