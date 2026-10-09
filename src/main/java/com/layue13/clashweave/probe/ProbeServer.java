package com.layue13.clashweave.probe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class ProbeServer {

    private static final ConcurrentLinkedQueue<Arrival> INPUT = new ConcurrentLinkedQueue<Arrival>();
    private final Map<Integer, Move> moves = new HashMap<Integer, Move>();
    private final Map<Integer, Long> guardPresses = new HashMap<Integer, Long>();
    private final Map<Integer, Integer> guardSequences = new HashMap<Integer, Integer>();
    private EntityCow[] cows;
    private ProbeDamage[] damage;
    private long damageStart;
    private long tick;
    private boolean autoStarted;
    private boolean cancelProbeHit;
    private EntityPlayerMP attacker;
    private EntityPlayerMP defender;
    private int guardTrial;
    private long impactTick;
    private int guardSuccess;
    private int guardTotal;
    private net.minecraft.entity.monster.EntityZombie threatFixture;
    private EntityPlayerMP threatPlayer;
    private long threatEnd;

    public static void log(String message) {
        System.out.println("CWPROBE " + message);
    }

    public static class Inbound implements IMessageHandler<ProbePacket, IMessage> {

        @Override
        public IMessage onMessage(ProbePacket packet, MessageContext context) {
            INPUT.add(new Arrival(context.getServerHandler().playerEntity, packet));
            return null;
        }
    }

    private static class Arrival {

        final EntityPlayerMP player;
        final ProbePacket packet;

        Arrival(EntityPlayerMP player, ProbePacket packet) {
            this.player = player;
            this.packet = packet;
        }
    }

    private static class Move {

        long id;
        long end;
        double x;
        double z;
        double startX;
        double speed;
        int sample;
        int valid;
        int rejects;
        String lane;
    }

    @SubscribeEvent
    public void login(PlayerLoggedInEvent e) {
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        p.inventory.mainInventory[0] = new ItemStack(ProbeBootstrap.blade);
        p.inventory.currentItem = 0;
        p.getEntityAttribute(SharedMonsterAttributes.maxHealth)
            .setBaseValue(200);
        p.setHealth(200);
        log("LOGIN name=" + p.getCommandSenderName() + " side=DEDICATED_OR_INTEGRATED");
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        WorldServer w = MinecraftServer.getServer()
            .worldServerForDimension(0);
        tick = w.getTotalWorldTime();
        if (!autoStarted && Boolean.getBoolean("clashweave.probe.autoServer")) {
            autoStarted = true;
            startDamage();
        }
        Arrival a;
        while ((a = INPUT.poll()) != null) {
            if (a.player.isDead || a.player.worldObj != w) continue;
            ProbePacket m = a.packet;
            if (!Double.isFinite(m.x) || !Double.isFinite(m.z)) continue;
            if (m.kind == 1) startMove(
                a.player,
                m.sequence % 3 == 1 ? "wall" : m.sequence % 3 == 2 ? "invalid" : "open",
                m.sequence);
            if (m.kind == 3) validateMove(a.player, m);
            if (m.kind == 4) {
                int last = guardSequences.containsKey(a.player.getEntityId())
                    ? guardSequences.get(a.player.getEntityId())
                    : -1;
                long age = tick - m.tick;
                boolean accept = m.sequence > last && age >= 0 && age <= ProbeConfig.rewindLimit;
                if (accept) {
                    guardSequences.put(a.player.getEntityId(), m.sequence);
                    guardPresses.put(a.player.getEntityId(), m.tick);
                }
                log(
                    "S3_INPUT name=" + a.player.getCommandSenderName()
                        + " seq="
                        + m.sequence
                        + " stamp="
                        + m.tick
                        + " arrival="
                        + tick
                        + " age="
                        + age
                        + " accepted="
                        + accept);
            }
            if (m.kind == 6) swing(a.player);
            if (m.kind == 7) startGuard(a.player);
            if (m.kind == 14) {
                a.player.motionX = 0.4;
                a.player.motionY = 0.25;
                a.player.motionZ = 0;
                a.player.velocityChanged = true;
                log("S2_VELOCITY_SENT x=0.4 y=0.25 origin=SERVER");
            }
            if (m.kind == 15) inputFixtures(a.player);
            if (m.kind == 8) {
                a.player.getEntityData()
                    .setBoolean("cwEngage", m.sequence == 1);
                ProbeBootstrap.channel.sendTo(new ProbePacket(5, m.sequence, tick, 0, 0), a.player);
            }
        }
        if (cows != null) damageTick();
        if (threatFixture != null) {
            threatFixture.setAttackTarget(threatPlayer);
            if (tick >= threatEnd) {
                threatFixture.setDead();
                threatFixture = null;
            }
        }
        for (EntityPlayerMP p : players()) {
            Move move = moves.get(p.getEntityId());
            if (move != null && tick >= move.end) {
                log(
                    "S2_END name=" + p.getCommandSenderName()
                        + " lane="
                        + move.lane
                        + " speed="
                        + move.speed
                        + " valid="
                        + move.valid
                        + " rejects="
                        + move.rejects
                        + " dx="
                        + (p.posX - move.startX)
                        + " x="
                        + p.posX
                        + " expectedX="
                        + move.x);
                ProbeBootstrap.channel.sendTo(new ProbePacket(9, move.rejects, tick, p.posX, move.x), p);
                moves.remove(p.getEntityId());
            }
        }
        if (defender != null && tick == impactTick) resolveGuard();
        if (defender != null && tick == impactTick + 10) {
            guardTrial++;
            if (guardTrial < 20) scheduleGuard();
            else {
                log(
                    "S3_SUMMARY success=" + guardSuccess
                        + " total="
                        + guardTotal
                        + " window="
                        + ProbeConfig.guardWindow
                        + " cap="
                        + ProbeConfig.rewindLimit
                        + " worldRollback=false lateUndo=false");
                ProbeBootstrap.channel.sendTo(new ProbePacket(10, guardSuccess, tick, guardTotal, 0), attacker);
                defender = null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<EntityPlayerMP> players() {
        return MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList;
    }

    public void startDamage() {
        WorldServer w = MinecraftServer.getServer()
            .worldServerForDimension(0);
        cows = new EntityCow[7];
        damage = new ProbeDamage[7];
        for (int i = 0; i < cows.length; i++) {
            cows[i] = new EntityCow(w);
            cows[i].getEntityAttribute(SharedMonsterAttributes.maxHealth)
                .setBaseValue(100);
            cows[i].setHealth(100);
            cows[i].setPosition(0, 100, 0);
            damage[i] = new ProbeDamage();
        }
        damageStart = w.getTotalWorldTime();
        log("S1_START interval=" + ProbeConfig.segmentTicks + " unspawnedEntities=true actualVanillaMethods=true");
    }

    private void damageTick() {
        int age = (int) (tick - damageStart);
        // Exercise the real vanilla entity tick, without contaminating the test world with AI entities.
        if (age > 0) for (EntityCow cow : cows) cow.onEntityUpdate();
        int interval = ProbeConfig.segmentTicks;
        if (age == 0 || age == interval || age == interval * 2) {
            int segment = age / interval;
            boolean v = cows[0].attackEntityFrom(DamageSource.generic, 2);
            boolean n = damage[1].hit(1, cows[1], segment, 2, false);
            boolean i = damage[2].hit(1, cows[2], segment, 2, true);
            boolean duplicate = damage[2].hit(1, cows[2], segment, 2, true);
            log(
                "S1_SEGMENT tick=" + age
                    + " vanilla="
                    + v
                    + " naive="
                    + n
                    + " isolated="
                    + i
                    + " duplicate="
                    + duplicate
                    + " hp="
                    + cows[0].getHealth()
                    + ","
                    + cows[1].getHealth()
                    + ","
                    + cows[2].getHealth());
        }
        if (age == 0) for (int i = 3; i <= 5; i++) cows[i].attackEntityFrom(DamageSource.generic, 4);
        if (age == interval) {
            damage[4].hit(2, cows[4], 0, 2, false);
            damage[5].hit(2, cows[5], 0, 2, true);
        }
        if (age == interval + 1) {
            boolean control = cows[3].attackEntityFrom(DamageSource.generic, 4);
            boolean naive = cows[4].attackEntityFrom(DamageSource.generic, 4);
            boolean isolated = cows[5].attackEntityFrom(DamageSource.generic, 4);
            log(
                "S1_OTHER tick=" + age
                    + " equalVanillaControl="
                    + control
                    + " naive="
                    + naive
                    + " isolated="
                    + isolated
                    + " hp="
                    + cows[3].getHealth()
                    + ","
                    + cows[4].getHealth()
                    + ","
                    + cows[5].getHealth());
            boolean strongerControl = cows[3].attackEntityFrom(DamageSource.generic, 6);
            boolean strongerIsolated = cows[5].attackEntityFrom(DamageSource.generic, 6);
            log(
                "S1_STRONGER control=" + strongerControl
                    + " isolated="
                    + strongerIsolated
                    + " hp="
                    + cows[3].getHealth()
                    + ","
                    + cows[5].getHealth());
            cows[6].hurtResistantTime = 13;
            cancelProbeHit = true;
            boolean canceled = damage[6].hit(3, cows[6], 0, 2, true);
            cancelProbeHit = false;
            log(
                "S1_CANCEL accepted=" + canceled
                    + " hp="
                    + cows[6].getHealth()
                    + " timer="
                    + cows[6].hurtResistantTime);
        }
        if (age == interval * 2 + 1) {
            log(
                "S1_SUMMARY vanillaHp=" + cows[0]
                    .getHealth() + " naiveHp=" + cows[1].getHealth() + " isolatedHp=" + cows[2].getHealth());
            cows = null;
        }
    }

    public void startMove(EntityPlayerMP p, String lane, int sequence) {
        if (moves.containsKey(p.getEntityId())) return;
        WorldServer w = (WorldServer) p.worldObj;
        double z = "wall".equals(lane) ? 4.5 : 0.5;
        for (int x = -2; x < 16; x++) for (int bz = -2; bz < 8; bz++) {
            w.setBlock(x, 64, bz, Blocks.stone);
            for (int y = 65; y < 69; y++) w.setBlock(x, y, bz, Blocks.air);
        }
        for (int y = 65; y < 69; y++) for (int bz = 3; bz <= 6; bz++) w.setBlock(3, y, bz, Blocks.stone);
        p.playerNetServerHandler.setPlayerLocation(0.5, 65, z, -90, 0);
        p.motionX = p.motionY = p.motionZ = 0;
        Move move = new Move();
        move.id = tick;
        move.end = tick + 40;
        move.x = move.startX = 0.5;
        move.z = z;
        move.speed = ProbeConfig.stepSpeed * (sequence % 2 == 0 ? 1 : 2);
        move.lane = lane;
        moves.put(p.getEntityId(), move);
        ProbeBootstrap.channel.sendTo(new ProbePacket(2, sequence, tick, move.speed, z), p);
        log("S2_START name=" + p.getCommandSenderName() + " lane=" + lane + " speed=" + move.speed + " id=" + move.id);
    }

    private void validateMove(EntityPlayerMP p, ProbePacket m) {
        Move move = moves.get(p.getEntityId());
        if (move == null) return;
        double dx = m.x - move.x;
        double dz = m.z - move.z;
        boolean valid = m.tick == move.id && m.sequence == move.sample + 1
            && m.sequence <= ProbeConfig.stepTicks
            && Math.hypot(dx, dz) <= move.speed + 0.001;
        if (valid) {
            AxisAlignedBB box = AxisAlignedBB
                .getBoundingBox(move.x - 0.3, 65, move.z - 0.3, move.x + 0.3, 66.8, move.z + 0.3);
            @SuppressWarnings("unchecked")
            List<AxisAlignedBB> obstacles = p.worldObj.getCollidingBoundingBoxes(p, box.addCoord(dx, 0, dz));
            for (AxisAlignedBB obstacle : obstacles) dx = obstacle.calculateXOffset(box, dx);
            box.offset(dx, 0, 0);
            for (AxisAlignedBB obstacle : obstacles) dz = obstacle.calculateZOffset(box, dz);
            valid = Math.abs(move.x + dx - m.x) < 0.001 && Math.abs(move.z + dz - m.z) < 0.001;
        }
        if (valid) {
            move.sample = m.sequence;
            move.x = m.x;
            move.z = m.z;
            move.valid++;
        } else {
            move.rejects++;
            p.playerNetServerHandler.setPlayerLocation(move.x, 65, move.z, -90, 0);
        }
        log(
            "S2_SAMPLE name=" + p
                .getCommandSenderName() + " sample=" + m.sequence + " valid=" + valid + " x=" + m.x + " z=" + m.z);
    }

    public void startGuard(EntityPlayerMP p) {
        if (defender != null) return;
        for (EntityPlayerMP other : players()) if (other != p) {
            attacker = p;
            defender = other;
            defender.playerNetServerHandler.setPlayerLocation(p.posX + 1, 65, p.posZ, 90, 0);
            guardTrial = guardSuccess = guardTotal = 0;
            scheduleGuard();
            return;
        }
        log("S3_BLOCKED requiresTwoPlayers=true");
    }

    private void scheduleGuard() {
        guardPresses.remove(defender.getEntityId());
        impactTick = tick + 20;
        ProbeBootstrap.channel.sendTo(new ProbePacket(11, guardTrial, tick, impactTick, guardTrial % 5), defender);
    }

    private void resolveGuard() {
        Long press = guardPresses.get(defender.getEntityId());
        boolean parry = press != null && tick >= press && tick - press < ProbeConfig.guardWindow;
        float before = defender.getHealth();
        if (!parry) new ProbeDamage(attacker).hit(guardTrial, defender, 0, 1, true);
        guardTotal++;
        if (parry) guardSuccess++;
        log(
            "S3_IMPACT trial=" + guardTrial
                + " lead="
                + (guardTrial % 5)
                + " tick="
                + tick
                + " press="
                + press
                + " parry="
                + parry
                + " damage="
                + (before - defender.getHealth()));
    }

    public void swing(EntityPlayerMP p) {
        ProbeBootstrap.channel.sendToAll(new ProbePacket(6, p.getEntityId(), tick, ProbeConfig.swingTicks, 0));
    }

    private boolean held(EntityPlayer p) {
        return p.getHeldItem() != null && p.getHeldItem()
            .getItem() == ProbeBootstrap.blade;
    }

    @SubscribeEvent
    public void attack(AttackEntityEvent e) {
        if (held(e.entityPlayer)) e.setCanceled(true);
    }

    @SubscribeEvent
    public void interact(EntityInteractEvent e) {
        if (held(e.entityPlayer)) e.setCanceled(true);
    }

    @SubscribeEvent
    public void block(PlayerInteractEvent e) {
        if (!held(e.entityPlayer)) return;
        if (e.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK
            || e.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR
            || e.entityPlayer.getEntityData()
                .getBoolean("cwEngage")
            || !ProbeConfig.allowBlocks
            || !ProbeInteraction.interactive(e.world, e.x, e.y, e.z)) e.setCanceled(true);
    }

    @SubscribeEvent
    public void damage(LivingAttackEvent e) {
        if (e.source == ProbeDamage.SOURCE && cancelProbeHit) e.setCanceled(true);
        if (!"clashweave.probe".equals(e.source.damageType) || !ProbeConfig.protectFriendly
            || !(e.source.getEntity() instanceof EntityPlayer)) return;
        EntityPlayer p = (EntityPlayer) e.source.getEntity();
        EntityLivingBase target = e.entityLiving;
        if (held(p) && (target instanceof EntityVillager
            || (target instanceof EntityTameable && ((EntityTameable) target).isTamed())
            || p.isOnSameTeam(target))) e.setCanceled(true);
    }

    private void inputFixtures(EntityPlayerMP p) {
        WorldServer w = (WorldServer) p.worldObj;
        w.setBlock(12, 64, 0, Blocks.stone);
        w.setBlock(12, 65, 0, Blocks.wooden_door, 0, 3);
        w.setBlock(12, 66, 0, Blocks.wooden_door, 8, 3);
        w.setBlock(13, 65, 0, Blocks.chest);
        boolean oldBlocks = ProbeConfig.allowBlocks;
        boolean oldProtect = ProbeConfig.protectFriendly;
        boolean oldEngage = p.getEntityData()
            .getBoolean("cwEngage");
        try {
            ProbeConfig.allowBlocks = true;
            p.getEntityData()
                .setBoolean("cwEngage", false);
            boolean door = p.theItemInWorldManager
                .activateBlockOrUseItem(p, w, p.getHeldItem(), 12, 65, 0, 1, 0.5F, 0.5F, 0.5F);
            int doorMeta = w.getBlockMetadata(12, 65, 0);
            p.getEntityData()
                .setBoolean("cwEngage", true);
            boolean engagedDoor = p.theItemInWorldManager
                .activateBlockOrUseItem(p, w, p.getHeldItem(), 12, 65, 0, 1, 0.5F, 0.5F, 0.5F);
            boolean noToggle = w.getBlockMetadata(12, 65, 0) == doorMeta;
            boolean engagedChest = p.theItemInWorldManager
                .activateBlockOrUseItem(p, w, p.getHeldItem(), 13, 65, 0, 1, 0.5F, 0.5F, 0.5F);
            p.getEntityData()
                .setBoolean("cwEngage", false);
            boolean chest = p.theItemInWorldManager
                .activateBlockOrUseItem(p, w, p.getHeldItem(), 13, 65, 0, 1, 0.5F, 0.5F, 0.5F);
            boolean opened = p.openContainer instanceof net.minecraft.inventory.ContainerChest;
            p.closeScreen();
            ProbeConfig.allowBlocks = false;
            boolean configClosed = !p.theItemInWorldManager
                .activateBlockOrUseItem(p, w, p.getHeldItem(), 13, 65, 0, 1, 0.5F, 0.5F, 0.5F);
            EntityVillager villager = new EntityVillager(w);
            boolean interact = p.interactWith(villager);
            ProbeConfig.protectFriendly = true;
            float before = villager.getHealth();
            new ProbeDamage(p).hit(100, villager, 0, 2, true);
            float protectedLoss = before - villager.getHealth();
            ProbeConfig.protectFriendly = false;
            new ProbeDamage(p).hit(101, villager, 0, 2, true);
            float unprotectedLoss = before - villager.getHealth();
            boolean left = forgeEventLeft(p, w);
            boolean pass = door && (doorMeta & 4) != 0
                && !engagedDoor
                && noToggle
                && !engagedChest
                && chest
                && opened
                && configClosed
                && !interact
                && protectedLoss == 0
                && unprotectedLoss == 2
                && left;
            log(
                "S5_WORLD_FIXTURES peacefulDoor=" + door
                    + " engagedDoor="
                    + engagedDoor
                    + " noToggle="
                    + noToggle
                    + " peacefulChest="
                    + opened
                    + " engagedChest="
                    + engagedChest
                    + " configClosed="
                    + configClosed
                    + " villagerInteract="
                    + interact
                    + " protectedLoss="
                    + protectedLoss
                    + " unprotectedLoss="
                    + unprotectedLoss
                    + " leftBlocked="
                    + left
                    + " passed="
                    + pass);
            if (!pass) throw new IllegalStateException("S5 world fixture failure");
            if (Boolean.getBoolean("clashweave.probe.negative")) {
                threatFixture = new net.minecraft.entity.monster.EntityZombie(w);
                threatFixture.setPosition(p.posX + 4, 65, p.posZ);
                threatFixture.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
                    .setBaseValue(0);
                threatFixture.setAttackTarget(p);
                w.spawnEntityInWorld(threatFixture);
                threatPlayer = p;
                threatEnd = tick + 30;
                log(
                    "S5_TARGET_SERVER entity=" + threatFixture.getEntityId()
                        + " target="
                        + p.getEntityId()
                        + " distance=4");
                ProbeBootstrap.channel.sendTo(new ProbePacket(16, threatFixture.getEntityId(), tick, 0, 0), p);
            }
        } finally {
            ProbeConfig.allowBlocks = oldBlocks;
            ProbeConfig.protectFriendly = oldProtect;
            p.getEntityData()
                .setBoolean("cwEngage", oldEngage);
            p.closeScreen();
        }
    }

    private boolean forgeEventLeft(EntityPlayerMP p, WorldServer w) {
        PlayerInteractEvent e = new PlayerInteractEvent(
            p,
            PlayerInteractEvent.Action.LEFT_CLICK_BLOCK,
            12,
            65,
            0,
            1,
            w);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(e);
        return e.isCanceled();
    }
}
