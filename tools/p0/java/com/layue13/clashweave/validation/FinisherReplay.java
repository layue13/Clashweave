package com.layue13.clashweave.validation;

import net.minecraft.client.Minecraft;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * P2 finisher: strikes the still zombie until it breaks; when the client sees the break it presses heavy. Every
 * {@code cw.p2.finishEvery}th break it presses nothing (the control: a plain heavy must not become a finisher, and a
 * heavy with no broken target must stay a heavy), and between breaks it also presses one heavy with the zombie whole.
 */
public final class FinisherReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final int end=Integer.getInteger("cw.p2.finishEnd",1500),delay=Integer.getInteger("cw.p2.finishDelay",0);
    private int ticks,breaks,pressAt=-1;
    private long sentFor=-1;
    private boolean done,hooked,broke;
    public FinisherReplay(){FMLCommonHandler.instance().bus().register(this);}
    private void hook(){
        hooked=true;
        try{
            java.lang.reflect.Field f=ClientProxy.class.getDeclaredField("presentation");f.setAccessible(true);
            ((com.layue13.clashweave.core.SemanticEvents)f.get(ClientProxy.instance)).subscribe(e->{
                if(e.kind==com.layue13.clashweave.core.SemanticEvents.Kind.BREAK&&mc.thePlayer!=null&&e.actor!=mc.thePlayer.getEntityId())broke=true;
            });
        }catch(Exception ex){System.out.println("FINISH_NO_HOOK "+ex);}
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||done||ClientProxy.instance.visuals.size()<2)return;
        if(!mc.thePlayer.getCommandSenderName().equals("P0A"))return;
        if(!hooked)hook();
        ClientProxy p=ClientProxy.instance;ClientProxy.Visual own=p.own();if(own==null)return;
        mc.gameSettings.hideGUI=true;mc.gameSettings.limitFramerate=60;mc.gameSettings.pauseOnLostFocus=false;
        if(++ticks<100)return;
        mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=0;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=0;
        if(ticks==101)mc.thePlayer.inventory.currentItem=0;
        String action=own.state.action.isEmpty()?"":own.state.action.substring(own.state.action.indexOf(':')+1);
        long instance=own.state.instance;
        if(broke){broke=false;breaks++;pressAt=ticks+delay;System.out.println("FINISH_BREAK n="+breaks+" tick="+ticks+" action="+action);}
        if(pressAt>=0&&ticks>=pressAt){
            pressAt=-1;
            if(breaks%4!=0){System.out.println("FINISH_PRESS tick="+ticks+" action="+action);p.send(Intent.HEAVY,-1);}
            else System.out.println("FINISH_SKIP tick="+ticks);
        }else if(pressAt<0){
            if(action.isEmpty()){if(ticks%12==0)p.send(Intent.LIGHT,-1);}
            else if(sentFor!=instance&&action.equals("light_1")&&p.elapsed(own,0)>=8){sentFor=instance;p.send(Intent.LIGHT,-1);}
        }
        if(ticks%50==0)System.out.println("FINISH_STATE tick="+ticks+" action="+action+" breaks="+breaks);
        if(ticks>=end){done=true;System.out.println("FINISH_COMPLETE breaks="+breaks);mc.shutdown();}
    }
}
