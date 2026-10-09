package com.layue13.clashweave.s1c;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/** Disposable event-only candidate. No changes before dispatching LivingAttackEvent. */
public final class EventGate {
    private static final Field LAST = ReflectionHelper.findField(EntityLivingBase.class, "lastDamage", "field_110153_bc");
    private final Deque<Frame> frames = new ArrayDeque<Frame>();
    private final Set<String> seen = new HashSet<String>();
    private static final class Frame {
        final EntityLivingBase target;
        final DamageSource source;
        final int timer;
        final float last;
        Frame(EntityLivingBase target, DamageSource source) {
            this.target = target; this.source = source;
            timer = target.hurtResistantTime; last = last(target);
        }
        void restore() { target.hurtResistantTime = timer; setLast(target, last); }
    }
    static float last(EntityLivingBase target) {
        try { return LAST.getFloat(target); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    static void setLast(EntityLivingBase target, float value) {
        try { LAST.setFloat(target, value); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    boolean apply(EntityLivingBase target, DamageSource source, float amount) {
        Frame frame = new Frame(target, source);
        frames.push(frame);
        try { return target.attackEntityFrom(source, amount); }
        finally { frame.restore(); frames.pop(); }
    }
    boolean hit(String action, EntityLivingBase target, int segment, DamageSource source, float amount) {
        String key = action + ":" + target.getUniqueID() + ":" + segment;
        if (!seen.add(key)) return false;
        return apply(target, source, amount);
    }
    void end(String action) { java.util.Iterator<String> it = seen.iterator(); while (it.hasNext()) if (it.next().startsWith(action + ":")) it.remove(); }
    int ledgerSize() { return seen.size(); }
    int depth() { return frames.size(); }
    private Frame match(EntityLivingBase target, DamageSource source) {
        Frame frame = frames.peek();
        return frame != null && frame.target == target && frame.source == source ? frame : null;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void attack(LivingAttackEvent event) {
        Frame frame = match(event.entityLiving, event.source);
        if (frame != null) event.entityLiving.hurtResistantTime = 0;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void hurt(LivingHurtEvent event) {
        Frame frame = match(event.entityLiving, event.source);
        if (frame != null) frame.restore();
    }
}
