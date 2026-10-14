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
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.content.ContentManifest;
import com.layue13.clashweave.content.EffectiveRules;
import com.layue13.clashweave.content.Handshake;
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.AppearanceState;
import com.layue13.clashweave.core.FacingHistory;
import com.layue13.clashweave.core.GuardTimeline;
import com.layue13.clashweave.core.InputGate;
import com.layue13.clashweave.core.Intent;
import com.layue13.clashweave.core.LatencyCompensation;
import com.layue13.clashweave.core.MovementBudget;
import com.layue13.clashweave.core.Scheduler;
import com.layue13.clashweave.network.ContentSyncMessage;
import com.layue13.clashweave.network.HelloMessage;
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
        long facingArrival;
        long receivedTick;
    }

    public static final class PlayerState {

        public final Scheduler scheduler;
        public final AppearanceState.Pending appearance = new AppearanceState.Pending();
        public final GuardTimeline guard = new GuardTimeline();
        public final long session = ThreadLocalRandom.current()
            .nextLong();
        public final InputGate gate = new InputGate(session);
        public final EntityPlayerMP player;
        public boolean engaged;
        public long lastTrigger;
        public long lastDodge = Long.MIN_VALUE / 2;
        public int dodgeOctant;
        public int revision;
        public long networkAfter;
        public int lock = -1;
        public long lastLockSwitch = Long.MIN_VALUE / 2;
        public long budgetNano;
        public long budgetTick;
        public float yaw;
        public float pitch;
        public final FacingHistory facingHistory = new FacingHistory();
        public final Map<Integer, FacingHistory.Result> requestFacing = new HashMap<>();
        public long instance;
        public MovementBudget movement;
        public double x;
        public double y;
        public double z;
        public long handshakeUntil;
        public boolean ready;
        public boolean sentDefinitions;
        /** Style whose definitions the client is being sent; an acknowledgement must name it. */
        public String definitionStyle = "";
        public boolean verified;
        public long helloDeadline;
        public int corrections;
        public double externalHorizontal;
        public double externalVertical;
        public boolean pendingImpulse;
        public double launchCredit, launchY;
        public long launchCreditUntil;
        public final Map<EntityLivingBase, Long> contacts = new HashMap<>();

        PlayerState(EntityPlayerMP player, CombatWeapons.Entry weapon) {
            this.player = player;
            this.scheduler = new Scheduler(weapon.definition, weapon.catalog);
            this.appearance.request(weapon.definition.appearance);
            this.appearance.apply(true);
            definitionStyle = weapon.definition.style;
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

    private long correctionSequence;
    private final CombatConfig config;
    private final EffectiveRules rules;
    private final List<PendingHello> hellos = new ArrayList<>();
    private final CombatPresentation presentation = new CombatPresentation();
    private final HitEffects effects;
    private final PostureTracker posture;
    private final ResistanceOwnership resistance;
    private final Map<UUID, PlayerState> states = new LinkedHashMap<>();
    private final List<Inbound> inbox = new ArrayList<>();
    private final List<Contact> pending = new ArrayList<>();
    public volatile long tick;
    public long lastNanos;
    private int nextRevision;

    private static final class CombatSource extends EntityDamageSource {

        CombatSource(EntityPlayer player) {
            super("player", player);
        }
    }

    private static final class PendingHello {

        EntityPlayerMP player;
        HelloMessage message;
        long queued;
    }

    public CombatServer(CombatConfig config, EffectiveRules rules) {
        this.config = config;
        this.rules = rules;
        effects = new HitEffects(config, presentation);
        posture = new PostureTracker(config, presentation, effects);
        resistance = new ResistanceOwnership(config.maxResistance);
    }

    public EffectiveRules rules() {
        return rules;
    }

    /** Netty thread: only queues; the comparison and any kick happen on the server thread. */
    public void hello(EntityPlayerMP player, HelloMessage message) {
        synchronized (hellos) {
            if (hellos.size() >= 64) return;
            PendingHello hello = new PendingHello();
            hello.player = player;
            hello.message = message;
            hello.queued = tick;
            hellos.add(hello);
        }
    }

    public PlayerState state(EntityPlayer player) {
        return states.get(player.getUniqueID());
    }

    public ResistanceOwnership ownership() {
        return resistance;
    }

    public static boolean armed(EntityPlayer player) {
        return CombatWeapons.armed(player.getHeldItem());
    }

    public void enqueue(EntityPlayerMP player, InputMessage message) {
        synchronized (inbox) {
            if (inbox.size() >= 512) return;
            Inbound input = new Inbound();
            input.player = player;
            input.message = message;
            input.arrival = System.nanoTime();
            PlayerState observed = state(player);
            input.facingArrival = observed == null ? 0 : observed.facingHistory.requestArrival(message.sequence);
            input.receivedTick = tick;
            inbox.add(input);
        }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        begin(event.player, false);
    }

    /** Respawn and dimension change replace the state but not the connection, so they keep its verification. */
    private void begin(EntityPlayer player, boolean verified) {
        if (player instanceof EntityPlayerMP) {
            EntityPlayerMP joined = (EntityPlayerMP) player;
            CombatWeapons.Entry held = CombatWeapons.entry(joined.getHeldItem());
            PlayerState state = new PlayerState(joined, held != null ? held : CombatWeapons.fallback());
            state.verified = verified;
            state.helloDeadline = verified ? Long.MAX_VALUE : tick + config.helloTimeoutTicks;
            presentation.rememberHealth(joined);
            state.revision = ++nextRevision;
            state.networkAfter = tick + 1;
            states.put(joined.getUniqueID(), state);
            state.facingHistory.observe(
                System.nanoTime(),
                joined.rotationYaw,
                joined.rotationPitch,
                joined.posX,
                joined.posY,
                joined.posZ,
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
        restart(event.player);
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        restart(event.player);
    }

    private void restart(EntityPlayer player) {
        PlayerState old = states.get(player.getUniqueID());
        boolean verified = old != null && old.verified;
        remove(player);
        begin(player, verified);
    }

    @SubscribeEvent
    public void death(LivingDeathEvent event) {
        if (event.entityLiving.worldObj.isRemote) return;
        resistance.remove(event.entityLiving);
        effects.remove(event.entityLiving);
        posture.remove(event.entityLiving);
        PlayerState state = event.entityLiving instanceof EntityPlayer ? state((EntityPlayer) event.entityLiving)
            : null;
        if (state != null) {
            state.scheduler.interrupt();
            state.guard.release(tick);
            state.contacts.clear();
            state.engaged = false;
        }
    }

    /**
     * A player at zero health stays in the world, not isDead, until it respawns. It must neither start actions nor
     * hit anything in that time, whatever it holds (keepInventory leaves the weapon in hand).
     */
    private static boolean dead(EntityPlayer player) {
        return player.isDead || player.getHealth() <= 0;
    }

    /** Server thread. Weapons change only between actions; a new style also makes the client re-acknowledge. */
    private void use(PlayerState state, CombatWeapons.Entry entry) {
        if (state.scheduler.weapon() != entry.definition) {
            boolean style = !state.scheduler.weapon().style.equals(entry.definition.style);
            state.scheduler.useWeapon(entry.definition, entry.catalog);
            if (style) {
                state.sentDefinitions = false;
                state.definitionStyle = entry.definition.style;
            }
        }
        state.appearance.request(entry.definition.appearance);
    }

    private void processHellos() {
        List<PendingHello> batch;
        synchronized (hellos) {
            if (hellos.isEmpty()) return;
            batch = new ArrayList<>(hellos);
            hellos.clear();
        }
        for (PendingHello hello : batch) {
            PlayerState state = state(hello.player);
            if (state == null) {
                // The login event can trail the first packet; give it a bounded number of ticks.
                if (tick - hello.queued < 200) synchronized (hellos) {
                    hellos.add(hello);
                }
                continue;
            }
            if (state.verified) continue;
            HelloMessage message = hello.message;
            ContentManifest.Comparison verdict = Handshake
                .check(rules.packGameplayHash, rules.presentationHash, message.gameplay, message.presentation);
            if (verdict == ContentManifest.Comparison.GAMEPLAY_MISMATCH) {
                reject(state, "gameplay content differs", message.gameplay, message.presentation);
                continue;
            }
            state.verified = true;
            if (verdict == ContentManifest.Comparison.MATCH) Clashweave.log
                .info("Clashweave: {} presented matching content hashes", state.player.getCommandSenderName());
            else Clashweave.log.warn(
                "Clashweave: {} has different presentation content only (server {}, client {}); allowed",
                state.player.getCommandSenderName(),
                rules.presentationHash,
                message.presentation);
            Clashweave.network.sendTo(
                new ContentSyncMessage(
                    rules.effectiveHash,
                    rules.overridesCanonical,
                    CombatWeapons.table()
                        .names()),
                state.player);
        }
    }

    private void reject(PlayerState state, String reason, String gameplay, String presentation) {
        state.helloDeadline = Long.MAX_VALUE;
        Clashweave.log.warn(
            "Clashweave: refusing {}: {} (gameplay server {} client {}; presentation server {} client {})",
            state.player.getCommandSenderName(),
            reason,
            rules.packGameplayHash,
            gameplay,
            rules.presentationHash,
            presentation);
        state.player.playerNetServerHandler.kickPlayerFromServer(
            "Clashweave: " + reason
                + ". Install the same content packs as the server (server gameplay "
                + abbreviate(rules.packGameplayHash)
                + ", yours "
                + abbreviate(gameplay)
                + ").");
    }

    private static String abbreviate(String hash) {
        return hash == null || hash.isEmpty() ? "none" : hash.substring(0, Math.min(12, hash.length()));
    }

    private void remove(EntityPlayer player) {
        presentation.remove(player);
        effects.remove(player);
        posture.remove(player);
        PlayerState state = states.remove(player.getUniqueID());
        if (state != null) state.scheduler.interrupt();
        resistance.remove(player);
        pending.removeIf(contact -> contact.attacker == state || contact.target == player);
        synchronized (inbox) {
            inbox.removeIf(input -> input.player == player);
        }
        synchronized (hellos) {
            hellos.removeIf(hello -> hello.player == player);
        }
    }

    public void stop() {
        resistance.clear();
        effects.clear();
        posture.clear();
        states.clear();
        pending.clear();
        synchronized (hellos) {
            hellos.clear();
        }
        synchronized (inbox) {
            inbox.clear();
        }
        presentation.stop();
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
        for (PlayerState state : ordered) {
            validateMovement(state);
            state.launchCredit = MovementBudget
                .remainingVertical(state.launchCredit, state.launchY, state.player.posY, tick, state.launchCreditUntil);
            state.launchY = state.player.posY;
        }
        effects.tick(tick);
        posture.tick(tick);
        // The same lock registers arrivals and cuts off inputs for a commit. No world work on Netty.
        long cutoff;
        List<Inbound> arrived;
        synchronized (inbox) {
            cutoff = System.nanoTime();
            arrived = new ArrayList<>(inbox);
            inbox.clear();
        }
        processHellos();
        for (PlayerState state : ordered) if (!state.verified && tick > state.helloDeadline)
            reject(state, "no content hashes within " + config.helloTimeoutTicks + " ticks", "", "");
        for (Inbound input : arrived) consume(input);
        presentation.flush();
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
        String validity = state.gate.validate(
            message.session,
            message.sequence,
            message.stamp,
            input.receivedTick,
            config.stampAge,
            config.futureTolerance);
        long effectiveStamp = InputGate.effectiveStamp(message.stamp, input.receivedTick);
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
                + " effectiveStamp="
                + effectiveStamp
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
            // The acknowledgement names the style it received, so a late ack for the previous weapon is ignored.
            if (message.target == state.definitionStyle.hashCode()) state.sentDefinitions = true;
            return;
        }
        if (!state.verified) {
            snapshot(state, message.sequence, "REJECT:UNVERIFIED", false);
            return;
        }
        if (!armed(input.player) || dead(input.player)) {
            snapshot(state, message.sequence, dead(input.player) ? "REJECT:DEAD" : "REJECT:UNARMED", false);
            return;
        }
        if (message.kind == -1) {
            if (state.scheduler.current() != null && message.target == (int) state.scheduler.current().id) {
                if (!state.scheduler.current()
                    .claimMovementReady()) {
                    trace(
                        "MOVE_READY_DUPLICATE player=" + input.player.getCommandSenderName()
                            + " id="
                            + state.scheduler.current().id
                            + " sequence="
                            + message.sequence);
                    return;
                }
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
                state.movement.creditVertical(state.launchCredit, state.launchCreditUntil);
                state.movement.creditVertical(
                    state.scheduler.current().definition.lift * config.impulseMultiplier,
                    state.scheduler.current().start + state.scheduler.current().definition.duration());
                state.x = input.player.posX;
                state.y = input.player.posY;
                state.z = input.player.posZ;
                snapshot(state, message.sequence, "MOVE_READY", false);
            }
            return;
        }
        Intent intent = Intent.values()[message.kind];
        if (posture.broken(state.player, tick) && (intent == Intent.LIGHT || intent == Intent.HEAVY
            || intent == Intent.SHEATHE
            || intent == Intent.GUARD_PRESS
            || intent == Intent.DODGE
            || intent == Intent.SPECIAL
            || intent == Intent.MODE)) {
            snapshot(state, message.sequence, "REJECT:BROKEN", false);
            return;
        }
        if (intent == Intent.CLEAR_BUFFER) {
            state.scheduler.clearBuffer();
            state.guard.release(tick);
        } else if (intent == Intent.GUARD_PRESS) {
            boolean accepted = state.scheduler.current() == null
                && state.guard.press(effectiveStamp, input.arrival, config.guardCooldown);
            snapshot(state, message.sequence, accepted ? "GUARD" : "REJECT:GUARD", false);
        } else if (intent == Intent.GUARD_RELEASE) {
            state.guard.release(effectiveStamp);
            snapshot(state, message.sequence, "RELEASE", false);
        } else if (intent == Intent.LOCK_SWITCH) {
            EntityLivingBase selected = null;
            if (state.lock >= 0 && input.arrival - state.lastLockSwitch >= config.lockFlickCooldownMillis * 1_000_000L
                && (message.target == -1 || message.target == 1)) {
                state.lastLockSwitch = input.arrival;
                selected = switchLock(input.player, state.lock, message.target);
                if (selected != null) state.lock = selected.getEntityId();
            }
            trace(
                "LOCK_SWITCH player=" + input.player.getCommandSenderName()
                    + " direction="
                    + message.target
                    + " selected="
                    + (selected == null ? -1 : selected.getEntityId()));
            snapshot(state, message.sequence, selected == null ? "REJECT:LOCK_SWITCH" : "LOCK_SWITCH", false);
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
        } else if (intent == Intent.SPECIAL || intent == Intent.MODE) {
            snapshot(state, message.sequence, "REJECT:UNIMPLEMENTED:" + intent.name(), false);
        } else if (intent == Intent.DODGE && tick - state.lastDodge < config.dodgeCooldown) {
            snapshot(state, message.sequence, "REJECT:DODGE_COOLDOWN", false);
        } else if (state.guard.held() && intent != Intent.DODGE
            && !(intent == Intent.LIGHT && state.scheduler.counter(tick))) {
                snapshot(state, message.sequence, "REJECT:GUARD_HELD", false);
            } else {
                if (intent == Intent.LIGHT && state.scheduler.counter(tick)) state.guard.release(tick);
                if (intent == Intent.DODGE) {
                    state.guard.release(tick);
                    state.lastDodge = tick;
                    state.dodgeOctant = message.target;
                    trace(
                        "DODGE_REQUEST player=" + input.player
                            .getCommandSenderName() + " octant=" + message.target + " tick=" + tick);
                }
                if (state.scheduler.current() == null) {
                    use(state, CombatWeapons.entry(input.player.getHeldItem()));
                    state.appearance.apply(true);
                }
                FacingHistory.Result facing = state.facingHistory.choose(
                    message.yaw,
                    message.pitch,
                    input.facingArrival,
                    input.player.rotationYaw,
                    input.player.rotationPitch,
                    input.player.posX,
                    input.player.posY,
                    input.player.posZ,
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
                long receiptAge = input.receivedTick - effectiveStamp;
                state.scheduler.request(
                    intent,
                    message.sequence,
                    tick,
                    message.origin,
                    receiptAge,
                    LatencyCompensation.limit(
                        receiptAge,
                        config.inputEdgeCompensationTicks,
                        serverPingMillis(input.player),
                        config.inputEdgeCompensationJitterTicks));
            }
    }

    /** Isolated MCP field access; mapping must be verified in the Forge development environment. */
    private static int serverPingMillis(EntityPlayerMP player) {
        return player == null ? 0 : player.ping;
    }

    private void advance(PlayerState state) {
        EntityPlayerMP player = state.player;
        if (!armed(player)) {
            state.guard.release(tick);
            state.scheduler.clearBuffer();
        }
        boolean wasSheathed = state.scheduler.sheathed();
        Scheduler.Instance running = state.scheduler.current();
        state.scheduler.tick(tick, player.onGround);
        if (!wasSheathed && state.scheduler.sheathed())
            presentation.sheathed(player, state.instance, tick, running == null ? "" : running.definition.id);
        if (state.scheduler.current() == null) {
            CombatWeapons.Entry resolved = CombatWeapons.entry(player.getHeldItem());
            if (resolved != null) use(state, resolved);
            state.appearance.apply(true);
        }
        presentation.health(player, tick);
        Scheduler.Instance action = state.scheduler.current();
        if (action != null && action.id != state.instance) {
            state.instance = action.id;
            trace(
                "START player=" + player
                    .getCommandSenderName() + " id=" + action.id + " action=" + action.definition.id + " tick=" + tick);
            FacingHistory.Result facing = state.requestFacing.remove(action.sequence);
            state.yaw = facing == null ? player.rotationYaw : facing.yaw;
            state.pitch = facing == null ? player.rotationPitch : facing.pitch;
            Entity lockedTarget = player.worldObj.getEntityByID(state.lock);
            float unassisted = state.yaw;
            if (config.lockAssistEnabled && lockedTarget instanceof EntityLivingBase
                && !lockedTarget.isDead
                && ((EntityLivingBase) lockedTarget).getHealth() > 0
                && player.getDistanceToEntity(lockedTarget) <= config.lockKeepRange
                && player.canEntityBeSeen(lockedTarget)) {
                state.yaw = com.layue13.clashweave.core.LockAssist.yaw(
                    state.yaw,
                    lockedTarget.posX - player.posX,
                    lockedTarget.posZ - player.posZ,
                    config.lockAssistCone,
                    config.lockAssistMaxDegrees);
            }
            if (state.lock >= 0) trace(
                "LOCK_AIM player=" + player.getCommandSenderName()
                    + " id="
                    + action.id
                    + " target="
                    + state.lock
                    + " enabled="
                    + config.lockAssistEnabled
                    + " before="
                    + unassisted
                    + " after="
                    + state.yaw
                    + " correction="
                    + Math.abs(FacingHistory.difference(state.yaw, unassisted))
                    + " targetYaw="
                    + (lockedTarget == null ? "NaN"
                        : Math.toDegrees(Math.atan2(player.posX - lockedTarget.posX, lockedTarget.posZ - player.posZ)))
                    + " pitchCorrection=0");
            if (facing != null) trace(
                "AIM player=" + player.getCommandSenderName()
                    + " id="
                    + action.id
                    + " requested="
                    + facing.requestedYaw
                    + " requestedPitch="
                    + facing.requestedPitch
                    + " selectedPitch="
                    + state.pitch
                    + " beforePitchDelta="
                    + Math.abs(facing.requestedPitch)
                    + " afterPitchDelta="
                    + Math.abs(state.pitch - facing.requestedPitch)
                    + " baseline="
                    + player.rotationYaw
                    + " selected="
                    + state.yaw
                    + " beforeDelta="
                    + Math.abs(FacingHistory.difference(player.rotationYaw, facing.requestedYaw))
                    + " afterDelta="
                    + Math.abs(FacingHistory.difference(state.yaw, facing.requestedYaw))
                    + " accepted="
                    + facing.accepted);
            presentation.started(player, action, tick, wasSheathed && !state.scheduler.sheathed());
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
        boolean trigger = !dead(player)
            && (action != null || state.guard.held() || state.lock >= 0 || player.hurtTime > 0);
        for (Object object : player.worldObj.getEntitiesWithinAABB(
            EntityLiving.class,
            player.boundingBox.expand(config.radius, config.radius, config.radius))) {
            EntityLiving mob = (EntityLiving) object;
            if (mob.getAttackTarget() == player && mob.getDistanceToEntity(player) <= config.radius && !dead(player))
                trigger = true;
        }
        if (trigger) state.lastTrigger = tick;
        // Waiting on the death screen is not combat: the death handler's disengage must not be undone by hurtTime.
        boolean engaged = !dead(player) && (trigger || state.engaged && tick - state.lastTrigger < config.disengage);
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
            if (!state.engaged || dead(state.player)) continue;
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
        for (Object object : player.worldObj.getEntitiesWithinAABB(
            EntityLivingBase.class,
            player.boundingBox.expand(config.lockAcquireRange, config.lockAcquireRange, config.lockAcquireRange))) {
            EntityLivingBase target = (EntityLivingBase) object;
            if (target == player || target.isDead
                || target.getHealth() <= 0
                || protectedTarget(player, target)
                || !player.canEntityBeSeen(target)) continue;
            double dx = target.posX - eye.xCoord;
            double dy = (target.boundingBox.minY + target.boundingBox.maxY) / 2 - eye.yCoord;
            double dz = target.posZ - eye.zCoord;
            double[] angles = com.layue13.clashweave.core.LockSelection
                .angles(dx, dy, dz, player.rotationYaw, player.rotationPitch);
            double score = com.layue13.clashweave.core.LockSelection.acquisitionScore(
                player.getDistanceToEntity(target),
                angles[0],
                angles[1],
                dy,
                config.lockAcquireRange,
                config.lockConeHalfAngle,
                config.lockConeVerticalHalfAngle);
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

    private EntityLivingBase switchLock(EntityPlayerMP player, int locked, int direction) {
        Entity current = player.worldObj.getEntityByID(locked);
        if (!(current instanceof EntityLivingBase) || current.isDead
            || ((EntityLivingBase) current).getHealth() <= 0
            || player.getDistanceToEntity(current) > config.lockKeepRange) return null;
        float yaw = (float) Math.toDegrees(Math.atan2(player.posX - current.posX, current.posZ - player.posZ));
        EntityLivingBase chosen = null;
        double best = Double.POSITIVE_INFINITY;
        for (Object object : player.worldObj.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) continue;
            EntityLivingBase candidate = (EntityLivingBase) object;
            double distance = player.getDistanceToEntity(candidate);
            if (candidate == player || candidate == current
                || candidate.isDead
                || candidate.getHealth() <= 0
                || distance > config.lockKeepRange
                || protectedTarget(player, candidate)
                || !player.canEntityBeSeen(candidate)) continue;
            double score = com.layue13.clashweave.core.ThirdPersonCamera
                .switchScore(yaw, candidate.posX - player.posX, candidate.posZ - player.posZ, direction, distance);
            if (score < best || score == best && chosen != null && candidate.getEntityId() < chosen.getEntityId()) {
                best = score;
                chosen = candidate;
            }
        }
        return chosen;
    }

    public boolean protectedTarget(EntityPlayer attacker, EntityLivingBase target) {
        return config.friendly && (target instanceof EntityVillager
            || target instanceof EntityTameable && ((EntityTameable) target).isTamed()
            || target instanceof EntityPlayer && attacker.isOnSameTeam(target));
    }

    private void collect(PlayerState state) {
        Scheduler.Instance action = state.scheduler.current();
        if (action == null || dead(state.player) || !armed(state.player)) return;
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
                (float) (state.pitch - definition.sweepElevation),
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
        if (defender != null && stepping(defender)) {
            trace(
                "DODGED player=" + defender.player
                    .getCommandSenderName() + " attacker=" + contact.attacker.player.getEntityId() + " tick=" + tick);
            return;
        }
        int guard = defender == null ? 0
            : defender.guard.defend(contact.tick, cutoff, config.perfectWindow, contact.facing);
        float damage = contact.instance.definition.damage * (float) contact.attacker.scheduler.weapon().damageMultiplier
            * (guard == 2 ? 0 : guard == 1 ? (float) config.blockDamage : 1);
        boolean hit = false;
        if (damage > 0) {
            hit = contact.target.attackEntityFrom(new CombatSource(contact.attacker.player), damage);
        }
        contact.instance.confirmedHit |= hit || guard > 0;
        contact.instance.confirmedDamageHit |= hit && guard == 0;
        presentation
            .contact(contact.attacker.player, contact.target, contact.instance, tick, contact.frozen, hit, guard);
        if (guard == 2) defender.scheduler.openCounter(tick, config.counterWindow);
        float stance = contact.instance.definition.posture >= 0 ? contact.instance.definition.posture
            : contact.instance.definition.damage * config.postureScale;
        stance *= (float) contact.attacker.scheduler.weapon().poiseMultiplier;
        if (guard == 2) posture.damage(contact.target, config.posturePrecise, tick);
        else if (guard == 1) posture.damage(contact.target, stance * config.postureBlockedShare, tick);
        else if (hit) posture.damage(contact.target, stance, tick);
        if (defender != null && hit) creditImpulse(defender);
        double launchCredit = effects
            .apply(contact.attacker.player, contact.target, contact.instance, tick, contact.frozen, hit, guard);
        if (defender != null && launchCredit > 0) {
            defender.launchCredit += launchCredit;
            defender.launchY = defender.player.posY;
            defender.launchCreditUntil = tick + config.launchCreditTicks;
            if (defender.movement != null) defender.movement.creditVertical(launchCredit, defender.launchCreditUntil);
        }
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
        if (!state.movement.accept(
            System.nanoTime(),
            tick,
            horizontal,
            vertical,
            walk,
            state.externalHorizontal,
            state.externalVertical)) {
            Clashweave.network.sendTo(
                new com.layue13.clashweave.network.CorrectionMessage(
                    ++correctionSequence,
                    state.x,
                    state.y + 1.6200000047683716D,
                    state.z),
                player);
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
        message.lockTarget = state.lock;
        message.lockKeepRange = config.lockKeepRange;
        message.yaw = state.yaw;
        message.appearance = state.appearance.current();
        message.style = state.scheduler.weapon().style;
        Scheduler.Instance action = state.scheduler.current();
        message.action = action == null ? "" : action.definition.id;
        message.instance = action == null ? 0 : action.id;
        message.start = action == null ? tick : action.start;
        message.confirmed = action != null && action.confirmedHit;
        message.guard = state.guard.held();
        message.result = result;
        if (result.equals("MOVE_READY")) {
            message.budgetNano = state.budgetNano;
            message.budgetTick = state.budgetTick;
            message.budgetX = state.x;
            message.budgetY = state.y;
            message.budgetZ = state.z;
        }
        message.feedbackFrozen = Boolean.getBoolean("clashweave.trace") ? frozen : 0;
        message.definitions = definitions || !state.sentDefinitions ? rules.wireJson(message.style) : "";
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

    /** The blow meets the guard: whatever delivered it is pushed back from the guarding player. */
    private static void recoil(EntityPlayerMP guard, Entity attacker, float strength) {
        if (strength <= 0 || !(attacker instanceof EntityLivingBase) || attacker instanceof EntityPlayer) return;
        double dx = attacker.posX - guard.posX, dz = attacker.posZ - guard.posZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-4) return;
        attacker.motionX = dx / length * strength;
        attacker.motionZ = dz / length * strength;
        attacker.motionY = Math.max(attacker.motionY, .12);
        attacker.velocityChanged = true;
    }

    /**
     * A precise guard turns the blow away before it lands: no damage, no knockback and no hurt flash, which cancelling
     * the later hurt event could not prevent. The counter window opens and the semantic result is published here,
     * because a cancelled hurt event never reaches the presentation flush.
     */
    /** Whether the player is inside the protected span of an action that declares one (a step). */
    private boolean stepping(PlayerState state) {
        Scheduler.Instance current = state.scheduler.current();
        return current != null && current.definition.protects(tick - current.start);
    }

    /** A broken monster cannot strike: its blows are cancelled before they land. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void brokenStrike(LivingAttackEvent event) {
        if (event.entityLiving.worldObj.isRemote) return;
        Entity striker = event.source.getEntity();
        if (striker instanceof EntityLivingBase && !(striker instanceof EntityPlayer)
            && posture.broken((EntityLivingBase) striker, tick)) {
            event.setCanceled(true);
            trace("BROKEN_STRIKE striker=" + striker.getEntityId() + " tick=" + tick);
        }
    }

    /** A broken target takes more of every blow, before any guard reduces it. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void breakBonus(LivingHurtEvent event) {
        if (event.entityLiving.worldObj.isRemote || !posture.broken(event.entityLiving, tick)) return;
        trace("BREAK_BONUS target=" + event.entityLiving.getEntityId() + " before=" + event.ammount + " tick=" + tick);
        event.ammount *= config.breakDamageBonus;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void attack(LivingAttackEvent event) {
        if (event.entityLiving.worldObj.isRemote || event.source instanceof CombatSource
            || !(event.entityLiving instanceof EntityPlayer)) return;
        PlayerState state = state((EntityPlayer) event.entityLiving);
        Entity attacker = event.source.getEntity();
        if (state == null || attacker == null || !armed(state.player) || event.ammount <= 0) return;
        if (stepping(state)) {
            event.setCanceled(true);
            trace(
                "DODGED player=" + state.player
                    .getCommandSenderName() + " attacker=" + attacker.getEntityId() + " tick=" + tick);
            return;
        }
        int guard = state.guard
            .defend(tick, System.nanoTime(), config.perfectWindow, facing(state.player, attacker, config.guardArc));
        if (guard != 2) return;
        event.setCanceled(true);
        state.pendingImpulse = true;
        state.scheduler.openCounter(tick, config.counterWindow);
        posture.damage(state.player, config.posturePrecise, tick);
        if (event.source.getSourceOfDamage() == attacker) recoil(state.player, attacker, config.parryRecoil);
        presentation.nativeParry(attacker, state.player, tick);
        trace(
            "NATIVE_PARRY player=" + state.player
                .getCommandSenderName() + " attacker=" + attacker.getEntityId() + " tick=" + tick);
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
        trace(
            "NATIVE_HURT player=" + state.player.getCommandSenderName()
                + " attacker="
                + attacker.getEntityId()
                + " guard="
                + guard
                + " held="
                + state.guard.held()
                + " facing="
                + facing(state.player, attacker, config.guardArc)
                + " yaw="
                + state.player.rotationYaw
                + " at="
                + attacker.posX
                + ","
                + attacker.posZ
                + " me="
                + state.player.posX
                + ","
                + state.player.posZ
                + " damage="
                + event.ammount
                + " tick="
                + tick);
        if (event.ammount > 0 && guard < 2) posture.damage(
            state.player,
            event.ammount * config.postureScale * (guard == 1 ? config.postureBlockedShare : 1),
            tick);
        if (guard > 0) event.ammount *= guard == 2 ? 0 : config.blockDamage;
        if (guard > 0 && event.source.getSourceOfDamage() == attacker)
            recoil(state.player, attacker, guard == 2 ? config.parryRecoil : config.blockRecoil);
        presentation.nativeGuard(event, guard, tick);
    }
}
