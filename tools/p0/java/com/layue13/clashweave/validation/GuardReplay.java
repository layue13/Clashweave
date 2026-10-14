package com.layue13.clashweave.validation;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ScreenShotHelper;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * P2: the player draws the blade and then holds and releases the guard in a repeating pattern (hold 30 ticks, free 12)
 * while a real zombie keeps attacking; photographs from the side every few ticks.
 */
public final class GuardReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final float angle=Float.parseFloat(System.getProperty("cw.p2.guardAngle","90"));
    private final int hold=Integer.getInteger("cw.p2.hold",30),free=Integer.getInteger("cw.p2.free",12),end=Integer.getInteger("cw.p2.end",420);
    private EntityOtherPlayerMP camera;
    private int ticks;
    private String shot;
    private boolean done;
    public GuardReplay(){FMLCommonHandler.instance().bus().register(this);}
    private void key(KeyBinding b,boolean down){KeyBinding.setKeyBindState(b.getKeyCode(),down);}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||done||ClientProxy.instance.visuals.size()<2)return;
        if(!mc.thePlayer.getCommandSenderName().equals("P0A"))return;
        ClientProxy.Visual own=ClientProxy.instance.own();if(own==null)return;
        mc.gameSettings.hideGUI=true;mc.gameSettings.limitFramerate=60;mc.gameSettings.pauseOnLostFocus=false;
        if(++ticks<100)return;
        if(ticks==101)mc.thePlayer.inventory.currentItem=0;
        if(ticks==110)ClientProxy.instance.send(Intent.LIGHT,-1);
        // Face the nearest zombie, as a player holding a guard would.
        net.minecraft.entity.Entity nearest=null;double best=1e9;
        for(Object o:mc.theWorld.loadedEntityList)if(o instanceof net.minecraft.entity.monster.EntityZombie){double d=((net.minecraft.entity.Entity)o).getDistanceSqToEntity(mc.thePlayer);if(d<best){best=d;nearest=(net.minecraft.entity.Entity)o;}}
        if(nearest!=null){float yaw=(float)Math.toDegrees(Math.atan2(-(nearest.posX-mc.thePlayer.posX),nearest.posZ-mc.thePlayer.posZ));mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=yaw;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=0;}
        int local=ticks-170;
        boolean shift=local>=0&&(local%(hold+free))<hold;
        key(mc.gameSettings.keyBindSneak,shift);
        if(local>=0){
            if(camera==null){camera=new EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Photo"));camera.setInvisible(true);}
            mc.renderViewEntity=camera;mc.gameSettings.thirdPersonView=1;
            camera.setPosition(mc.thePlayer.posX,mc.thePlayer.boundingBox.minY,mc.thePlayer.posZ);
            camera.prevPosX=camera.lastTickPosX=camera.posX;camera.prevPosY=camera.lastTickPosY=camera.posY;camera.prevPosZ=camera.lastTickPosZ=camera.posZ;
            camera.rotationYaw=camera.prevRotationYaw=angle;camera.rotationPitch=camera.prevRotationPitch=8;
            if(local%3==0&&shot==null)shot="guard-"+local;
            if(local%10==0)System.out.println("GUARD_STATE local="+local+" shift="+shift+" guarding="+ClientProxy.instance.guarding(own)+" action="+own.state.action);
        }
        if(local>=end){done=true;System.out.println("GUARD_COMPLETE");mc.shutdown();}
    }

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||shot==null)return;
        ScreenShotHelper.saveScreenshot(new File("."),"guard-"+shot+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        shot=null;
    }
}
