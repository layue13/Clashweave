package com.layue13.clashweave.validation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;

import com.layue13.clashweave.forge.ResistanceOwnership;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

@Mod(modid = "clashweavevalidation", name = "P0 isolated validation", version = "1", dependencies = "required-after:clashweave")
public final class Validation {

    private boolean done;

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        FMLCommonHandler.instance().bus().register(this);
        FMLCommonHandler.instance().bus().register(new NetworkScenario());
        Supplement supplement = new Supplement();
        FMLCommonHandler.instance().bus().register(supplement);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(supplement);
        FMLCommonHandler.instance().bus().register(new MovementScenario());
        FMLCommonHandler.instance().bus().register(new EngagementScenario());
        FMLCommonHandler.instance().bus().register(new LockScenario());
        FMLCommonHandler.instance().bus().register(new LockSupportScenario());
        SweepScenario sweep=new SweepScenario();
        FMLCommonHandler.instance().bus().register(sweep);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(sweep);
        if (FMLCommonHandler.instance().getSide().isClient()) {
            try {
                Class.forName("com.layue13.clashweave.validation.ClientReplay").newInstance();
                if(Boolean.getBoolean("cw.p0.lockSupport"))Class.forName("com.layue13.clashweave.validation.LockSupportReplay").newInstance();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private void require(boolean value, String label) {
        System.out.println("P0_ASSERT " + label + "=" + value);
        if (!value) throw new AssertionError(label);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("cw.p0.serverCheck")) return;
        done = true;
        WorldServer world = MinecraftServer.getServer().worldServerForDimension(0);
        ResistanceOwnership ownership = new ResistanceOwnership(8);
        EntityCow cow = new EntityCow(world);
        cow.setPosition(0, 100, 0);
        Map<EntityLivingBase, Set<UUID>> wanted = new HashMap<>();
        Set<UUID> owners = new HashSet<>();
        owners.add(UUID.randomUUID());
        owners.add(UUID.randomUUID());
        wanted.put(cow, owners);
        ownership.reconcile(wanted);
        require(cow.maxHurtResistantTime == 8 && ownership.owners(cow) == 2, "twoOwners");
        require(cow.attackEntityFrom(DamageSource.generic, 2), "firstHit");
        for (int hit = 1; hit < 3; hit++) {
            for (int tick = 0; tick < 4; tick++) cow.onEntityUpdate();
            require(cow.attackEntityFrom(DamageSource.generic, 2), "gap4Hit" + hit);
        }
        require(cow.getHealth() == 4, "threeHitsLoss6");
        owners.remove(owners.iterator().next());
        ownership.reconcile(wanted);
        require(cow.maxHurtResistantTime == 8 && ownership.owners(cow) == 1, "oneOwnerRemains");
        wanted.clear();
        ownership.reconcile(wanted);
        require(cow.maxHurtResistantTime == 20, "restore20");
        wanted.put(cow, owners);
        ownership.reconcile(wanted);
        cow.maxHurtResistantTime = 12;
        wanted.clear();
        ownership.reconcile(wanted);
        require(cow.maxHurtResistantTime == 12, "externalWritePreserved");
        cow.maxHurtResistantTime = 20;
        wanted.put(cow, owners);
        ownership.reconcile(wanted);
        cow.dimension = -1;
        ownership.reconcile(wanted);
        require(cow.maxHurtResistantTime == 20, "dimensionRestore");
        EntityCow plain = new EntityCow(world);
        require(firstEqualHit(plain) == 10, "vanillaEqualSourceWindow10");
        EntityCow shortened = new EntityCow(world);
        owners.clear();
        owners.add(UUID.randomUUID());
        wanted.clear();
        wanted.put(shortened, owners);
        ownership.reconcile(wanted);
        require(firstEqualHit(shortened) == 4, "engagedEqualSourceWindow4");
        ownership.clear();
        OverrideCow override = new OverrideCow(world);
        wanted.clear();
        wanted.put(override, owners);
        ownership.reconcile(wanted);
        require(override.attackEntityFrom(DamageSource.generic, 2) && override.observedMaximum == 8, "entityOverrideSeesPersistent8");
        override.fail = true;
        float health = override.getHealth();
        try { override.attackEntityFrom(DamageSource.generic, 2); } catch (IllegalStateException expected) { }
        require(override.getHealth() == health && override.maxHurtResistantTime == 8, "overrideExceptionNoExtraDamageOrTransientReset");
        ownership.clear();
        require(override.maxHurtResistantTime == 20, "overrideExitRestore");
        EntityCow foreign = new EntityCow(world);
        wanted.clear();
        wanted.put(foreign, owners);
        ownership.reconcile(wanted);
        foreign.maxHurtResistantTime = 12;
        ownership.reconcile(wanted);
        foreign.maxHurtResistantTime = 8;
        wanted.clear();
        ownership.reconcile(wanted);
        require(foreign.maxHurtResistantTime == 8, "observedForeignOwnershipDoesNotReturnWhenValueMatchesAgain");
        EntityCow transition = new EntityCow(world);
        transition.attackEntityFrom(DamageSource.generic, 2);
        wanted.clear();
        wanted.put(transition, owners);
        ownership.reconcile(wanted);
        require(transition.hurtResistantTime == 20, "entryKeepsOldTimer20");
        require(!transition.attackEntityFrom(DamageSource.generic, 2), "oldTimerRejectsEqualDamage");
        require(firstEqualHit(transition) == 16, "oldTimerTransitionFirstEqual16");
        ownership.clear();
        System.out.println("P0_SERVER_CHECK COMPLETE");
        MinecraftServer.getServer().initiateShutdown();
    }

    private int firstEqualHit(EntityCow cow) {
        cow.attackEntityFrom(DamageSource.generic, 2);
        for (int elapsed = 1; elapsed <= 20; elapsed++) {
            cow.onEntityUpdate();
            if (cow.attackEntityFrom(DamageSource.generic, 2)) {
                System.out.println("P0_TIMER max=" + cow.maxHurtResistantTime + " firstEqualAccepted=" + elapsed);
                return elapsed;
            }
        }
        return -1;
    }

    private static final class OverrideCow extends EntityCow {
        private int observedMaximum;
        private boolean fail;

        OverrideCow(WorldServer world) { super(world); }

        @Override
        public boolean attackEntityFrom(DamageSource source, float amount) {
            observedMaximum = maxHurtResistantTime;
            if (fail) throw new IllegalStateException("deliberate third-party entity override");
            return super.attackEntityFrom(source, amount);
        }
    }
}
