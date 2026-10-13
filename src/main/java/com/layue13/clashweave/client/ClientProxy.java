package com.layue13.clashweave.client;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.content.EffectiveRules;
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.Intent;
import com.layue13.clashweave.forge.BlockInteraction;
import com.layue13.clashweave.forge.CombatServer;
import com.layue13.clashweave.forge.CombatWeapons;
import com.layue13.clashweave.forge.CommonProxy;
import com.layue13.clashweave.network.ContentSyncMessage;
import com.layue13.clashweave.network.HelloMessage;
import com.layue13.clashweave.network.InputMessage;
import com.layue13.clashweave.network.StateMessage;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Client state belongs to an authoritative instance, never to a camera mode. */
public final class ClientProxy extends CommonProxy {

    /**
     * The player's own ground flag as vanilla computed it for this tick, sampled before a root move can clear it. Input
     * handling runs at the start of the next tick, where {@code onGround} still holds the root move's side effect.
     */
    private boolean physicalGround = true;

    public static final class Visual {

        public StateMessage state;
        public long received;
        public double moved;
        public boolean movementReady;
        /** Resolved when the state arrives, so a frame follows references instead of looking up strings. */
        PresentationAssets.Render render;
        PresentationAssets.Preset preset;
        public ActionCatalog catalog;
        public ActionCatalog.Definition definition;
        boolean returnsToSheath;

        /** Diagnostics for fixtures: what the state's IDs resolved to. */
        public String resolvedRender() {
            return render == null ? "" : render.id;
        }

        public boolean hasPreset() {
            return preset != null;
        }
    }

    public static ClientProxy instance;
    public final Map<Integer, Visual> visuals = new HashMap<>();
    /** Mirror of visuals keyed without boxing, for lookups made on the render path. */
    private final net.minecraft.util.IntHashMap byEntity = new net.minecraft.util.IntHashMap();
    /** The catalog of the local player's current weapon. */
    public ActionCatalog actions;
    /** Catalogs the server itself sent (post-override); preferred over the local pack for the same style. */
    private final Map<String, ActionCatalog> authoritative = new HashMap<>();
    private EffectiveRules rules;
    private boolean helloSent, warmed;
    private final Queue<ContentSyncMessage> syncs = new ConcurrentLinkedQueue<>();
    private CombatWeapons.Table localTable;
    private ActionCatalog.Definition predictedDefinition;
    public final LockCamera lockCamera = new LockCamera(this);
    public String feedback = "";
    public int corrections;
    public long lastFeedbackFrozen;
    private long frameFeedbackFrozen;
    private final com.layue13.clashweave.core.SemanticEvents presentation = new com.layue13.clashweave.core.SemanticEvents();
    private final Queue<com.layue13.clashweave.network.SemanticMessage> events = new ConcurrentLinkedQueue<>();
    private long lastEvent;
    private final Queue<StateMessage> incoming = new ConcurrentLinkedQueue<>();
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final KeyBinding sheathe = new KeyBinding("key.clashweave.sheathe", Keyboard.KEY_R, "Clashweave");
    private final KeyBinding dodge = new KeyBinding("key.clashweave.dodge", Keyboard.KEY_LMENU, "Clashweave");
    private final KeyBinding special = new KeyBinding("key.clashweave.special", Keyboard.KEY_V, "Clashweave");
    private final KeyBinding mode = new KeyBinding("key.clashweave.mode", Keyboard.KEY_B, "Clashweave");
    private long session;
    private long serverTick;
    private long anchor;
    private long observedClockTick;
    private long observedClockNano;
    private long tickPeriod = 50_000_000L;
    private int sequence;
    private boolean shift;
    private boolean sentGuard;
    private boolean predictedEngagement;
    private int predictedSequence;
    private String predictedAction = "";
    private boolean gui;

    @Override
    public void initialize() {
        instance = this;
        MinecraftForge.EVENT_BUS.register(new SoulsCamera.LocalVisibility());
        minecraft.mouseHelper = lockCamera.wrap(minecraft.mouseHelper);
        presentation.subscribe(new DefaultPresentation(minecraft, this));
        ClientRegistry.registerKeyBinding(sheathe);
        ClientRegistry.registerKeyBinding(dodge);
        ClientRegistry.registerKeyBinding(special);
        ClientRegistry.registerKeyBinding(mode);
        cpw.mods.fml.client.registry.RenderingRegistry
            .registerEntityRenderingHandler(net.minecraft.entity.player.EntityPlayer.class, new KatanaPlayerRenderer());
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(lockCamera);
        FMLCommonHandler.instance()
            .bus()
            .register(lockCamera);
        MinecraftForge.EVENT_BUS.register(new WeaponRendering());
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        rules = Clashweave.content.rules;
        WeaponRendering.load();
        FMLCommonHandler.instance()
            .bus()
            .register(new ViewPreservingCorrections());
    }

