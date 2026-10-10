package com.layue13.clashweave.validation;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import com.layue13.clashweave.Clashweave;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
public final class CompositionScenario {
    private EntityPlayerMP a; private EntityZombie target; private int ticks,wait; private boolean started;
    private EntityZombie spawn(double angle){EntityZombie z=new EntityZombie(a.worldObj);z.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(4000);z.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);z.setHealth(4000);place(z,angle,8,64);a.worldObj.spawnEntityInWorld(z);return z;}
    private void place(EntityZombie z,double angle,double r,double y){z.setPosition(a.posX-Math.sin(Math.toRadians(angle))*r,y,a.posZ+Math.cos(Math.toRadians(angle))*r);z.motionX=z.motionY=z.motionZ=0;z.setAttackTarget(null);}
    private void phase(String name){String text="P0_COMPOSITION_PHASE name="+name+" target="+target.getEntityId()+" tick="+Clashweave.server.tick;a.addChatMessage(new ChatComponentText(text));System.out.println(text);}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
        if(!Boolean.getBoolean("cw.p0.composition") || event.phase!=TickEvent.Phase.END || MinecraftServer.getServer().getCurrentPlayerCount()<2)return;
        if(a==null)for(Object o:MinecraftServer.getServer().getConfigurationManager().playerEntityList){EntityPlayerMP p=(EntityPlayerMP)o;if(p.getCommandSenderName().equals("P0A"))a=p;else p.playerNetServerHandler.setPlayerLocation(-15,64,-15,0,0);}
        if(!started){if(target==null && ++wait>=30){target=spawn(0);phase("WAIT");}if(target==null || Clashweave.server.state(a).lock!=target.getEntityId())return;started=true;ticks=Boolean.getBoolean("cw.p0.firstOnly")?1000:0;}
        ticks++;
        if(Boolean.getBoolean("cw.p0.cameraProbe")){
            String[] phases={"GOLDEN_STATIC_THIRD","GOLDEN_STATIC_FIRST","GOLDEN_ORBIT_THIRD","GOLDEN_ORBIT_FIRST","THIRDS_STATIC_THIRD","THIRDS_STATIC_FIRST","THIRDS_ORBIT_THIRD","THIRDS_ORBIT_FIRST","CENTER_STATIC_THIRD","CENTER_STATIC_FIRST","CENTER_ORBIT_THIRD","CENTER_ORBIT_FIRST","PROBE_DISABLED","PROBE_F5_FRONT"};
            int index=(ticks-1)/100;if(index>=phases.length){System.out.println("P0_NETWORK COMPLETE");MinecraftServer.getServer().initiateShutdown();return;}
            if((ticks-1)%100==0)phase(phases[index]);place(target,phases[index].contains("ORBIT")?(ticks%100)*.25:0,8,64);return;
        }
        String[] names={"STILL","ORBIT","JITTER","JUMP","MOUSE","FIRST"};int i=(ticks-1)/200;
        if(i>=names.length){System.out.println("P0_NETWORK COMPLETE");MinecraftServer.getServer().initiateShutdown();return;}
        if((ticks-1)%200==0){phase(names[i]);if(i==4){spawn(30);spawn(-30);}}
        double t=(ticks-1)%200/20.0;
        double angle=i==1?t*30:0;
        place(target,angle,8,i==3?64+Math.sin(t*2*Math.PI):64);
        if(i==2)target.setPosition(a.posX+.3*Math.sin(2*Math.PI*5*t),64,a.posZ+8);
    }
}
