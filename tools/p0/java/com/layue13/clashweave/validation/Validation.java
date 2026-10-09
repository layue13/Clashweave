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
        System.out.println("P0_SERVER_CHECK COMPLETE");
        MinecraftServer.getServer().initiateShutdown();
    }
}
