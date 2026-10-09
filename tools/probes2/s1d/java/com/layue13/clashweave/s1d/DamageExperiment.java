package com.layue13.clashweave.s1d;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

@Mod(modid = "cw_s1d_experiment", name = "Clashweave S1d experiment", version = "round3")
public final class DamageExperiment {
    private static final DamageSource SOURCE = new DamageSource("clashweave.s1d");
    private final EventGate gate = new EventGate();
    private EntityLivingBase observed;
    private String scenario;
    private String path;
    private boolean nestedAccepted;
    private float nestedLoss;
    private int nestedCalls;
    private int failures;
    private int pairs;

    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) {
        MinecraftServer server = MinecraftServer.getServer();
        try {
            WorldServer world = server.worldServerForDimension(0);
            for(boolean player:new boolean[]{false,true}) for(boolean before:new boolean[]{false,true}) {
                for(String name:new String[]{"attack-normal","hurt-high","hurt-low","attack-lowest-before","attack-lowest-after","hurt-highest-before","hurt-highest-after"}) pair(world,name,player,before);
                for(String name:new String[]{"cancel-attack","cancel-hurt","zero","already-dead","lethal","throw-attack","throw-hurt","throw-attack-after-clear","throw-hurt-before-restore"}) {boundary(world,name,player,before,false);boundary(world,name,player,before,true);}
            }
            ledger(world); lifecycle(world); intervals(world); otherSources(world); transition(world);
            System.out.println("CW3 S1D_RESULT pairs="+pairs+" mismatches="+failures+" owned="+gate.ownedSize()+" ledger="+gate.ledgerSize()+" verdict="+(failures==0?"PASS":"FAIL"));
        } finally { unregister(); observed = null; server.initiateShutdown(); }
    }

    private EntityLivingBase prepared(WorldServer world, boolean player) {
        EntityLivingBase entity = player
            ? new EntityPlayerMP(MinecraftServer.getServer(), world, new GameProfile(UUID.randomUUID(), "S1dFixture"), new ItemInWorldManager(world))
            : new EntityCow(world);
        if (player) cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(EntityPlayerMP.class, (EntityPlayerMP) entity, Integer.valueOf(0), "field_147101_bU");
        entity.setPosition(0.5,80,0.5);
        entity.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(100);
        entity.setHealth(100);
        // Exact shared baseline from real vanilla damage, not a copied formula.
        if (!entity.attackEntityFrom(DamageSource.generic, 4)) throw new IllegalStateException("Seed rejected");
        for (int i = 0; i < 7; i++) {
            entity.onEntityUpdate();
            // MP's normal onUpdate separately decrements this field; fixture is not logged in.
            if (player) entity.hurtResistantTime--;
        }
        if (entity.getHealth() != 96 || entity.hurtResistantTime != 13 || EventGate.last(entity) != 4) throw new IllegalStateException("Unexpected baseline");
        gate.enter(entity);
        return entity;
    }
    private void unregister() {
        MinecraftForge.EVENT_BUS.unregister(this);
        MinecraftForge.EVENT_BUS.unregister(gate);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().unregister(gate);
    }
    private void register(boolean callbackBeforeGate) {
        unregister();
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(gate);
        if (callbackBeforeGate) { MinecraftForge.EVENT_BUS.register(this); MinecraftForge.EVENT_BUS.register(gate); }
        else { MinecraftForge.EVENT_BUS.register(gate); MinecraftForge.EVENT_BUS.register(this); }
    }
    private void pair(WorldServer world, String name, boolean player, boolean before) {
        register(before);
        scenario = name;
        EntityLivingBase control = prepared(world, player);
        EntityLivingBase candidate = prepared(world, player);
        // Both paths use identical native amounts; 6 reaches Hurt through the original differential gate.
        run(control, false, name.contains("hurt") ? 6 : 2);
        boolean acceptedControl = nestedAccepted;
        float lossControl = nestedLoss;
        float hpControl=control.getHealth(),lastControl=EventGate.last(control);int timerControl=control.hurtResistantTime;
        int callsControl = nestedCalls;
        run(candidate, true, name.contains("hurt") ? 6 : 2);
        boolean equivalent = acceptedControl == nestedAccepted && lossControl == nestedLoss && hpControl==candidate.getHealth() && lastControl==EventGate.last(candidate) && timerControl==candidate.hurtResistantTime;
        if (!equivalent) failures++;
        pairs++; gate.leave(control);gate.leave(candidate);
        System.out.println("CW3 S1D_PAIR scenario=" + name + " player="+player+" listenerRegisteredBeforeGate=" + before + " controlNested=" + acceptedControl + " candidateNested=" + nestedAccepted + " controlLoss=" + lossControl + " candidateLoss=" + nestedLoss + " callbacks=" + callsControl + "," + nestedCalls + " equivalent=" + equivalent);
    }
    private void run(EntityLivingBase entity, boolean candidate, float amount) {
        observed = entity; path = candidate ? "candidate" : "control";
        nestedAccepted = false; nestedLoss = 0; nestedCalls = 0;
        boolean accepted = candidate ? gate.apply(entity, SOURCE, amount) : entity.attackEntityFrom(SOURCE, amount);
        System.out.println("CW3 S1D_OUTER scenario=" + scenario + " path=" + path + " accepted=" + accepted + " hp=" + entity.getHealth() + " timer=" + entity.hurtResistantTime + " last=" + EventGate.last(entity));
        observed = null;
    }
    private void nested(String where) {
        int timer = observed.hurtResistantTime; float last = EventGate.last(observed), hp = observed.getHealth();
        // Fresh source object deliberately does not equal registered SOURCE.
        boolean result = observed.attackEntityFrom(new DamageSource("other.s1d"), 4);
        float loss = hp - observed.getHealth();
        nestedAccepted |= result; nestedLoss += loss; nestedCalls++;
        System.out.println("CW3 S1D_NESTED scenario=" + scenario + " path=" + path + " at=" + where + " timer=" + timer + " last=" + last + " accepted=" + result + " loss=" + loss);
    }
    private boolean watched(EntityLivingBase entity, DamageSource source) { return entity == observed && source == SOURCE; }
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void attackNormal(LivingAttackEvent e) {
        if (!watched(e.entityLiving, e.source)) return;
        if (scenario.equals("attack-normal") || scenario.equals("player-attack")) nested("attack-NORMAL");
        if (scenario.equals("cancel-attack")) e.setCanceled(true);
        if (scenario.equals("throw-attack")) throw new FixtureException();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void attackLowest(LivingAttackEvent e) {
        if (!watched(e.entityLiving, e.source)) return;
        if (scenario.startsWith("attack-lowest")) nested("attack-LOWEST");
        if (scenario.equals("throw-attack-after-clear")) {
            System.out.println("CW3 S1D_THROW at=attack-LOWEST timer=" + observed.hurtResistantTime + " last=" + EventGate.last(observed));
            throw new FixtureException();
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void hurtHighest(LivingHurtEvent e) {
        if (watched(e.entityLiving, e.source) && scenario.startsWith("hurt-highest")) nested("hurt-HIGHEST");
        if (watched(e.entityLiving, e.source) && scenario.equals("throw-hurt-before-restore")) {
            System.out.println("CW3 S1D_THROW at=hurt-HIGHEST timer=" + observed.hurtResistantTime + " last=" + EventGate.last(observed));
            throw new FixtureException();
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void hurtHigh(LivingHurtEvent e) {
        if (!watched(e.entityLiving, e.source)) return;
        if (scenario.equals("hurt-high") || scenario.equals("player-hurt")) nested("hurt-HIGH");
        if (scenario.equals("cancel-hurt")) e.setCanceled(true);
        if (scenario.equals("throw-hurt")) throw new FixtureException();
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public void hurtLow(LivingHurtEvent e) {
        if (watched(e.entityLiving, e.source) && scenario.equals("hurt-low")) nested("hurt-LOW");
    }
    public static final class FixtureException extends RuntimeException { private static final long serialVersionUID = 1L; }
    private void boundary(WorldServer world,String name,boolean player,boolean before,boolean history) {
        register(before);scenario=name;String[] states=new String[2];
        for(int i=0;i<2;i++){
            EntityLivingBase target=history?prepared(world,player):fresh(world,player);gate.enter(target);
            if(name.equals("already-dead"))target.setHealth(0);
            if(name.equals("lethal"))target.setHealth(1);
            observed=target;path=i==0?"control":"candidate";
            float hp=target.getHealth();boolean threw=false,accepted=false;
            try{accepted=i==0?target.attackEntityFrom(SOURCE,name.equals("zero")?0:2):gate.apply(target,SOURCE,name.equals("zero")?0:2);}
            catch(FixtureException ex){threw=true;}finally{observed=null;}
            states[i]=accepted+":"+threw+":"+(hp-target.getHealth())+":"+target.getHealth()+":"+target.hurtResistantTime+":"+EventGate.last(target)+":"+target.maxHurtResistantTime;
            gate.leave(target);boolean restored=target.maxHurtResistantTime==20;if(!restored)failures++;
            System.out.println("CW3 S1D_BOUNDARY scenario="+name+" player="+player+" before="+before+" history="+history+" path="+path+" state="+states[i]+" lifecycleRestored="+restored);
        }
        boolean equal=states[0].equals(states[1]);if(!equal)failures++;
        System.out.println("CW3 S1D_BOUNDARY_PAIR scenario="+name+" player="+player+" before="+before+" history="+history+" equivalent="+equal);
    }
    private EntityLivingBase fresh(WorldServer world,boolean player){
        EntityLivingBase e=player?new EntityPlayerMP(MinecraftServer.getServer(),world,new GameProfile(UUID.randomUUID(),"S1dFixture"),new ItemInWorldManager(world)):new EntityCow(world);
        if(player)cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(EntityPlayerMP.class,(EntityPlayerMP)e,Integer.valueOf(0),"field_147101_bU");
        e.setPosition(0.5,80,0.5);e.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(100);e.setHealth(100);return e;
    }
    private void advance(EntityLivingBase e,int ticks){for(int i=0;i<ticks;i++){e.onEntityUpdate();if(e instanceof EntityPlayerMP&&e.hurtResistantTime>0)e.hurtResistantTime--;}}
    private void ledger(WorldServer w){unregister();MinecraftForge.EVENT_BUS.register(gate);cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(gate);EntityLivingBase e=fresh(w,false);gate.enter(e);
        boolean first=gate.hit("a",e,0,SOURCE,2),duplicate=gate.hit("a",e,0,SOURCE,2);advance(e,4);
        boolean second=gate.hit("a",e,1,SOURCE,2);int size=gate.ledgerSize();gate.end("a");int end=gate.ledgerSize();gate.leave(e);
        if(!first||duplicate||!second||size!=2||end!=0)failures++;
        System.out.println("CW3 S1D_LEDGER first="+first+" duplicate="+duplicate+" second="+second+" beforeEnd="+size+" afterEnd="+end);
    }
    private void intervals(WorldServer w){for(boolean p:new boolean[]{false,true})for(int gap:new int[]{4,7}){
        EntityLivingBase e=fresh(w,p);gate.enter(e);int successes=0;float hp=e.getHealth();
        for(int i=0;i<3;i++){if(i>0)advance(e,gap);if(gate.hit("interval",e,i,SOURCE,2))successes++;}
        float loss=hp-e.getHealth();gate.end("interval");gate.leave(e);if(successes!=3||loss!=6)failures++;
        System.out.println("CW3 S1D_INTERVAL player="+p+" gap="+gap+" successes="+successes+" loss="+loss+" restored="+e.maxHurtResistantTime);
    }}
    private void lifecycle(WorldServer w){for(String why:new String[]{"leave","dead","dimension","logout","foreign","nondefault","entity-dimension"}){
        EntityLivingBase e=fresh(w,why.equals("logout")||why.equals("dimension"));if(why.equals("nondefault"))e.maxHurtResistantTime=30;gate.enter(e);gate.enter(e);
        if(why.equals("entity-dimension")){e.dimension=1;gate.prune();}else if(why.equals("dead")){e.setHealth(0);gate.prune();}else if(why.equals("dimension")){cpw.mods.fml.common.FMLCommonHandler.instance().bus().post(new cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent((EntityPlayerMP)e,0,1));}
        else if(why.equals("logout")){cpw.mods.fml.common.FMLCommonHandler.instance().bus().post(new cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent((EntityPlayerMP)e));}
        else{if(why.equals("foreign"))e.maxHurtResistantTime=12;gate.leave(e);}
        int expected=why.equals("foreign")?12:why.equals("nondefault")?30:20;boolean ok=e.maxHurtResistantTime==expected&&gate.ownedSize()==0;if(!ok)failures++;
        System.out.println("CW3 S1D_LIFECYCLE reason="+why+" max="+e.maxHurtResistantTime+" owned="+gate.ownedSize()+" valid="+ok);
    }}
    private void transition(WorldServer w){EntityLivingBase e=fresh(w,false);e.attackEntityFrom(DamageSource.generic,2);advance(e,7);gate.enter(e);int remaining=e.hurtResistantTime,first=-1;
        for(int i=0;i<=10;i++){if(i>0)advance(e,1);float hp=e.getHealth();boolean accepted=gate.apply(e,SOURCE,2);if(accepted&&hp-e.getHealth()==2){first=i;break;}}
        gate.leave(e);System.out.println("CW3 S1D_TRANSITION oldTimerOnEnter="+remaining+" firstFullDamageAfterEnter="+first);if(remaining!=13||first!=9)failures++;
    }
    private void otherSources(WorldServer w){for(boolean p:new boolean[]{false,true})for(int max:new int[]{20,8})for(String source:new String[]{"onFire","secondAttacker"}){
        int first=-1;for(int gap=1;gap<=10;gap++){
            EntityLivingBase e=fresh(w,p);if(max==8)gate.enter(e);
            DamageSource s=source.equals("onFire")?DamageSource.onFire:DamageSource.causePlayerDamage((EntityPlayerMP)fresh(w,true));
            DamageSource seed=source.equals("onFire")?s:DamageSource.causePlayerDamage((EntityPlayerMP)fresh(w,true));
            e.attackEntityFrom(seed,2);advance(e,gap);float hp=e.getHealth();boolean accepted=e.attackEntityFrom(s,2);float loss=hp-e.getHealth();
            if(accepted&&loss==2&&first<0)first=gap;
            System.out.println("CW3 S1D_OTHER player="+p+" max="+max+" source="+source+" gap="+gap+" accepted="+accepted+" loss="+loss);gate.leave(e);
        }
        if(first!=max/2)failures++;
        System.out.println("CW3 S1D_OTHER_THRESHOLD player="+p+" max="+max+" source="+source+" firstFullDamageGap="+first);
    }}
}
