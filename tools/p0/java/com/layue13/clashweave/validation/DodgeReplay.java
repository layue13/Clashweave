package com.layue13.clashweave.validation;

import net.minecraft.client.Minecraft;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * P2-d: after drawing, takes a step in a different direction each cycle (back, right, left, forward) and, five ticks
 * into it, asks for a second one that the cooldown must refuse. The server puts the player back between cycles.
 */
public final class DodgeReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final int cycles=Integer.getInteger("cw.p2.dodgeCycles",12);
    private static final int[] OCTANTS={4,2,6,0};
    private int ticks,idle,cycle,since=-1;
    private boolean done;
    public DodgeReplay(){FMLCommonHandler.instance().bus().register(this);}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||done||ClientProxy.instance.visuals.size()<2)return;
        if(!mc.thePlayer.getCommandSenderName().equals("P0A"))return;
        ClientProxy p=ClientProxy.instance;ClientProxy.Visual own=p.own();if(own==null)return;
        mc.gameSettings.hideGUI=true;mc.gameSettings.limitFramerate=60;mc.gameSettings.pauseOnLostFocus=false;
        if(++ticks<100)return;
        mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=0;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=0;
        if(ticks==101)mc.thePlayer.inventory.currentItem=0;
        if(ticks==110)p.send(Intent.LIGHT,-1);
        if(ticks<200)return;
        String action=own.state.action.isEmpty()?"":own.state.action.substring(own.state.action.indexOf(':')+1);
        if(since>=0)since++;
        if(since==5){System.out.println("DODGE_SECOND_SEND cycle="+cycle);p.send(Intent.DODGE,0);}
        if(action.isEmpty()){
            if(++idle>=70&&cycle<cycles&&since<0){
                int octant=OCTANTS[cycle%OCTANTS.length];
                System.out.println("DODGE_SEND cycle="+cycle+" octant="+octant+" x="+mc.thePlayer.posX+" z="+mc.thePlayer.posZ);
                p.send(Intent.DODGE,octant);since=0;cycle++;idle=0;
            }
        }else{
            idle=0;
            if(action.equals("step")&&since>=8&&since%8==0)System.out.println("DODGE_POS cycle="+cycle+" x="+mc.thePlayer.posX+" z="+mc.thePlayer.posZ);
        }
        if(since>=40){since=-1;}
        if(cycle>=cycles&&idle>=80){done=true;System.out.println("DODGE_COMPLETE");mc.shutdown();}
    }
}
