package com.layue13.clashweave.validation;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import com.layue13.clashweave.Clashweave;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Real network players; no manually fired lifecycle events. */
public final class Supplement {
    private int ticks;
    private boolean started;
    private EntityPlayerMP previous;
    private float before;

    private EntityPlayerMP player(String name) {
        for (Object object : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) object;
            if (player.getCommandSenderName().equals(name)) return player;
        }
        return null;
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("cw.p0.supplement") || event.phase != TickEvent.Phase.END) return;
        if (!started && player("P0A") != null && player("P0B") != null) started = true;
        if (!started) return;
        ticks++;
        EntityPlayerMP b = player("P0B");
        if (b != null && ticks == 110) {
            EntityPlayerMP a = player("P0A");
            net.minecraft.entity.passive.EntityVillager villager = new net.minecraft.entity.passive.EntityVillager(b.worldObj);
            net.minecraft.entity.passive.EntityWolf wolf = new net.minecraft.entity.passive.EntityWolf(b.worldObj);
            net.minecraft.entity.passive.EntityCow cow = new net.minecraft.entity.passive.EntityCow(b.worldObj);
            wolf.setTamed(true);
            net.minecraft.scoreboard.Scoreboard board = b.worldObj.getScoreboard();
            net.minecraft.scoreboard.ScorePlayerTeam team = board.createTeam("p0friendly");
            board.func_151392_a(a.getCommandSenderName(),team.getRegisteredName());
            board.func_151392_a(b.getCommandSenderName(),team.getRegisteredName());
            System.out.println("P0_SUPPLEMENT friendly villager="+Clashweave.server.protectedTarget(a,villager)+" tamed="+Clashweave.server.protectedTarget(a,wolf)+" team="+Clashweave.server.protectedTarget(a,b)+" cow="+Clashweave.server.protectedTarget(a,cow));
            Clashweave.config.friendly=false;
            System.out.println("P0_SUPPLEMENT friendlyDisabled="+!Clashweave.server.protectedTarget(a,villager));
            Clashweave.config.friendly=true;
            board.removeTeam(team);
        }
        if (b != null && ticks == 100) {
            b.inventory.armorInventory[0] = new ItemStack(Items.iron_boots);
            b.inventory.armorInventory[1] = new ItemStack(Items.iron_leggings);
            b.inventory.armorInventory[2] = new ItemStack(Items.iron_chestplate);
            b.inventory.armorInventory[3] = new ItemStack(Items.iron_helmet);
            b.addPotionEffect(new PotionEffect(Potion.resistance.id, 100, 0));
            before = b.getHealth();
        }
        if (b != null && (ticks == 101 || ticks == 105 || ticks == 109)) {
            boolean hit = b.attackEntityFrom(DamageSource.causePlayerDamage(player("P0A")), 2);
            System.out.println("P0_SUPPLEMENT armorPotion tick=" + ticks + " accepted=" + hit + " armor=" + b.getTotalArmorValue() + " loss=" + (before-b.getHealth()) + " max=" + b.maxHurtResistantTime + " timer=" + b.hurtResistantTime);
        }
        if (b != null && ticks == 120) {
            b.maxHurtResistantTime = 12;
            System.out.println("P0_SUPPLEMENT externalWrite value=12");
        }
        if (b != null && ticks == 130) {
            System.out.println("P0_SUPPLEMENT externalPreserved=" + (b.maxHurtResistantTime == 12));
            // End this fixture's foreign-write case before independently testing owned death restoration.
            Clashweave.server.ownership().remove(b);
            b.maxHurtResistantTime = 20;
        }
        if (b != null && ticks == 135) {
            previous = b;
            b.attackEntityFrom(DamageSource.outOfWorld, 1000);
        }
        if (ticks == 150 && b != null) System.out.println("P0_SUPPLEMENT respawn oldRestored=" + (previous.maxHurtResistantTime == 20) + " newEntity=" + (b != previous) + " session=" + Clashweave.server.state(b).session);
        if (b != null && ticks == 180) {
            b.inventory.setInventorySlotContents(0, new ItemStack(Clashweave.katana));
            previous = b;
            MinecraftServer.getServer().getConfigurationManager().transferPlayerToDimension(b, -1);
            System.out.println("P0_SUPPLEMENT dimensionExit max=" + b.maxHurtResistantTime + " dimension=" + b.dimension);
        }
        if (b != null && ticks == 200) {
            MinecraftServer.getServer().getConfigurationManager().transferPlayerToDimension(b, 0);
            System.out.println("P0_SUPPLEMENT dimensionReturn max=" + b.maxHurtResistantTime + " dimension=" + b.dimension);
        }
        if (ticks == 460) {
            System.out.println("P0_SUPPLEMENT COMPLETE remaining=" + MinecraftServer.getServer().getCurrentPlayerCount());
            MinecraftServer.getServer().initiateShutdown();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void death(LivingDeathEvent event) {
        if (Boolean.getBoolean("cw.p0.supplement") && event.entityLiving instanceof EntityPlayerMP) System.out.println("P0_SUPPLEMENT death max=" + event.entityLiving.maxHurtResistantTime);
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (Boolean.getBoolean("cw.p0.supplement")) System.out.println("P0_SUPPLEMENT logout max=" + event.player.maxHurtResistantTime);
    }
}
