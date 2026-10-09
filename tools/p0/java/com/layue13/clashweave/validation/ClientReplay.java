package com.layue13.clashweave.validation;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.common.MinecraftForge;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Isolated script drives production intents and captures actual framebuffer, never supplies hits. */
public final class ClientReplay {
    private final Minecraft mc = Minecraft.getMinecraft();
    private int ticks;
    private int connected;
    private long requested;
    private int cycle;
    private final Set<String> captures = new HashSet<>();
    private String capture;
    private long guardInstance;
    private int releaseAt;
    private boolean locked;
    private int movementCase;
    private long movementInstance;
    private int movementSteps;
    private long endedOrigin;
    private boolean lateInjected;
    private long engagementBase = -1;
    private int engagementStage;
    private final java.util.Map<Integer,Boolean> virtualKeys = new java.util.HashMap<>();

    private void virtualKey(KeyBinding binding, boolean down) {
        virtualKeys.put(binding.getKeyCode(), down);
        KeyBinding.setKeyBindState(binding.getKeyCode(), down);
    }

    public ClientReplay() {
        // Vanilla's asynchronous initial audio loader can overlap FML's immediate resource reload.
        // Wait only in this fixture; do not patch the production SoundManager.
        try {
            Object manager = cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.audio.SoundHandler.class,
                mc.getSoundHandler(), "sndManager", "field_147694_f");
            long until = System.nanoTime() + 3_000_000_000L;
            while (System.nanoTime() < until && !(Boolean) cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(
                net.minecraft.client.audio.SoundManager.class, (net.minecraft.client.audio.SoundManager) manager, "loaded", "field_148617_f")) Thread.sleep(10);
            System.out.println("P0_AUDIO initial loader settled");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        if (mc.thePlayer == null) {
            if (ticks == 40 && mc.currentScreen instanceof GuiMainMenu) {
                cpw.mods.fml.client.FMLClientHandler.instance().setupServerList();
                cpw.mods.fml.client.FMLClientHandler.instance().connectToServer(null, new ServerData("P0", "127.0.0.1:" + Integer.getInteger("cw.p0.port", 25580)));
            }
            return;
        }
        if (Boolean.getBoolean("cw.p0.supplement") && connected > 60 && mc.thePlayer.getHealth() <= 0) {
            if (ticks % 10 == 0) mc.thePlayer.respawnPlayer();
            return;
        }
        ClientProxy proxy = ClientProxy.instance;
        ClientProxy.Visual visual = proxy.own();
        if (visual == null || proxy.actions == null) return;
        connected++;
        if (Boolean.getBoolean("cw.p0.manual")) return;
        if (Boolean.getBoolean("cw.p0.engagement")) {
            if (mc.thePlayer.getCommandSenderName().equals("P0A")) {
                if (engagementBase < 0 && proxy.visuals.size() >= 2) engagementBase = proxy.stamp();
                long phase = engagementBase < 0 ? -1 : proxy.stamp() - engagementBase;
                if (phase >= 300 && engagementStage == 0) {
                    for (ClientProxy.Visual remote : proxy.visuals.values()) if (remote != visual) { proxy.send(Intent.LOCK, remote.state.entity); break; }
                    engagementStage++;
                }
                if (phase >= 340 && engagementStage == 1) { proxy.send(Intent.LOCK, -1); engagementStage++; }
                if (phase >= 440 && engagementStage == 2) { proxy.send(Intent.LIGHT, -1); engagementStage++; }
                if (connected % 25==0) {
                    mc.playerController.onPlayerRightClick(mc.thePlayer,mc.theWorld,mc.thePlayer.getHeldItem(),0,64,1,1,net.minecraft.util.Vec3.createVectorHelper(.5,65,1.5));
                    System.out.println("P0_ENGAGEMENT attemptChest engaged="+proxy.engaged());
                }
                if (mc.currentScreen!=null && connected % 25==4) mc.thePlayer.closeScreen();
                // A held key crosses several state transitions; it must not create a new press.
                virtualKey(mc.gameSettings.keyBindSneak,!(phase>=170 && phase<205));
                virtualKey(mc.gameSettings.keyBindForward,phase>=320 && phase<325);
                System.out.println("P0_ENGAGEMENT client tick="+connected+" engaged="+proxy.engaged()+" sneak="+mc.thePlayer.movementInput.sneak+" forward="+mc.thePlayer.movementInput.moveForward);
            }
            return;
        }
        if (Boolean.getBoolean("cw.p0.movement")) {
            movement(proxy, visual);
            return;
        }
        if (Boolean.getBoolean("cw.p0.supplement")) {
            supplement(proxy, visual);
            return;
        }
        mc.gameSettings.pauseOnLostFocus = false;
        mc.gameSettings.limitFramerate = 60;
        boolean attacker = mc.thePlayer.getCommandSenderName().equals("P0A");
        if (!attacker) {
            for (Object object : mc.theWorld.playerEntities) {
                net.minecraft.entity.player.EntityPlayer opponent = (net.minecraft.entity.player.EntityPlayer) object;
                if (opponent != mc.thePlayer) mc.thePlayer.rotationYaw = (float) Math.toDegrees(Math.atan2(
                    -(opponent.posX - mc.thePlayer.posX), opponent.posZ - mc.thePlayer.posZ));
            }
            if (!locked && connected > 20) {
                for (ClientProxy.Visual remote : proxy.visuals.values()) {
                    if (remote != visual) {
                        proxy.send(Intent.LOCK, remote.state.entity);
                        locked = true;
                        break;
                    }
                }
            }
            for (ClientProxy.Visual remote : proxy.visuals.values()) {
                if (remote != visual && remote.state.instance != 0 && remote.state.instance != guardInstance
                    && proxy.elapsed(remote, 0) >= 1 && !remote.state.action.equals("sheathe")) {
                    guardInstance = remote.state.instance;
                    virtualKey(mc.gameSettings.keyBindSneak, true);
                    releaseAt = connected + 9;
                }
            }
            if (connected >= releaseAt) virtualKey(mc.gameSettings.keyBindSneak, false);
            if (connected % 20 == 0) System.out.println("P0_REMOTE action=" + proxy.action(visual) + " sneak=" + mc.thePlayer.movementInput.sneak);
            return;
        }
        double elapsed = proxy.elapsed(visual, 0);
        String action = visual.state.action;
        if (!Boolean.getBoolean("cw.p0.renderOnly")) {
            if (endedOrigin==0 && visual.state.instance!=0) endedOrigin=visual.state.instance;
            if (endedOrigin!=0 && visual.state.instance==0 && !lateInjected) {
                try {
                    java.lang.reflect.Field sequence = ClientProxy.class.getDeclaredField("sequence");
                    sequence.setAccessible(true);
                    int next=sequence.getInt(proxy)+1;
                    com.layue13.clashweave.network.InputMessage stale = new com.layue13.clashweave.network.InputMessage(visual.state.session,proxy.stamp(),next,Intent.LIGHT,-1);
                    stale.origin=endedOrigin;
                    com.layue13.clashweave.Clashweave.network.sendToServer(stale);
                    sequence.setInt(proxy,next);
                    lateInjected=true;
                    System.out.println("P0_LATE_WIRE seq="+next+" origin="+endedOrigin);
                } catch(Exception exception) { throw new IllegalStateException(exception); }
            }
            if (visual.state.instance==0 && !proxy.action(visual).isEmpty()) System.out.println("P0_PENDING engaged="+proxy.engaged());
        }
        if (Boolean.getBoolean("cw.p0.renderOnly")) {
            if (connected==44) { mc.gameSettings.thirdPersonView=0; mc.thePlayer.rotationPitch=70; }
            if (connected==46) capture="pose-waist-0";
            if (connected==47) mc.thePlayer.rotationPitch=0;
            virtualKey(mc.gameSettings.keyBindForward,connected>=48 && connected<58);
            virtualKey(mc.gameSettings.keyBindJump,connected>=60 && connected<68);
            virtualKey(mc.gameSettings.keyBindSneak,connected>=72 && connected<79);
            String pose = connected==52 ? "walk" : connected==66 ? "jump" : connected==75 ? "sneak" : null;
            if (pose != null) {
                System.out.println("P0_POSE "+pose+" onGround="+mc.thePlayer.onGround+" feetY="+mc.thePlayer.boundingBox.minY+" water="+mc.thePlayer.isInWater());
                mc.gameSettings.thirdPersonView=1;
                capture="pose-"+pose+"-1";
            }
        }
        if (connected == 80) request(Intent.LIGHT);
        if (connected > 100 && action.isEmpty() && connected % 25 == 0) {
            cycle++;
            requested = -1;
            request(visual.state.sheathed ? Intent.LIGHT : cycle % 3 == 0 ? Intent.SHEATHE : cycle % 3 == 1 ? Intent.LIGHT : Intent.HEAVY);
        }
        if (visual.state.instance != requested && !action.isEmpty()) {
            // Queue before the cancel window; the production scheduler still requires confirmed contact.
            boolean chain = action.equals("iai") && elapsed >= 6 && elapsed < 8
                || action.equals("light_1") && elapsed >= 5 && elapsed < 7
                || action.equals("light_2") && elapsed >= 4 && elapsed < 6;
            if (chain) {
                requested = visual.state.instance;
                request(action.equals("light_1") && cycle % 2 == 1 ? Intent.HEAVY : Intent.LIGHT);
            }
        }
        String phase = action.equals("sheathe") && elapsed >= 2 && elapsed <= 5 ? "sheathe"
            : action.equals("iai") && elapsed >= 1 && elapsed <= 4 ? "draw"
            : action.equals("heavy") && elapsed >= 6 && elapsed < 7 ? "swing"
            : visual.state.sheathed && action.isEmpty() && connected > 40 ? "sheathed" : null;
        if (phase != null && capture == null) {
            for (int view : new int[] {0, 2}) {
                String key = phase + "-" + view;
                if (!captures.contains(key)) {
                    mc.gameSettings.thirdPersonView = view;
                    // Camera turns around the committed body; the fixture never steers its frozen attack.
                    mc.thePlayer.rotationYaw = visual.state.yaw + 40;
                    capture = key;
                    break;
                }
            }
        }
        if (connected % 40 == 0) System.out.println("P0_REPLAY tick=" + connected + " action=" + action + " progress=" + elapsed + " feedback=" + proxy.feedback);
    }