    @Override
    public void receive(StateMessage message) {
        if (incoming.size() < 1024) incoming.add(message);
    }

    @Override
    public void receive(com.layue13.clashweave.network.SemanticMessage message) {
        if (events.size() < 1024) events.add(message);
    }

    @Override
    public void receive(ContentSyncMessage message) {
        if (syncs.size() < 8) syncs.add(message);
    }

    /** O(1) and allocation free; used by the renderer for every armed player each frame. */
    public Visual visualOf(int entity) {
        return (Visual) byEntity.lookup(entity);
    }

    /** The catalog this client uses for a style: the server's own copy if it sent one, else the local pack's. */
    private ActionCatalog catalogFor(String style) {
        ActionCatalog sent = authoritative.get(style);
        return sent != null ? sent : rules.catalog(style);
    }

    /** True while the local player shows an entry pose the server has not yet confirmed. */
    public boolean predicted(Visual visual) {
        return visual == own() && !predictedAction.isEmpty();
    }

    public ActionCatalog.Definition definition(Visual visual) {
        if (visual == null) return null;
        return predicted(visual) ? predictedDefinition : visual.definition;
    }

    private void handshake() {
        if (!helloSent) {
            helloSent = true;
            EffectiveRules local = Clashweave.content.rules;
            Clashweave.network.sendToServer(new HelloMessage(local.packGameplayHash, local.presentationHash));
        }
    }

    private void synchronize(ContentSyncMessage message) {
        if (minecraft.isIntegratedServerRunning()) {
            // Same JVM: the server already owns the table and rules; a client-built copy must not replace them.
            Clashweave.log.info("Clashweave: integrated server, keeping its weapon table ({})", message.effectiveHash);
            return;
        }
        try {
            long begin = System.nanoTime();
            EffectiveRules told = Clashweave.content.rulesFor(EffectiveRules.Overrides.parse(message.overrides));
            long derived = System.nanoTime() - begin;
            if (!told.effectiveHash.equals(message.effectiveHash)) {
                // Our pack plus the server's overrides do not reproduce the server's rules: do not act on them.
                Clashweave.log.error(
                    "Clashweave: negotiated rules differ (server {}, derived {}); connection refused",
                    message.effectiveHash,
                    told.effectiveHash);
                rejectContent("Clashweave: negotiated gameplay rules differ");
                return;
            }
            CombatWeapons.install(
                CombatWeapons.build(
                    message.items,
                    told,
                    0.0001,
                    1000,
                    name -> (net.minecraft.item.Item) net.minecraft.item.Item.itemRegistry.getObject(name)));
            rules = told;
            Clashweave.log.info(
                "Clashweave: joined with negotiated rules {} ({} ms to derive them) and weapon table {}",
                message.effectiveHash,
                String.format(java.util.Locale.ROOT, "%.1f", derived / 1e6),
                message.items);
        } catch (RuntimeException e) {
            Clashweave.log.error("Clashweave: server content sync rejected: {}", String.valueOf(e));
            rejectContent("Clashweave: invalid server content sync");
        }
    }

    private void rejectContent(String reason) {
        CombatWeapons.install(null);
        minecraft.getNetHandler()
            .getNetworkManager()
            .closeChannel(new net.minecraft.util.ChatComponentText(reason));
    }

    public long stamp() {
        // A received END tick is an observation, not a synchronized clock. Keep one tick
        // of phase uncertainty so a boundary input cannot claim the next server tick early.
        return serverTick + Math.max(0, Math.min(2, Math.max(0, System.nanoTime() - anchor) / tickPeriod) - 1);
    }

    public Visual own() {
        return minecraft.thePlayer == null ? null : visualOf(minecraft.thePlayer.getEntityId());
    }

    public boolean engaged() {
        Visual own = own();
        return predictedEngagement || !predictedAction.isEmpty() || own != null && own.state.engaged;
    }

    public String action(Visual visual) {
        if (visual == own() && !predictedAction.isEmpty()) return predictedAction;
        return visual == null ? "" : visual.state.action;
    }

