package com.layue13.clashweave.s1d;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/** Persistent engagement ownership; damage dispatch never changes vanilla fields. */
public final class EventGate {
    private static final Field LAST = ReflectionHelper.findField(EntityLivingBase.class,"lastDamage","field_110153_bc");
    private final Map<EntityLivingBase,Owned> owned=new IdentityHashMap<EntityLivingBase,Owned>();
    private final Set<String> seen=new HashSet<String>();
    static final class Owned { final int original,applied,dimension; Owned(EntityLivingBase e,int n){original=e.maxHurtResistantTime;applied=n;dimension=e.dimension;} }
    void enter(EntityLivingBase e){if(!owned.containsKey(e)){int n=Integer.getInteger("cw.s1d.max",8);if(n<1)throw new IllegalArgumentException("max must be positive");owned.put(e,new Owned(e,n));e.maxHurtResistantTime=n;}}
    void leave(EntityLivingBase e){Owned o=owned.remove(e);if(o!=null&&e.maxHurtResistantTime==o.applied)e.maxHurtResistantTime=o.original;}
    @SubscribeEvent(priority=cpw.mods.fml.common.eventhandler.EventPriority.LOWEST) public void attackMarker(net.minecraftforge.event.entity.living.LivingAttackEvent e) { /* read-only same-priority registration witness */ }
    @SubscribeEvent(priority=cpw.mods.fml.common.eventhandler.EventPriority.HIGHEST) public void hurtMarker(net.minecraftforge.event.entity.living.LivingHurtEvent e) { /* read-only registration witness */ }
    @SubscribeEvent public void death(LivingDeathEvent e){leave(e.entityLiving);}
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e){leave(e.player);}
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){leave(e.player);}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END)prune();}
    void prune(){for(EntityLivingBase e:new java.util.ArrayList<EntityLivingBase>(owned.keySet()))if(!e.isEntityAlive()||e.dimension!=owned.get(e).dimension)leave(e);}
    static float last(EntityLivingBase e){try{return LAST.getFloat(e);}catch(IllegalAccessException x){throw new IllegalStateException(x);}}
    boolean apply(EntityLivingBase e,DamageSource s,float amount){return e.attackEntityFrom(s,amount);}
    boolean hit(String action,EntityLivingBase e,int segment,DamageSource s,float amount){if(!seen.add(action+":"+e.getUniqueID()+":"+segment))return false;return apply(e,s,amount);}
    void end(String action){java.util.Iterator<String> it=seen.iterator();while(it.hasNext())if(it.next().startsWith(action+":"))it.remove();}
    int ledgerSize(){return seen.size();} int ownedSize(){return owned.size();}
}
