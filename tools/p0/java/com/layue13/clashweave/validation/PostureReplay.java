package com.layue13.clashweave.validation;

import net.minecraft.client.Minecraft;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * P2-b: keeps striking the still zombie in front of the player (light, light, and on idle again) so that its posture
 * wears down to a break; the server prints every posture event. Real input path.
 */
public final class PostureReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final int end=Integer.getInteger("cw.p2.postureEnd",900);
    private int ticks;
    private long sentFor=-1;
    private boolean done;
    public PostureReplay(){FMLCommonHandler.instance().bus().register(this);}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||done||ClientProxy.instance.visuals.size()<2)return;
        if(!mc.thePlayer.getCommandSenderName().equals("P0A"))return;
        ClientProxy p=ClientProxy.instance;ClientProxy.Visual own=p.own();if(own==null)return;
        mc.gameSettings.hideGUI=true;mc.gameSettings.limitFramerate=60;mc.gameSettings.pauseOnLostFocus=false;
        if(++ticks<100)return;
        mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=0;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=0;
        if(ticks==101)mc.thePlayer.inventory.currentItem=0;
        String action=own.state.action.isEmpty()?"":own.state.action.substring(own.state.action.indexOf(':')+1);
        long instance=own.state.instance;
        if(action.isEmpty()){
            if(ticks%12==0)p.send(Intent.LIGHT,-1);
        }else if(sentFor!=instance&&action.equals("light_1")&&p.elapsed(own,0)>=8){
            sentFor=instance;p.send(Intent.LIGHT,-1);
        }
        if(ticks%20==0)System.out.println("POSTURE_STATE tick="+ticks+" action="+action);
        if(ticks>=end){done=true;System.out.println("POSTURE_COMPLETE");mc.shutdown();}
    }
}
