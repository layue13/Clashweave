package com.layue13.clashweave.forge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.layue13.clashweave.core.Scheduler;
import com.layue13.clashweave.core.SemanticEvents;

/** Authority-result to presentation adapter; does not mutate health, guards, actions or movement. */
final class CombatPresentation {

    private final SemanticEvents events = new SemanticEvents();
    private final Map<UUID, Float> health = new HashMap<>();

    private static final class NativeGuard {

        LivingHurtEvent event;
        int guard;
        long tick;
    }

    private final List<NativeGuard> nativeGuards = new ArrayList<>();

    CombatPresentation() {
        events.subscribe(new SemanticDispatcher()::publish);
    }

    void started(EntityPlayer player, Scheduler.Instance action, long tick, boolean drawn) {
        events.actionStarted(
            player.getEntityId(),
            action.id,
            tick,
            action.definition.id,
            drawn,
            action.definition.active > 0);
    }

    void sheathed(EntityPlayer player, long instance, long tick) {
        events.emit(
            new SemanticEvents.Event(
                SemanticEvents.Kind.SHEATHE,
                player.getEntityId(),
                -1,
                instance,
                tick,
                0,
                "sheathe"));
    }

    void rememberHealth(EntityLivingBase player) {
        if (player instanceof EntityPlayer) health.put(player.getUniqueID(), player.getHealth());
    }

    void health(EntityPlayer player, long tick) {
        Float old = health.put(player.getUniqueID(), player.getHealth());
        if (old != null && player.getHealth() < old)
            events.emit(new SemanticEvents.Event(SemanticEvents.Kind.HURT, player.getEntityId(), -1, 0, tick, 0, ""));
    }

    void contact(EntityPlayer attacker, EntityLivingBase target, Scheduler.Instance action, long tick, long frozen,
        boolean hit, int guard) {
        events.contact(
            attacker.getEntityId(),
            target.getEntityId(),
            action.id,
            tick,
            frozen,
            action.definition.id,
            hit,
            guard);
        rememberHealth(target);
    }

    void nativeGuard(LivingHurtEvent event, int guard, long tick) {
        if (guard > 0) {
            NativeGuard result = new NativeGuard();
            result.event = event;
            result.guard = guard;
            result.tick = tick;
            nativeGuards.add(result);
        }
    }

    void flush() {
        for (NativeGuard result : nativeGuards) if (!result.event.isCanceled()) events.contact(
            result.event.source.getEntity()
                .getEntityId(),
            result.event.entityLiving.getEntityId(),
            0,
            result.tick,
            0,
            "",
            false,
            result.guard);
        nativeGuards.clear();
    }

    void remove(EntityPlayer player) {
        health.remove(player.getUniqueID());
    }

    void stop() {
        health.clear();
        nativeGuards.clear();
    }
}
