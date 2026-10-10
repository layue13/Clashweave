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
    private int previousSouls=-1;
    private EntityPlayerMP a; private net.minecraft.entity.EntityLivingBase target; private int ticks,wait; private boolean started;
    private int soulsKind=1;private double soulsSpawnRange=8;
    private net.minecraft.entity.EntityLivingBase spawn(double angle){net.minecraft.entity.EntityLiving z=Boolean.getBoolean("cw.p0.souls") && soulsKind==0?new net.minecraft.entity.monster.EntitySpider(a.worldObj):Boolean.getBoolean("cw.p0.souls") && soulsKind==2?new net.minecraft.entity.monster.EntityEnderman(a.worldObj):new EntityZombie(a.worldObj);z.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(4000);z.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);z.setHealth(4000);if(Boolean.getBoolean("cw.p0.souls")){z.tasks.taskEntries.clear();z.targetTasks.taskEntries.clear();}place(z,angle,soulsSpawnRange,64);a.worldObj.spawnEntityInWorld(z);return z;}
    private void place(net.minecraft.entity.EntityLivingBase z,double angle,double r,double y){z.setPosition(a.posX-Math.sin(Math.toRadians(angle))*r,y,a.posZ+Math.cos(Math.toRadians(angle))*r);z.motionX=z.motionY=z.motionZ=0;if(z instanceof net.minecraft.entity.EntityLiving)((net.minecraft.entity.EntityLiving)z).setAttackTarget(null);if(z instanceof net.minecraft.entity.EntityCreature)((net.minecraft.entity.EntityCreature)z).setTarget(null);}
    private void phase(String name){String text="P0_COMPOSITION_PHASE name="+name+" target="+target.getEntityId()+" tick="+Clashweave.server.tick;a.addChatMessage(new ChatComponentText(text));if(Boolean.getBoolean("cw.p0.souls"))for(Object o:MinecraftServer.getServer().getConfigurationManager().playerEntityList){EntityPlayerMP observer=(EntityPlayerMP)o;if(observer!=a)observer.addChatMessage(new ChatComponentText(text));}System.out.println(text);}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
        if(!System.getProperty("cw.p0.cameraSuite","").isEmpty())return;
        if(!Boolean.getBoolean("cw.p0.composition") || event.phase!=TickEvent.Phase.END || MinecraftServer.getServer().getCurrentPlayerCount()<2)return;
        if(a==null)for(Object o:MinecraftServer.getServer().getConfigurationManager().playerEntityList){EntityPlayerMP p=(EntityPlayerMP)o;if(p.getCommandSenderName().equals("P0A"))a=p;else p.playerNetServerHandler.setPlayerLocation(-15,64,-15,Boolean.getBoolean("cw.p0.souls")?-45:0,Boolean.getBoolean("cw.p0.souls")?-2:0);}
        if(!started){if(target==null && ++wait>=30){target=spawn(0);phase("WAIT");}if(target==null || Clashweave.server.state(a).lock!=target.getEntityId())return;started=true;ticks=Boolean.getBoolean("cw.p0.firstOnly")?1000:0;}
        ticks++;
        if(Boolean.getBoolean("cw.p0.souls")){ souls();return; }
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

    private void souls(){
        int index=(ticks-1)/120, local=(ticks-1)%120;
        if(index>=12){System.out.println("P0_NETWORK COMPLETE");MinecraftServer.getServer().initiateShutdown();return;}
        double range=index<9?new double[]{2,4,8}[index/3]:8;
        double height=index<9?new double[]{.9,1.8,2.9}[index%3]:1.8;
        String name=index<9?"SOULS_R"+(int)range+"_H"+height:index==9?"OFF_R8_H1.8":index==10?"SOULS_WALL":"SOULS_CEILING";
        if(index!=previousSouls){
            previousSouls=index;
            a.capabilities.disableDamage=true;
            for(Object o:MinecraftServer.getServer().getConfigurationManager().playerEntityList)((EntityPlayerMP)o).capabilities.disableDamage=true;
            a.worldObj.setWorldTime(18000);
            soulsKind=index<9?index%3:1;
            a.playerNetServerHandler.setPlayerLocation(0,64,index>=10?4:0,0,0);
            soulsSpawnRange=range;
            target.setDead();target=spawn(0);
            for(int x=-7;x<=7;x++)for(int y=64;y<=68;y++)for(int z=-9;z<=1;z++)a.worldObj.setBlockToAir(x,y,z);
            if(index==10)for(int x=-7;x<=7;x++)for(int y=64;y<=68;y++)a.worldObj.setBlock(x,y,-3,net.minecraft.init.Blocks.stone);
            if(index==11)for(int x=-7;x<=7;x++)for(int z=-9;z<=-2;z++)a.worldObj.setBlock(x,66,z,net.minecraft.init.Blocks.stone);
            phase(name);
        }
        double playerZ=index>=10?4-Math.min(4,local*.04):0;
        if(index>=10)a.playerNetServerHandler.setPlayerLocation(0,64,playerZ,0,0);
        Clashweave.server.state(a).lock=target.getEntityId();place(target,0,range,64);
    }
    public static void setSize(net.minecraft.entity.Entity e,double height){
        try{java.lang.reflect.Method method=cpw.mods.fml.relauncher.ReflectionHelper.findMethod(net.minecraft.entity.Entity.class,e,new String[]{"setSize","func_70105_a"},float.class,float.class);method.invoke(e,(float)(.6*height/1.95),(float)height);if(e instanceof EntityZombie){java.lang.reflect.Method scale=cpw.mods.fml.relauncher.ReflectionHelper.findMethod(EntityZombie.class,(EntityZombie)e,new String[]{"func_146069_a"},float.class);scale.invoke(e,1F);}}catch(Exception ex){throw new RuntimeException(ex);}
    }
}