    public double elapsed(Visual visual, float partial) {
        if (visual == null) return 0;
        // Predict the entry pose immediately; advance only after the server assigns an instance.
        // Advancing an unacknowledged timeline caused visible rewinds on every round trip.
        if (visual == own() && !predictedAction.isEmpty()) return 0;
        long start = visual.state.start;
        // Wall interpolation already accounts for the fractional frame. Do not add partial ticks twice.
        return Math.max(0, visual.state.tick + (System.nanoTime() - visual.received) / (double) tickPeriod - start);
    }

    public void send(Intent intent, int target) {
        if (session == 0 || minecraft.thePlayer == null) return;
        long pressed = System.nanoTime();
        int next = ++sequence;
        Visual observed = own();
        InputMessage request = new InputMessage(session, stamp(), next, intent, target);
        request.origin = observed == null ? 0 : observed.state.instance;
        request.yaw = minecraft.thePlayer.rotationYaw;
        request.pitch = minecraft.thePlayer.rotationPitch;
        // Publish the press-time look before the intent on the same connection. C03 imposes no turn rate.
        if (intent == Intent.LIGHT || intent == Intent.HEAVY || intent == Intent.SHEATHE)
            minecraft.thePlayer.sendQueue.addToSendQueue(
                new net.minecraft.network.play.client.C03PacketPlayer.C05PacketPlayerLook(
                    request.yaw,
                    request.pitch,
                    physicalGround));
        Clashweave.network.sendToServer(request);
        if (intent == Intent.LIGHT || intent == Intent.HEAVY || intent == Intent.SHEATHE) {
            Visual own = own();
            String prediction = "";
            ActionCatalog.Definition predicted = null;
            if (own != null && own.state.action.isEmpty() && own.catalog != null) {
                // The style's own entry rules: the same table the server's scheduler consults.
                String entryAction = own.catalog.entryTarget(own.state.sheathed, intent);
                if (entryAction != null) {
                    prediction = entryAction;
                    predicted = own.catalog.byId(entryAction);
                }
            }
            if (!prediction.isEmpty() && intent != Intent.SHEATHE) {
                minecraft.thePlayer.playSound("random.bow", .35F, .75F);
                if (Boolean.getBoolean("clashweave.trace")) System.out
                    .println("CW_LOCAL_SWING seq=" + next + " press=" + pressed + " feedback=" + System.nanoTime());
            }
            predictedAction = prediction;
            predictedDefinition = predicted;
            predictedSequence = next;
            predictedEngagement = !prediction.isEmpty();
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (minecraft.thePlayer == null || minecraft.theWorld == null) {
            lockCamera.clear();
            incoming.clear();
            events.clear();
            lastEvent = 0;
            visuals.clear();
            byEntity.clearMap();
            authoritative.clear();
            syncs.clear();
            if (helloSent || localTable != null) {
                // Back to this client's own table and rules until the next server tells it otherwise.
                helloSent = false;
                rules = Clashweave.content.rules;
                CombatWeapons.install(localTable);
            }
            session = 0;
            predictedEngagement = false;
            predictedAction = "";
            return;
        }
        if (!warmed) {
            warmed = true;
            PresentationAssets.warm(minecraft.getTextureManager());
        }
        if (localTable == null) localTable = CombatWeapons.table();
        handshake();
        ContentSyncMessage sync;
        while ((sync = syncs.poll()) != null) synchronize(sync);
        StateMessage message;
        while ((message = incoming.poll()) != null) apply(message);
        com.layue13.clashweave.network.SemanticMessage semantic;
        while ((semantic = events.poll()) != null) if (semantic.id > lastEvent) {
            lastEvent = semantic.id;
            presentation.emit(semantic.event);
            if (Boolean.getBoolean("clashweave.trace")) System.out.println(
                "CW_PRESENT id=" + semantic.id
                    + " kind="
                    + semantic.event.kind
                    + " actor="
                    + semantic.event.actor
                    + " owner="
                    + minecraft.thePlayer.getEntityId());
        }
        lockCamera.tick();
        boolean armed = CombatServer.armed(minecraft.thePlayer);
        boolean openGui = minecraft.currentScreen != null;
        boolean shiftNow = minecraft.gameSettings.keyBindSneak.getIsKeyPressed();
        if (!armed || openGui || !engaged()) {
            if (sentGuard) send(Intent.GUARD_RELEASE, -1);
            sentGuard = false;
        } else if (shiftNow != shift) {
            send(shiftNow ? Intent.GUARD_PRESS : Intent.GUARD_RELEASE, -1);
            sentGuard = shiftNow;
        }
        shift = shiftNow;
        if (armed && !openGui) {
            while (sheathe.isPressed()) send(Intent.SHEATHE, -1);
            while (dodge.isPressed()) send(Intent.DODGE, -1);
            while (special.isPressed()) send(Intent.SPECIAL, -1);
            while (mode.isPressed()) send(Intent.MODE, -1);
            KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindAttack.getKeyCode(), false);
            if (engaged()) KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindUseItem.getKeyCode(), false);
            if (!(minecraft.thePlayer.movementInput instanceof CombatMovementInput)) {
                minecraft.thePlayer.movementInput = new CombatMovementInput(minecraft.thePlayer.movementInput);
            }
            minecraft.thePlayer.movementInput.updatePlayerMoveState();
            physicalGround = minecraft.thePlayer.onGround;
            Visual visual = own();
            if (visual != null && visual.movementReady && visual.definition != null && !visual.state.action.isEmpty()) {
                ActionCatalog.Definition definition = visual.definition;
                double delta = Math.min(definition.speed, definition.distance - visual.moved);
                if (delta > 0 && elapsed(visual, 0) < definition.duration()) {
                    double yaw = Math.toRadians(visual.state.yaw);
                    minecraft.thePlayer.moveEntity(-Math.sin(yaw) * delta, 0, Math.cos(yaw) * delta);
                    visual.moved += delta;
                }
            }
        }
        if (openGui && !gui) {
            send(Intent.CLEAR_BUFFER, -1);
            predictedAction = "";
        }
        gui = openGui;
    }

    private void apply(StateMessage message) {
        Visual previous = visuals.get(message.entity);
        if (previous != null && message.revision < previous.state.revision) return;
        long now = System.nanoTime();
        boolean local = message.entity == minecraft.thePlayer.getEntityId();
        if (local && Boolean.getBoolean("clashweave.trace")
            && (previous == null || message.revision != previous.state.revision)) {
            System.out.println(
                "CW_ENGAGEMENT player=" + minecraft.thePlayer.getCommandSenderName()
                    + " engaged="
                    + message.engaged
                    + " revision="
                    + message.revision
                    + " tick="
                    + message.tick
                    + " receive="
                    + now);
        }
        if (local && message.session != 0) {
            if (!predictedAction.isEmpty() && message.ack >= predictedSequence
                && Boolean.getBoolean("clashweave.trace")) {
                System.out.println(
                    "CW_ALIGN player=" + minecraft.thePlayer.getCommandSenderName()
                        + " seq="
                        + predictedSequence
                        + " predicted="
                        + 0
                        + " authoritative="
                        + (message.tick - message.start)
                        + " sameAction="
                        + message.action.equals(predictedAction));
            }
            if (session != message.session) {
                sequence = 0;
                predictedAction = "";
                sentGuard = false;
                observedClockTick = 0;
                observedClockNano = 0;
                tickPeriod = 50_000_000L;
            }
            session = message.session;
            if (message.tick > observedClockTick && observedClockNano != 0) {
                long period = (now - observedClockNano) / (message.tick - observedClockTick);
                tickPeriod = Math.max(50_000_000L, Math.min(1_000_000_000L, period));
            }
            if (message.tick > observedClockTick) {
                observedClockTick = message.tick;
                observedClockNano = now;
            }
            serverTick = message.tick;
            anchor = now;
            if (!message.definitions.isEmpty()) {
                authoritative.put(message.style, new ActionCatalog(new StringReader(message.definitions)));
                InputMessage acknowledgement = new InputMessage(session, stamp(), ++sequence, null, -1);
                acknowledgement.kind = -2;
                // Names the style received, so the server can tell this ack from one for a previous weapon.
                acknowledgement.target = message.style.hashCode();
                Clashweave.network.sendToServer(acknowledgement);
            }
            if (message.ack >= predictedSequence && message.ack > 0) {
                if (!predictedAction.isEmpty() && !message.action.equals(predictedAction)) corrections++;
                predictedAction = "";
            }
            predictedEngagement = false;
            if (!message.result.isEmpty()) {
                feedback = message.result;
                if (message.feedbackFrozen != 0) {
                    lastFeedbackFrozen = message.feedbackFrozen;
                    System.out.println(
                        "CW_FEEDBACK player=" + minecraft.thePlayer.getCommandSenderName()
                            + " result="
                            + feedback
                            + " frozen="
                            + message.feedbackFrozen
                            + " receive="
                            + now
                            + " corrections="
                            + corrections);
                }
                if (message.result.equals("MOVE_CORRECT")) corrections++;

            }
        }
        Visual visual = new Visual();
        visual.state = message;
        visual.received = now;
        if (previous != null && previous.state.instance == message.instance) {
            visual.moved = previous.moved;
            visual.movementReady = previous.movementReady;
        }
        if (local && message.result.equals("MOVE_READY")) visual.movementReady = true;
        resolve(visual, previous);
        if (local) actions = visual.catalog;
        visuals.put(message.entity, visual);
        byEntity.addKey(message.entity, visual);
        if (local && message.instance != 0 && (previous == null || previous.state.instance != message.instance)) {
            send(null, (int) message.instance);
        }
    }

    /** Follows the message's IDs to prebuilt objects once, so a frame never looks anything up by string. */
    private void resolve(Visual visual, Visual previous) {
        StateMessage message = visual.state;
        ActionCatalog catalog = catalogFor(message.style);
        visual.catalog = catalog;
        if (previous != null && previous.render != null && previous.state.appearance.equals(message.appearance)) {
            visual.render = previous.render;
            visual.preset = previous.preset;
        } else {
            visual.render = PresentationAssets.render(message.appearance.skin);
            visual.preset = PresentationAssets.preset(message.appearance.animations);
        }
        boolean acting = catalog != null && !message.action.isEmpty();
        visual.definition = acting ? catalog.byId(message.action) : null;
        visual.returnsToSheath = acting && catalog.entersSheathed(message.action);
    }

    private boolean blockInteraction() {
        Visual own = own();
        MovingObjectPosition hit = minecraft.objectMouseOver;
        return !engaged() && own != null
            && own.state.blocks
            && hit != null
            && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && BlockInteraction.interactive(minecraft.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ));
    }

    @SubscribeEvent
    public void mouse(MouseEvent event) {
        if (event.dx != 0 || event.dy != 0) lockCamera.mouse(System.nanoTime(), 0);
        if (minecraft.thePlayer == null || minecraft.currentScreen != null || !CombatServer.armed(minecraft.thePlayer))
            return;
        if (event.button == 0 || event.button == 1 && !blockInteraction() || event.button == 2) {
            event.setCanceled(true);
            if (event.buttonstate) {
                send(
                    event.button == 0 ? Intent.LIGHT : event.button == 1 ? Intent.HEAVY : Intent.LOCK,
                    minecraft.objectMouseOver != null && minecraft.objectMouseOver.entityHit != null
                        ? minecraft.objectMouseOver.entityHit.getEntityId()
                        : -1);
            }
        }
    }

    @SubscribeEvent
    public void cameraKey(cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent event) {
        if (Keyboard.getEventKeyState()
            && Keyboard.getEventKey() == minecraft.gameSettings.keyBindTogglePerspective.getKeyCode())
            lockCamera.manualViewChange();
    }

    @SubscribeEvent
    public void hud(RenderGameOverlayEvent.Text event) {
        if (minecraft.thePlayer == null || !CombatServer.armed(minecraft.thePlayer)) return;
        Visual own = own();
        event.left.add("Clashweave " + (engaged() ? "ENGAGED" : "PEACE") + " " + feedback);
        if (Boolean.getBoolean("clashweave.trace") && lastFeedbackFrozen != 0
            && frameFeedbackFrozen != lastFeedbackFrozen) {
            frameFeedbackFrozen = lastFeedbackFrozen;
            System.out.println("CW_FRAME frozen=" + lastFeedbackFrozen + " frame=" + System.nanoTime());
        }
        if (own != null) event.left.add(action(own) + " " + (int) elapsed(own, 0) + "t #" + own.state.instance);
    }

    private final class CombatMovementInput extends MovementInput {

        private final MovementInput original;

        CombatMovementInput(MovementInput original) {
            this.original = original;
        }

        @Override
        public void updatePlayerMoveState() {
            original.updatePlayerMoveState();
            moveStrafe = original.moveStrafe;
            moveForward = original.moveForward;
            jump = original.jump;
            sneak = original.sneak;
            if (CombatServer.armed(minecraft.thePlayer) && minecraft.currentScreen == null && engaged()) {
                if (sneak) {
                    moveStrafe /= 0.3f;
                    moveForward /= 0.3f;
                }
                sneak = false;
            }
        }
    }
}
