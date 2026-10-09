package com.layue13.clashweave.probe.client;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import com.layue13.clashweave.probe.ProbeBootstrap;
import com.layue13.clashweave.probe.ProbeConfig;
import com.layue13.clashweave.probe.ProbePacket;
import com.layue13.clashweave.probe.ProbeProxy;
import com.layue13.clashweave.probe.ProbeServer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ProbeClient extends ProbeProxy {

    private final ConcurrentLinkedQueue<ProbePacket> queue = new ConcurrentLinkedQueue<ProbePacket>();
    final Map<Integer, Swing> swings = new HashMap<Integer, Swing>();
    private final ProbeEdges edges = new ProbeEdges();
    private long localTick;
    private long lastEngage;
    private boolean forceEngage;
    private boolean engaged;
    private boolean connected;
    private int worldTicks;
    private int sequence;
    private long moveId;
    private int moveSample;
    private long moveAt;
    private double moveSpeed;
    private boolean invalidMove;
    private boolean moveActive;
    private int moveTrial;
    private int moveTotal;
    private long nextMove;
    private double predictedEnd;
    private long guardAt;
    private long guardStamp;
    private int guardTrial;
    private boolean guardPending;
    private boolean guardStarted;
    private boolean guardDone;
    private boolean visualsDone;
    private int visualTick;
    private int screenshots;
    private boolean screenshotPending;
    private int corrections;
    private volatile int wireCorrections;
    private boolean wireHooked;
    private boolean complete;
    private boolean velocityStarted;
    private long velocityAt;
    private volatile int wireVelocities;
    private boolean nativeOnly;
    private int threatEntity;
    private long threatCheck;

    static final class Swing {

        int instance;
        long localStart;
        int duration;

        float progress(long now, float partial) {
            return Math.max(0, Math.min(1, (now - localStart + partial) / duration));
        }
    }

    @Override
    public void init() {
        if (!ProbeConfig.enabled) return;
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        MinecraftForge.EVENT_BUS.register(this);
        ProbeRendering rendering = new ProbeRendering(this);
        MinecraftForge.EVENT_BUS.register(rendering);
        rendering.init();
    }

    @Override
    public void accept(ProbePacket packet) {
        queue.add(packet);
    }

    private boolean held(EntityPlayer p) {
        return p != null && p.getHeldItem() != null
            && p.getHeldItem()
                .getItem() == ProbeBootstrap.blade;
    }

    private boolean automatic() {
        return !System.getProperty("clashweave.probe.role", "")
            .isEmpty();
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        localTick++;
        if (!connected && automatic() && mc.currentScreen instanceof GuiMainMenu) {
            connected = true;
            mc.gameSettings.pauseOnLostFocus = false;
            mc.gameSettings.limitFramerate = 60;
            mc.gameSettings.renderDistanceChunks = 4;
            mc.gameSettings.showDebugInfo = false;
            cpw.mods.fml.client.FMLClientHandler.instance()
                .setupServerList();
            cpw.mods.fml.client.FMLClientHandler.instance()
                .connectToServer(
                    mc.currentScreen,
                    new net.minecraft.client.multiplayer.ServerData(
                        "Clashweave probes",
                        "127.0.0.1:" + Integer.getInteger("clashweave.probe.port", 25565)));
        }
        if (mc.thePlayer == null || mc.theWorld == null) return;
        worldTicks++;
        if (automatic()) {
            KeyBinding.unPressAllKeys();
            mc.setIngameNotInFocus();
        }
        if (!wireHooked) hookCorrections(mc);
        ProbePacket packet;
        while ((packet = queue.poll()) != null) receive(mc, packet);
        if (held(mc.thePlayer) && !(mc.thePlayer.movementInput instanceof ProbeMovementInput)) {
            mc.thePlayer.movementInput = new ProbeMovementInput(mc.thePlayer.movementInput);
            mc.playerController.resetBlockRemoving();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        }
        boolean threatened = forceEngage || mc.thePlayer.isSwingInProgress || mc.thePlayer.hurtTime > 0;
        for (Object object : mc.theWorld.loadedEntityList) {
            if (object instanceof EntityMob) {
                EntityMob mob = (EntityMob) object;
                if (mob.getAttackTarget() == mc.thePlayer
                    && mob.getDistanceToEntity(mc.thePlayer) <= ProbeConfig.engageRadius) threatened = true;
            }
        }
        if (threatened) lastEngage = localTick;
        engaged = threatened || (lastEngage != 0 && localTick - lastEngage <= ProbeConfig.disengageTicks);
        boolean active = held(mc.thePlayer) && engaged && mc.currentScreen == null;
        if (mc.thePlayer.movementInput instanceof ProbeMovementInput) {
            ProbeMovementInput input = (ProbeMovementInput) mc.thePlayer.movementInput;
            input.suppressSneak = active;
            if (!held(mc.thePlayer)) mc.thePlayer.movementInput = input.original;
        }
        int shiftCode = mc.gameSettings.keyBindSneak.getKeyCode();
        boolean shift = !automatic() && (shiftCode >= 0 ? org.lwjgl.input.Keyboard.isKeyDown(shiftCode)
            : org.lwjgl.input.Mouse.isButtonDown(shiftCode + 100));
        if (edges.sample(shift, active, mc.currentScreen != null)) {
            ProbeServer.log("S5_GUARD_PRESS tick=" + localTick);
            ProbeBootstrap.channel.sendToServer(new ProbePacket(4, ++sequence, mc.theWorld.getTotalWorldTime(), 0, 0));
        }
        if (guardPending && localTick >= guardAt) {
            guardPending = false;
            ProbeBootstrap.channel.sendToServer(new ProbePacket(4, ++sequence, guardStamp, 0, 0));
            ProbeServer.log("S3_SEND trial=" + guardTrial + " stamp=" + guardStamp + " local=" + localTick);
            if (Boolean.getBoolean("clashweave.probe.negative") && guardTrial == 19) {
                ProbeBootstrap.channel.sendToServer(new ProbePacket(4, sequence, guardStamp, 0, 0));
                ProbeBootstrap.channel.sendToServer(new ProbePacket(4, ++sequence, guardStamp - 20, 0, 0));
                ProbeBootstrap.channel.sendToServer(new ProbePacket(4, ++sequence, guardStamp + 100, 0, 0));
            }
        }
        if (moveActive && localTick >= moveAt && moveSample < ProbeConfig.stepTicks) {
            if (moveSample == 0) wireCorrections = 0;
            moveSample++;
            double dx = nativeOnly ? (moveSample == 1 ? 5 : 0) : invalidMove && moveSample == 1 ? 5 : moveSpeed;
            // Player self-motion is predicted through the same real world collision solver as vanilla.
            mc.thePlayer.moveEntity(dx, 0, 0);
            mc.thePlayer.motionX = mc.thePlayer.motionZ = 0;
            if (!nativeOnly) ProbeBootstrap.channel
                .sendToServer(new ProbePacket(3, moveSample, moveId, mc.thePlayer.posX, mc.thePlayer.posZ));
            predictedEnd = mc.thePlayer.posX;
            ProbeServer.log("S2_PREDICT sample=" + moveSample + " x=" + predictedEnd);
            if (moveSample == ProbeConfig.stepTicks) {
                moveActive = false;
                corrections = wireCorrections;
            }
        }
        if (threatCheck != 0 && localTick >= threatCheck) {
            net.minecraft.entity.Entity entity = mc.theWorld.getEntityByID(threatEntity);
            boolean localTarget = entity instanceof EntityMob && ((EntityMob) entity).getAttackTarget() == mc.thePlayer;
            ProbeServer.log(
                "S5_TARGET_REPLICA entityPresent=" + (entity != null)
                    + " localAttackTarget="
                    + localTarget
                    + " clientEngaged="
                    + engaged
                    + " serverTargetConfirmed=true");
            threatCheck = 0;
        }
        if (automatic() && "attacker".equals(System.getProperty("clashweave.probe.role"))) runAutomatic(mc);
    }

    private void receive(Minecraft mc, ProbePacket p) {
        if (p.kind == 2) {
            moveId = p.tick;
            moveSample = 0;
            moveSpeed = p.x;
            invalidMove = p.sequence % 3 == 2;
            nativeOnly = Boolean.getBoolean("clashweave.probe.negative") && p.sequence == 0;
            moveAt = localTick + 6; // Allow reset/chunk packets and their acknowledgement to finish.
            moveActive = true;
            wireCorrections = 0;
        }
        if (p.kind == 16) {
            threatEntity = p.sequence;
            threatCheck = localTick + 5;
        }
        if (p.kind == 5) forceEngage = p.sequence == 1;
        if (p.kind == 6) {
            Swing swing = new Swing();
            swing.instance = (int) p.tick;
            swing.localStart = localTick;
            swing.duration = (int) p.x;
            swings.put(p.sequence, swing);
            ProbeServer
                .log("S4_START entity=" + p.sequence + " instance=" + swing.instance + " localStart=" + localTick);
        }
        if (p.kind == 9) {
            ProbeServer.log(
                "S2_RESULT trial=" + moveTrial
                    + " predicted="
                    + predictedEnd
                    + " server="
                    + p.x
                    + " validated="
                    + p.z
                    + " delta="
                    + Math.abs(p.x - predictedEnd)
                    + " rejects="
                    + p.sequence
                    + " S08="
                    + wireCorrections
                    + " postMotionS08="
                    + (wireCorrections - corrections));
            moveTotal++;
            moveTrial++;
            nextMove = localTick + 10;
            if (moveTotal == 1) ProbeBootstrap.channel.sendToServer(new ProbePacket(15, 0, 0, 0, 0));
        }
        if (p.kind == 10) {
            ProbeServer.log("S3_CLIENT_SUMMARY success=" + p.sequence + " total=" + p.x);
            guardDone = true;
        }
        if (p.kind == 11) {
            guardTrial = p.sequence;
            guardStamp = (long) p.x - (long) p.z;
            double oneWayTicks = Integer.getInteger("clashweave.probe.rtt", 0) / 100.0;
            guardAt = localTick + Math.round(guardStamp - p.tick - oneWayTicks);
            guardPending = true;
            ProbeServer.log(
                "S3_SCHEDULE trial=" + guardTrial
                    + " lead="
                    + p.z
                    + " localDue="
                    + guardAt
                    + " calibratedOneWayTicks="
                    + oneWayTicks);
        }
    }

    private void hookCorrections(Minecraft mc) {
        try {
            net.minecraft.network.NetworkManager manager = mc.getNetHandler()
                .getNetworkManager();
            java.lang.reflect.Field field = cpw.mods.fml.relauncher.ReflectionHelper
                .findField(net.minecraft.network.NetworkManager.class, "channel", "field_150746_k");
            io.netty.channel.Channel channel = (io.netty.channel.Channel) field.get(manager);
            channel.pipeline()
                .addBefore("packet_handler", "cw_probe_count", new io.netty.channel.ChannelInboundHandlerAdapter() {

                    @Override
                    public void channelRead(io.netty.channel.ChannelHandlerContext context, Object message)
                        throws Exception {
                        if (message instanceof net.minecraft.network.play.server.S08PacketPlayerPosLook)
                            wireCorrections++;
                        if (message instanceof net.minecraft.network.play.server.S12PacketEntityVelocity) {
                            net.minecraft.network.play.server.S12PacketEntityVelocity velocity = (net.minecraft.network.play.server.S12PacketEntityVelocity) message;
                            if (velocity.func_149412_c() == mc.thePlayer.getEntityId()) {
                                wireVelocities++;
                                ProbeServer.log(
                                    "S2_VELOCITY_RECEIVED x=" + velocity.func_149411_d() / 8000.0
                                        + " y="
                                        + velocity.func_149410_e() / 8000.0);
                            }
                        }
                        super.channelRead(context, message);
                    }
                });
            wireHooked = true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void runAutomatic(Minecraft mc) {
        if (worldTicks == 80) ProbeInputReplay.run(mc);
        if (worldTicks > 100 && moveTotal < Integer.getInteger("clashweave.probe.moveTrials", 12)
            && !moveActive
            && localTick >= nextMove) {
            nextMove = localTick + 1000;
            ProbeBootstrap.channel.sendToServer(new ProbePacket(1, moveTrial, 0, 0, 0));
        }
        if (moveTotal >= Integer.getInteger("clashweave.probe.moveTrials", 12) && !velocityStarted) {
            velocityStarted = true;
            velocityAt = localTick;
            wireVelocities = 0;
            ProbeBootstrap.channel.sendToServer(new ProbePacket(14, 0, 0, 0, 0));
        }
        if (velocityStarted && localTick >= velocityAt + 40 && !guardStarted) {
            ProbeServer.log(
                "S2_VELOCITY_RESULT packets=" + wireVelocities
                    + " finalX="
                    + mc.thePlayer.posX
                    + " finalY="
                    + mc.thePlayer.posY);
            guardStarted = true;
            ProbeBootstrap.channel.sendToServer(new ProbePacket(7, 0, 0, 0, 0));
        }
        if (guardDone && !visualsDone) {
            visualTick++;
            if (visualTick == 1) {
                mc.gameSettings.thirdPersonView = 0;
                mc.thePlayer.rotationYaw = 0;
                mc.thePlayer.rotationPitch = 0;
                ProbeBootstrap.channel.sendToServer(new ProbePacket(6, 0, 0, 0, 0));
            }
            Swing swing = swings.get(mc.thePlayer.getEntityId());
            if (swing != null) {
                long age = localTick - swing.localStart;
                if (age == 2 || age == 6 || age == 9) {
                    mc.gameSettings.thirdPersonView = age == 2 ? 0 : age == 6 ? 1 : 2;
                    screenshotPending = true;
                    ProbeServer.log(
                        "S4_VIEW view=" + mc.gameSettings.thirdPersonView
                            + " instance="
                            + swing.instance
                            + " elapsed="
                            + age
                            + " progress="
                            + swing.progress(localTick, 0));
                }
                if (age > swing.duration + 5) visualsDone = true;
            }
        }
        if (visualsDone && !complete) {
            complete = true;
            ProbeServer.log("CLIENT_COMPLETE moves=" + moveTotal + " screenshots=" + screenshots);
            if (Boolean.getBoolean("clashweave.probe.exit")) mc.shutdown();
        }
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END && screenshotPending) {
            Minecraft mc = Minecraft.getMinecraft();
            screenshotPending = false;
            screenshots++;
            ScreenShotHelper.saveScreenshot(
                mc.mcDataDir,
                "cw-probe-view-" + mc.gameSettings.thirdPersonView + ".png",
                mc.displayWidth,
                mc.displayHeight,
                mc.getFramebuffer());
        }
    }

    @SubscribeEvent
    public void mouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!held(mc.thePlayer) || mc.currentScreen != null || (event.button != 0 && event.button != 1)) return;
        MovingObjectPosition hit = mc.objectMouseOver;
        boolean allowBlock = event.button == 1 && !engaged
            && ProbeConfig.allowBlocks
            && hit != null
            && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && com.layue13.clashweave.probe.ProbeInteraction
                .interactive(mc.theWorld, hit.blockX, hit.blockY, hit.blockZ);
        if (allowBlock) return;
        event.setCanceled(true);
        if (event.buttonstate && !automatic()) {
            ProbeBootstrap.channel.sendToServer(new ProbePacket(6, 0, 0, 0, 0));
            ProbeServer.log(
                "S5_MOUSE intent=" + (event.button == 0 ? "LIGHT_ATTACK" : "HEAVY_ATTACK") + " vanillaCanceled=true");
        }
    }

    @SubscribeEvent
    public void hud(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!held(mc.thePlayer)) return;
        Swing swing = swings.get(mc.thePlayer.getEntityId());
        event.left.add("Clashweave S1-S5 probes | " + (engaged ? "ENGAGED" : "PEACEFUL"));
        if (swing != null)
            event.left.add("instance=" + swing.instance + " progress=" + swing.progress(localTick, event.partialTicks));
    }

    long clock() {
        return localTick;
    }
}
