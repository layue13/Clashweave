package com.layue13.clashweave.validation;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.core.Scheduler;
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

    /** A human who dies in the play session comes back on the arena with a katana, not under the floor with nothing. */
    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!Boolean.getBoolean("cw.p2.play")) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        player.inventory.setInventorySlotContents(0, new ItemStack(Clashweave.katana));
        player.inventory.currentItem = 0;
        player.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(200);
        player.setHealth(200);
        player.playerNetServerHandler.setPlayerLocation(0, 64, 0, 0, 0);
    }

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
            zombie.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue((Boolean.getBoolean("cw.p0.manual") && !Boolean.getBoolean("cw.p1b.chase")) ? 20 : 200);
            zombie.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue((Boolean.getBoolean("cw.p0.manual") && !Boolean.getBoolean("cw.p1b.chase")) ? .23 : 0);
            zombie.setHealth((Boolean.getBoolean("cw.p0.manual") && !Boolean.getBoolean("cw.p1b.chase")) ? 20 : 200);
            // P1-b: ordinary hits would shove the target out of reach before the chain starts; the lift is not knockback.
            if (Boolean.getBoolean("cw.p1b.chase")) zombie.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1.0);
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

    private boolean dodgeWall;
    private long dodgeIdle;

    /**
     * P2-d: a wall two blocks behind P0A, a zombie that does not strike by itself 2.4 blocks in front, and blows timed
     * against the step the player really takes: 2 ticks in (inside the protected span) and 8 ticks in (outside it).
     * P0A is put back where it started once a step is over.
     */
    private void dodgeRig() {
        EntityPlayerMP a = null;
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (p.getCommandSenderName().equals("P0A")) a = p;
        }
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (a != null && p != a && p.getDistanceSqToEntity(a) < 400) p.playerNetServerHandler.setPlayerLocation(25, 64, 25, 0, 0);
        }
        if (bothTicks % 100 == 0) System.out.println("P2_DODGE_RIG tick=" + bothTicks + " a=" + (a != null) + " state=" + (a != null && Clashweave.server.state(a) != null) + " pos=" + (a == null ? "" : a.posX + "," + a.posZ));
        if (a == null || Clashweave.server.state(a) == null) return;
        WorldServer world = a.getServerForPlayer();
        if (!dodgeWall) {
            dodgeWall = true;
            for (int x = -4; x <= 4; x++) for (int y = 64; y <= 66; y++) world.setBlock(x, y, -2, Blocks.stone);
        }
        a.setHealth(a.getMaxHealth());
        EntityZombie zombie = null;
        for (Object e : a.worldObj.loadedEntityList) if (e instanceof EntityZombie) { zombie = (EntityZombie) e; break; }
        if (zombie == null) return;
        zombie.setHealth(zombie.getMaxHealth());
        zombie.getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(0);
        zombie.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);
        zombie.setAttackTarget(null);
        zombie.setPosition(0, 64, 2.4);
        zombie.motionX = zombie.motionZ = 0;
        Scheduler.Instance step = Clashweave.server.state(a).scheduler.current();
        if (step != null && step.definition.id.endsWith("step")) {
            dodgeIdle = bothTicks;
            long age = Clashweave.server.tick - step.start;
            if (age == 2 || age == 8) {
                a.hurtResistantTime = 0;
                float before = a.getHealth();
                boolean applied = a.attackEntityFrom(net.minecraft.util.DamageSource.causeMobDamage(zombie), 3f);
                System.out.println("P2_DODGE_BLOW age=" + age + " applied=" + applied + " hp=" + before + "->" + a.getHealth());
            }
            if (age == 1 || age == 7) System.out.println("P2_DODGE_POS age=" + age + " x=" + a.posX + " z=" + a.posZ);
        } else if (bothTicks - dodgeIdle > 50 && (Math.abs(a.posX) > .05 || Math.abs(a.posZ) > .05)) {
            a.playerNetServerHandler.setPlayerLocation(0, 64, 0, 0, 0);
        }
    }

    /** For a human at the keyboard: keeps a few zombies walking at P0A, day or night, and keeps P0A fed. */
    private void playRig() {
        EntityPlayerMP a = null;
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (p.getCommandSenderName().equals("P0A")) a = p;
        }
        if (a == null || Clashweave.server.state(a) == null) return;
        if (bothTicks % 100 == 0) a.getFoodStats().addStats(20, 1f);
        a.getServerForPlayer().getGameRules().setOrCreateGameRule("keepInventory", "true");
        if (a.posY < 62 && !a.capabilities.isFlying) a.playerNetServerHandler.setPlayerLocation(0, 64, 0, a.rotationYaw, 0);
        WorldServer w = a.getServerForPlayer();
        int alive = 0;
        for (Object e : w.loadedEntityList) if (e instanceof EntityZombie && !((EntityZombie) e).isDead) {
            alive++;
            try { java.lang.reflect.Field fire = net.minecraft.entity.Entity.class.getDeclaredField("isImmuneToFire"); fire.setAccessible(true); fire.setBoolean(e, true); ((EntityZombie) e).extinguish(); } catch (Exception ex) { /* burns in daylight */ }
        }
        // The arena is a stone floor at y 63 over a flat world: a zombie that wanders off it falls and dies, so bring it back.
        for (Object e : w.loadedEntityList) if (e instanceof EntityZombie && !((EntityZombie) e).isDead) {
            EntityZombie z = (EntityZombie) e;
            if (z.posY < 62.5 || Math.abs(z.posX) > 26 || Math.abs(z.posZ) > 46) {
                double angle = w.rand.nextDouble() * Math.PI * 2;
                z.setPosition(Math.max(-20, Math.min(20, a.posX)) + Math.cos(angle) * 6, 64, Math.max(-40, Math.min(40, a.posZ)) + Math.sin(angle) * 6);
                z.motionX = z.motionY = z.motionZ = 0;
                z.fallDistance = 0;
            }
        }
        if (bothTicks % 60 == 0 && alive < 3) {
            EntityZombie z = new EntityZombie(w);
            double angle = w.rand.nextDouble() * Math.PI * 2;
            z.setPosition(Math.max(-24, Math.min(24, a.posX + Math.cos(angle) * 8)), 64, Math.max(-44, Math.min(44, a.posZ + Math.sin(angle) * 8)));
            w.spawnEntityInWorld(z);
        }
    }

    private int swarmSpawned, swarmRescues;

    /**
     * P2 close-out: four zombies with their real AI hunt P0A for the whole run; killed ones are replaced. P0A keeps its
     * real health until it would die, then is topped up (counted), so the run measures the fight rather than ending it.
     */
    private void swarmRig() {
        EntityPlayerMP a = null;
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (p.getCommandSenderName().equals("P0A")) a = p;
        }
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (a != null && p != a && p.getDistanceSqToEntity(a) < 1600) p.playerNetServerHandler.setPlayerLocation(-28, 64, -48, 0, 0);
        }
        if (a == null || Clashweave.server.state(a) == null) return;
        if (a.getHealth() < 40) {
            a.setHealth(a.getMaxHealth());
            swarmRescues++;
            System.out.println("P2_SWARM_RESCUE tick=" + bothTicks + " n=" + swarmRescues);
        }
        WorldServer w = a.getServerForPlayer();
        int alive = 0;
        for (Object e : w.loadedEntityList) if (e instanceof EntityZombie && !((EntityZombie) e).isDead) {
            EntityZombie z = (EntityZombie) e;
            alive++;
            z.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(.23);
            try { java.lang.reflect.Field fire = net.minecraft.entity.Entity.class.getDeclaredField("isImmuneToFire"); fire.setAccessible(true); fire.setBoolean(z, true); z.extinguish(); } catch (Exception ex) { /* burns in daylight */ }
            if (z.getAttackTarget() == null) z.setAttackTarget(a);
        }
        if (bothTicks % 20 == 0) while (alive < 4) {
            EntityZombie z = new EntityZombie(w);
            double angle = (swarmSpawned % 4) * Math.PI / 2 + Math.PI / 4;
            z.setPosition(a.posX + Math.cos(angle) * 5, 64, a.posZ + Math.sin(angle) * 5);
            z.setAttackTarget(a);
            w.spawnEntityInWorld(z);
            swarmSpawned++;
            alive++;
        }
        if (bothTicks % 100 == 0) System.out.println("P2_SWARM tick=" + bothTicks + " hp=" + a.getHealth() + " zombies=" + alive + " spawned=" + swarmSpawned + " rescues=" + swarmRescues + " pos=" + a.posX + "," + a.posZ);
    }

    private long pvpHold;

    /** P2 close-out: both players are the fighters; the zombie is removed, they are kept facing within reach, health topped up now and then. */
    private void pvpRig() {
        EntityPlayerMP a = null, b = null;
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (p.getCommandSenderName().equals("P0A")) a = p;
            else if (p.getCommandSenderName().equals("P0B")) b = p;
        }
        if (a == null || b == null || Clashweave.server.state(a) == null || Clashweave.server.state(b) == null) return;
        for (Object e : a.worldObj.loadedEntityList) if (e instanceof EntityZombie) ((EntityZombie) e).setDead();
        if (bothTicks % 40 == 0 && (a.getHealth() < 60 || b.getHealth() < 60)) {
            a.setHealth(a.getMaxHealth());
            b.setHealth(b.getMaxHealth());
            System.out.println("P2_PVP_TOPUP tick=" + bothTicks);
        }
        double distance = a.getDistanceToEntity(b);
        boolean quiet = Clashweave.server.state(a).scheduler.current() == null && Clashweave.server.state(b).scheduler.current() == null;
        if (bothTicks - pvpHold > 6 && (distance > 4 || distance < 1) && quiet) {
            pvpHold = bothTicks;
            a.playerNetServerHandler.setPlayerLocation(0, 64, 0, 0, 0);
            b.playerNetServerHandler.setPlayerLocation(0, 64, 2.2, 180, 0);
        }
        if (bothTicks % 100 == 0) System.out.println("P2_PVP tick=" + bothTicks + " hpA=" + a.getHealth() + " hpB=" + b.getHealth() + " dist=" + distance);
    }

    private boolean guardHeld;
    private long guardSince = -1, guardReleased = -1;

    /**
     * P2: the zombie stands still in front of where P0A looks and does not strike by itself; the blows come from here,
     * timed against the guard the player really holds: 3 ticks after a press (precise), 15 ticks after (plain), and
     * 12 ticks after a release (no guard).
     */
    private void guardRig() {
        EntityPlayerMP a = null;
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (p.getCommandSenderName().equals("P0A")) a = p;
        }
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (a != null && p != a && p.getDistanceSqToEntity(a) < 400) p.playerNetServerHandler.setPlayerLocation(25, 64, 25, 0, 0);
        }
        if (a == null || Clashweave.server.state(a) == null) return;
        a.setHealth(a.getMaxHealth());
        a.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1.0);
        EntityZombie zombie = null;
        for (Object e : a.worldObj.loadedEntityList) if (e instanceof EntityZombie) { zombie = (EntityZombie) e; break; }
        if (zombie == null) return;
        zombie.setHealth(zombie.getMaxHealth());
        zombie.getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(0);
        zombie.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);
        double yaw = Math.toRadians(a.rotationYaw);
        zombie.setAttackTarget(null);
        if (bothTicks % 5 == 0 || zombie.getDistanceSqToEntity(a) > 16) {
            zombie.setPosition(a.posX - Math.sin(yaw) * 2.4, a.posY, a.posZ + Math.cos(yaw) * 2.4);
            zombie.motionX = zombie.motionZ = 0;
        }
        boolean held = Clashweave.server.state(a).guard.held();
        if (held && !guardHeld) guardSince = bothTicks;
        if (!held && guardHeld) guardReleased = bothTicks;
        guardHeld = held;
        long sincePress = held ? bothTicks - guardSince : -1, sinceRelease = !held && guardReleased >= 0 ? bothTicks - guardReleased : -1;
        String kind = held && sincePress == 3 ? "PRECISE" : held && sincePress == 15 ? "PLAIN" : sinceRelease == 12 ? "FREE" : null;
        if (kind != null) {
            zombie.hurtTime = 0;
            a.hurtResistantTime = 0;
            double before = zombie.getDistanceSqToEntity(a);
            boolean applied = a.attackEntityFrom(net.minecraft.util.DamageSource.causeMobDamage(zombie), 3f);
            System.out.println("P2_BLOW tick=" + bothTicks + " kind=" + kind + " applied=" + applied + " zombieBefore=" + zombie.posX + "," + zombie.posZ + " motion=" + zombie.motionX + "," + zombie.motionZ + " distSqBefore=" + before + " me=" + a.posX + "," + a.posZ + " yaw=" + a.rotationYaw + " counterOpen=" + Clashweave.server.state(a).scheduler.counter(Clashweave.server.tick) + " hp=" + a.getHealth());
        }
    }

    private long chaseBusy;

    /** P1-b: puts the still zombie (full health) back in front of P0A whenever the chain is over and everything is quiet. */
    private boolean judgeKilled;
    private int judgeCastTick = -1;

    private void chaseTarget() {
        EntityPlayerMP a = null;
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList)
            if (((EntityPlayerMP) o).getCommandSenderName().equals("P0A")) a = (EntityPlayerMP) o;
        if (a == null || Clashweave.server.state(a) == null) return;
        // The zombie would otherwise shove the attacker back out of the launcher's reach.
        a.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1.0);
        a.setHealth(a.getMaxHealth());
        a.getFoodStats().addStats(20, 1f);
        // The second player stands beside the first by default and would take the hits: keep it out of the way.
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP other = (EntityPlayerMP) o;
            if (!other.getCommandSenderName().equals("P0A") && other.getDistanceSqToEntity(a) < 400)
                other.playerNetServerHandler.setPlayerLocation(25, 64, 25, 0, 0);
        }
        EntityZombie zombie = null;
        for (Object e : a.worldObj.loadedEntityList) if (e instanceof EntityZombie) { zombie = (EntityZombie) e; break; }
        if (zombie == null) return;
        if (Boolean.getBoolean("cw.p2.judgeKill") && !judgeKilled) {
            // The caster dies 20 ticks into a judgement cut that holds the zombie: nothing may land and the zombie is released.
            Scheduler.Instance cast = Clashweave.server.state(a).scheduler.current();
            if (cast != null && cast.definition.id.endsWith(":judgement") && bothTicks > 0 && cast.definition.release > 20) {
                if (judgeCastTick < 0) judgeCastTick = bothTicks;
                if (bothTicks - judgeCastTick >= 20) { judgeKilled = true; a.attackEntityFrom(net.minecraft.util.DamageSource.outOfWorld, 1000f); System.out.println("JUDGE_KILL tick=" + bothTicks); }
            }
        }
        if (Boolean.getBoolean("cw.p1b.noAttack")) { zombie.getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(0); zombie.setAttackTarget(null); zombie.tasks.taskEntries.clear(); zombie.targetTasks.taskEntries.clear(); }
        try { java.lang.reflect.Field fire = net.minecraft.entity.Entity.class.getDeclaredField("isImmuneToFire"); fire.setAccessible(true); fire.setBoolean(zombie, true); zombie.extinguish(); } catch (Exception e) { /* daylight burning stays */ }
        if (Clashweave.server.state(a).scheduler.current() != null || !zombie.onGround) chaseBusy = bothTicks;
        if (bothTicks % 10 == 0) System.out.println("P1B_STATE tick=" + bothTicks + " zombie=" + zombie.getEntityId() + "@" + zombie.posX + "," + zombie.posY + "," + zombie.posZ + " hp=" + zombie.getHealth() + " player=" + a.posX + "," + a.posZ + " yaw=" + a.rotationYaw + " busy=" + (bothTicks - chaseBusy));
        if (bothTicks - chaseBusy < 40 || bothTicks % 5 != 0) return;
        zombie.setHealth(zombie.getMaxHealth());
        zombie.setPosition(0, 64, 1.7);
        zombie.motionX = zombie.motionY = zombie.motionZ = 0;
        zombie.hurtTime = 0;
        a.playerNetServerHandler.setPlayerLocation(0, 64, 0, 0, 0);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("cw.p0.network") || event.phase != TickEvent.Phase.END) return;
        if (Boolean.getBoolean("cw.p2.play")) { bothTicks++; playRig(); return; }
        if (MinecraftServer.getServer().getCurrentPlayerCount() < 2) return;
        bothTicks++;
        if (Boolean.getBoolean("cw.p1b.chase")) chaseTarget();
        if (Boolean.getBoolean("cw.p2.guard")) guardRig();
        if (Boolean.getBoolean("cw.p2.swarm")) swarmRig();
        if (Boolean.getBoolean("cw.p2.pvp")) pvpRig();
        if (Boolean.getBoolean("cw.p2.dodge")) dodgeRig();
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
