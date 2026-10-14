package com.layue13.clashweave.client;

import net.minecraft.util.IntHashMap;

import com.layue13.clashweave.client.body.BodyRig;

/**
 * Rigs for entities that have no combat Visual. IntHashMap keeps the per-frame lookup free of boxing. Entities that
 * leave
 * are never told to us, so the map is dropped now and then: a rig is only smoothing state, and a rig that restarts
 * seeds
 * itself from the pose it is first asked for, so nothing visible is lost.
 */
final class BodyRigs {

    private static final long LIFETIME_NANOS = 120_000_000_000L;
    private static IntHashMap rigs = new IntHashMap();
    private static long created = System.nanoTime();

    private BodyRigs() {}

    static BodyRig of(int entityId, long now) {
        if (now - created > LIFETIME_NANOS) {
            rigs = new IntHashMap();
            created = now;
        }
        BodyRig rig = (BodyRig) rigs.lookup(entityId);
        if (rig == null) {
            rig = new BodyRig();
            rigs.addKey(entityId, rig);
        }
        return rig;
    }
}
