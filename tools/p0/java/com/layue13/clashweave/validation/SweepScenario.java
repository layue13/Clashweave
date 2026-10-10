package com.layue13.clashweave.validation;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import com.layue13.clashweave.Clashweave;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Real geometry and attackEntityFrom; the fixture only arranges the four requested scenes. */
public final class SweepScenario {
    private int scenario;
    private int contacts;
    @SubscribeEvent public void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if(event.entityLiving==target && event.source.getEntity() instanceof EntityPlayerMP && event.source.getEntity().getCommandSenderName().equals("P0A")) {
            contacts++;System.out.println("P0_SWEEP_DAMAGE case="+scenario+" source="+event.source.getDamageType()+" amount="+event.ammount+" attackerFeet="+event.source.getEntity().posY+" attackerGround="+event.source.getEntity().onGround);
        }
    }
    private int wait;
    private EntityLiving target;
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void tick(TickEvent.ServerTickEvent event) {
        if(!Boolean.getBoolean("cw.p0.sweepTest") || event.phase!=TickEvent.Phase.END) return;
        EntityPlayerMP a=null;
        if(MinecraftServer.getServer().getCurrentPlayerCount()<2) return;
        for(Object o:MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p=(EntityPlayerMP)o;
            if(p.getCommandSenderName().equals("P0A")) a=p;
        }
        if(a==null || ++wait<110) return;
        if(target!=null && contacts>0 && target.getHealth()<200 && Clashweave.server.state(a).scheduler.current()==null) {
            System.out.println("P0_SWEEP case="+scenario+" success=true loss="+(200-target.getHealth())+" targetHeight="+target.height+" targetY="+target.posY);
            target.setDead();target=null;wait=100;
            if(scenario==4) {System.out.println("P0_SWEEP COMPLETE");MinecraftServer.getServer().initiateShutdown();return;}
        }
        if(target==null) {
            scenario++;contacts=0;a.worldObj.setWorldTime(18000);a.playerNetServerHandler.setPlayerLocation(0,64,0,0,0);
            if(scenario==3)a.worldObj.setBlock(0,64,2,Blocks.stone_stairs,2,3);else a.worldObj.setBlock(0,64,2,Blocks.air);
            if(scenario==1) target=new EntitySpider(a.worldObj);
            else if(scenario==2) {EntityCow cow=new EntityCow(a.worldObj);cow.setGrowingAge(-24000);target=cow;}
            else target=new EntityZombie(a.worldObj);
            target.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(200);
            target.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);target.setHealth(200);
            target.setPosition(0,scenario==3?65:64,scenario==3?2.7:1.9);target.setCustomNameTag("sweep:"+scenario);a.worldObj.spawnEntityInWorld(target);
            System.out.println("P0_SWEEP_SETUP case="+scenario+" target="+target.getEntityId()+" height="+target.height);
        }
        target.setPosition(0,scenario==3?65:64,scenario==3?2.7:1.9);
        target.motionX=target.motionY=target.motionZ=0;target.fallDistance=0;
        if(wait>240) {System.out.println("P0_SWEEP case="+scenario+" success=false health="+target.getHealth());throw new AssertionError("sweep case "+scenario);}
    }
}
