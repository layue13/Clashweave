package com.layue13.clashweave.validation;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import com.layue13.clashweave.core.LockFollow;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class LockSupportReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private String phase="";
    private int serial;
    private int targetId;
    private int elapsed;
    private boolean attack;
    private String capture;
    private int markerFailures;
    public LockSupportReplay(){FMLCommonHandler.instance().bus().register(this);MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void phase(ClientChatReceivedEvent event){
        if(!Boolean.getBoolean("cw.p0.lockSupport") || mc.thePlayer==null || !mc.thePlayer.getCommandSenderName().equals("P0A"))return;
        String text=event.message.getUnformattedText();if(!text.startsWith("P0_LOCK_PHASE"))return;
        Map<String,String> values=new HashMap<>();for(String part:text.split(" ")){String[] pair=part.split("=");if(pair.length==2)values.put(pair[0],pair[1]);}
        targetId=Integer.parseInt(values.get("target"));phase=values.get("name");serial=Integer.parseInt(values.get("serial"));elapsed=0;attack=false;
        ClientProxy proxy=ClientProxy.instance;
        if(phase.startsWith("MARK_") || phase.equals("AIM_READY")){
            Clashweave.config.lockFollowFirstPerson=LockFollow.Mode.OFF;Clashweave.config.lockFollowThirdPerson=LockFollow.Mode.OFF;
            mc.gameSettings.thirdPersonView=0;mc.thePlayer.rotationYaw=0;mc.thePlayer.rotationPitch=0;
            if(phase.equals("MARK_UNLOCK") || (phase.equals("MARK_LOCK") || phase.equals("MARK_RELOCK") || phase.equals("AIM_READY")) && (proxy.own()==null || proxy.own().state.lockTarget<0))proxy.send(Intent.LOCK,-1);
        } else if(phase.equals("AIM") || phase.startsWith("CAMERA_")){
            mc.displayGuiScreen(null);mc.thePlayer.rotationYaw=0;mc.thePlayer.rotationPitch=0;
            mc.gameSettings.thirdPersonView=phase.equals("AIM")?0:1;
            Clashweave.config.lockFollowFirstPerson=LockFollow.Mode.OFF;
            Clashweave.config.lockFollowThirdPerson=phase.equals("CAMERA_STRONG")?LockFollow.Mode.STRONG:LockFollow.Mode.OFF;
        } else if(phase.equals("WEAK") || phase.equals("STRONG")){
            mc.thePlayer.rotationYaw=0;mc.thePlayer.rotationPitch=0;
            Clashweave.config.lockFollowFirstPerson=LockFollow.Mode.WEAK;Clashweave.config.lockFollowThirdPerson=LockFollow.Mode.STRONG;
            mc.gameSettings.thirdPersonView=phase.equals("WEAK")?0:1;
        } else if(phase.equals("GUI")) mc.displayGuiScreen(new net.minecraft.client.gui.inventory.GuiInventory(mc.thePlayer));
        else if(phase.equals("FINISH")){mc.displayGuiScreen(null);proxy.send(Intent.LOCK,-1);}
        System.out.println(text+" receivedNano="+System.nanoTime());
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("cw.p0.lockSupport") || event.phase!=TickEvent.Phase.END || mc.thePlayer==null || !mc.thePlayer.getCommandSenderName().equals("P0A"))return;
        ClientProxy proxy=ClientProxy.instance;ClientProxy.Visual own=proxy.own();if(own==null)return;
        elapsed++;
        if(phase.equals("WAIT") && elapsed%20==0 && mc.theWorld.getEntityByID(targetId)!=null
            && com.layue13.clashweave.forge.CombatServer.armed(mc.thePlayer) && own.state.lockTarget<0)proxy.send(Intent.LOCK,-1);
        if(phase.startsWith("MARK_") && elapsed==25){
            boolean expected=phase.equals("MARK_LOCK") || phase.equals("MARK_RELOCK");
            boolean actual=proxy.lockCamera.target()!=null;if(expected!=actual)markerFailures++;
            System.out.println("P0_LOCK_MARKER phase="+phase+" expected="+expected+" actual="+actual+" failures="+markerFailures+" lock="+own.state.lockTarget);
            capture="lock-"+phase.toLowerCase();
        }
        if(((phase.equals("AIM") || phase.equals("AIM_READY") || phase.startsWith("CAMERA_")) && elapsed==8 || phase.equals("OCCLUDED") && elapsed==35) && !attack){
            attack=true;
            if(phase.equals("OCCLUDED")){Clashweave.config.lockFollowThirdPerson=LockFollow.Mode.OFF;mc.thePlayer.rotationYaw=0;mc.thePlayer.rotationPitch=0;}
            proxy.send(Intent.LIGHT,-1);
            int seq=(Integer)cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(ClientProxy.class,proxy,"sequence");
            System.out.println("P0_LOCK_ATTACK phase="+phase+" serial="+serial+" seq="+seq+" yaw="+mc.thePlayer.rotationYaw+" nano="+System.nanoTime());
        }
        if((phase.equals("WEAK") || phase.equals("STRONG")) && elapsed==30){
            if(Boolean.getBoolean("cw.p0.followOnly")){
                net.minecraft.util.MouseHelper fake=new net.minecraft.util.MouseHelper(){@Override public void mouseXYChange(){deltaX=33;deltaY=0;}};
                net.minecraft.util.MouseHelper gate=proxy.lockCamera.wrap(fake);gate.mouseXYChange();
                double f=mc.gameSettings.mouseSensitivity*.6+.2;
                mc.thePlayer.setAngles((float)(gate.deltaX*f*f*f*8),0);
                System.out.println("P0_LOCK_MOUSE phase="+phase+" rawDx=33 gateDx="+gate.deltaX+" delta="+(33*f*f*f*8*.15)+" nano="+System.nanoTime());
            } else {
                mc.thePlayer.setAngles(5/.15F,0);proxy.lockCamera.mouse(System.nanoTime(),5);
                System.out.println("P0_LOCK_MOUSE phase="+phase+" delta=5 nano="+System.nanoTime());
            }
        }
        if(phase.equals("STRONG") && (elapsed==25 || elapsed==65))proxy.send(Intent.LIGHT,-1);
        if(phase.equals("STRONG") && elapsed==95){mc.gameSettings.thirdPersonView=0;System.out.println("P0_LOCK_F5 nano="+System.nanoTime());}
        if(phase.equals("STRONG") && elapsed==105){mc.gameSettings.thirdPersonView=1;System.out.println("P0_LOCK_F5 nano="+System.nanoTime());}
        if(!phase.isEmpty())System.out.println("P0_LOCK_CAMERA phase="+phase+" elapsed="+elapsed+" nano="+System.nanoTime()+" yaw="+mc.thePlayer.rotationYaw+" view="+mc.gameSettings.thirdPersonView);
    }
    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END || capture==null || mc.thePlayer==null)return;
        ScreenShotHelper.saveScreenshot(new File("."),"p0-"+capture+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());capture=null;
    }
}
