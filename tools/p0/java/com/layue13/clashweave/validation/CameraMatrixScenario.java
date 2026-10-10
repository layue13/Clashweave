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

/** Isolated arrangement only. Real entity dimensions and production camera/projection paths. */
public final class CameraMatrixScenario {
    private EntityPlayerMP a,b;
    private EntityLivingBase target;
    private int ticks,index=-1,wait;private double motionAngle;
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        String suite=System.getProperty("cw.p0.cameraSuite","");
        if(suite.isEmpty() || event.phase!=TickEvent.Phase.END || MinecraftServer.getServer().getCurrentPlayerCount()<2)return;
        for(Object o:MinecraftServer.getServer().getConfigurationManager().playerEntityList){EntityPlayerMP p=(EntityPlayerMP)o;if(p.getCommandSenderName().equals("P0A"))a=p;else b=p;}
        if(++wait<100)return;
        a.capabilities.disableDamage=true;b.capabilities.disableDamage=true;
        int duration=suite.equals("motion")?240:suite.equals("audit")?60:120;
        int current=ticks/duration,local=ticks%duration;
        int count=suite.equals("audit")?60:suite.equals("real")?20:suite.equals("motion")?3:suite.equals("compare")?2:9;
        if(current>=count){System.out.println("P0_NETWORK COMPLETE");MinecraftServer.getServer().initiateShutdown();return;}
        double r=suite.equals("audit")?1.9:suite.equals("real")?new double[]{1.5,2.5,4,8}[current%4]:suite.equals("motion")?8:suite.equals("compare")?4:new double[]{2,4,8}[current/3];
        String kind=suite.equals("audit")?new String[]{"CHICKEN","SPIDER","ZOMBIE"}[current/20]:suite.equals("real")?new String[]{"CHICKEN","SPIDER","CAVE_SPIDER","ZOMBIE","PLAYER"}[current/4]:suite.equals("sprint")?new String[]{"SPIDER","ZOMBIE","ENDERMAN"}[current%3]:"ZOMBIE";
        String name=suite.equals("audit")?"AUDIT_"+kind+"_"+current:suite.equals("real")?"REAL_"+kind+"_R"+r:suite.equals("motion")?new String[]{"MOTION_45","MOTION_90","MOTION_STRAFE"}[current]:suite.equals("compare")?new String[]{"COMPARE_SOULS","OFF_COMPARE"}[current]:"SPRINT_R"+(int)r+"_H"+new double[]{.9,1.8,2.9}[current%3];
        if(index!=current){
            boolean retain=suite.equals("motion") && index>=0;
            if(!retain){
            if(target!=null && target!=b)target.setDead();
            a.playerNetServerHandler.setPlayerLocation(0,64,0,0,0);
            b.playerNetServerHandler.setPlayerLocation(-15,64,-15,0,0);
            a.worldObj.setWorldTime(18000);
            target=kind.equals("PLAYER")?b:kind.equals("CHICKEN")?new EntityChicken(a.worldObj):kind.equals("SPIDER")?new EntitySpider(a.worldObj):kind.equals("CAVE_SPIDER")?new EntityCaveSpider(a.worldObj):kind.equals("ENDERMAN")?new net.minecraft.entity.monster.EntityEnderman(a.worldObj):new EntityZombie(a.worldObj);
            if(target instanceof EntityLiving){EntityLiving e=(EntityLiving)target;e.tasks.taskEntries.clear();e.targetTasks.taskEntries.clear();e.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);e.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(4000);e.setHealth(4000);}
            target.setPosition(0,64,r);
            if(target!=b)a.worldObj.spawnEntityInWorld(target);
            }
            index=current;
            String text="P0_COMPOSITION_PHASE name="+name+" target="+target.getEntityId()+" tick="+Clashweave.server.tick;
            a.addChatMessage(new ChatComponentText(text));System.out.println(text);
            System.out.println("P0_CAMERA_ENTITY phase="+name+" class="+target.getClass().getSimpleName()+" height="+target.height+" width="+target.width);
        }
        if(suite.equals("motion") && current<2 && (local>0 || current>0))motionAngle+=(current==0?45:90)/20.0;
        double angle=suite.equals("motion")?motionAngle:0;
        double x=-Math.sin(Math.toRadians(angle))*r,z=Math.cos(Math.toRadians(angle))*r;
        if(target==b)b.playerNetServerHandler.setPlayerLocation(x,64,z,180,0);
        else target.setPosition(x,64,z);
        target.motionX=target.motionY=target.motionZ=0;
        if(target instanceof net.minecraft.entity.EntityCreature)((net.minecraft.entity.EntityCreature)target).setTarget(null);
        if(local==100)System.out.println("P0_CAMERA_POSITION phase="+name+" actualRange="+Math.hypot(target.posX-a.posX,target.posZ-a.posZ)+" feetDelta="+(target.boundingBox.minY-a.boundingBox.minY));
        if(!suite.equals("audit"))Clashweave.server.state(a).lock=target.getEntityId();
        if(suite.equals("motion") && current==2 && local==30)System.out.println("P0_CAMERA_STRAFE normalMovement=true");
        ticks++;
    }
}