    private void request(Intent intent) {
        mc.thePlayer.rotationYaw=0;
        ClientProxy.instance.send(intent, -1);
        System.out.println("P0_REQUEST intent=" + intent + " nano=" + System.nanoTime());
    }

    private void supplement(ClientProxy proxy, ClientProxy.Visual visual) {
        if (mc.thePlayer.isDead) {
            mc.thePlayer.respawnPlayer();
            return;
        }
        if (connected == 20) {
            for (Object object : mc.theWorld.playerEntities) {
                net.minecraft.entity.player.EntityPlayer other = (net.minecraft.entity.player.EntityPlayer) object;
                if (other != mc.thePlayer) proxy.send(Intent.LOCK, other.getEntityId());
            }
        }
        if (connected == 40 && mc.thePlayer.getCommandSenderName().equals("P0A")) {
            try {
                java.lang.reflect.Field sequence = ClientProxy.class.getDeclaredField("sequence");
                java.lang.reflect.Field token = ClientProxy.class.getDeclaredField("session");
                sequence.setAccessible(true);
                token.setAccessible(true);
                int next = sequence.getInt(proxy);
                long session = token.getLong(proxy);
                for (String attack : new String[] {"SESSION", "REPLAY", "FUTURE", "STALE"}) {
                    long stamp = proxy.stamp();
                    int seq = attack.equals("REPLAY") ? 1 : ++next;
                    if (attack.equals("FUTURE")) stamp += 1000;
                    if (attack.equals("STALE")) stamp -= 1000;
                    com.layue13.clashweave.Clashweave.network.sendToServer(new com.layue13.clashweave.network.InputMessage(
                        attack.equals("SESSION") ? session + 1 : session, stamp, seq, Intent.SPECIAL, -1));
                    System.out.println("P0_FORGED test=" + attack + " seq=" + seq + " stamp=" + stamp);
                }
                sequence.setInt(proxy, next);
            } catch (Exception exception) { throw new IllegalStateException(exception); }
        }
        if (connected == 400 && mc.thePlayer.getCommandSenderName().equals("P0B")) mc.getNetHandler().getNetworkManager().closeChannel(
            new net.minecraft.util.ChatComponentText("P0 lifecycle logout"));
    }

