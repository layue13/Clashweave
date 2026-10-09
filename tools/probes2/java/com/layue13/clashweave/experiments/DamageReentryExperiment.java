package com.layue13.clashweave.experiments;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Separate experimental mod: never bundled in Clashweave's production jar. */
@Mod(modid = "cw_s1b_experiment", name = "Clashweave S1b experiment", version = "round2")
public final class DamageReentryExperiment {

    private static final DamageSource MOD_SOURCE = new DamageSource("clashweave.s1b");
    private EntityCow observed;
    private String label;
    private boolean nestedAccepted;
    private float nestedLoss;
    private int nestedCalls;

    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) {
        MinecraftServer server = MinecraftServer.getServer();
        MinecraftForge.EVENT_BUS.register(this);
        try {
            WorldServer world = server.worldServerForDimension(0);
            EntityCow control = prepared(world);
            EntityCow scoped = prepared(world);
            boolean controlOuter = run(control, "vanilla-control", false);
            boolean controlNested = nestedAccepted;
            float controlLoss = nestedLoss;
            int controlCalls = nestedCalls;
            boolean scopedOuter = run(scoped, "scoped-candidate", true);
            boolean equivalent = controlNested == nestedAccepted && controlLoss == nestedLoss;
            boolean restored = scoped.hurtResistantTime == control.hurtResistantTime
                && ScopedDamage.last(scoped) == ScopedDamage.last(control);
            System.out.println("CW2 S1B_RESULT recursiveEquivalent=" + equivalent
                + " controlNested=" + controlNested + " scopedNested=" + nestedAccepted
                + " controlNestedLoss=" + controlLoss + " scopedNestedLoss=" + nestedLoss
                + " controlOuter=" + controlOuter + " scopedOuter=" + scopedOuter
                + " controlCallbacks=" + controlCalls + " scopedCallbacks=" + nestedCalls
                + " finalGateRestored=" + restored + " verdict=" + (equivalent ? "INCOMPLETE" : "FAIL")
                + " stopBeforeOtherProbes=true");
        } finally {
            observed = null;
            MinecraftForge.EVENT_BUS.unregister(this);
            server.initiateShutdown();
        }
    }

    private EntityCow prepared(WorldServer world) {
        EntityCow cow = new EntityCow(world);
        cow.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(100.0);
        cow.setHealth(100.0F);
        if (!cow.attackEntityFrom(DamageSource.generic, 4.0F)) {
            throw new IllegalStateException("Vanilla seed damage was rejected");
        }
        for (int tick = 0; tick < 7; tick++) cow.onEntityUpdate();
        if (cow.getHealth() != 96.0F || cow.hurtResistantTime != 13 || ScopedDamage.last(cow) != 4.0F) {
            throw new IllegalStateException("Unexpected vanilla baseline");
        }
        return cow;
    }

    private boolean run(EntityCow target, String name, boolean isolated) {
        observed = target;
        label = name;
        nestedAccepted = false;
        nestedLoss = 0.0F;
        nestedCalls = 0;
        System.out.println("CW2 S1B_BEFORE path=" + label + " hp=" + target.getHealth()
            + " timer=" + target.hurtResistantTime + " last=" + ScopedDamage.last(target));
        boolean accepted = isolated ? ScopedDamage.apply(target, MOD_SOURCE, 2.0F)
            : target.attackEntityFrom(MOD_SOURCE, 2.0F);
        System.out.println("CW2 S1B_AFTER path=" + label + " outerAccepted=" + accepted
            + " hp=" + target.getHealth() + " timer=" + target.hurtResistantTime
            + " last=" + ScopedDamage.last(target));
        observed = null;
        return accepted;
    }

    @SubscribeEvent
    public void otherSourceDuringAttack(LivingAttackEvent event) {
        if (event.entityLiving != observed || event.source != MOD_SOURCE) return;
        nestedCalls++;
        float before = observed.getHealth();
        int timer = observed.hurtResistantTime;
        float last = ScopedDamage.last(observed);
        nestedAccepted = observed.attackEntityFrom(DamageSource.generic, 4.0F);
        nestedLoss = before - observed.getHealth();
        System.out.println("CW2 S1B_REENTRY path=" + label + " event=LivingAttackEvent"
            + " source=generic amount=4.0 timerAtCallback=" + timer + " lastAtCallback=" + last
            + " accepted=" + nestedAccepted + " loss=" + nestedLoss);
    }
}
