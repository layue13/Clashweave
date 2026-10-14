package com.layue13.clashweave.validation;

import net.minecraft.client.Minecraft;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * P2-e: draws with an iai on the still zombie, sheathes again, and repeats, so that every cycle is one iai hit; the
 * server prints the delayed strikes the equipped ability schedules (or none when it is switched off).
 */
public final class SaReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final int cycles=Integer.getInteger("cw.p2.saCycles",10);
    private int ticks,idle,cycle;
    private boolean done;
    public SaReplay(){FMLCommonHandler.instance().bus().register(this);}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||done||ClientProxy.instance.visuals.size()<2)return;
        if(!mc.thePlayer.getCommandSenderName().equals("P0A"))return;
        ClientProxy p=ClientProxy.instance;ClientProxy.Visual own=p.own();if(own==null)return;
        mc.gameSettings.hideGUI=true;mc.gameSettings.limitFramerate=60;mc.gameSettings.pauseOnLostFocus=false;
        if(++ticks<100)return;
        mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=0;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=0;
        if(ticks==101)mc.thePlayer.inventory.currentItem=0;
        String action=own.state.action.isEmpty()?"":own.state.action.substring(own.state.action.indexOf(':')+1);
        if(!action.isEmpty()){idle=0;return;}
        if(++idle<60)return;
        idle=0;
        if(own.state.sheathed){
            if(cycle>=cycles){done=true;System.out.println("SA_COMPLETE");mc.shutdown();return;}
            System.out.println("SA_IAI cycle="+cycle);cycle++;p.send(Intent.LIGHT,-1);
        }else p.send(Intent.SHEATHE,-1);
    }
}
