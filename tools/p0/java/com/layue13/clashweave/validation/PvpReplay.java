package com.layue13.clashweave.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * P2 close-out: both players run this with real input. In a 160-tick cycle P0A strikes in chains while P0B holds its
 * guard (pressing again partway so the precise window is met at varying moments), then the roles swap. Whoever holds
 * the guard also tries a counter near the middle of each hold; the mode key is used whenever the gauge is full.
 */
public final class PvpReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final int end=Integer.getInteger("cw.p2.pvpTicks",1600);
    private int ticks,cycles;
    private boolean done,sneaking,parried;
    private int counters;
    private boolean hooked;
    public PvpReplay(){FMLCommonHandler.instance().bus().register(this);}
    private void hook(){
        hooked=true;
        // The client's own presentation events tell it when its guard met a blow precisely, so it can answer with a counter.
        try{
            java.lang.reflect.Field f=ClientProxy.class.getDeclaredField("presentation");f.setAccessible(true);
            ((com.layue13.clashweave.core.SemanticEvents)f.get(ClientProxy.instance)).subscribe(e->{
                if(e.kind==com.layue13.clashweave.core.SemanticEvents.Kind.PARRY&&mc.thePlayer!=null&&e.target==mc.thePlayer.getEntityId())parried=true;
            });
        }catch(Exception ex){System.out.println("PVP_NO_PARRY_HOOK "+ex);}
    }
    private void key(KeyBinding b,boolean down){KeyBinding.setKeyBindState(b.getKeyCode(),down);}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||done||ClientProxy.instance.visuals.size()<2)return;
        if(!hooked)hook();
        ClientProxy p=ClientProxy.instance;ClientProxy.Visual own=p.own();if(own==null)return;
        boolean first=mc.thePlayer.getCommandSenderName().equals("P0A");
        mc.gameSettings.hideGUI=true;mc.gameSettings.limitFramerate=60;mc.gameSettings.pauseOnLostFocus=false;
        if(++ticks<120)return;
        if(ticks==121)mc.thePlayer.inventory.currentItem=0;
        int local=ticks-120;
        net.minecraft.entity.Entity other=null;
        for(Object o:mc.theWorld.loadedEntityList)if(o instanceof net.minecraft.entity.player.EntityPlayer&&o!=mc.thePlayer)other=(net.minecraft.entity.Entity)o;
        if(other!=null){float yaw=(float)Math.toDegrees(Math.atan2(-(other.posX-mc.thePlayer.posX),other.posZ-mc.thePlayer.posZ));mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=yaw;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=0;}
        int phase=(int)((System.currentTimeMillis()/50)%160);
        boolean attacker=first?phase<80:phase>=80;
        int inRole=phase%80;
        // The defender holds from tick 6 to 70 of its turn, released for 6 ticks at 30 so that presses recur at other offsets.
        boolean guard=!attacker&&((inRole>=6&&inRole<30)||(inRole>=36&&inRole<70));
        if(guard!=sneaking){sneaking=guard;key(mc.gameSettings.keyBindSneak,guard);}
        String action=own.state.action.isEmpty()?"":own.state.action.substring(own.state.action.indexOf(':')+1);
        if(own.state.gauge>=100&&own.state.mode==0&&action.isEmpty()&&!guard&&local%7==0){System.out.println("PVP_MODE local="+local+" me="+mc.thePlayer.getCommandSenderName());p.send(Intent.MODE,-1);}
        else if(attacker&&action.isEmpty()&&inRole%14==0)p.send(Intent.LIGHT,-1);
        else if(parried&&action.isEmpty()){parried=false;counters++;System.out.println("PVP_COUNTER_SEND local="+local+" me="+mc.thePlayer.getCommandSenderName());p.send(Intent.LIGHT,-1);}
        if(phase==159)cycles++;
        if(local%100==0)System.out.println("PVP_STATE local="+local+" me="+mc.thePlayer.getCommandSenderName()+" hp="+mc.thePlayer.getHealth()+" gauge="+own.state.gauge+" mode="+own.state.mode+" action="+action+" role="+(attacker?"attack":"guard"));
        if(local>=end){done=true;key(mc.gameSettings.keyBindSneak,false);System.out.println("PVP_COMPLETE me="+mc.thePlayer.getCommandSenderName());mc.shutdown();}
    }
}
