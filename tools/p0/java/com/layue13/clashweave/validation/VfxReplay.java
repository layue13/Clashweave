package com.layue13.clashweave.validation;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.util.ScreenShotHelper;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * P3: the blade trail. A free third-person camera looks at P0A from a fixed angle while P0A chains light blows and an
 * iai against the zombie; a frame is saved every other tick of every blow, so the trail can be inspected as it grows.
 */
public final class VfxReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final float angle=Float.parseFloat(System.getProperty("cw.p3.angle","90"));
    private final int shots=Integer.getInteger("cw.p3.shots",60);
    private EntityOtherPlayerMP camera;
    private int ticks,taken,idle,blow;
    private String shot;
    private boolean done;
    public VfxReplay(){FMLCommonHandler.instance().bus().register(this);}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||done||ClientProxy.instance.visuals.size()<2)return;
        if(!mc.thePlayer.getCommandSenderName().equals("P0A"))return;
        ClientProxy p=ClientProxy.instance;ClientProxy.Visual own=p.own();if(own==null)return;
        mc.gameSettings.hideGUI=true;mc.gameSettings.limitFramerate=60;mc.gameSettings.pauseOnLostFocus=false;
        if(++ticks<100)return;
        if(ticks==101)mc.thePlayer.inventory.currentItem=0;
        // Face the nearest zombie.
        net.minecraft.entity.Entity nearest=null;double best=1e9;
        for(Object o:mc.theWorld.loadedEntityList)if(o instanceof net.minecraft.entity.monster.EntityZombie){double d=((net.minecraft.entity.Entity)o).getDistanceSqToEntity(mc.thePlayer);if(d<best){best=d;nearest=(net.minecraft.entity.Entity)o;}}
        if(nearest!=null){float yaw=(float)Math.toDegrees(Math.atan2(-(nearest.posX-mc.thePlayer.posX),nearest.posZ-mc.thePlayer.posZ));mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=yaw;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=0;}
        if(camera==null){camera=new EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Photo"));camera.setInvisible(true);}
        mc.renderViewEntity=camera;mc.gameSettings.thirdPersonView=1;
        camera.setPosition(mc.thePlayer.posX,mc.thePlayer.boundingBox.minY,mc.thePlayer.posZ);
        camera.prevPosX=camera.lastTickPosX=camera.posX;camera.prevPosY=camera.lastTickPosY=camera.posY;camera.prevPosZ=camera.lastTickPosZ=camera.posZ;
        camera.rotationYaw=camera.prevRotationYaw=angle;camera.rotationPitch=camera.prevRotationPitch=10;
        String eventKind=System.getProperty("cw.p3.event");
        if(eventKind!=null){
            // A bound event played at a monster (or the wielder), one frame per tick, so the data and entity filter are judged alone.
            int local=ticks-110,k=((local%50)+50)%50;
            if(local>=0&&k==0){
                String mob=System.getProperty("cw.p3.mob","zombie");
                net.minecraft.entity.Entity target=null;
                if(mob.equals("player"))target=mc.thePlayer;
                else{
                    for(Object o:mc.theWorld.loadedEntityList)if(o instanceof net.minecraft.entity.monster.EntityZombie){target=(net.minecraft.entity.Entity)o;break;}
                    if(!mob.equals("zombie")){
                        net.minecraft.entity.EntityLiving spawned=mob.equals("skeleton")?new net.minecraft.entity.monster.EntitySkeleton(mc.theWorld):new net.minecraft.entity.monster.EntityPigZombie(mc.theWorld);
                        spawned.setPosition(mc.thePlayer.posX,mc.thePlayer.boundingBox.minY,mc.thePlayer.posZ-1.6);
                        mc.theWorld.spawnEntityInWorld(spawned);target=spawned;
                    }
                }
                boolean played=target!=null&&com.layue13.clashweave.client.vfx.Vfx.INSTANCE.playEvent(eventKind,target);
                System.out.println("VFX_EVENT_TEST kind="+eventKind+" mob="+mob+" played="+played);
            }
            if(local>=0&&k<20&&shot==null&&taken<shots)shot="vfx-ev-"+eventKind+"-"+System.getProperty("cw.p3.mob","zombie")+"-"+(taken++);
            if(taken>=shots){done=true;System.out.println("VFX_COMPLETE taken="+taken);mc.shutdown();}
            return;
        }
        String burst=System.getProperty("cw.p3.burst");
        if(burst!=null){
            // Effects thrown straight at the camera's subject, one frame per tick, so the rendering is judged alone.
            int local=ticks-110,k=((local%40)+40)%40;
            if(local>=0&&k==0){
                int handle=com.layue13.clashweave.client.vfx.Vfx.INSTANCE.library().handle(burst);
                System.out.println("VFX_BURST handle="+handle);
                com.layue13.clashweave.client.vfx.Vfx.INSTANCE.play(handle,mc.thePlayer.posX,mc.thePlayer.boundingBox.minY+1.2,mc.thePlayer.posZ-.6,0,.12f,-1);
            }
            if(local>=0&&k<16&&shot==null&&taken<shots)shot="vfx-"+burst.substring(burst.indexOf(':')+1)+"-"+(taken++);
            if(taken>=shots){done=true;System.out.println("VFX_COMPLETE taken="+taken);mc.shutdown();}
            return;
        }
        String action=own.state.action.isEmpty()?"":own.state.action.substring(own.state.action.indexOf(':')+1);
        if(!action.isEmpty()&&!action.equals("sheathe")){idle=0;if(ticks%2==0&&shot==null&&taken<shots)shot="vfx-"+(int)angle+"-"+(taken++);return;}
        if(++idle<25)return;
        idle=0;
        // Light chain, then an iai from the sheath, repeated.
        if(own.state.sheathed){System.out.println("VFX_IAI taken="+taken);p.send(Intent.LIGHT,-1);blow=0;}
        else if(blow<3){blow++;System.out.println("VFX_LIGHT n="+blow);p.send(Intent.LIGHT,-1);}
        else{blow=0;p.send(Intent.SHEATHE,-1);}
        if(taken>=shots&&idle==0&&action.isEmpty()){done=true;System.out.println("VFX_COMPLETE taken="+taken);mc.shutdown();}
    }

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||shot==null)return;
        ScreenShotHelper.saveScreenshot(new File("."),shot+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        shot=null;
    }
}
