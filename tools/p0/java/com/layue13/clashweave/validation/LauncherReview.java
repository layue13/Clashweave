package com.layue13.clashweave.validation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.Scheduler;
import com.layue13.clashweave.network.InputMessage;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Review-only server fixtures, works against both frozen jars without linking new methods. */
public final class LauncherReview {
    private static final class CountedZombie extends EntityZombie {
        int queries;
        CountedZombie(net.minecraft.world.World world){super(world);}
        @Override public boolean isInWater(){queries++;return false;}
    }
    private int elapsed, stage;
    private long firstTick, budgetNano;
    private Object budget;
    private EntityZombie target;
    private Scheduler.Instance reviewAction;
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event)throws Exception {
        if(event.phase!=TickEvent.Phase.END||!"review".equals(System.getProperty("cw.p1.launcher")))return;
        EntityPlayerMP player=null;
        for(Object o:MinecraftServer.getServer().getConfigurationManager().playerEntityList){EntityPlayerMP p=(EntityPlayerMP)o;if(p.getCommandSenderName().equals("P0A"))player=p;}
        if(player==null||Clashweave.server.state(player)==null)return;
        if(++elapsed<80)return;
        com.layue13.clashweave.forge.CombatServer.PlayerState state=Clashweave.server.state(player);
        if(stage==0){
            player.playerNetServerHandler.setPlayerLocation(0,64,0,0,0);
            target=new EntityZombie(player.worldObj);target.setPosition(0,64,1.8);target.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(200);target.setHealth(200);target.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);player.worldObj.spawnEntityInWorld(target);
            Constructor<Scheduler.Instance> c=Scheduler.Instance.class.getDeclaredConstructor(long.class,long.class,int.class,ActionCatalog.Definition.class);c.setAccessible(true);
            Field f=Scheduler.class.getDeclaredField("current");f.setAccessible(true);state.scheduler.interrupt();reviewAction=c.newInstance(123456L,Clashweave.server.tick,0,Ids.katana().get("launcher"));f.set(state.scheduler,reviewAction);stage=1;
        }else if(stage==1 && state.ready){
            budget=state.movement;budgetNano=state.budgetNano;firstTick=Clashweave.server.tick;
            Field last=state.gate.getClass().getDeclaredField("lastSequence");last.setAccessible(true);
            InputMessage duplicate=new InputMessage(state.session,Clashweave.server.tick,last.getInt(state.gate)+1,null,123456);
            Clashweave.server.enqueue(player,duplicate);stage=2;
        }else if(stage==2 && Clashweave.server.tick>=firstTick+2){
            System.out.println("P1_AUDIT_READY rebuilt="+(state.movement!=budget)+" timerChanged="+(state.budgetNano!=budgetNano));stage=3;
        }else if(stage==3 && target.motionY>0){
            Field f=Clashweave.server.getClass().getDeclaredField("effects");f.setAccessible(true);Object effects=f.get(Clashweave.server);
            Field t=effects.getClass().getDeclaredField("targets");t.setAccessible(true);Map records=(Map)t.get(effects);
            System.out.println("P1_AUDIT_UNLOAD before="+records.size()+" dead="+target.isDead+" inList="+target.worldObj.loadedEntityList.contains(target));
            target.worldObj.onEntityRemoved(target);firstTick=Clashweave.server.tick;stage=4;
        }else if(stage==4 && Clashweave.server.tick>=firstTick+1){
            Field f=Clashweave.server.getClass().getDeclaredField("effects");f.setAccessible(true);Object effects=f.get(Clashweave.server);Field t=effects.getClass().getDeclaredField("targets");t.setAccessible(true);
            System.out.println("P1_AUDIT_UNLOAD after="+((Map)t.get(effects)).size()+" dead="+target.isDead+" inList="+target.worldObj.loadedEntityList.contains(target)+" indexed="+(target.worldObj.getEntityByID(target.getEntityId())==target));
            // Not spawned: isolate apply's calls from unrelated vanilla tick water queries.
            CountedZombie counter=new CountedZombie(player.worldObj);counter.queries=0;
            Method apply=effects.getClass().getDeclaredMethod("apply",EntityPlayerMP.class,net.minecraft.entity.EntityLivingBase.class,Scheduler.Instance.class,long.class,long.class,boolean.class,int.class);apply.setAccessible(true);
            apply.invoke(effects,player,counter,reviewAction,Clashweave.server.tick,System.nanoTime(),true,0);
            System.out.println("P1_AUDIT_EXEMPT queries="+counter.queries);
            System.out.println("P1_COMPLETE review");MinecraftServer.getServer().initiateShutdown();stage=5;
        }
        if(elapsed>180)throw new IllegalStateException("review fixture timeout stage="+stage);
    }
}
