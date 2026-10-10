package com.layue13.clashweave.validation;
import java.io.File;
import java.nio.FloatBuffer;import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;import net.minecraft.client.renderer.entity.RenderManager;import net.minecraft.entity.Entity;import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.client.event.ClientChatReceivedEvent;import net.minecraftforge.client.event.RenderWorldLastEvent;import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.BufferUtils;import org.lwjgl.opengl.GL11;import org.lwjgl.util.glu.GLU;
import com.layue13.clashweave.Clashweave;import com.layue13.clashweave.client.ClientProxy;import com.layue13.clashweave.core.Intent;import com.layue13.clashweave.core.ThirdPersonCamera;
import cpw.mods.fml.common.FMLCommonHandler;import cpw.mods.fml.common.eventhandler.SubscribeEvent;import cpw.mods.fml.common.gameevent.TickEvent;
public final class CompositionReplay {
    private final Minecraft mc=Minecraft.getMinecraft();public static String phase="";private int elapsed,targetId;private String capture;
    public CompositionReplay(){FMLCommonHandler.instance().bus().register(this);MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void chat(ClientChatReceivedEvent e){String s=e.message.getUnformattedText();if(!s.startsWith("P0_COMPOSITION_PHASE "))return;
        for(String v:s.split(" ")){if(v.startsWith("name="))phase=v.substring(5);if(v.startsWith("target="))targetId=Integer.parseInt(v.substring(7));}elapsed=0;
        mc.gameSettings.thirdPersonView=phase.contains("FIRST") || phase.equals("FIRST")?0:phase.equals("PROBE_F5_FRONT")?2:1;
        if(phase.equals("FIRST")){mc.thePlayer.rotationYaw=35;mc.thePlayer.rotationPitch=12;}
        mc.gameSettings.viewBobbing=false;mc.gameSettings.fovSetting=70;
        if(phase.startsWith("GOLDEN"))Clashweave.config.lockCompositionPreset=ThirdPersonCamera.Preset.GOLDEN;
        if(phase.startsWith("THIRDS"))Clashweave.config.lockCompositionPreset=ThirdPersonCamera.Preset.THIRDS;
        if(phase.startsWith("CENTER"))Clashweave.config.lockCompositionPreset=ThirdPersonCamera.Preset.CENTER;
        System.setProperty("cw.p0.probeEnabled",Boolean.toString(!phase.equals("PROBE_DISABLED")));
        System.out.println("P0_COMPOSITION_PHASE name="+phase+" receivedNano="+System.nanoTime());
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END || mc.thePlayer==null || !mc.thePlayer.getCommandSenderName().equals("P0A"))return;elapsed++;ClientProxy p=ClientProxy.instance;
        if(phase.equals("WAIT") && elapsed%20==0 && mc.theWorld.getEntityByID(targetId)!=null && p.own()!=null && p.own().state.lockTarget<0)p.send(Intent.LOCK,-1);
        if(phase.equals("FIRST") && elapsed==20){mc.thePlayer.setAngles(5/.15F,0);p.lockCamera.mouse(System.nanoTime(),5);}
        if(phase.equals("FIRST") && elapsed==50)p.send(Intent.LIGHT,-1);
        if(phase.equals("MOUSE")){
            if(elapsed<=80 && elapsed%4==0){p.lockCamera.mouse(System.nanoTime(),0);System.out.println("P0_FLICK_CASE kind=stationary serial="+(elapsed/4)+" delta=0");}
            if(elapsed>80 && elapsed<=160 && elapsed%4==0){p.lockCamera.mouse(System.nanoTime(),2);System.out.println("P0_FLICK_CASE kind=small serial="+((elapsed-80)/4)+" delta=2");}
            if(elapsed==170 || elapsed==172 || elapsed==190){p.lockCamera.mouse(System.nanoTime(),elapsed==172?-30:30);System.out.println("P0_FLICK_CASE kind=large elapsed="+elapsed);}
            if(elapsed==174 || elapsed==175)p.send(Intent.LOCK_SWITCH,1);
        }
        if(elapsed==80 && Boolean.getBoolean("cw.p0.cameraProbe"))capture="composition-"+phase.toLowerCase();
    }
    @SubscribeEvent public void world(RenderWorldLastEvent e){if(mc.thePlayer==null || !mc.thePlayer.getCommandSenderName().equals("P0A") || phase.equals("WAIT"))return;
        FloatBuffer model=BufferUtils.createFloatBuffer(16),projection=BufferUtils.createFloatBuffer(16),out=BufferUtils.createFloatBuffer(3);IntBuffer viewport=BufferUtils.createIntBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,model);GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,projection);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        double yaw=Math.toRadians(mc.thePlayer.rotationYaw);GLU.gluProject((float)(mc.thePlayer.posX-Math.sin(yaw)*10000-RenderManager.renderPosX),(float)(mc.thePlayer.getPosition(1).yCoord-RenderManager.renderPosY),(float)(mc.thePlayer.posZ+Math.cos(yaw)*10000-RenderManager.renderPosZ),model,projection,viewport,out);
        double horizon=1-out.get(1)/mc.displayHeight;
        System.out.println("P0_COMPOSITION_FRAME phase="+phase+" elapsed="+elapsed+" nano="+System.nanoTime()+" yaw="+mc.thePlayer.rotationYaw+" pitch="+mc.thePlayer.rotationPitch+" horizon="+horizon+" view="+mc.gameSettings.thirdPersonView);
    }
    @SubscribeEvent public void frame(TickEvent.RenderTickEvent e){if(e.phase==TickEvent.Phase.END && capture!=null && mc.thePlayer!=null){ScreenShotHelper.saveScreenshot(new File("."),"p0-"+capture+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());capture=null;}}
}
