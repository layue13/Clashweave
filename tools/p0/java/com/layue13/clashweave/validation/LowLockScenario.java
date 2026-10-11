package com.layue13.clashweave.validation;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import com.layue13.clashweave.Clashweave;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Fixture arranges real entities; every acquisition/unlock travels through production LOCK messages. */
public final class LowLockScenario {
    private EntityPlayerMP a,b;
    private EntityLivingBase target;
    private int wait,ticks,index=-1;
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(!Boolean.getBoolean("cw.p0.lowLock") || event.phase!=TickEvent.Phase.END || MinecraftServer.getServer().getCurrentPlayerCount()<2)return;
        for(Object o:MinecraftServer.getServer().getConfigurationManager().playerEntityList){EntityPlayerMP p=(EntityPlayerMP)o;if(p.getCommandSenderName().equals("P0A"))a=p;else b=p;}
        if(++wait<100)return;
        int group=ticks/180;
        if(group>=20){System.out.println("P0_NETWORK COMPLETE");MinecraftServer.getServer().initiateShutdown();return;}
        String kind=new String[]{"CHICKEN","SPIDER","CAVE_SPIDER","ZOMBIE","PLAYER"}[group/4];
        double range=new double[]{1,1.5,2.5,4}[group%4];
        String name=kind+"_R"+range;
        a.capabilities.disableDamage=b.capabilities.disableDamage=true;
        if(index!=group){
            if(target!=null && target!=b)target.setDead();
            a.worldObj.setWorldTime(18000);
            a.playerNetServerHandler.setPlayerLocation(0,64,0,0,0);
            b.playerNetServerHandler.setPlayerLocation(30,64,30,0,0);
            target=kind.equals("PLAYER")?b:kind.equals("CHICKEN")?new EntityChicken(a.worldObj):kind.equals("SPIDER")?new EntitySpider(a.worldObj):kind.equals("CAVE_SPIDER")?new EntityCaveSpider(a.worldObj):new EntityZombie(a.worldObj);
            target.setPosition(0,64,range);
            if(target instanceof EntityLiving){EntityLiving e=(EntityLiving)target;e.tasks.taskEntries.clear();e.targetTasks.taskEntries.clear();e.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);a.worldObj.spawnEntityInWorld(target);}
            else b.playerNetServerHandler.setPlayerLocation(0,64,range,180,0);
            String text="P0_LOW_LOCK_PHASE name="+name+" target="+target.getEntityId();
            a.addChatMessage(new ChatComponentText(text));System.out.println(text);
            index=group;
        }
        if(target!=b){target.setPosition(0,64,range);target.motionX=target.motionY=target.motionZ=0;}
        if(target instanceof net.minecraft.entity.EntityCreature)((net.minecraft.entity.EntityCreature)target).setTarget(null);
        // Record actual geometry each tick, before the next input is consumed. No lock field is assigned.
        double dx=target.posX-a.posX,dz=target.posZ-a.posZ,r=Math.hypot(dx,dz);
        double eyeDy=target.posY+target.getEyeHeight()-(a.posY+a.getEyeHeight());
        double centerDy=(target.boundingBox.minY+target.boundingBox.maxY)/2-(a.posY+a.getEyeHeight());
        double yaw=Math.toDegrees(Math.atan2(-dx,dz));
        double horizontal=Math.abs(com.layue13.clashweave.core.FacingHistory.difference(yaw,a.rotationYaw));
        double vertical=Math.abs(Math.toDegrees(Math.atan2(eyeDy,r))+a.rotationPitch);
        net.minecraft.util.Vec3 look=a.getLookVec();double length=Math.sqrt(r*r+eyeDy*eyeDy);
        double angle=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,(dx*look.xCoord+eyeDy*look.yCoord+dz*look.zCoord)/length))));
        System.out.println("P0_LOW_LOCK_GEOMETRY phase="+name+" tick="+Clashweave.server.tick+" target="+target.getEntityId()+" height="+target.height+" playerEye="+a.getEyeHeight()+" targetEye="+target.getEyeHeight()+" range="+r+" horizontal="+horizontal+" verticalEye="+vertical+" threeD="+angle+" verticalCenter="+Math.abs(Math.toDegrees(Math.atan2(centerDy,r))+a.rotationPitch)+" centerDy="+centerDy+" yaw="+a.rotationYaw+" pitch="+a.rotationPitch+" visible="+a.canEntityBeSeen(target));
        ticks++;
    }
}
