package com.layue13.clashweave.forge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;

/** Persistent target state, never a call-frame damage gate. */
public final class ResistanceOwnership {

    private static final class Entry {

        EntityLivingBase entity;
        int original;
        int written;
        int dimension;
        boolean external;
        Set<UUID> owners;
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    private final int maximum;

    public ResistanceOwnership(int maximum) {
        this.maximum = maximum;
    }

    public void reconcile(Map<EntityLivingBase, Set<UUID>> desired) {
        Set<UUID> wanted = new HashSet<>();
        for (Map.Entry<EntityLivingBase, Set<UUID>> request : desired.entrySet()) {
            EntityLivingBase entity = request.getKey();
            if (entity.isDead || entity.getHealth() <= 0
                || request.getValue()
                    .isEmpty())
                continue;
            wanted.add(entity.getUniqueID());
            Entry entry = entries.get(entity.getUniqueID());
            if (entry == null) {
                entry = new Entry();
                entry.entity = entity;
                entry.original = entity.maxHurtResistantTime;
                entry.written = maximum;
                entry.dimension = entity.dimension;
                entity.maxHurtResistantTime = maximum;
                entries.put(entity.getUniqueID(), entry);
            }
            if (entity.maxHurtResistantTime != entry.written) entry.external = true;
            entry.owners = new HashSet<>(request.getValue());
        }
        Iterator<Map.Entry<UUID, Entry>> iterator = entries.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Entry> mapped = iterator.next();
            Entry entry = mapped.getValue();
            if (!wanted.contains(mapped.getKey()) || entry.entity.isDead
                || entry.entity.getHealth() <= 0
                || entry.entity.dimension != entry.dimension) {
                restore(entry);
                iterator.remove();
            }
        }
    }

    public void remove(EntityLivingBase entity) {
        Entry entry = entries.remove(entity.getUniqueID());
        if (entry != null) restore(entry);
        Iterator<Entry> iterator = entries.values()
            .iterator();
        while (iterator.hasNext()) {
            Entry owned = iterator.next();
            owned.owners.remove(entity.getUniqueID());
            if (owned.owners.isEmpty()) {
                restore(owned);
                iterator.remove();
            }
        }
    }

    public void clear() {
        for (Entry entry : entries.values()) restore(entry);
        entries.clear();
    }

    private void restore(Entry entry) {
        if (!entry.external && entry.entity.maxHurtResistantTime == entry.written)
            entry.entity.maxHurtResistantTime = entry.original;
    }

    public int owners(EntityLivingBase entity) {
        Entry entry = entries.get(entity.getUniqueID());
        return entry == null ? 0 : entry.owners.size();
    }
}
