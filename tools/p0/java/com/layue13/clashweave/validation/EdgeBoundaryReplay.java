package com.layue13.clashweave.validation;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.Intent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Each probe begins at a fresh natural light_1; frame crossing controls client age, not server state. */
public final class EdgeBoundaryReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final Field sequence;
    private int ready;
    private int until;
    private int trial;
    private int k;
    private int stage;
    private int started;
    private int idle;
    private long origin;
    private boolean boot;
    private boolean reported;

    public EdgeBoundaryReplay() throws NoSuchFieldException {
        sequence=ClientProxy.class.getDeclaredField("sequence");sequence.setAccessible(true);
        FMLCommonHandler.instance().bus().register(this);
    }

    private int send(ClientProxy proxy) throws IllegalAccessException {
        proxy.send(Intent.LIGHT,-1);return sequence.getInt(proxy);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) throws IllegalAccessException {
        if(event.phase!=TickEvent.Phase.END || mc.thePlayer==null
            || !mc.thePlayer.getCommandSenderName().equals("P0A"))return;
        ClientProxy proxy=ClientProxy.instance;ClientProxy.Visual visual=proxy.own();
        if(visual==null || proxy.actions==null || proxy.visuals.size()<2)return;
        mc.thePlayer.rotationYaw=0;mc.thePlayer.rotationPitch=0;
        if(++ready<100)return;
        if(until==0){for(ActionCatalog.Edge edge:proxy.actions.get("light_1").edges)
            if(edge.input==Intent.LIGHT && edge.target.equals("light_2"))until=edge.until;
            if(until==0)throw new IllegalStateException("Missing LIGHT edge");
            System.out.println("P0_EDGE_PLAN until="+until+" kMin="+(until-7)+" kMax="+(until+1)+" repeats=5 trials=45");}
        if(visual.state.sheathed){if(!boot && visual.state.action.isEmpty()){
            int seq=send(proxy);boot=true;System.out.println("P0_EDGE_BOOT seq="+seq);}
            return;}
        if(stage==1 && visual.state.action.equals("light_1")){
            origin=visual.state.instance;stage=2;
            System.out.println("P0_EDGE_ORIGIN trial="+trial+" k="+k+" instance="+origin+" start="+visual.state.start);}
        if(stage>0 && ready-started>80){finish("TIMEOUT");}
        if(stage==2 && (!visual.state.action.equals("light_1") || visual.state.instance!=origin))finish("ACTION_ENDED_BEFORE_PRESS");
        if(stage==3 && visual.state.action.isEmpty())finish("NATURAL_IDLE_AFTER_PRESS");
        if(stage!=0)return;
        if(!visual.state.action.isEmpty()){idle=0;return;}
        if(++idle<3)return;
        if(trial==45){if(!reported){reported=true;System.out.println("P0_EDGE_CLIENT COMPLETE trials="+trial+" nano="+System.nanoTime());}return;}
        k=until-7+(trial%9);trial++;stage=1;started=ready;idle=0;origin=0;
        int seq=send(proxy);
        System.out.println("P0_EDGE_BEGIN trial="+trial+" k="+k+" entrySeq="+seq+" nano="+System.nanoTime());
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) throws IllegalAccessException {
        if(event.phase!=TickEvent.Phase.END || stage!=2 || mc.thePlayer==null)return;
        ClientProxy proxy=ClientProxy.instance;ClientProxy.Visual visual=proxy.own();
        if(visual==null || visual.state.instance!=origin || !visual.state.action.equals("light_1"))return;
        double age=proxy.elapsed(visual,0);
        if(age<k)return;
        if(age>=k+1){finish("AGE_OVERSHOOT");return;}
        int seq=send(proxy);stage=3;
        System.out.println("P0_EDGE_PRESS trial="+trial+" k="+k+" seq="+seq+" instance="+origin
            +" clientAge="+age+" visualTick="+visual.state.tick+" start="+visual.state.start+" nano="+System.nanoTime());
    }

    private void finish(String reason){
        System.out.println("P0_EDGE_FINISH trial="+trial+" k="+k+" instance="+origin+" reason="+reason+" nano="+System.nanoTime());
        stage=0;idle=0;
    }
}
