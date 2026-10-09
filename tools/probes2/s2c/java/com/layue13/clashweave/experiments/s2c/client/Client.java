package com.layue13.clashweave.experiments.s2c.client;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.MouseEvent;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import com.layue13.clashweave.experiments.s2c.Experiment;
import com.layue13.clashweave.experiments.s2c.Proxy;
import com.layue13.clashweave.experiments.s2c.Record;

public class Client extends Proxy {
    private final ConcurrentLinkedQueue<Record> records=new ConcurrentLinkedQueue<Record>();
    private boolean connected, hello, hooked, authority, prediction, done;
    private long readyAt=-1, localTick, moveAt=-1, reportAt=-1, checkAt=-1, exitAt=-1;
    private int trial, mode, revision, sample, checkCase;
    private double speed, expectedX, expectedZ;
    private volatile int corrections;
    private int lastSnapshotRevision;
    public void init() { FMLCommonHandler.instance().bus().register(this); MinecraftForge.EVENT_BUS.register(this); }
    public void accept(Record record) { records.add(record); }
    private void send(Record r) { Experiment.wire.sendToServer(r); }
    private void log(String text) { System.out.println("CWMC "+text); }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase==TickEvent.Phase.END) {
            Minecraft current=Minecraft.getMinecraft();
            if(current.thePlayer!=null && current.theWorld!=null) {
                Record received;
                while((received=records.poll())!=null) receive(current,received);
            }
            return;
        }
        localTick++; Minecraft mc=Minecraft.getMinecraft();
        if(!connected && mc.currentScreen instanceof GuiMainMenu) {
            connected=true; mc.gameSettings.pauseOnLostFocus=false;
            FMLClientHandler.instance().setupServerList();
            FMLClientHandler.instance().connectToServer(mc.currentScreen,new ServerData("S2b S5b","127.0.0.1:25576"));
        }
        if(mc.thePlayer==null || mc.theWorld==null) {
            if(localTick%100==0) {
                log("WAIT screen="+(mc.currentScreen==null?"null":mc.currentScreen.getClass().getName()));
                if(mc.currentScreen instanceof net.minecraft.client.gui.GuiDisconnected) {
                    net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,"connection-debug.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                }
            }
            return;
        }
        KeyBinding.unPressAllKeys(); mc.setIngameNotInFocus();
        if(checkAt>=0) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),true);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),true);
        }
        if(!hooked) hook(mc);
        if(!(mc.thePlayer.movementInput instanceof Wrapped)) mc.thePlayer.movementInput=new Wrapped(new MovementInputFromOptions(mc.gameSettings));
        if(!hello && mc.thePlayer.ticksExisted>30) { hello=true; send(new Record(0,0,localTick,0,0,0,0,0)); }
        if(readyAt>=0 && localTick>=readyAt){send(new Record(10,trial,localTick,0,0,0,0,0));readyAt=-1;}
        if(moveAt>=0 && localTick>=moveAt && sample<Experiment.duration) {
            if(sample==0) corrections=0;
            if(mode==7 || mode==8 || mode==9) {
                if(sample==0) {
                    double x=mc.thePlayer.posX+5;
                    send(new Record(4,trial,localTick,x,mc.thePlayer.posY,mc.thePlayer.posZ,0,0));
                    mc.thePlayer.setPosition(x,mc.thePlayer.posY,mc.thePlayer.posZ);
                    mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(
                        mc.thePlayer.posX,mc.thePlayer.boundingBox.minY,mc.thePlayer.posY,mc.thePlayer.posZ,mc.thePlayer.onGround));
                    if(mode==9){for(double extra:new double[]{6.5,0.5})mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(extra,mc.thePlayer.boundingBox.minY,mc.thePlayer.posY,mc.thePlayer.posZ,true));log("S2C_MULTI_SENT trial="+trial+" count=3 nano="+System.nanoTime());}
                    log("S2C_NATIVE_SENT trial="+trial+" x="+x+" nano="+System.nanoTime());
                }
            } else {
                if(mode==10 && sample==0)send(new Record(12,trial,localTick,0,0,0,0,0));
                if(mode==4 && sample==0) mc.thePlayer.jump();
                double dx=speed, dz=0;
                if(mode==3) { dx=speed/Math.sqrt(2); dz=dx; }
                mc.thePlayer.moveEntity(dx,0,dz);
            }
            sample++;
            if(sample==Experiment.duration) {
                expectedX=mc.thePlayer.posX; expectedZ=mc.thePlayer.posZ;
                reportAt=localTick+10; moveAt=-1;
            }
        }
        if(reportAt>=0 && localTick>=reportAt) {
            log("S2C_RESULT trial="+trial+" mode="+mode+" S08="+corrections+" predictedX="+expectedX+" predictedZ="+expectedZ
                +" currentX="+mc.thePlayer.posX+" currentY="+mc.thePlayer.posY+" currentZ="+mc.thePlayer.posZ);
            send(new Record(2,trial,localTick,mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ,corrections,0)); reportAt=-1;
        }
        if(checkAt>=0 && localTick>=checkAt) {
            boolean opened=mc.thePlayer.openContainer!=mc.thePlayer.inventoryContainer;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),true);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),true);
            mc.thePlayer.movementInput.updatePlayerMoveState();
            MovementInput m=mc.thePlayer.movementInput;
            log("S5B_INPUT case="+checkCase+" engaged="+isEngaged()+" sneak="+m.sneak+" forward="+m.moveForward+" opened="+opened);
            send(new Record(8,checkCase,localTick,m.moveForward,opened?1:0,0,isEngaged()?1:0,m.sneak?1:0));
            KeyBinding.unPressAllKeys(); mc.thePlayer.closeScreen(); checkAt=-1;
        }
        if(done && localTick>=exitAt) { log("CLIENT_COMPLETE"); mc.shutdown(); }
    }
    private boolean isEngaged() { return authority || prediction; }
    private void receive(Minecraft mc, Record r) {
        if(r.kind==1) { trial=r.id; mode=(int)r.b; speed=r.a; sample=0; readyAt=localTick+12; moveAt=-1; reportAt=-1; }
        if(r.kind==11){moveAt=localTick+1;}
        if(r.kind==3) {
            boolean stale=r.id<=revision;
            if(!stale) { revision=r.id; authority=r.a==1; prediction=false; mc.thePlayer.movementInput.updatePlayerMoveState(); }
            Record ack=new Record(7,r.id,localTick,r.nano,stale?1:0,0,r.a,isEngaged()?1:0);
            send(ack); lastSnapshotRevision=r.id;
            log("S5B_APPLY revision="+r.id+" sourceTick="+r.tick+" clientTick="+localTick+" stale="+stale+" delayMs="+((ack.nano-r.nano)/1e6));
        }
        if(r.kind==6) { prediction=true; send(new Record(6,r.id,localTick,0,0,0,r.a,0)); }
        if(r.kind==8) {
            checkCase=r.id;
            if(!isEngaged()) mc.playerController.onPlayerRightClick(mc.thePlayer,mc.theWorld,mc.thePlayer.getHeldItem(),1,64,1,1,Vec3.createVectorHelper(1.5,65,1.5));
            else {
                // An adversarial native packet independently exercises the server interaction guard.
                mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(1,64,1,1,mc.thePlayer.getHeldItem(),0.5F,0.5F,0.5F));
            }
            checkAt=localTick+7;
        }
        if(r.kind==9) { done=true; exitAt=localTick+10; }
    }
    private void hook(Minecraft mc) {
        try {
            Object manager=mc.thePlayer.sendQueue.getNetworkManager();
            for(Field f:manager.getClass().getDeclaredFields()) if(Channel.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true); Channel channel=(Channel)f.get(manager);
                channel.pipeline().addBefore("packet_handler","cw-ms-observe",new ChannelInboundHandlerAdapter() {
                    public void channelRead(ChannelHandlerContext context,Object packet) throws Exception {
                        if(packet instanceof S08PacketPlayerPosLook) {
                            corrections++; log("S2C_S08 trial="+trial+" localTick="+localTick+" nano="+System.nanoTime());
                        }
                        super.channelRead(context,packet);
                    }
                }); hooked=true; return;
            }
            throw new IllegalStateException("Network channel not found");
        } catch(IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    @SubscribeEvent public void mouse(MouseEvent event) {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.thePlayer==null || mc.currentScreen!=null) return;
        if(event.button==0) { event.setCanceled(true); mc.playerController.resetBlockRemoving(); }
        if(event.button==1 && (isEngaged() || mc.objectMouseOver==null || mc.objectMouseOver.entityHit!=null)) event.setCanceled(true);
    }
    private final class Wrapped extends MovementInput {
        final MovementInput original;
        Wrapped(MovementInput input) { original=input; }
        public void updatePlayerMoveState() {
            original.updatePlayerMoveState(); moveForward=original.moveForward; moveStrafe=original.moveStrafe; jump=original.jump; sneak=original.sneak;
            if(isEngaged() && sneak) { sneak=false; moveForward/=0.3F; moveStrafe/=0.3F; }
        }
    }
}