    private void movement(ClientProxy proxy, ClientProxy.Visual visual) {
        if (!mc.thePlayer.getCommandSenderName().equals("P0A")) return;
        if (proxy.visuals.size() < 2) return;
        if (connected > 80 && visual.state.action.isEmpty() && connected % 25 == 0) request(Intent.LIGHT);
        if (visual.state.instance != 0 && visual.state.instance != movementInstance) {
            movementInstance = visual.state.instance;
            movementCase++;
            movementSteps = 0;
        }
        virtualKey(mc.gameSettings.keyBindForward, (movementCase == 3 || movementCase==10 || movementCase==11) && !visual.state.action.isEmpty());
        virtualKey(mc.gameSettings.keyBindRight, movementCase == 2 && !visual.state.action.isEmpty());
        if (visual.movementReady && !visual.state.action.isEmpty()) {
            movementSteps++;
            virtualKey(mc.gameSettings.keyBindJump,movementCase==12 && movementSteps<=8);
            if (movementCase==12 && movementSteps<=10) System.out.println("P0_MOVEMENT jumpStep="+movementSteps+" jumpInput="+mc.thePlayer.movementInput.jump+" onGround="+mc.thePlayer.onGround+" water="+mc.thePlayer.isInWater()+" feetY="+mc.thePlayer.boundingBox.minY);
            if (movementCase == 5 || movementCase == 6) {
                com.layue13.clashweave.core.ActionCatalog.Definition definition = proxy.actions.get(visual.state.action);
                double extra = Math.min(definition.distance - visual.moved, definition.speed * (movementCase == 5 ? .2 : .5));
                if (extra > 0) {
                    mc.thePlayer.moveEntity(0, 0, extra);
                    visual.moved += extra;
                }
            }
            if (movementSteps == 1 && movementCase == 7) {
                mc.thePlayer.setPosition(mc.thePlayer.posX+5, mc.thePlayer.posY, mc.thePlayer.posZ);
                mc.getNetHandler().addToSendQueue(new net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX,mc.thePlayer.boundingBox.minY,mc.thePlayer.posY,mc.thePlayer.posZ,true));
                System.out.println("P0_MOVEMENT inject=horizontal5 nano=" + System.nanoTime());
            }
            if (movementSteps == 1 && movementCase == 8) {
                mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY+5, mc.thePlayer.posZ);
                mc.getNetHandler().addToSendQueue(new net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX,mc.thePlayer.boundingBox.minY,mc.thePlayer.posY,mc.thePlayer.posZ,true));
                System.out.println("P0_MOVEMENT inject=vertical5 nano=" + System.nanoTime());
            }
            if (movementSteps == 1 && movementCase == 9) {
                for (int packet=0;packet<3;packet++) mc.getNetHandler().addToSendQueue(new net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX,mc.thePlayer.boundingBox.minY,mc.thePlayer.posY,mc.thePlayer.posZ+2+packet*.1,true));
                System.out.println("P0_MOVEMENT inject=wall3 nano=" + System.nanoTime());
            }
        }
        if (connected % 25 == 0) System.out.println("P0_MOVEMENT clientCase=" + movementCase + " x=" + mc.thePlayer.posX + " y=" + mc.thePlayer.posY + " z=" + mc.thePlayer.posZ + " corrections=" + proxy.corrections);
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || capture == null || mc.thePlayer == null) return;
        ScreenShotHelper.saveScreenshot(new File("."), "p0-" + capture + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        ClientProxy.Visual visual = ClientProxy.instance.own();
        System.out.println("P0_CAPTURE " + capture + " instance=" + visual.state.instance + " progress=" + ClientProxy.instance.elapsed(visual,0) + " nano=" + System.nanoTime());
        String previousCapture = capture;
        captures.add(capture);
        capture = null;
        if (!previousCapture.startsWith("pose-") && previousCapture.endsWith("-0")) {
            String paired=previousCapture.substring(0,previousCapture.length()-1)+"2";
            if (!captures.contains(paired)) { mc.gameSettings.thirdPersonView=2; capture=paired; }
        }
    }

    @SubscribeEvent
    public void audio(PlaySoundSourceEvent event) {
        if (event.name.equals("random.successful_hit") || event.name.equals("random.anvil_land")) System.out.println("P0_AUDIO source=" + event.name + " frozen=" + ClientProxy.instance.lastFeedbackFrozen + " nano=" + System.nanoTime());
    }

    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.HIGHEST)
    public void physicalMouse(net.minecraftforge.client.event.MouseEvent event) {
        // Automated fixtures use explicit intents. Desktop clicks must not contaminate the replay.
        // Manual mode retains the real production input mapping.
        if (!Boolean.getBoolean("cw.p0.manual")) event.setCanceled(true);
    }

    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.HIGHEST)
    public void physicalKeyboard(cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent event) {
        if (!Boolean.getBoolean("cw.p0.manual")) {
            KeyBinding.unPressAllKeys();
            for (java.util.Map.Entry<Integer,Boolean> key : virtualKeys.entrySet()) KeyBinding.setKeyBindState(key.getKey(),key.getValue());
        }
    }
}
