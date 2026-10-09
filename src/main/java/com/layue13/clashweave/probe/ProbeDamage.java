package com.layue13.clashweave.probe;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;

import cpw.mods.fml.relauncher.ReflectionHelper;

final class ProbeDamage {

    static final DamageSource SOURCE = new net.minecraft.util.EntityDamageSource("clashweave.probe", null);
    private static final Field LAST = ReflectionHelper
        .findField(EntityLivingBase.class, "lastDamage", "field_110153_bc");
    private final Set<String> seen = new HashSet<String>();
    private final DamageSource source;

    ProbeDamage() {
        source = SOURCE;
    }

    ProbeDamage(net.minecraft.entity.player.EntityPlayer attacker) {
        source = new net.minecraft.util.EntityDamageSource("clashweave.probe", attacker);
    }

    boolean hit(long instance, EntityLivingBase target, int segment, float amount, boolean restore) {
        String key = instance + ":" + target.getUniqueID() + ":" + segment;
        if (!seen.add(key)) return false;
        int timer = target.hurtResistantTime;
        try {
            float last = LAST.getFloat(target);
            target.hurtResistantTime = 0;
            try {
                return target.attackEntityFrom(source, amount);
            } finally {
                if (restore) {
                    target.hurtResistantTime = timer;
                    LAST.setFloat(target, last);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
