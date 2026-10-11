package com.layue13.clashweave.validation;

import java.lang.reflect.Field;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.core.Scheduler;
import com.layue13.clashweave.forge.CombatServer;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Read-only samples before production END consumes intents; no production branch output or ping writes. */
public final class ComboLatencySample {
    private Object config;
    private int cap;
    private int jitter;

    private int optional(String name) throws IllegalAccessException {
        try { Field f=config.getClass().getField(name);return f.getInt(config); }
        catch (NoSuchFieldException baseline) { return 0; }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void tick(TickEvent.ServerTickEvent event) throws ReflectiveOperationException {
        if(event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("cw.p0.soulsView")
            || !Boolean.getBoolean("clashweave.trace"))return;
        CombatServer server=Clashweave.server;
        if(config==null){Field f=CombatServer.class.getDeclaredField("config");f.setAccessible(true);
            config=f.get(server);cap=optional("inputEdgeCompensationTicks");jitter=optional("inputEdgeCompensationJitterTicks");}
        for(Object object:MinecraftServer.getServer().getConfigurationManager().playerEntityList){
            EntityPlayerMP player=(EntityPlayerMP)object;
            if(!player.getCommandSenderName().equals("P0A"))continue;
            CombatServer.PlayerState state=server.state(player);
            Scheduler.Instance action=state==null?null:state.scheduler.current();
            System.out.println("P0_COMP_SAMPLE tick="+(server.tick+1)+" ping="+player.ping+" cap="+cap
                +" jitter="+jitter+" instance="+(action==null?0:action.id)+" start="+(action==null?0:action.start)
                +" nano="+System.nanoTime());
        }
    }
}
