package com.layue13.clashweave.validation;

import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import com.layue13.clashweave.Clashweave;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Separate controls and combined target-switch/frame-stall experiment, without camera modifications. */
public final class CameraPauseScenario {
    private EntityPlayerMP a,b;private EntityZombie target;private int wait,ticks,index=-1;private double angle;
    private EntityZombie spawn(double yaw){EntityZombie z=new EntityZombie(a.worldObj);z.tasks.taskEntries.clear();z.targetTasks.taskEntries.clear();z.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);z.setPosition(-Math.sin(Math.toRadians(yaw))*8,64,Math.cos(Math.toRadians(yaw))*8);a.worldObj.spawnEntityInWorld(z);return z;}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
        if(!Boolean.getBoolean("cw.p0.cameraDiagnosis") || event.phase!=TickEvent.Phase.END || MinecraftServer.getServer().getCurrentPlayerCount()<2)return;
        for(Object o:MinecraftServer.getServer().getConfigurationManager().playerEntityList){EntityPlayerMP p=(EntityPlayerMP)o;if(p.getCommandSenderName().equals("P0A"))a=p;else b=p;}
        if(++wait<100)return;
        int stage=ticks/240,local=ticks%240;if(stage>=3){System.out.println("P0_NETWORK COMPLETE");MinecraftServer.getServer().initiateShutdown();return;}
        String name=new String[]{"PAUSE_ONLY","SWITCH_ONLY","SWITCH_PAUSE"}[stage];
        if(index!=stage){if(target!=null)target.setDead();angle=0;target=spawn(angle);a.playerNetServerHandler.setPlayerLocation(0,64,0,0,0);b.playerNetServerHandler.setPlayerLocation(30,64,30,0,0);a.worldObj.setWorldTime(18000);a.capabilities.disableDamage=b.capabilities.disableDamage=true;String text="P0_PAUSE_PHASE name="+name;a.addChatMessage(new ChatComponentText(text));System.out.println(text);index=stage;}
        if(local==80){
            if(stage>0){target.setDead();angle+=30;target=spawn(angle);}
            String text="P0_PAUSE_EVENT name="+name+" target="+target.getEntityId()+" sleep="+(stage==1?0:200);a.addChatMessage(new ChatComponentText(text));System.out.println(text);
        }
        angle+=90/20.0;target.setPosition(-Math.sin(Math.toRadians(angle))*8,64,Math.cos(Math.toRadians(angle))*8);target.motionX=target.motionY=target.motionZ=0;target.setTarget(null);
        Clashweave.server.state(a).lock=target.getEntityId();ticks++;
    }
}
