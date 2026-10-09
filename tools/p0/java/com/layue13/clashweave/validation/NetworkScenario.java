package com.layue13.clashweave.validation;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import com.layue13.clashweave.Clashweave;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class NetworkScenario {
    private int bothTicks;
    private long sum;
    private long maximum;
    private int samples;
    private long prepared = -1;

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("cw.p0.network")) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        WorldServer world = player.getServerForPlayer();
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) world.setBlock(x, 63, z, Blocks.stone);
        boolean a = player.getCommandSenderName().equals("P0A");
        player.inventory.setInventorySlotContents(0, new ItemStack(Clashweave.katana));
        player.inventory.currentItem = 0;
        player.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(200);
        player.setHealth(200);
        player.playerNetServerHandler.setPlayerLocation(a ? 0 : Boolean.getBoolean("cw.p0.renderOnly") ? 6 : 1.15, 64, a ? 0 : 1.9, a ? 0 : 180, 0);
        if (a && !Boolean.getBoolean("cw.p0.renderOnly")) {
            EntityZombie zombie = new EntityZombie(world);
            zombie.setPosition(Boolean.getBoolean("cw.p0.renderOnly") ? -6 : -1.15, 64, Boolean.getBoolean("cw.p0.engagement") ? 9 : 2);
            zombie.setAttackTarget(player);
            zombie.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(Boolean.getBoolean("cw.p0.manual") ? 20 : 200);
            zombie.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(Boolean.getBoolean("cw.p0.manual") ? .23 : 0);
            zombie.setHealth(Boolean.getBoolean("cw.p0.manual") ? 20 : 200);
            world.spawnEntityInWorld(zombie);
        }
        System.out.println("P0_NETWORK login=" + player.getCommandSenderName());
        if (Boolean.getBoolean("cw.p0.manual")) world.setBlock(-5,64,-5,Blocks.chest);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("cw.p0.network") || event.phase != TickEvent.Phase.END) return;
        if (MinecraftServer.getServer().getCurrentPlayerCount() < 2) return;
        bothTicks++;
        boolean prepare = false;
        for (Object object : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP)object;
            com.layue13.clashweave.forge.CombatServer.PlayerState state = Clashweave.server.state(player);
            if (player.getCommandSenderName().equals("P0A") && state != null && state.scheduler.current() == null && state.instance != prepared) {
                prepared = state.instance;
                prepare = true;
            }
        }
        for (Object object : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) object;
            com.layue13.clashweave.forge.CombatServer.PlayerState state = Clashweave.server.state(player);
            if (!Boolean.getBoolean("cw.p0.supplement") && !Boolean.getBoolean("cw.p0.manual") && !Boolean.getBoolean("cw.p0.engagement") && state != null && state.scheduler.current() == null && prepare) {
                boolean a = player.getCommandSenderName().equals("P0A");
                player.playerNetServerHandler.setPlayerLocation(a ? 0 : Boolean.getBoolean("cw.p0.renderOnly") ? 6 : 1.15, 64, a ? 0 : 1.9, a ? 0 : 180, 0);
                System.out.println("P0_PREP player=" + player.getCommandSenderName() + " afterInstance=" + prepared);
            }
        }
        long cost = Clashweave.server.lastNanos;
        sum += cost;
        maximum = Math.max(maximum, cost);
        samples++;
        if (bothTicks % 100 == 0) System.out.println("P0_PERF samples=" + samples + " meanNanos=" + sum / samples + " maxNanos=" + maximum);
        if (bothTicks == 1000 && !Boolean.getBoolean("cw.p0.manual")) {
            System.out.println("P0_NETWORK COMPLETE");
            MinecraftServer.getServer().initiateShutdown();
        }
    }
}
