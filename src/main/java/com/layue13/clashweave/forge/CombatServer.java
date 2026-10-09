package com.layue13.clashweave.forge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EntityDamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.GuardTimeline;
import com.layue13.clashweave.core.InputGate;
import com.layue13.clashweave.core.Intent;
import com.layue13.clashweave.core.MovementBudget;
import com.layue13.clashweave.core.Scheduler;
import com.layue13.clashweave.network.InputMessage;
import com.layue13.clashweave.network.StateMessage;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Global phases: inbound, all schedulers, freeze all contacts, delayed commits, snapshots. */
public final class CombatServer {

    private static final class Inbound {

        EntityPlayerMP player;
        InputMessage message;
        long arrival;
        long receivedTick;
    }

    public static final class PlayerState {

        public final Scheduler scheduler;
        public final GuardTimeline guard = new GuardTimeline();
        public final long session = ThreadLocalRandom.current()
            .nextLong();
        public final InputGate gate = new InputGate(session);
        public final EntityPlayerMP player;
        public boolean engaged;
        public long lastTrigger;
        public int revision;
        public long networkAfter;
        public int lock = -1;
        public long budgetNano;
        public long budgetTick;
        public float yaw;
        public float pitch;
        public final com.layue13.clashweave.core.FacingHistory facingHistory = new com.layue13.clashweave.core.FacingHistory();
        public final Map<Integer, com.layue13.clashweave.core.FacingHistory.Result> requestFacing = new HashMap<>();
        public long instance;
        public MovementBudget movement;
        public double x;
        public double y;
        public double z;
        public long handshakeUntil;
        public boolean ready;
        public boolean sentDefinitions;
        public int corrections;
        public double externalHorizontal;
        public double externalVertical;
        public boolean pendingImpulse;
        public final Map<EntityLivingBase, Long> contacts = new HashMap<>();

        PlayerState(EntityPlayerMP player, ActionCatalog actions) {
            this.player = player;
            this.scheduler = new Scheduler(actions);
        }
    }

    private static final class Contact {

        PlayerState attacker;
        EntityLivingBase target;
        Scheduler.Instance instance;
        long tick;
        long frozen;
        boolean facing;
    }

    private final CombatConfig config;
    private final ResistanceOwnership resistance;
    private final Map<UUID, PlayerState> states = new LinkedHashMap<>();
    private final List<Inbound> inbox = new ArrayList<>();
    private final List<Contact> pending = new ArrayList<>();
    private final String definitionJson;
    public volatile long tick;
    public long lastNanos;
    private int nextRevision;

    private static final class CombatSource extends EntityDamageSource {

        CombatSource(EntityPlayer player) {
            super("player", player);
        }
    }

    public CombatServer(CombatConfig config) {
        this.config = config;
        resistance = new ResistanceOwnership(config.maxResistance);
        JsonObject data = new JsonObject();
        data.addProperty("bufferTicks", config.actions.bufferTicks);
        data.add(
            "actions",
            new Gson().toJsonTree(
                config.actions.all()
                    .values()));
        definitionJson = data.toString();
    }

    public PlayerState state(EntityPlayer player) {
        return states.get(player.getUniqueID());
    }

    public ResistanceOwnership ownership() {
        return resistance;
    }

    public static boolean armed(EntityPlayer player) {
        return player.getHeldItem() != null && player.getHeldItem()
            .getItem() == Clashweave.katana;
    }

    public void enqueue(EntityPlayerMP player, InputMessage message) {
        synchronized (inbox) {
            if (inbox.size() >= 512) return;
            Inbound input = new Inbound();
            input.player = player;
            input.message = message;
            input.arrival = System.nanoTime();
            input.receivedTick = tick;
            inbox.add(input);
        }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            PlayerState state = new PlayerState((EntityPlayerMP) event.player, config.actions);
            state.revision = ++nextRevision;
            state.networkAfter = tick + 1;
            states.put(event.player.getUniqueID(), state);
            state.facingHistory.observe(
                System.nanoTime(),
                event.player.rotationYaw,
                event.player.rotationPitch,
                event.player.posX,
                event.player.posY,
                event.player.posZ,
                true,
                true);
            FacingPackets.install(state.player.playerNetServerHandler.netManager, state.facingHistory);
            // Login is fired inside Forge's handshake completion handler. Publish on a later tick.
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        remove(event.player);
    }

