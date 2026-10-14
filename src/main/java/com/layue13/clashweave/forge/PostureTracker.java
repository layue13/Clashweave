package com.layue13.clashweave.forge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import com.layue13.clashweave.core.Posture;

/**
 * Stance stability of every entity that has been struck or has struck into a guard. Server side, keyed by UUID like the
 * control budget, dropped when the entity goes. A broken monster neither moves nor strikes; the break is published as
 * a semantic event; what a break does to damage is applied by the caller.
 */
final class PostureTracker {

    private static final class Entry {

        EntityLivingBase entity;
        int dimension;
        final Posture posture;

        Entry(Posture posture) {
            this.posture = posture;
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    private final CombatConfig config;
    private final CombatPresentation presentation;
    private final HitEffects effects;

    PostureTracker(CombatConfig config, CombatPresentation presentation, HitEffects effects) {
        this.config = config;
        this.presentation = presentation;
        this.effects = effects;
    }

    private Entry entry(EntityLivingBase entity) {
        Entry entry = entries.get(entity.getUniqueID());
        if (entry == null || entry.entity != entity || entry.dimension != entity.dimension) {
            entry = new Entry(
                new Posture(
                    config.postureMax,
                    config.postureRecoveryDelay,
                    config.postureRecoveryPerTick,
                    config.postureResetPercent));
            entry.entity = entity;
            entry.dimension = entity.dimension;
            entries.put(entity.getUniqueID(), entry);
        }
        return entry;
    }

    /** Whether this kind of entity takes part: not the ones the lift effects also leave alone. */
    boolean applies(EntityLivingBase entity) {
        return !entity.isDead && entity.getHealth() > 0 && !effects.exempt(entity);
    }

    boolean broken(EntityLivingBase entity, long tick) {
        Entry entry = entries.get(entity.getUniqueID());
        return entry != null && entry.entity == entity && entry.posture.broken(tick);
    }

    /** Wears the target's stability down; announces the break the moment it happens. */
    void damage(EntityLivingBase target, float amount, long tick) {
        if (amount <= 0 || !applies(target)) return;
        int ticks = target instanceof EntityPlayer ? config.breakTicksPlayer : config.breakTicksMonster;
        if (entry(target).posture.damage(amount, tick, ticks)) {
            presentation.broken(target, tick);
            trace("BREAK target=" + target.getEntityId() + " tick=" + tick + " ticks=" + ticks);
        }
    }

    float value(EntityLivingBase target) {
        Entry entry = entries.get(target.getUniqueID());
        return entry == null ? config.postureMax : entry.posture.value();
    }

    void tick(long tick) {
        entries.entrySet()
            .removeIf(mapEntry -> {
                Entry entry = mapEntry.getValue();
                EntityLivingBase entity = entry.entity;
                if (entity.isDead || entity.getHealth() <= 0
                    || entity.dimension != entry.dimension
                    || entity.worldObj.getEntityByID(entity.getEntityId()) != entity) return true;
                entry.posture.advance(tick);
                if (entry.posture.broken(tick) && entity instanceof EntityLiving) {
                    // A broken monster stands where it is: no walking, no path.
                    entity.motionX = 0;
                    entity.motionZ = 0;
                    ((EntityLiving) entity).getNavigator()
                        .clearPathEntity();
                }
                return entry.posture.settled(tick);
            });
    }

    void remove(EntityLivingBase entity) {
        entries.remove(entity.getUniqueID());
    }

    void clear() {
        entries.clear();
    }

    private static void trace(String text) {
        if (Boolean.getBoolean("clashweave.trace")) System.out.println("CW_POSTURE " + text);
    }
}
