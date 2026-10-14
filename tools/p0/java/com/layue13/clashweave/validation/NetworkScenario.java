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
    private EntityZombie viewDummy;
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
        for (int x = -30; x <= 30; x++) for (int z = -50; z <= 50; z++) world.setBlock(x, 63, z, Blocks.stone);
        boolean a = player.getCommandSenderName().equals("P0A");
        player.inventory.setInventorySlotContents(0, new ItemStack(Clashweave.katana));
        player.inventory.currentItem = 0;
        String armor = System.getProperty("cw.c2b.armor");
        if (a && armor != null) {
            // 0 boots, 1 leggings, 2 chestplate, 3 helmet. "chest", "legs" or "boots" equip one piece; others all four.
            net.minecraft.item.Item[] pieces = armor.startsWith("leather")
                ? new net.minecraft.item.Item[] { net.minecraft.init.Items.leather_boots, net.minecraft.init.Items.leather_leggings, net.minecraft.init.Items.leather_chestplate, net.minecraft.init.Items.leather_helmet }
                : new net.minecraft.item.Item[] { net.minecraft.init.Items.iron_boots, net.minecraft.init.Items.iron_leggings, net.minecraft.init.Items.iron_chestplate, net.minecraft.init.Items.iron_helmet };
            for (int slot = 0; slot < 4; slot++) {
                boolean wanted = armor.endsWith("-chest") ? slot == 2 : armor.endsWith("-legs") ? slot == 1 : armor.endsWith("-boots") ? slot == 0 : true;
                if (wanted) player.inventory.armorInventory[slot] = new ItemStack(pieces[slot]);
            }
            System.out.println("C2B_ARMOR equipped " + armor);
        }
        player.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(200);
        player.setHealth(200);
        player.playerNetServerHandler.setPlayerLocation(a ? 0 : Boolean.getBoolean("cw.p0.renderOnly") ? 6 : 1.15, 64, a ? 0 : 1.9, a ? 0 : 180, 0);
        if (a && !Boolean.getBoolean("cw.p0.lockSupport") && !Boolean.getBoolean("cw.p0.composition") && !Boolean.getBoolean("cw.p0.renderOnly") && !Boolean.getBoolean("cw.p0.fastTurn") && !Boolean.getBoolean("cw.p0.correctionTest") && !Boolean.getBoolean("cw.p0.viewTest") && !Boolean.getBoolean("cw.p0.lockTest") && !Boolean.getBoolean("cw.p0.sweepTest")) {
            EntityZombie zombie = new EntityZombie(world);
            zombie.setPosition(Boolean.getBoolean("cw.p0.renderOnly") ? -6 : -1.15, 64, Boolean.getBoolean("cw.p0.engagement") ? 9 : 2);
            zombie.setAttackTarget(player);
            zombie.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(Boolean.getBoolean("cw.p0.manual") ? 20 : 200);
            zombie.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(Boolean.getBoolean("cw.p0.manual") ? .23 : 0);
            zombie.setHealth(Boolean.getBoolean("cw.p0.manual") ? 20 : 200);
            world.spawnEntityInWorld(zombie);
        }
        if (!a && Boolean.getBoolean("cw.b6.sleep")) {
            // A real bed, night, and the server puts P0B to sleep in it the way a player would.
            world.setBlock(5, 64, -5, Blocks.bed, 0, 3);
            world.setBlock(5, 64, -4, Blocks.bed, 8, 3);
            world.setWorldTime(18000);
            player.playerNetServerHandler.setPlayerLocation(5.5, 64, -5.5, 0, 0);
        }
        if (a && Boolean.getBoolean("cw.b4.walkers")) {
            // Real server-driven monsters (their own AI and pathing) walking at the player; fire-proof so daylight leaves them be.
            net.minecraft.entity.EntityLiving[] walkers = { new EntityZombie(world), new net.minecraft.entity.monster.EntitySkeleton(world), new net.minecraft.entity.monster.EntityPigZombie(world), new net.minecraft.entity.monster.EntityEnderman(world) };
            for (int i = 0; i < walkers.length; i++) {
                walkers[i].setPosition(-9 + 6 * i, 64, -16);
                try { java.lang.reflect.Field fire = net.minecraft.entity.Entity.class.getDeclaredField("isImmuneToFire"); fire.setAccessible(true); fire.setBoolean(walkers[i], true); } catch (Exception e) { System.out.println("B4_FIRE_UNAVAILABLE " + e); }
                walkers[i].setAttackTarget(player);
                System.out.println("B4_SPAWN " + walkers[i].getClass().getSimpleName() + " ok=" + world.spawnEntityInWorld(walkers[i]) + " pos=" + walkers[i].posX + "," + walkers[i].posY + "," + walkers[i].posZ);
            }
        }
        System.out.println("P0_NETWORK login=" + player.getCommandSenderName());
        if (Boolean.getBoolean("cw.p0.manual")) world.setBlock(-5,64,-5,Blocks.chest);
    }

    @SubscribeEvent(priority=cpw.mods.fml.common.eventhandler.EventPriority.HIGHEST)
    public void viewTarget(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("cw.p0.fastTurn") && !Boolean.getBoolean("cw.p0.correctionTest") && !Boolean.getBoolean("cw.p0.viewTest") || event.phase!=TickEvent.Phase.END) return;

            java.util.List list = MinecraftServer.getServer().getConfigurationManager().playerEntityList;
            EntityPlayerMP a = null, b = null;
            for (Object object : list) { EntityPlayerMP player = (EntityPlayerMP)object;
                if (player.getCommandSenderName().equals("P0A")) a=player; else b=player; }
            if (a!=null && b!=null) {
                if (viewDummy==null) {
                    viewDummy=new EntityZombie(a.worldObj);
                    viewDummy.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(1000);
                    viewDummy.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);
                    viewDummy.setHealth(1000);viewDummy.setPosition(a.posX,a.posY,a.posZ+1.9);a.worldObj.spawnEntityInWorld(viewDummy);
                    System.out.println("P0_VIEW_TARGET stationaryZombie="+viewDummy.getEntityId());
                }
                viewDummy.setPosition(a.posX, a.posY, a.posZ+1.9);
                viewDummy.motionX=0;viewDummy.motionY=0;viewDummy.motionZ=0;viewDummy.fallDistance=0;
                viewDummy.setAttackTarget(a);

            }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("cw.p0.network") || event.phase != TickEvent.Phase.END) return;
        if (MinecraftServer.getServer().getCurrentPlayerCount() < 2) return;
        bothTicks++;
        if (Boolean.getBoolean("cw.b6.fp2")) for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (!p.getCommandSenderName().equals("P0A")) continue;
            WorldServer w = p.getServerForPlayer();
            if (bothTicks == 20) {
                // A pool, a ladder against a wall, and a saddled pig: the states the first-person arms have to follow.
                for (int x = 10; x <= 14; x++) for (int z = 10; z <= 40; z++) { w.setBlock(x, 61, z, Blocks.stone); for (int y = 62; y <= 63; y++) w.setBlock(x, y, z, Blocks.water); }
                for (int y = 64; y <= 70; y++) { w.setBlock(20, y, 10, Blocks.stone); w.setBlock(20, y, 11, Blocks.ladder, 3, 3); }
                net.minecraft.entity.passive.EntityPig pig = new net.minecraft.entity.passive.EntityPig(w);
                pig.setPosition(26.5, 64, 10.5);
                pig.setSaddled(true);
                w.spawnEntityInWorld(pig);
            }
            if (bothTicks % 30 == 0) System.out.println("B6_FP2 tick=" + bothTicks + " pos=" + p.posX + "," + p.posY + "," + p.posZ + " inWater=" + p.isInWater() + " riding=" + p.isRiding() + " ladder=" + p.isOnLadder());
            // Keep the swimmer in the pool: the client's clock drifts against this one, so put it back whenever it leaves.
            if (bothTicks >= 90 && bothTicks <= 215 && (bothTicks == 90 || bothTicks % 5 == 0 && (!p.isInWater() || p.posZ > 38))) p.playerNetServerHandler.setPlayerLocation(12.5, 63.0, 11.5, 0, 0);
            if (bothTicks == 220) p.playerNetServerHandler.setPlayerLocation(20.5, 64, 11.5, 180, 0);
            if (bothTicks == 330) p.playerNetServerHandler.setPlayerLocation(26.5, 64, 12.5, 0, 0);
            if (bothTicks == 430) { p.capabilities.allowFlying = true; p.capabilities.isFlying = true; p.sendPlayerAbilities(); p.playerNetServerHandler.setPlayerLocation(10.5, 70, 5.5, 0, 0); }
            if (bothTicks == 345) for (Object e : w.loadedEntityList) if (e instanceof net.minecraft.entity.passive.EntityPig) p.mountEntity((net.minecraft.entity.Entity) e);
        }
        if (Boolean.getBoolean("cw.b4.walkers") && bothTicks % 10 == 0) for (Object o : MinecraftServer.getServer().worldServers[0].loadedEntityList) if (o instanceof net.minecraft.entity.monster.EntityEnderman) {
            net.minecraft.entity.monster.EntityEnderman en = (net.minecraft.entity.monster.EntityEnderman) o;
            // Its own AI teleports it about (here it landed below the floor): bring it back and keep it after the player.
            if (en.posY < 60 || en.getEntityToAttack() == null) { if (en.posY < 60) en.setPosition(9, 64, -16); for (Object q : MinecraftServer.getServer().getConfigurationManager().playerEntityList) if (((EntityPlayerMP) q).getCommandSenderName().equals("P0A")) en.setTarget((EntityPlayerMP) q); }
            System.out.println("B4_ENDERMAN_SERVER tick=" + bothTicks + " pos=" + en.posX + "," + en.posY + "," + en.posZ + " dead=" + en.isDead + " target=" + en.getEntityToAttack());
        }
        if (Boolean.getBoolean("cw.b6.sleep") && (bothTicks == 40 || bothTicks == 60)) for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (p.getCommandSenderName().equals("P0B") && bothTicks == 40) p.playerNetServerHandler.setPlayerLocation(5.5, 64, -5.5, 0, 0);
            else if (p.getCommandSenderName().equals("P0B")) System.out.println("B6_SLEEP result=" + p.sleepInBedAt(5, 64, -4) + " sleeping=" + p.isPlayerSleeping() + " pos=" + p.posX + "," + p.posY + "," + p.posZ);
        }
        if(Boolean.getBoolean("cw.p0.correctionTest")) for(Object o:MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p=(EntityPlayerMP)o;if(!p.getCommandSenderName().equals("P0A"))continue;
            if(bothTicks==100) {p.inventory.currentItem=1;p.playerNetServerHandler.setPlayerLocation(p.posX,p.posY,p.posZ,45,20);System.out.println("P0_CORRECTION unmarked yaw=45 pitch=20");}
            if(bothTicks==140) {Clashweave.network.sendTo(new com.layue13.clashweave.network.CorrectionMessage(1,p.posX,p.posY+1.6200000047683716D,p.posZ),p);p.playerNetServerHandler.setPlayerLocation(p.posX,p.posY,p.posZ,-90,-30);System.out.println("P0_CORRECTION marked emptyHand=true yaw=-90 pitch=-30");}
            if(bothTicks==180) Clashweave.network.sendTo(new com.layue13.clashweave.network.CorrectionMessage(2,p.posX,p.posY+1.6200000047683716D,p.posZ),p);
            if(bothTicks==200) {p.playerNetServerHandler.setPlayerLocation(p.posX,p.posY,p.posZ,120,-10);System.out.println("P0_CORRECTION expired yaw=120 pitch=-10");}
        }
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
            if (!Boolean.getBoolean("cw.p0.lockSupport") && !Boolean.getBoolean("cw.p0.composition") && !Boolean.getBoolean("cw.p0.fastTurn") && !Boolean.getBoolean("cw.p0.correctionTest") && !Boolean.getBoolean("cw.p0.viewTest") && !Boolean.getBoolean("cw.p0.lockTest") && !Boolean.getBoolean("cw.p0.sweepTest") && !Boolean.getBoolean("cw.p0.supplement") && !Boolean.getBoolean("cw.p0.manual") && !Boolean.getBoolean("cw.p0.engagement") && state != null && state.scheduler.current() == null && prepare) {
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
        if (!Boolean.getBoolean("cw.p0.lockSupport") && !Boolean.getBoolean("cw.p0.composition") && bothTicks == (Boolean.getBoolean("cw.p0.edgeBoundary") ? 2400 : Boolean.getBoolean("cw.p0.correctionTest") ? 260 : Boolean.getBoolean("cw.p0.viewTest") ? 1800 : 1000) && !Boolean.getBoolean("cw.p0.manual")) {
            System.out.println("P0_NETWORK COMPLETE");
            MinecraftServer.getServer().initiateShutdown();
        }
    }
}
