package com.layue13.clashweave.validation;

import net.minecraft.client.Minecraft;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * P2-f: tries the mode key on an empty gauge (must be refused), fills the gauge by striking the still zombie, starts
 * the mode, plays iai twice inside it and once after it has ended, so the server's trace shows the doubled afterimage
 * and the end of the mode.
 */
public final class ModeReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private int ticks,phase,idle,iai;
    private long sentFor=-1;
    private boolean done,early;
    public ModeReplay(){FMLCommonHandler.instance().bus().register(this);}

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
        if(ticks%40==0)System.out.println("MODE_STATE tick="+ticks+" phase="+phase+" gauge="+own.state.gauge+" mode="+own.state.mode+" action="+action+" sheathed="+own.state.sheathed);
        if(phase==0){
            // 1: the key on an empty gauge
            if(!early&&ticks>=130){early=true;System.out.println("MODE_SEND_EARLY");p.send(Intent.MODE,-1);}
            if(ticks>=160)phase=1;
            return;
        }
        if(phase==1){
            // 2: fill the gauge with real blows
            if(own.state.gauge>=100&&action.isEmpty()){phase=2;idle=0;return;}
            if(action.isEmpty()){if(ticks%12==0)p.send(Intent.LIGHT,-1);}
            else if(sentFor!=instance&&action.equals("light_1")&&p.elapsed(own,0)>=8){sentFor=instance;p.send(Intent.LIGHT,-1);}
            return;
        }
        if(phase==2){
            // 3: start the mode once everything is quiet
            if(!action.isEmpty()){idle=0;return;}
            if(++idle>=50){System.out.println("MODE_SEND gauge="+own.state.gauge);p.send(Intent.MODE,-1);phase=3;idle=0;}
            return;
        }
        if(phase==3){
            // 4: iai twice inside the mode, once after it
            if(!action.isEmpty()){idle=0;return;}
            if(++idle<(phase==3&&own.state.mode>0?25:60))return;
            idle=0;
            if(own.state.sheathed){
                System.out.println("MODE_IAI n="+iai+" mode="+own.state.mode);iai++;p.send(Intent.LIGHT,-1);
                if(iai>=3)phase=4;
            }else p.send(Intent.SHEATHE,-1);
            return;
        }
        if(phase==4){
            if(!action.isEmpty()){idle=0;return;}
            if(++idle>=120){done=true;System.out.println("MODE_COMPLETE mode="+own.state.mode);mc.shutdown();}
        }
    }
}
