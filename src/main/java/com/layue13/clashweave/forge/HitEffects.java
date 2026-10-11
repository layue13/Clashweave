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
        boolean hit, int guard) {
        if (action.definition.effects.isEmpty()) return 0;
        boolean alive = !entity.isDead && entity.getHealth() > 0;
        boolean exempt = exempt(entity);
        if (!HitEffect.allowed(hit, guard, alive, exempt)) {
            trace("SKIP target=" + entity.getEntityId() + " guard=" + guard + " hit=" + hit + " exempt=" + exempt);
            return 0;
        }
        double[] credit = { 0 };
        HitEffect.submit(action.definition.effects, hit, guard, alive, exempt, effect -> {
            if (effect.type != HitEffect.Type.LAUNCH || effect.vy <= 0) return;
            Target target = target(entity);
            boolean accepted = target.budget.consume(tick, config.launchCost, entity.onGround);
            if (accepted) {
                entity.motionY = Math.max(entity.motionY, effect.vy);
                entity.velocityChanged = true;
                if (entity instanceof EntityPlayerMP)
                    ((EntityPlayerMP) entity).playerNetServerHandler.sendPacket(new S12PacketEntityVelocity(entity));
                credit[0] += effect.vy * config.impulseMultiplier;
                presentation.launch(attacker, entity, action, tick, frozen);
            }
            trace(
                "LAUNCH target=" + entity.getEntityId()
                    + " accepted="
                    + accepted
                    + " tick="
                    + tick
                    + " remaining="
                    + target.budget.remaining()
                    + " protectionUntil="
                    + target.budget.protectedUntil()
                    + " vy="
                    + entity.motionY
                    + " hp="
                    + entity.getHealth());
        });
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
