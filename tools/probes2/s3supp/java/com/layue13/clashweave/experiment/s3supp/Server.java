package com.layue13.clashweave.experiment.s3supp;

import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.DamageSource;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class Server {
    static final ConcurrentLinkedQueue<Arrival> queue = new ConcurrentLinkedQueue<Arrival>();
    static volatile long observedTick;
    static class Arrival { final Packet p;final long wall,tick; Arrival(Packet p){this.p=p;wall=System.currentTimeMillis();tick=observedTick;} }
    public static class Inbound implements IMessageHandler<Packet,IMessage> {
        public IMessage onMessage(Packet p,MessageContext c){Arrival a=new Arrival(p); queue.add(a);
            Experiment.log("RECEIVE id="+p.id+" kind="+p.kind+" stamp="+p.stamp+" tickObserved="+a.tick+" recvWall="+a.wall+" sendNano="+p.send);return null;}
    }
    EntityPlayerMP attacker,defender;
    long tick,hit,anchorWall,press=-9999,receivedAt=Long.MAX_VALUE;
    long readyAt; int trial=-1,total=0,eligible=0,eligibleSuccess=0; boolean frozen;long freezeNano;
    int trialsPerLead=Integer.getInteger("cw.s3supp.repetitions",10);
    int legacyTrials=Integer.getInteger("cw.s3supp.legacy",0);
    int guardWindow=Integer.getInteger("cw.s3supp.window",5);
    int ageCap=Integer.getInteger("cw.s3supp.ageCap",8);
    int leads=Integer.getInteger("cw.s3supp.leads",5);
    int onlyDefer=Integer.getInteger("cw.s3supp.onlyDefer",0);
    int allTrials(){ return legacyTrials+(onlyDefer>0?1:2)*leads*trialsPerLead; }
    int lead(){return trial<legacyTrials ? 2 : ((trial-legacyTrials)/trialsPerLead)%leads;}
    int delay(){return trial<legacyTrials?0:(onlyDefer>0?onlyDefer:2+(trial-legacyTrials)/(leads*trialsPerLead));}
    @SubscribeEvent public void login(PlayerLoggedInEvent e){EntityPlayerMP p=(EntityPlayerMP)e.player;
        WireTrace.attach(p.playerNetServerHandler.netManager,"SERVER",p.getCommandSenderName().equals("GuardB"));
        for(int x=-2;x<=4;x++)for(int z=-2;z<=3;z++)p.worldObj.setBlock(x,64,z,net.minecraft.init.Blocks.stone);
        p.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(1000);p.setHealth(1000);
        p.capabilities.disableDamage=false;
        if(p.getCommandSenderName().equals("GuardA"))attacker=p;else if(p.getCommandSenderName().equals("GuardB"))defender=p;
        Experiment.log("LOGIN name="+p.getCommandSenderName());
    }
    @SubscribeEvent public void run(TickEvent.ServerTickEvent e){
        if(e.phase==TickEvent.Phase.START){observedTick=MinecraftServer.getServer().worldServerForDimension(0).getTotalWorldTime();return;}
        tick=MinecraftServer.getServer().worldServerForDimension(0).getTotalWorldTime();observedTick=tick;
        Arrival a;while((a=queue.poll())!=null){Packet p=a.p;if(p.kind==3){readyAt=tick+30;Experiment.log("READY tick="+tick);}if(p.kind==2 && p.id==trial){
            long age=tick-p.stamp;boolean accept=age>=0&&age<=ageCap;
            if(accept){press=p.stamp;receivedAt=tick;}
            Experiment.log("CONSUME id="+p.id+" stamp="+p.stamp+" consumeTick="+tick+" recvObserved="+a.tick+" age="+age+" queueMs="+(System.currentTimeMillis()-a.wall)+" sendNano="+p.send+" recvWall="+a.wall+" consumeWall="+System.currentTimeMillis()+" accepted="+accept);
        }}
        if(attacker==null||defender==null||readyAt==0)return;
        if(trial==-1 && tick>=readyAt){schedule();}
        if(trial>=0 && trial<allTrials()){
            if(tick==hit){frozen=true;freezeNano=System.nanoTime();Experiment.log("FREEZE id="+trial+" tick="+tick+" target="+defender.getEntityId()+" attacker="+attacker.getEntityId()+" distance="+attacker.getDistanceToEntity(defender));}
            if(frozen && tick==hit+delay())resolve();
            if(tick==hit+delay()+5){if(trial+1<allTrials())schedule();else{
                Experiment.log("DONE total="+total+" eligible="+eligible+" eligibleSuccess="+eligibleSuccess+" conditionalAll="+(eligible==eligibleSuccess)+" window="+guardWindow+" ageCap="+ageCap+" worldRollback=false");
                Experiment.wire.sendTo(new Packet(4,trial,0,0,tick,0,0,0,0),attacker);trial=allTrials();}}
        }
    }
    void schedule(){trial++;press=-9999;receivedAt=Long.MAX_VALUE;frozen=false;
        hit=tick+12;anchorWall=System.nanoTime();
        attacker.playerNetServerHandler.setPlayerLocation(0.5,65,0.5,0,0);
        defender.playerNetServerHandler.setPlayerLocation(1.5,65,0.5,180,0);
        defender.hurtResistantTime=0;defender.setHealth(1000);
        // Both are real players. Trial geometry is frozen at hit, never recomputed at commit.
        Experiment.wire.sendTo(new Packet(1,trial,lead(),delay(),tick,hit-lead(),anchorWall,0,0),defender);
        Experiment.log("PLAN id="+trial+" lead="+lead()+" defer="+delay()+" anchorTick="+tick+" hit="+hit+" stamp="+(hit-lead())+" anchorNano="+anchorWall+" legacy="+(trial<legacyTrials));
    }
    void resolve(){
        frozen=false;long deadline;WireTrace.Seen seen;
        synchronized(WireTrace.guardLock){deadline=System.nanoTime();seen=WireTrace.inputs.get(trial);}
        long actualStamp=seen==null?-9999:seen.packet.stamp;
        long arrival=seen==null?Long.MAX_VALUE:seen.nano;
        boolean window=actualStamp>=hit-(guardWindow-1)&&actualStamp<=hit;
        boolean before=arrival<deadline;boolean parry=window&&before;
        float hp=defender.getHealth();boolean accepted=false;
        if(!parry){defender.hurtResistantTime=0;accepted=defender.attackEntityFrom(DamageSource.causePlayerDamage(attacker),1);}
        total++;if(window&&before){eligible++;if(parry&&hp==defender.getHealth())eligibleSuccess++;}
        Experiment.log("RESULT id="+trial+" lead="+lead()+" defer="+delay()+" hit="+hit+" commit="+tick+" deadlineNano="+deadline+" commitWall="+System.currentTimeMillis()+" stamp="+actualStamp+" arrivalNano="+arrival+" window="+window+" before="+before+" parry="+parry+" vanillaAccepted="+accepted+" loss="+(hp-defender.getHealth())+" feedbackDelayMs="+((deadline-freezeNano)/1e6));
    }
}
