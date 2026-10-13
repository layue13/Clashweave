package com.layue13.clashweave.validation;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
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
 * C2a: photographs P0A from an orbit camera at fixed angle to its committed facing, at fixed fractions of every action
 * the running scenario (--souls-view combo cycles) plays. Observer only: it sends no input and changes no model, mount
 * or pose. Opt-in.
 */
public final class PoseVerifyReplay {
    private static final double[] FRACTIONS={0.08,0.25,0.42,0.58,0.75,0.92};
    private final Minecraft mc=Minecraft.getMinecraft();
    private final float angle=Float.parseFloat(System.getProperty("cw.c2a.poseVerify"));
    private final Set<String> captured=new HashSet<>();
    private EntityOtherPlayerMP camera;
    private int ticks;
    private String shot;
    private String shotAction="";
    private double shotElapsed;
    private boolean done;
    private boolean active;
    private int savedView;

    public PoseVerifyReplay(){FMLCommonHandler.instance().bus().register(this);}

    private void camera(float facing){
        if(camera==null)camera=new EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Photo"));
        mc.renderViewEntity=camera;
        camera.setPosition(mc.thePlayer.posX,mc.thePlayer.boundingBox.minY,mc.thePlayer.posZ);
        camera.prevPosX=camera.lastTickPosX=camera.posX;camera.prevPosY=camera.lastTickPosY=camera.posY;camera.prevPosZ=camera.lastTickPosZ=camera.posZ;
        camera.rotationYaw=camera.prevRotationYaw=facing+angle;camera.rotationPitch=camera.prevRotationPitch=0;
        mc.gameSettings.thirdPersonView=1;
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||done||ClientProxy.instance.visuals.size()<2)return;
        ClientProxy p=ClientProxy.instance;ClientProxy.Visual own=p.own();if(own==null)return;
        mc.gameSettings.hideGUI=true;mc.gameSettings.limitFramerate=60;mc.gameSettings.pauseOnLostFocus=false;
        if(!mc.thePlayer.getCommandSenderName().equals("P0A"))return;
        if(++ticks<100)return;
        // The scenario only sends input while the souls camera is active, so the orbit camera exists only while an action
        // plays and the player's own view is restored in between.
        if(own.state.action.isEmpty()){
            if(active){mc.renderViewEntity=mc.thePlayer;mc.gameSettings.thirdPersonView=savedView;active=false;}
            return;
        }
        if(!active){savedView=mc.gameSettings.thirdPersonView;active=true;}
        camera(own.state.yaw);
        String action=own.state.action.isEmpty()?"":Ids.local(own.state.action);
        double total=own.definition==null?0:own.definition.duration();
        double elapsed=p.elapsed(own,0);
        if(shot==null&&!action.isEmpty()&&total>0){
            for(int i=0;i<FRACTIONS.length;i++){
                String name=action+"-"+i;
                if(!captured.contains(name)&&elapsed>=FRACTIONS[i]*total){shot=name;shotAction=action;shotElapsed=elapsed;break;}
            }
        }
    }

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||shot==null)return;
        ScreenShotHelper.saveScreenshot(new File("."),"pose-"+shot+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        ClientProxy.Visual v=ClientProxy.instance.own();
        System.out.println("POSE_CAPTURE name="+shot+" angle="+angle+" tick="+ticks+" action="+v.state.action+" elapsed="+shotElapsed
            +" total="+(v.definition==null?0:v.definition.duration())+" sheathed="+v.state.sheathed+" engaged="+v.state.engaged);
        captured.add(shot);shot=null;
    }
}
