package com.layue13.clashweave.forge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

import com.layue13.clashweave.core.ControlBudget;
import com.layue13.clashweave.core.HitEffect;
import com.layue13.clashweave.core.Scheduler;

/** Post-commit effects, independent target ownership and no DamageSource/timer mutations. */
final class HitEffects {

    private static final class Target {

        EntityLivingBase entity;
        int dimension;
        ControlBudget budget;
        /** Accepted launches in the current chain, and whether a slam is on its way down. */
        int chain;
        long slamSince = -1;
        /** The last tick the target was seen off the ground. */
        long lastAir = Long.MIN_VALUE / 2;
    }

    /** How long a slammed target is watched for its landing before the effect is dropped. */
    private static final int SLAM_WATCH_TICKS = 40;

    /** Where a delayed strike is handed over; the combat server owns the queue and the settlement. */
    interface DelayedSink {

        void schedule(EntityPlayerMP attacker, EntityLivingBase target, HitEffect effect, long tick);
    }

    private DelayedSink sink = (attacker, target, effect, tick) -> {};

    void sink(DelayedSink next) {
        sink = next;
    }

    private final Map<UUID, Target> targets = new HashMap<>();
    private final CombatConfig config;
    private final CombatPresentation presentation;

    HitEffects(CombatConfig config, CombatPresentation presentation) {
        this.config = config;
        this.presentation = presentation;
    }

    private Target target(EntityLivingBase entity) {
        Target target = targets.get(entity.getUniqueID());
        if (target == null || target.entity != entity || target.dimension != entity.dimension) {
            target = new Target();
            target.entity = entity;
            target.dimension = entity.dimension;
            target.budget = new ControlBudget(
                config.controlCapacity,
                config.launchProtectionTicks,
                config.controlResetTicks,
                config.controlExpiryTicks);
            targets.put(entity.getUniqueID(), target);
        }
        return target;
    }

    boolean exempt(EntityLivingBase entity) {
        if (entity instanceof EntityFlying || entity instanceof EntityWaterMob
            || config.launchExcludeSwimming && entity.isInWater()) return true;
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (player.capabilities.isCreativeMode || player.capabilities.isFlying) return true;
        }
        String name = EntityList.getEntityString(entity);
        for (String excluded : config.launchExemptEntities) if (excluded.equals(name)) return true;
        return false;
    }

    double apply(EntityPlayerMP attacker, EntityLivingBase entity, Scheduler.Instance action, long tick, long frozen,
        boolean hit, int guard, java.util.List<HitEffect> attached) {
        if (action.definition.effects.isEmpty() && attached.isEmpty()) return 0;
        boolean alive = !entity.isDead && entity.getHealth() > 0;
        boolean exempt = exempt(entity);
        if (!HitEffect.allowed(hit, guard, alive, exempt)) {
            trace("SKIP target=" + entity.getEntityId() + " guard=" + guard + " hit=" + hit + " exempt=" + exempt);
            return 0;
        }
        double[] credit = { 0 };
        java.util.function.Consumer<HitEffect> run = effect -> {
            if (effect.type == HitEffect.Type.DELAYED_SLASH) {
                sink.schedule(attacker, entity, effect, tick);
                return;
            }
            if (effect.vy <= 0) return;
            Target target = target(entity);
            if (effect.type == HitEffect.Type.SLAM) {
                boolean airborne = !entity.onGround || !config.slamRequiresAir
                    || tick - target.lastAir <= config.slamGraceTicks;
                boolean falling = target.slamSince >= 0;
                boolean accepted = airborne && !falling && target.budget.exhaust(tick, entity.onGround);
                if (accepted) {
                    entity.motionY = -effect.vy;
                    entity.velocityChanged = true;
                    if (entity instanceof EntityPlayerMP) ((EntityPlayerMP) entity).playerNetServerHandler
                        .sendPacket(new S12PacketEntityVelocity(entity));
                    target.slamSince = tick;
                }
                trace(
                    "SLAM target=" + entity.getEntityId()
                        + " accepted="
                        + accepted
                        + " airborne="
                        + airborne
                        + " tick="
                        + tick
                        + " chain="
                        + target.chain
                        + " protectionUntil="
                        + target.budget.protectedUntil()
                        + " vy="
                        + entity.motionY
                        + " hp="
                        + entity.getHealth());
                return;
            }
            if (effect.type != HitEffect.Type.LAUNCH) return;
            boolean accepted = target.budget.consume(tick, config.launchCost, entity.onGround);
            double vy = HitEffect.decayed(effect.vy, target.chain, config.chainDecay);
            if (accepted) {
                target.chain++;
                entity.motionY = Math.max(entity.motionY, vy);
                entity.velocityChanged = true;
                if (entity instanceof EntityPlayerMP)
                    ((EntityPlayerMP) entity).playerNetServerHandler.sendPacket(new S12PacketEntityVelocity(entity));
                credit[0] += vy * config.impulseMultiplier;
                presentation.launch(attacker, entity, action, tick, frozen);
            }
            trace(
                "LAUNCH target=" + entity.getEntityId()
                    + " accepted="
                    + accepted
                    + " tick="
                    + tick
                    + " chain="
                    + target.chain
                    + " remaining="
                    + target.budget.remaining()
                    + " protectionUntil="
                    + target.budget.protectedUntil()
                    + " vy="
                    + entity.motionY
                    + " hp="
                    + entity.getHealth());
        };
        HitEffect.submit(action.definition.effects, hit, guard, alive, exempt, run);
        if (!attached.isEmpty()) HitEffect.submit(attached, hit, guard, alive, exempt, run);
        return credit[0];
    }

    void tick(long tick) {
        targets.entrySet()
            .removeIf(entry -> {
                Target target = entry.getValue();
                EntityLivingBase entity = target.entity;
                if (entity.isDead || entity.getHealth() <= 0
                    || entity.dimension != target.dimension
                    || entity.worldObj.getEntityByID(entity.getEntityId()) != entity) return true;
                target.budget.advance(tick, entity.onGround);
                if (!entity.onGround) target.lastAir = tick;
                if (target.budget.remaining() == config.controlCapacity) target.chain = 0;
                if (target.slamSince >= 0 && (entity.onGround || tick - target.slamSince > SLAM_WATCH_TICKS)) {
                    if (entity.onGround) presentation.slam(entity, tick);
                    target.slamSince = -1;
                }
                return target.budget.remaining() == config.controlCapacity && target.budget.expired(tick);
            });
    }

    void remove(EntityLivingBase entity) {
        targets.remove(entity.getUniqueID());
    }

    void clear() {
        targets.clear();
    }

    private static void trace(String text) {
        if (Boolean.getBoolean("clashweave.trace")) System.out.println("CW_EFFECT " + text);
    }
}
