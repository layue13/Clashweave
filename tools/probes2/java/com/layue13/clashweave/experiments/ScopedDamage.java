package com.layue13.clashweave.experiments;

import java.lang.reflect.Field;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;

import cpw.mods.fml.relauncher.ReflectionHelper;

/** Disposable S1b candidate, deliberately outside the production source set. */
final class ScopedDamage {

    private static final Field LAST = ReflectionHelper.findField(
        EntityLivingBase.class, "lastDamage", "field_110153_bc");

    static float last(EntityLivingBase target) {
        try {
            return LAST.getFloat(target);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static boolean apply(EntityLivingBase target, DamageSource source, float amount) {
        int timer = target.hurtResistantTime;
        float previous = last(target);
        target.hurtResistantTime = 0;
        try {
            return target.attackEntityFrom(source, amount);
        } finally {
            target.hurtResistantTime = timer;
            try {
                LAST.setFloat(target, previous);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
