package com.layue13.clashweave.validation;

import net.minecraft.client.Minecraft;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * P2-g: the key-triggered ability. Sheathed, it is refused as busy; after a drawn blow it is used, used again at once
 * (cooldown), and used a third time once the cooldown has passed; the server's trace shows each verdict and the cuts.
 */
public final class SpecialReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private int ticks,phase,idle,wait;
    private boolean done;
    public SpecialReplay(){FMLCommonHandler.instance().bus().register(this);}

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
        if(++idle<50)return;
        idle=0;
        if(phase==0&&Boolean.getBoolean("cw.p2.judgeKill")){System.out.println("SPECIAL_LIGHT");p.send(Intent.LIGHT,-1);phase=2;return;}  // blade out first: the long cast is the one that can be cut short
        if(phase==0){System.out.println("SPECIAL_SEND n=0 sheathed="+own.state.sheathed);p.send(Intent.SPECIAL,-1);phase=1;return;}
        if(phase==1){System.out.println("SPECIAL_LIGHT");p.send(Intent.LIGHT,-1);phase=2;return;}
        if(Boolean.getBoolean("cw.p2.judgeKill")){
            // the server kills the caster mid-cast: one cast, then give the aftermath time to show in the trace
            if(phase==2){System.out.println("SPECIAL_SEND kill-run");p.send(Intent.SPECIAL,-1);phase=9;wait=0;return;}
            if(phase==9&&++wait>=4){done=true;System.out.println("SPECIAL_COMPLETE");mc.shutdown();}
            return;
        }
        if(phase==2){System.out.println("SPECIAL_SEND n=1");p.send(Intent.SPECIAL,-1);phase=3;wait=0;return;}
        if(phase==3){System.out.println("SPECIAL_SEND n=2 (cooldown)");p.send(Intent.SPECIAL,-1);phase=4;wait=0;return;}
        if(phase==4){
            if(++wait<8)return;  // 8 x 50 idle ticks passes the 300 tick cooldown
            System.out.println("SPECIAL_SEND n=3 (after cooldown)");p.send(Intent.SPECIAL,-1);phase=5;wait=0;return;
        }
        if(phase==5&&++wait>=2){done=true;System.out.println("SPECIAL_COMPLETE");mc.shutdown();}
    }
}