    @SubscribeEvent
    public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        remove(event.player);
        login(new PlayerEvent.PlayerLoggedInEvent(event.player));
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        remove(event.player);
        login(new PlayerEvent.PlayerLoggedInEvent(event.player));
    }

    @SubscribeEvent
    public void death(LivingDeathEvent event) {
        if (event.entityLiving.worldObj.isRemote) return;
        resistance.remove(event.entityLiving);
        PlayerState state = event.entityLiving instanceof EntityPlayer ? state((EntityPlayer) event.entityLiving)
            : null;
        if (state != null) {
            state.scheduler.interrupt();
            state.guard.release(tick);
            state.contacts.clear();
            state.engaged = false;
        }
    }

    private void remove(EntityPlayer player) {
        PlayerState state = states.remove(player.getUniqueID());
        if (state != null) state.scheduler.interrupt();
        resistance.remove(player);
        pending.removeIf(contact -> contact.attacker == state || contact.target == player);
        synchronized (inbox) {
            inbox.removeIf(input -> input.player == player);
        }
    }

    public void stop() {
        resistance.clear();
        states.clear();
        pending.clear();
        synchronized (inbox) {
            inbox.clear();
        }
        tick = 0;
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long begin = System.nanoTime();
        tick++;
        List<PlayerState> ordered = new ArrayList<>(states.values());
        ordered.sort(
            Comparator.comparing(
                state -> state.player.getUniqueID()
                    .toString()));
        // Check the old envelope before an input can cancel/replace its action and discard that budget.
        for (PlayerState state : ordered) validateMovement(state);
        // The same lock registers arrivals and cuts off inputs for a commit. No world work on Netty.
        long cutoff;
        List<Inbound> arrived;
        synchronized (inbox) {
            cutoff = System.nanoTime();
            arrived = new ArrayList<>(inbox);
            inbox.clear();
        }
        for (Inbound input : arrived) consume(input);
        for (PlayerState state : ordered) advance(state);
        reconcile(ordered);
        for (PlayerState state : ordered) collect(state);
        pending.sort(
            Comparator.comparing(
                (Contact contact) -> contact.target.getUniqueID()
                    .toString())
                .thenComparing(
                    contact -> contact.attacker.player.getUniqueID()
                        .toString())
                .thenComparingLong(contact -> contact.instance.id));
        Iterator<Contact> iterator = pending.iterator();
        while (iterator.hasNext()) {
            Contact contact = iterator.next();
            if (!(contact.target instanceof EntityPlayer)
                || GuardTimeline.ready(tick, cutoff, contact.tick, contact.frozen, config.defer)) {
                commit(contact, contact.target instanceof EntityPlayer ? cutoff : System.nanoTime());
                iterator.remove();
            }
        }
        for (PlayerState state : ordered) {
            snapshot(state, 0, "", false);
        }
        lastNanos = System.nanoTime() - begin;
    }

    private void consume(Inbound input) {
        PlayerState state = state(input.player);
        if (state == null) return;
        InputMessage message = input.message;
        String validity = state.gate
            .validate(message.session, message.sequence, message.stamp, input.receivedTick, config.stampAge);
        trace(
            "INPUT player=" + input.player.getCommandSenderName()
                + " seq="
                + message.sequence
                + " kind="
                + message.kind
                + " target="
                + message.target
                + " origin="
                + message.origin
                + " stamp="
                + message.stamp
                + " receivedTick="
                + input.receivedTick
                + " consumedTick="
                + tick
                + " arrival="
                + input.arrival
                + " gate="
                + validity);
        if (!validity.equals("OK")) {
            snapshot(state, message.sequence, "REJECT:" + validity, false);
            return;
        }
        if (message.kind == -2) {
            state.sentDefinitions = true;
            return;
        }
        if (!armed(input.player) || input.player.isDead) {
            snapshot(state, message.sequence, input.player.isDead ? "REJECT:DEAD" : "REJECT:UNARMED", false);
            return;
        }
        if (message.kind == -1) {
            if (state.scheduler.current() != null && message.target == (int) state.scheduler.current().id) {
                state.ready = true;
                state.budgetNano = System.nanoTime();
                state.budgetTick = tick;
                state.movement = new MovementBudget(
                    state.budgetNano,
                    state.scheduler.current().definition.speed,
                    state.scheduler.current().definition.distance,
                    config.movementMargin,
                    config.jumpAllowance,
                    config.movementEpsilon);
                state.x = input.player.posX;
                state.y = input.player.posY;
                state.z = input.player.posZ;
                snapshot(state, message.sequence, "MOVE_READY", false);
            }
            return;
        }
        Intent intent = Intent.values()[message.kind];
        if (intent == Intent.CLEAR_BUFFER) {
            state.scheduler.clearBuffer();
            state.guard.release(tick);
        } else if (intent == Intent.GUARD_PRESS) {
            boolean accepted = state.scheduler.current() == null
                && state.guard.press(message.stamp, input.arrival, config.guardCooldown);
            snapshot(state, message.sequence, accepted ? "GUARD" : "REJECT:GUARD", false);
        } else if (intent == Intent.GUARD_RELEASE) {
            state.guard.release(message.stamp);
            snapshot(state, message.sequence, "RELEASE", false);
        } else if (intent == Intent.LOCK) {
            String result = "REJECT:LOCK";
            if (state.lock >= 0) {
                state.lock = -1;
                result = "UNLOCK";
            } else {
                EntityLivingBase target = acquireLock(input.player);
                if (target != null) {
                    state.lock = target.getEntityId();
                    result = "LOCK";
                }
            }
            trace(
                "LOCK_TARGET player=" + input.player
                    .getCommandSenderName() + " target=" + state.lock + " hint=" + message.target);
            snapshot(state, message.sequence, result, false);
        } else if (intent == Intent.DODGE || intent == Intent.SPECIAL || intent == Intent.MODE) {
            snapshot(state, message.sequence, "REJECT:UNIMPLEMENTED:" + intent.name(), false);
        } else if (state.guard.held() && !(intent == Intent.LIGHT && state.scheduler.counter(tick))) {
            snapshot(state, message.sequence, "REJECT:GUARD_HELD", false);
        } else {
            if (intent == Intent.LIGHT && state.scheduler.counter(tick)) state.guard.release(tick);
            com.layue13.clashweave.core.FacingHistory.Result facing = state.facingHistory.choose(
                message.yaw,
                message.pitch,
                input.arrival,
                input.player.rotationYaw,
                input.player.rotationPitch,
                input.player.posX,
                input.player.posY,
                input.player.posZ,
                config.facingMaxTurn,
                config.facingTolerance,
                config.facingHistoryAge * 1_000_000L,
                config.facingHistoryDrift);
            state.requestFacing.put(message.sequence, facing);
            trace(
                "FACING player=" + input.player.getCommandSenderName()
                    + " seq="
                    + message.sequence
                    + " requested="
                    + message.yaw
                    + " known="
                    + input.player.rotationYaw
                    + " chosen="
                    + facing.yaw
                    + " accepted="
                    + facing.accepted
                    + " reason="
                    + facing.reason);
            state.scheduler.request(intent, message.sequence, tick, message.origin);
        }
    }

    private void advance(PlayerState state) {
        EntityPlayerMP player = state.player;
        if (!armed(player)) {
            state.guard.release(tick);
            state.scheduler.clearBuffer();
        }
        state.scheduler.tick(tick);
        Scheduler.Instance action = state.scheduler.current();
        if (action != null && action.id != state.instance) {
            state.instance = action.id;
            trace(
                "START player=" + player
                    .getCommandSenderName() + " id=" + action.id + " action=" + action.definition.id + " tick=" + tick);
            com.layue13.clashweave.core.FacingHistory.Result facing = state.requestFacing.remove(action.sequence);
            state.yaw = facing == null ? player.rotationYaw : facing.yaw;
            state.pitch = facing == null ? player.rotationPitch : facing.pitch;
            if (facing != null) trace(
                "AIM player=" + player.getCommandSenderName()
                    + " id="
                    + action.id
                    + " requested="
                    + facing.requestedYaw
                    + " baseline="
                    + player.rotationYaw
                    + " selected="
                    + state.yaw
                    + " beforeDelta="
                    + Math.abs(
                        com.layue13.clashweave.core.FacingHistory.difference(player.rotationYaw, facing.requestedYaw))
                    + " afterDelta="
                    + Math.abs(com.layue13.clashweave.core.FacingHistory.difference(state.yaw, facing.requestedYaw))
                    + " accepted="
                    + facing.accepted);
            state.ready = false;
            state.movement = null;
            state.externalHorizontal = 0;
            state.externalVertical = 0;
            state.handshakeUntil = tick + 8;
        }
        for (Scheduler.Result result : state.scheduler.drainResults()) {
            state.requestFacing.remove(result.sequence);
            snapshot(state, result.sequence, result.status, false);
        }
        Entity lock = player.worldObj.getEntityByID(state.lock);
        if (lock == null || !com.layue13.clashweave.core.LockSelection.keep(
            player.getDistanceToEntity(lock),
            config.lockKeepRange,
            !lock.isDead && (!(lock instanceof EntityLivingBase) || ((EntityLivingBase) lock).getHealth() > 0)))
            state.lock = -1;
        boolean trigger = action != null || state.guard.held() || state.lock >= 0 || player.hurtTime > 0;
        for (Object object : player.worldObj.getEntitiesWithinAABB(
            EntityLiving.class,
            player.boundingBox.expand(config.radius, config.radius, config.radius))) {
            EntityLiving mob = (EntityLiving) object;
            if (mob.getAttackTarget() == player && mob.getDistanceToEntity(player) <= config.radius) trigger = true;
        }
        if (trigger) state.lastTrigger = tick;
        boolean engaged = trigger || state.engaged && tick - state.lastTrigger < config.disengage;
        if (engaged) player.setSneaking(false);
        if (state.engaged != engaged) {
            state.engaged = engaged;
            state.revision = ++nextRevision;
            trace(
                "ENGAGEMENT player=" + player.getCommandSenderName()
                    + " engaged="
                    + engaged
                    + " revision="
                    + state.revision
                    + " tick="
                    + tick
                    + " sent="
                    + System.nanoTime());
            snapshot(state, 0, "ENGAGEMENT", false);
        }
        state.guard.prune(tick - config.stampAge - config.defer - 4);
    }

    private void reconcile(List<PlayerState> ordered) {
        Map<EntityLivingBase, Set<UUID>> desired = new HashMap<>();
        for (PlayerState state : ordered) {
            state.contacts.entrySet()
                .removeIf(
                    entry -> tick > entry.getValue() || entry.getKey().isDead
                        || entry.getKey().dimension != state.player.dimension);
            if (!state.engaged || state.player.isDead) continue;
            own(desired, state.player, state.player.getUniqueID());
            for (EntityLivingBase target : state.contacts.keySet()) own(desired, target, state.player.getUniqueID());
            Entity locked = state.player.worldObj.getEntityByID(state.lock);
            if (locked instanceof EntityLivingBase) own(desired, (EntityLivingBase) locked, state.player.getUniqueID());
            for (Object object : state.player.worldObj.getEntitiesWithinAABB(
                EntityLiving.class,
                state.player.boundingBox.expand(config.radius, config.radius, config.radius))) {
                EntityLiving mob = (EntityLiving) object;
                if (mob.getAttackTarget() == state.player && mob.getDistanceToEntity(state.player) <= config.radius) {
                    own(desired, mob, state.player.getUniqueID());
                }
            }
        }
        resistance.reconcile(desired);
    }

    private void own(Map<EntityLivingBase, Set<UUID>> desired, EntityLivingBase target, UUID owner) {
        desired.computeIfAbsent(target, entity -> new HashSet<>())
            .add(owner);
    }

    /** Hint-free authoritative selection; visibility and friendly policy are checked before scoring. */
    public EntityLivingBase acquireLock(EntityPlayer player) {
        EntityLivingBase selected = null;
        double best = Double.POSITIVE_INFINITY;
        net.minecraft.util.Vec3 eye = net.minecraft.util.Vec3
            .createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        net.minecraft.util.Vec3 look = player.getLookVec();
        for (Object object : player.worldObj.getEntitiesWithinAABB(
            EntityLivingBase.class,
            player.boundingBox.expand(config.lockAcquireRange, config.lockAcquireRange, config.lockAcquireRange))) {
            EntityLivingBase target = (EntityLivingBase) object;
            if (target == player || target.isDead
                || target.getHealth() <= 0
                || protectedTarget(player, target)
                || !player.canEntityBeSeen(target)) continue;
            double dx = target.posX - eye.xCoord;
            double dy = target.posY + target.getEyeHeight() - eye.yCoord;
            double dz = target.posZ - eye.zCoord;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double cosine = length == 0 ? 1 : (dx * look.xCoord + dy * look.yCoord + dz * look.zCoord) / length;
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cosine))));
            double score = com.layue13.clashweave.core.LockSelection
                .score(player.getDistanceToEntity(target), angle, config.lockAcquireRange, config.lockConeHalfAngle);
            if (com.layue13.clashweave.core.LockSelection.better(
                score,
                target.getEntityId(),
                best,
                selected == null ? Integer.MAX_VALUE : selected.getEntityId())) {
                selected = target;
                best = score;
            }
        }
        return selected;
    }

    public boolean protectedTarget(EntityPlayer attacker, EntityLivingBase target) {
        return config.friendly && (target instanceof EntityVillager
            || target instanceof EntityTameable && ((EntityTameable) target).isTamed()
            || target instanceof EntityPlayer && attacker.isOnSameTeam(target));
    }

    private void collect(PlayerState state) {
        Scheduler.Instance action = state.scheduler.current();
        if (action == null || state.player.isDead || !armed(state.player)) return;
        int elapsed = (int) (tick - action.start);
        ActionCatalog.Definition definition = action.definition;
        if (elapsed < definition.startup || elapsed >= definition.startup + definition.active) return;
        double previous = -definition.arc / 2 + definition.arc * (elapsed - definition.startup) / definition.active;
        double next = -definition.arc / 2 + definition.arc * (elapsed - definition.startup + 1) / definition.active;
        for (Object object : state.player.worldObj.getEntitiesWithinAABB(
            EntityLivingBase.class,
            state.player.boundingBox.expand(definition.reach, 1, definition.reach))) {
            EntityLivingBase target = (EntityLivingBase) object;
            if (target == state.player || target.isDead || protectedTarget(state.player, target)) continue;
            boolean intersected = SweepGeometry.intersects(
                state.player,
                target,
                definition.reach,
                previous,
                next,
                state.yaw,
                state.pitch,
                config.sweepLayers,
                config.sweepBottom,
                config.sweepTop);
            if (!intersected || !action.claim(
                target.getUniqueID()
                    .toString(),
                0)) continue;
            state.contacts.put(target, tick + config.disengage);
            Contact contact = new Contact();
            contact.attacker = state;
            contact.target = target;
            contact.instance = action;
            contact.tick = tick;
            contact.frozen = System.nanoTime();
            contact.facing = facing(target, state.player, config.guardArc);
            pending.add(contact);
            trace(
                "FREEZE player=" + state.player.getCommandSenderName()
                    + " targetPlayer="
                    + (target instanceof EntityPlayer)
                    + " target="
                    + target.getEntityId()
                    + " id="
                    + action.id
                    + " hitTick="
                    + tick
                    + " frozen="
                    + contact.frozen);
        }
    }

    private static boolean facing(EntityLivingBase defender, Entity attacker, double arc) {
        double angle = Math.toDegrees(Math.atan2(-(attacker.posX - defender.posX), attacker.posZ - defender.posZ));
        double delta = (angle - defender.rotationYaw + 540) % 360 - 180;
        return Math.abs(delta) <= arc / 2;
    }

    private void commit(Contact contact, long cutoff) {
        if (contact.target.isDead || contact.target.getHealth() <= 0
            || contact.target.dimension != contact.attacker.player.dimension) return;
        PlayerState defender = contact.target instanceof EntityPlayer ? state((EntityPlayer) contact.target) : null;
        int guard = defender == null ? 0
            : defender.guard.defend(contact.tick, cutoff, config.perfectWindow, contact.facing);
        float damage = contact.instance.definition.damage
            * (guard == 2 ? 0 : guard == 1 ? (float) config.blockDamage : 1);
        boolean hit = false;
        if (damage > 0) {
            hit = contact.target.attackEntityFrom(new CombatSource(contact.attacker.player), damage);
        }
        contact.instance.confirmedHit |= hit || guard > 0;
        if (guard == 2) defender.scheduler.openCounter(tick, config.counterWindow);
        if (defender != null && hit) creditImpulse(defender);
        trace(
            "COMMIT player=" + contact.attacker.player.getCommandSenderName()
                + " targetPlayer="
                + (contact.target instanceof EntityPlayer)
                + " target="
                + contact.target.getEntityId()
                + " id="
                + contact.instance.id
                + " hitTick="
                + contact.tick
                + " commitTick="
                + tick
                + " frozen="
                + contact.frozen
                + " cutoff="
                + cutoff
                + " guard="
                + guard
                + " damage="
                + damage
                + " hit="
                + hit
                + " hp="
                + contact.target.getHealth()
                + " facing="
                + contact.facing);
        snapshot(
            contact.attacker,
            0,
            guard == 2 ? "PARRY" : guard == 1 ? "BLOCK" : hit ? "HIT" : "IMMUNE",
            false,
            contact.frozen);
        if (defender != null)
            snapshot(defender, 0, guard == 2 ? "PARRY" : guard == 1 ? "BLOCK" : "HURT", false, contact.frozen);
    }

    private void validateMovement(PlayerState state) {
        if (state.movement == null || state.scheduler.current() == null || !state.ready) return;
        EntityPlayerMP player = state.player;
        double horizontal = Math.hypot(player.posX - state.x, player.posZ - state.z);
        double vertical = player.posY - state.y;
        double walk = player.capabilities.isFlying ? config.flyingAllowance
            : player.isSprinting() ? config.sprintAllowance : config.walkAllowance;
        if (state.pendingImpulse) {
            creditImpulse(state);
            state.pendingImpulse = false;
        }
        if (!state.movement
            .accept(System.nanoTime(), horizontal, vertical, walk, state.externalHorizontal, state.externalVertical)) {
            player.playerNetServerHandler
                .setPlayerLocation(state.x, state.y, state.z, player.rotationYaw, player.rotationPitch);
            state.corrections++;
            trace(
                "MOVE_CORRECT player=" + player.getCommandSenderName()
                    + " tick="
                    + tick
                    + " nano="
                    + System.nanoTime()
                    + " corrections="
                    + state.corrections);
            snapshot(state, 0, "MOVE_CORRECT", false);
        } else {
            state.x = player.posX;
            state.y = player.posY;
            state.z = player.posZ;
        }
    }

    private void snapshot(PlayerState state, int sequence, String result, boolean definitions) {
        snapshot(state, sequence, result, definitions, 0);
    }

    private void snapshot(PlayerState state, int sequence, String result, boolean definitions, long frozen) {
        if (state.player.playerNetServerHandler == null || tick <= state.networkAfter) return;
        StateMessage message = new StateMessage();
        message.entity = state.player.getEntityId();
        message.tick = tick;
        message.session = state.session;
        message.revision = state.revision;
        message.ack = sequence;
        message.engaged = state.engaged;
        message.sheathed = state.scheduler.sheathed();
        message.blocks = config.blocks;
        message.yaw = state.yaw;
        Scheduler.Instance action = state.scheduler.current();
        message.action = action == null ? "" : action.definition.id;
        message.instance = action == null ? 0 : action.id;
        message.start = action == null ? tick : action.start;
        message.confirmed = action != null && action.confirmedHit;
        message.result = result;
        if (result.equals("MOVE_READY")) {
            message.budgetNano = state.budgetNano;
            message.budgetTick = state.budgetTick;
            message.budgetX = state.x;
            message.budgetY = state.y;
            message.budgetZ = state.z;
        }
        message.feedbackFrozen = Boolean.getBoolean("clashweave.trace") ? frozen : 0;
        message.definitions = definitions || !state.sentDefinitions ? definitionJson : "";
        if (sequence > 0)
            trace("ACK player=" + state.player.getCommandSenderName() + " seq=" + sequence + " result=" + result);
        Clashweave.network.sendTo(message, state.player);
        for (PlayerState watcher : states.values()) {
            if (watcher != state && tick > watcher.networkAfter && watcher.player.dimension == state.player.dimension) {
                Clashweave.network.sendTo(new StateMessage(message), watcher.player);
            }
        }
    }

    private static void trace(String value) {
        if (Boolean.getBoolean("clashweave.trace")) System.out.println("CW " + value);
    }

    private void creditImpulse(PlayerState state) {
        state.externalHorizontal += Math.hypot(state.player.motionX, state.player.motionZ) * config.impulseMultiplier;
        state.externalVertical += Math.max(0, state.player.motionY) * config.impulseMultiplier;
    }

    @SubscribeEvent
    public void jump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        if (event.entityLiving.worldObj.isRemote || !(event.entityLiving instanceof EntityPlayer)) return;
        PlayerState state = state((EntityPlayer) event.entityLiving);
        if (state != null && state.movement != null
            && event.entityLiving.worldObj.checkBlockCollision(event.entityLiving.boundingBox.offset(0, -0.05, 0)))
            state.externalVertical += config.jumpAllowance;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (!armed(event.entityPlayer)) return;
        PlayerState state = state(event.entityPlayer);
        if (event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK
            || event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
                && (!config.blocks || state != null && state.engaged)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void attack(AttackEntityEvent event) {
        if (armed(event.entityPlayer)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void entityInteract(EntityInteractEvent event) {
        if (armed(event.entityPlayer)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void hurt(LivingHurtEvent event) {
        if (event.entityLiving.worldObj.isRemote || event.source instanceof CombatSource
            || !(event.entityLiving instanceof EntityPlayer)) return;
        PlayerState state = state((EntityPlayer) event.entityLiving);
        Entity attacker = event.source.getEntity();
        if (state == null || attacker == null || !armed(state.player)) return;
        if (event.ammount > 0) state.pendingImpulse = true;
        int guard = state.guard
            .defend(tick, System.nanoTime(), config.perfectWindow, facing(state.player, attacker, config.guardArc));
        if (guard > 0) event.ammount *= guard == 2 ? 0 : config.blockDamage;
    }
}
