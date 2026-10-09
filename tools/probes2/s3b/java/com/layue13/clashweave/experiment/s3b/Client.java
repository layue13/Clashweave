package com.layue13.clashweave.experiment.s3b;

import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class Client extends Proxy {
    static class Arr { final Packet p;final long wall;Arr(Packet p,long w){this.p=p;wall=w;} }
    final ConcurrentLinkedQueue<Arr> queue=new ConcurrentLinkedQueue<Arr>();
    long local,worldTicks,dueLocal,dueWall;boolean connected,ready,sending,complete,hooked;Packet pending;
    public void init(){cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(this);}
    public void accept(Packet p,long receipt){queue.add(new Arr(p,receipt));}
    @SubscribeEvent public void run(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.START)return;
        Minecraft mc=Minecraft.getMinecraft();local++;
        if(!connected&&mc.currentScreen instanceof GuiMainMenu){connected=true;mc.gameSettings.pauseOnLostFocus=false;
            mc.gameSettings.limitFramerate=60;mc.gameSettings.renderDistanceChunks=3;
            cpw.mods.fml.client.FMLClientHandler.instance().setupServerList();
            cpw.mods.fml.client.FMLClientHandler.instance().connectToServer(mc.currentScreen,new net.minecraft.client.multiplayer.ServerData("S3b","127.0.0.1:25573"));}
        if(mc.thePlayer==null||mc.theWorld==null)return;worldTicks++;
        if(!hooked){WireTrace.attach(mc.getNetHandler().getNetworkManager(),"CLIENT");hooked=true;}
        net.minecraft.client.settings.KeyBinding.unPressAllKeys();mc.setIngameNotInFocus();
        if(!ready && worldTicks>40 && "attacker".equals(System.getProperty("cw.s3b.role"))){ready=true;Experiment.wire.sendToServer(new Packet(3,0,0,0,0,0,0,System.currentTimeMillis(),local));}
        Arr a;while((a=queue.poll())!=null){Packet p=a.p;if(p.kind==1){pending=p;sending=true;
            dueLocal=local+Math.round(p.stamp-p.tick-Integer.getInteger("cw.s3b.rtt",0)/100.0);
            dueWall=p.wall+(p.stamp-p.tick)*50;
            Experiment.log("CLIENT_PLAN id="+p.id+" anchor="+p.tick+" stamp="+p.stamp+" receiveWall="+a.wall+" consumeWall="+System.currentTimeMillis()+" local="+local+" dueLocal="+dueLocal+" dueWall="+dueWall+" clientWorld="+mc.theWorld.getTotalWorldTime());
        }if(p.kind==4){complete=true;Experiment.log("CLIENT_COMPLETE");mc.shutdown();}}
        if(sending){boolean legacy=pending.id<Integer.getInteger("cw.s3b.legacy",0);
            if(legacy?local>=dueLocal:System.currentTimeMillis()>=dueWall){sending=false;long send=System.currentTimeMillis();
                // Script clock is deliberately a shared host calibration fixture, not a production trust model.
                long stamp=legacy?pending.stamp:pending.tick+Math.round((send-pending.wall)/50.0);
                Experiment.wire.sendToServer(new Packet(2,pending.id,pending.lead,pending.delay,pending.tick,stamp,pending.wall,send,local));
                Experiment.log("SEND id="+pending.id+" intendedStamp="+pending.stamp+" stamp="+stamp+" sendWall="+send+" dueWall="+dueWall+" local="+local+" clientWorld="+mc.theWorld.getTotalWorldTime()+" legacy="+legacy);
            }}
    }
}
