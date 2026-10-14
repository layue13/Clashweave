package com.layue13.clashweave.validation;

import net.minecraft.client.Minecraft;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * P1-b: drives the real input path through light, light, heavy (the launcher) and then the aerial follow-ups, one cycle
 * after another against the still zombie the server puts back in front of the player. Each intent is sent when the
 * playing action has reached its cancel window, so the result depends on the real hit, budget and decay rules.
 */
public final class ChaseReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final int cycles=Integer.getInteger("cw.p1b.cycles",20);
    private int ticks,cycle,airLights;
    private long lastInstance=-1,sentFor=-1;
    private int idleTicks;
    private boolean done;

    public ChaseReplay(){FMLCommonHandler.instance().bus().register(this);}

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
            // The server puts the target back after a quiet spell; the next cycle starts once it has had time to.
            if(++idleTicks>=70&&cycle<cycles){
                if(!own.state.sheathed||cycle==0){System.out.println("P1B_CYCLE start="+cycle+" tick="+ticks+" sheathed="+own.state.sheathed);p.send(Intent.LIGHT,-1);cycle++;airLights=0;idleTicks=0;}
            }
            if(cycle>=cycles&&idleTicks>=100){done=true;System.out.println("P1B_COMPLETE cycles="+cycles);mc.shutdown();}
            return;
        }
        idleTicks=0;
        if(instance!=lastInstance){
            lastInstance=instance;
            if(action.equals("air_light"))airLights++;
            System.out.println("P1B_ACTION cycle="+cycle+" action="+action+" tick="+ticks+" airLights="+airLights);
        }
        double elapsed=p.elapsed(own,0);
        if(sentFor==instance)return;
        Intent next=null;
        if(action.equals("light_1")&&elapsed>=8)next=Intent.LIGHT;
        else if(action.equals("light_2")&&elapsed>=7)next=Intent.HEAVY;
        else if(action.equals("launcher")&&elapsed>=8)next=Intent.LIGHT;
        else if(action.equals("air_light")&&elapsed>=7)next=airLights<Integer.getInteger("cw.p1b.airLights",2)?Intent.LIGHT:Intent.HEAVY;
        if(next!=null){sentFor=instance;p.send(next,-1);System.out.println("P1B_SEND cycle="+cycle+" from="+action+" intent="+next+" elapsed="+elapsed);}
    }
}
