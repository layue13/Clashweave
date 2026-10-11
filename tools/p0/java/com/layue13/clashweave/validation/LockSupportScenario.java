package com.layue13.clashweave.validation;

import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import com.layue13.clashweave.Clashweave;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Isolated target placement and scenario phases; all player attacks use production input. */
public final class LockSupportScenario {
    private int ticks;
    private boolean started;
    private EntityZombie target;
    private EntityPlayerMP a;
    private String phase="";
    private double angle;
    private double distance=1.9;

    private void target() {
        target=new EntityZombie(a.worldObj);
        target.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(4000);
        target.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);
        target.setHealth(4000);target.setPosition(a.posX,64,a.posZ+1.9); a.worldObj.spawnEntityInWorld(target);
    }
    private void phase(String name, int serial, double degrees, boolean assist) {
        phase=name; angle=degrees; Clashweave.config.lockAssistEnabled=assist;
        String text="P0_LOCK_PHASE name="+name+" serial="+serial+" angle="+degrees+" assist="+assist+" target="+target.getEntityId()+" tick="+Clashweave.server.tick;
        a.addChatMessage(new ChatComponentText(text)); System.out.println(text);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("cw.p0.lockSupport") || event.phase!=TickEvent.Phase.END
            || MinecraftServer.getServer().getCurrentPlayerCount()<2) return;
        if(a==null)for(Object o:MinecraftServer.getServer().getConfigurationManager().playerEntityList){
            EntityPlayerMP p=(EntityPlayerMP)o;
            if(p.getCommandSenderName().equals("P0A"))a=p;
            else p.playerNetServerHandler.setPlayerLocation(-12,64,-12,0,0);
        }
        ticks++;
        if(!started){
            if(target==null && ticks>=30){target();phase("WAIT",0,0,true);}
            if(target==null || Clashweave.server.state(a)==null || Clashweave.server.state(a).lock!=target.getEntityId())return;
            started=true;ticks=Boolean.getBoolean("cw.p0.followOnly")?1190:0;System.out.println("P0_LOCK_READY actualInputAccepted=true");
        }
        if(Boolean.getBoolean("cw.p0.followOnly") && ticks==1460){System.out.println("P0_NETWORK COMPLETE");MinecraftServer.getServer().initiateShutdown();return;}
        if(ticks==40){phase("MARK_LOCK",0,0,true);}
        if(ticks==80)phase("MARK_UNLOCK",0,0,true);
        if(ticks==120){target.setDead();phase("MARK_DEAD",0,0,true);}
        if(ticks==160){target();phase("MARK_RELOCK",0,0,true);}
        if(ticks==200){distance=22;phase("MARK_RANGE",0,0,true);}
        if(ticks==230){distance=1.9;phase("AIM_READY",0,0,true);}
        if(ticks>=260 && ticks<1220 && (ticks-260)%24==0){
            int n=(ticks-260)/24;phase("AIM",n,new double[]{10,30,60}[(n/2)%3],n%2==1);
        }
        if(ticks==1220)phase("WEAK",0,0,true);
        if(ticks==1340)phase("STRONG",0,0,true);
        if(phase.equals("WEAK"))angle=(ticks-1220)<60?(ticks-1220):-130;
        if(phase.equals("STRONG"))angle=(ticks-1340)<60?(ticks-1340)*3:200+(ticks-1400)*2;
        if(ticks>=1460 && ticks<2100 && (ticks-1460)%32==0){
            int n=(ticks-1460)/32;phase(n%2==0?"CAMERA_OFF":"CAMERA_STRONG",n,30,true);
        }
        if(ticks==2100){phase("OCCLUDED",0,30,true);for(int x=(int)Math.floor(a.posX)-2;x<=(int)Math.floor(a.posX)+2;x++)for(int y=64;y<=68;y++)a.worldObj.setBlock(x,y,(int)Math.floor(a.posZ)+1,net.minecraft.init.Blocks.stone);}
        if(ticks==2160){for(int x=(int)Math.floor(a.posX)-2;x<=(int)Math.floor(a.posX)+2;x++)for(int y=64;y<=68;y++)a.worldObj.setBlock(x,y,(int)Math.floor(a.posZ)+1,net.minecraft.init.Blocks.air);phase("GUI",0,30,true);}
        if(ticks==2200)phase("FINISH",0,0,true);
        if(target!=null && !target.isDead){
            target.setPosition(a.posX-Math.sin(Math.toRadians(angle))*distance,64,a.posZ+Math.cos(Math.toRadians(angle))*distance);
            target.motionX=0;target.motionY=0;target.motionZ=0;target.setAttackTarget(null);
        }
        if(ticks==2240){System.out.println("P0_LOCK_SUPPORT COMPLETE");System.out.println("P0_NETWORK COMPLETE");MinecraftServer.getServer().initiateShutdown();}
    }
}
