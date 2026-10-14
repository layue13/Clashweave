package com.layue13.clashweave.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * P2 close-out: P0A fights four hunting zombies with real input: it faces the nearest one and, in a hundred-tick
 * cycle, strikes in chains, holds the guard, steps aside once and strikes again; the special key and the mode key are
 * used whenever they are available. The state is printed every fifty ticks.
 */
public final class SwarmReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final int end=Integer.getInteger("cw.p2.swarmTicks",1800);
    private int ticks,cycle;
    private boolean done,sneaking;
    public SwarmReplay(){FMLCommonHandler.instance().bus().register(this);}
    private void key(KeyBinding b,boolean down){KeyBinding.setKeyBindState(b.getKeyCode(),down);}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||done||ClientProxy.instance.visuals.size()<2)return;
        if(!mc.thePlayer.getCommandSenderName().equals("P0A"))return;
        ClientProxy p=ClientProxy.instance;ClientProxy.Visual own=p.own();if(own==null)return;
        mc.gameSettings.hideGUI=true;mc.gameSettings.limitFramerate=60;mc.gameSettings.pauseOnLostFocus=false;
        if(++ticks<100)return;
        if(ticks==101)mc.thePlayer.inventory.currentItem=0;
        int local=ticks-100;
        net.minecraft.entity.Entity nearest=null;double best=1e9;
        for(Object o:mc.theWorld.loadedEntityList)if(o instanceof net.minecraft.entity.monster.EntityZombie&&!((net.minecraft.entity.Entity)o).isDead){double d=((net.minecraft.entity.Entity)o).getDistanceSqToEntity(mc.thePlayer);if(d<best){best=d;nearest=(net.minecraft.entity.Entity)o;}}
        if(nearest!=null){float yaw=(float)Math.toDegrees(Math.atan2(-(nearest.posX-mc.thePlayer.posX),nearest.posZ-mc.thePlayer.posZ));mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=yaw;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=0;}
        String action=own.state.action.isEmpty()?"":own.state.action.substring(own.state.action.indexOf(':')+1);
        int phase=local%100;
        boolean guard=phase>=50&&phase<75;
        if(guard!=sneaking){sneaking=guard;key(mc.gameSettings.keyBindSneak,guard);}
        if(!guard&&action.isEmpty()){
            if(own.state.gauge>=100&&own.state.mode==0){System.out.println("SWARM_MODE local="+local);p.send(Intent.MODE,-1);}
            else if(phase==0&&cycle%3==0){System.out.println("SWARM_SPECIAL local="+local);p.send(Intent.SPECIAL,-1);}
            else if(phase==76){p.send(Intent.DODGE,0);}
            else if(phase%12==0)p.send(Intent.LIGHT,-1);
        }
        if(phase==99)cycle++;
        if(local%50==0)System.out.println("SWARM_STATE local="+local+" hp="+mc.thePlayer.getHealth()+" gauge="+own.state.gauge+" mode="+own.state.mode+" action="+action+" nearest="+(nearest==null?-1:Math.sqrt(best)));
        if(local>=end){done=true;key(mc.gameSettings.keyBindSneak,false);System.out.println("SWARM_COMPLETE");mc.shutdown();}
    }
}
