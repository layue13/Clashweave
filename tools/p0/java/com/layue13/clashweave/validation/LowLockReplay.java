package com.layue13.clashweave.validation;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import com.layue13.clashweave.core.LockFollow;

public final class LowLockReplay {
    private final Minecraft mc=Minecraft.getMinecraft();private String phase="";private int elapsed,trial;
    public LowLockReplay(){FMLCommonHandler.instance().bus().register(this);MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void chat(ClientChatReceivedEvent e){String s=e.message.getUnformattedText();if(!s.startsWith("P0_LOW_LOCK_PHASE "))return;for(String v:s.split(" "))if(v.startsWith("name="))phase=v.substring(5);elapsed=trial=0;}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END || mc.thePlayer==null || !mc.thePlayer.getCommandSenderName().equals("P0A") || phase.isEmpty())return;
        elapsed++;mc.gameSettings.thirdPersonView=0;Clashweave.config.lockFollowFirstPerson=LockFollow.Mode.OFF;
        mc.thePlayer.rotationYaw=mc.thePlayer.rotationPitch=0;
        ClientProxy p=ClientProxy.instance;if(p.own()==null)return;
        int local=elapsed-12;
        if(local>=0 && local%8==0 && trial<20){
            p.send(Intent.LOCK,-1);trial++;
            int seq=cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(ClientProxy.class,p,"sequence");
            System.out.println("P0_LOW_LOCK_REQUEST phase="+phase+" trial="+trial+" seq="+seq+" nano="+System.nanoTime()+" beforeLock="+p.own().state.lockTarget);
        }
        if(local>=0 && local%8==4 && trial<=20 && p.own().state.lockTarget>=0)p.send(Intent.LOCK,-1);
    }
}
