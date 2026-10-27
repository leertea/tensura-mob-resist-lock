package com.example.mobresistlock;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * When mob learning is disabled, every non-player mob keeps only the resistances it had
 * the first time it took damage (its "baseline", saved in the mob's own NBT). Any
 * resistance that shows up later is removed again.
 */
public class ResistanceGuard {
    private static final String BASELINE_KEY = MobResistLock.MODID + ":baseline";
    /** How long (ticks) a mob is watched after taking damage. */
    private static final long WATCH_TICKS = 200;
    private static final int CHECK_INTERVAL = 5;

    private final Map<Mob, Long> watched = new WeakHashMap<>();

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) {
            return;
        }
        ensureBaseline(mob);
        MinecraftServer server = mob.getServer();
        if (server != null) {
            watched.put(mob, (long) server.getTickCount());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % CHECK_INTERVAL != 0 || watched.isEmpty()) {
            return;
        }
        if (LockState.get(server).mobsCanLearn()) {
            watched.clear();
            return;
        }
        Iterator<Map.Entry<Mob, Long>> it = watched.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Mob, Long> entry = it.next();
            Mob mob = entry.getKey();
            if (mob == null || mob.isRemoved() || !mob.isAlive()) {
                it.remove();
                continue;
            }
            purge(mob);
            if (now - entry.getValue() > WATCH_TICKS) {
                it.remove();
            }
        }
    }

    private void ensureBaseline(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (data.contains(BASELINE_KEY, Tag.TAG_LIST)) {
            return;
        }
        Set<String> current = TensuraBridge.currentResistances(mob);
        if (current == null) {
            return; // Tensura/ManasCore bridge not available -> nothing to protect
        }
        ListTag list = new ListTag();
        for (String id : current) {
            list.add(StringTag.valueOf(id));
        }
        data.put(BASELINE_KEY, list);
    }

    private void purge(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(BASELINE_KEY, Tag.TAG_LIST)) {
            return;
        }
        Set<String> baseline = new HashSet<>();
        for (Tag t : data.getList(BASELINE_KEY, Tag.TAG_STRING)) {
            baseline.add(t.getAsString());
        }
        int removed = TensuraBridge.removeResistancesNotIn(mob, baseline);
        if (removed > 0) {
            MobResistLock.LOGGER.debug("Removed {} newly learned resistance(s) from {}", removed, mob);
        }
    }
}
