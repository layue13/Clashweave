package com.layue13.clashweave.validation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.Scheduler;
import com.layue13.clashweave.forge.CombatServer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Isolated validation: real geometry for the matrix; explicitly injected contacts for budget stress. */
public final class LauncherScenario {
    private int elapsed, index=-1, sample;
    private EntityLivingBase target, second;
    private EntityPlayerMP a,b;
    private double origin,peak;
    private float health;

    private int beforeCorrections;
    private boolean airborneBudget;

    private Scheduler.Instance instance(String action,long start) throws Exception {
        Constructor<Scheduler.Instance> c=Scheduler.Instance.class.getDeclaredConstructor(long.class,long.class,int.class,ActionCatalog.Definition.class);
        c.setAccessible(true);return c.newInstance(100000L+sample++,start,0,Clashweave.config.actions.get(action));
    }
    private void force(EntityPlayerMP player,String action) throws Exception {
        CombatServer.PlayerState s=Clashweave.server.state(player);s.scheduler.interrupt();s.scheduler.clearBuffer();
        Field f=Scheduler.class.getDeclaredField("current");f.setAccessible(true);f.set(s.scheduler,instance(action,Clashweave.server.tick));
    }
    private void contact(EntityLivingBase entity) throws Exception {
        Class<?> type=Class.forName("com.layue13.clashweave.forge.CombatServer$Contact");Constructor<?> c=type.getDeclaredConstructor();c.setAccessible(true);Object hit=c.newInstance();
        Object[][] values={{"attacker",Clashweave.server.state(a)},{"target",entity},{"instance",instance("launcher",Clashweave.server.tick)},{"tick",Clashweave.server.tick},{"frozen",System.nanoTime()},{"facing",true}};
        for(Object[] value:values){Field f=type.getDeclaredField((String)value[0]);f.setAccessible(true);f.set(hit,value[1]);}
        if(entity instanceof EntityPlayerMP){Field f=CombatServer.class.getDeclaredField("pending");f.setAccessible(true);((List)f.get(Clashweave.server)).add(hit);}
        else {Method m=CombatServer.class.getDeclaredMethod("commit",type,long.class);m.setAccessible(true);m.invoke(Clashweave.server,hit,System.nanoTime());}
    }
    private void prepare(boolean player) throws Exception {
        if(target!=null && !(target instanceof EntityPlayerMP))target.setDead();
        if(second!=null){second.setDead();second=null;}
        b.setGameType(net.minecraft.world.WorldSettings.GameType.SURVIVAL);
        for(EntityPlayerMP p:new EntityPlayerMP[]{a,b}){CombatServer.PlayerState s=Clashweave.server.state(p);s.scheduler.interrupt();s.scheduler.clearBuffer();s.guard.release(Clashweave.server.tick);p.motionX=0;p.motionY=0;p.motionZ=0;p.setHealth(200);p.fallDistance=0;}
        a.playerNetServerHandler.setPlayerLocation(0,64,0,0,0);b.playerNetServerHandler.setPlayerLocation((player||index==86||index==87||index==89)?0:8,64,(player||index==86||index==87||index==89)?1.8:0,180,0);
        if(player || index==86 || index==87 || index==89)target=b;
        else {int kind=index/20;target=index>=90&&index<=109?new EntityEnderman(a.worldObj):index==111?new EntityZombie(a.worldObj):kind==0?new EntityZombie(a.worldObj):kind==1?new EntitySpider(a.worldObj):kind==2?new EntityChicken(a.worldObj):kind==3?new EntityEnderman(a.worldObj):(index==80||index==88)?new EntityZombie(a.worldObj):index==81?new EntityWither(a.worldObj):index==82?new EntityGhast(a.worldObj):index==83?new EntityChicken(a.worldObj):index==84?new EntitySpider(a.worldObj):new EntityCaveSpider(a.worldObj);
            target.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(200);target.setHealth(200);if(target.getEntityAttribute(SharedMonsterAttributes.movementSpeed)!=null)target.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);target.setPosition(0,64,1.8);a.worldObj.spawnEntityInWorld(target);}
        if(index==88||index==111){second=new EntityZombie(a.worldObj);second.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(200);second.setHealth(200);second.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);second.setPosition(.3,64,1.8);a.worldObj.spawnEntityInWorld(second);}
        if(index==89)b.setGameType(net.minecraft.world.WorldSettings.GameType.CREATIVE);
        origin=target.posY;peak=origin;health=target.getHealth();airborneBudget=false;beforeCorrections=Clashweave.server.state(b).corrections;
    }
    private Object budgetField(Object budget,String name)throws Exception {
        try {Field f=budget.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(budget);}
        catch(NoSuchFieldException missing){
            if(!name.equals("launchAllowance"))throw missing;
            double value=((Number)budgetField(budget,"spentLaunch")).doubleValue();
            for(Object credit:(List)budgetField(budget,"credits")){Field f=credit.getClass().getDeclaredField("remaining");f.setAccessible(true);value+=f.getDouble(credit);}
            return value;
        }
    }
    @SubscribeEvent(priority=cpw.mods.fml.common.eventhandler.EventPriority.HIGHEST)
    public void audit(TickEvent.ServerTickEvent e){
        if(!Boolean.getBoolean("cw.p1.audit")||e.phase!=TickEvent.Phase.END||b==null||index<0||elapsed<25||elapsed>70)return;
        CombatServer.PlayerState s=Clashweave.server.state(b);if(s==null)return;
        try {System.out.println("P1_BUDGET case="+index+" tick="+(Clashweave.server.tick+1)+" y="+b.posY+" baseY="+s.y+" ground="+b.onGround+" credit="+s.launchCredit+" allowance="+(s.movement==null?-1:budgetField(s.movement,"launchAllowance"))+" vertical="+(s.movement==null?-1:budgetField(s.movement,"vertical"))+" external="+s.externalVertical+" ready="+s.ready+" nano="+System.nanoTime());}catch(Exception failure){throw new IllegalStateException(failure);}
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e) {
        String mode=System.getProperty("cw.p1.launcher");if(mode==null||mode.equals("review")||e.phase!=TickEvent.Phase.END)return;
        for(Object o:MinecraftServer.getServer().getConfigurationManager().playerEntityList){EntityPlayerMP p=(EntityPlayerMP)o;if(p.getCommandSenderName().equals("P0A"))a=p;else b=p;}
        if(a==null||b==null||Clashweave.server.state(a)==null||Clashweave.server.state(b)==null)return;
        try {
            boolean player=mode.equals("player");
            if(mode.equals("enderman")||mode.equals("control")||mode.equals("native-final"))a.worldObj.setWorldTime(18000);
            if(mode.equals("control")||mode.equals("native-final"))a.inventory.armorInventory[3]=new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.pumpkin);
            if(mode.equals("native-final") && index<0){
                a.worldObj.getGameRules().setOrCreateGameRule("doMobSpawning","false");
                for(Object entity:new java.util.ArrayList(a.worldObj.loadedEntityList))if(entity instanceof EntityLivingBase && !(entity instanceof EntityPlayerMP))((EntityLivingBase)entity).setDead();
            }
            if(index<0){if(++elapsed<80)return;index=mode.equals("control")?89:mode.equals("extras")?86:mode.equals("enderman")?60:0;elapsed=0;prepare(player);}
            elapsed++;peak=Math.max(peak,target.posY);
            if((index==86 && elapsed==20)||(index==87 && elapsed==29))Clashweave.server.state(b).guard.press(Clashweave.server.tick,index==86?System.nanoTime()-1_000_000_000L:System.nanoTime(),0);
            if(elapsed==25){System.out.println("P1_BEGIN case="+index+" mode="+mode+" entity="+target.getClass().getSimpleName()+" target="+target.getEntityId()+" tick="+Clashweave.server.tick+" x="+target.posX+" z="+target.posZ+" nano="+System.nanoTime());
                if(player){a.addChatMessage(new ChatComponentText("P1_BEGIN "+index));b.addChatMessage(new ChatComponentText("P1_BEGIN "+index));}
                else if(index==110)contact(target);else if(index<80 || index>=83)force(a,"launcher");else contact(target);}
            if(index==111 && elapsed==29){target.setPosition(.2,64,1.4);second.setPosition(.2,64,2.2);target.motionX=target.motionZ=second.motionX=second.motionZ=0;}
            if(index==110 && elapsed==33)force(a,"light_1");
            if(!player && index==80 && (elapsed==36||elapsed==47||elapsed==58)) {contact(target);}
            if(!player && index>=83 && index<=85 && elapsed==38){float before=target.getHealth();force(a,"light_1");System.out.println("P1_FOLLOW case="+index+" before="+before+" y="+target.posY);}
            if(player && elapsed==45)force(b,"heavy");
            if(player && target.motionY>=.59 && !airborneBudget){airborneBudget=true;force(b,"heavy");System.out.println("P1_AIR_BUDGET tick="+Clashweave.server.tick+" credit="+Clashweave.server.state(b).launchCredit);}
            if(elapsed==(player?125:70)){
                System.out.println("P1_RESULT case="+index+" mode="+mode+" entity="+target.getClass().getSimpleName()+" target="+target.getEntityId()+" dy="+(peak-origin)+" damage="+(health-target.getHealth())+" corrections="+(Clashweave.server.state(b).corrections-beforeCorrections)+" tick="+Clashweave.server.tick+" x="+target.posX+" z="+target.posZ+" nano="+System.nanoTime());
                if(player){a.addChatMessage(new ChatComponentText("P1_END "+index));b.addChatMessage(new ChatComponentText("P1_END "+index));}
                if(second!=null)System.out.println("P1_MULTI secondDamage="+(200-second.getHealth())+" secondY="+second.posY);
                if(++index==(player?20:mode.equals("extras")?90:mode.equals("enderman")?80:mode.equals("control")?112:86)){System.out.println("P1_COMPLETE mode="+mode);MinecraftServer.getServer().initiateShutdown();return;}
                elapsed=0;prepare(player);
            }
        } catch(Exception failure){throw new IllegalStateException("P1 fixture case="+index,failure);}
    }
}
