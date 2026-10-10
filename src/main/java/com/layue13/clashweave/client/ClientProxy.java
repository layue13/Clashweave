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
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.Intent;
import com.layue13.clashweave.forge.BlockInteraction;
import com.layue13.clashweave.forge.CombatServer;
import com.layue13.clashweave.forge.CommonProxy;
import com.layue13.clashweave.network.InputMessage;
import com.layue13.clashweave.network.StateMessage;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Client state belongs to an authoritative instance, never to a camera mode. */
public final class ClientProxy extends CommonProxy {

    public static final class Visual {

        public StateMessage state;
        public long received;
        public double moved;
        public boolean movementReady;
    }

    public static ClientProxy instance;
    public final Map<Integer, Visual> visuals = new HashMap<>();
    public ActionCatalog actions;
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
        presentation.subscribe(new DefaultPresentation(minecraft, this));
        ClientRegistry.registerKeyBinding(sheathe);
        ClientRegistry.registerKeyBinding(dodge);
        ClientRegistry.registerKeyBinding(special);
        ClientRegistry.registerKeyBinding(mode);
        cpw.mods.fml.client.registry.RenderingRegistry
            .registerEntityRenderingHandler(net.minecraft.entity.player.EntityPlayer.class, new KatanaPlayerRenderer());
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new KatanaRendering());
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        KatanaRendering.load();
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

    public long stamp() {
        // A received END tick is an observation, not a synchronized clock. Keep one tick
        // of phase uncertainty so a boundary input cannot claim the next server tick early.
        return serverTick + Math.max(0, Math.min(2, Math.max(0, System.nanoTime() - anchor) / tickPeriod) - 1);
    }

    public Visual own() {
        return minecraft.thePlayer == null ? null : visuals.get(minecraft.thePlayer.getEntityId());
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
                    minecraft.thePlayer.onGround));
        Clashweave.network.sendToServer(request);
        if (intent == Intent.LIGHT || intent == Intent.HEAVY || intent == Intent.SHEATHE) {
            Visual own = own();
            String prediction = "";
            if (own != null && own.state.action.isEmpty()) {
                if (intent == Intent.LIGHT) prediction = own.state.sheathed ? "iai" : "light_1";
                else if (intent == Intent.HEAVY && !own.state.sheathed) prediction = "heavy";
                else if (intent == Intent.SHEATHE && !own.state.sheathed) prediction = "sheathe";
            }
            if (!prediction.isEmpty() && intent != Intent.SHEATHE) {
                minecraft.thePlayer.playSound("random.bow", .35F, .75F);
                if (Boolean.getBoolean("clashweave.trace")) System.out
                    .println("CW_LOCAL_SWING seq=" + next + " press=" + pressed + " feedback=" + System.nanoTime());
            }
            predictedAction = prediction;
            predictedSequence = next;
            predictedEngagement = !prediction.isEmpty();
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (minecraft.thePlayer == null || minecraft.theWorld == null) {
            incoming.clear();
            events.clear();
            lastEvent = 0;
            visuals.clear();
            session = 0;
            predictedEngagement = false;
            predictedAction = "";
            return;
        }
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
            Visual visual = own();
            if (visual != null && visual.movementReady && actions != null && !visual.state.action.isEmpty()) {
                ActionCatalog.Definition definition = actions.get(visual.state.action);
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
                actions = new ActionCatalog(new StringReader(message.definitions));
                InputMessage acknowledgement = new InputMessage(session, stamp(), ++sequence, null, -1);
                acknowledgement.kind = -2;
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
        visuals.put(message.entity, visual);
        if (local && message.instance != 0 && (previous == null || previous.state.instance != message.instance)) {
            send(null, (int) message.instance);
        }
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
