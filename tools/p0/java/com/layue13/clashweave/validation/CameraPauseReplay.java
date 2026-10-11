package com.layue13.clashweave.validation;

import java.nio.FloatBuffer;import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;import net.minecraft.client.renderer.entity.RenderManager;import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.ClientChatReceivedEvent;import net.minecraftforge.client.event.RenderWorldLastEvent;import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.BufferUtils;import org.lwjgl.opengl.GL11;import org.lwjgl.util.glu.GLU;
import com.layue13.clashweave.client.ClientProxy;import com.layue13.clashweave.client.LockCamera;import com.layue13.clashweave.core.CameraBearing;import com.layue13.clashweave.core.FacingHistory;import com.layue13.clashweave.core.ThirdPersonCamera;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;import cpw.mods.fml.common.eventhandler.EventPriority;

/** All samples stay in ignored process logs; only bins and a one-page plot are archived. */
public final class CameraPauseReplay {
    private final Minecraft mc=Minecraft.getMinecraft();private String phase="";private int sleep;private long eventNano;
    private final FloatBuffer model=BufferUtils.createFloatBuffer(16),projection=BufferUtils.createFloatBuffer(16),out=BufferUtils.createFloatBuffer(3);private final IntBuffer viewport=BufferUtils.createIntBuffer(16);
    public CameraPauseReplay(){MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void chat(ClientChatReceivedEvent e){String s=e.message.getUnformattedText();if(s.startsWith("P0_PAUSE_PHASE ")){phase=s.substring(s.indexOf("name=")+5);eventNano=0;mc.gameSettings.thirdPersonView=1;mc.gameSettings.viewBobbing=false;if(Boolean.getBoolean("cw.p0.diagnosisNoFeed"))com.layue13.clashweave.Clashweave.config.cameraFeedForwardMaxSpeed=0;}if(s.startsWith("P0_PAUSE_EVENT ")){for(String v:s.split(" "))if(v.startsWith("sleep="))sleep=Integer.parseInt(v.substring(6));eventNano=System.nanoTime();System.out.println("P0_PAUSE_EVENT phase="+phase+" nano="+eventNano+" sleep="+sleep);}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void world(RenderWorldLastEvent e){
        if(mc.thePlayer==null || !mc.thePlayer.getCommandSenderName().equals("P0A") || phase.isEmpty())return;
        EntityLivingBase target=ClientProxy.instance.lockCamera.target();if(target==null)return;
        model.clear();projection.clear();viewport.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,model);GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,projection);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        double t=e.partialTicks,tx=target.lastTickPosX+(target.posX-target.lastTickPosX)*t,tz=target.lastTickPosZ+(target.posZ-target.lastTickPosZ)*t;
        double ty=target.lastTickPosY+(target.posY-target.lastTickPosY)*t+(target.boundingBox.minY+target.boundingBox.maxY)/2-target.posY;
        out.clear();GLU.gluProject((float)(tx-RenderManager.renderPosX),(float)(ty-RenderManager.renderPosY),(float)(tz-RenderManager.renderPosZ),model,projection,viewport,out);
        double actual=Math.toDegrees(Math.atan2(-(tx-mc.thePlayer.posX),tz-mc.thePlayer.posZ));
        LockCamera camera=ClientProxy.instance.lockCamera;
        ThirdPersonCamera.Filter filter=cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(LockCamera.class,camera,"filter");
        CameraBearing bearing=cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(LockCamera.class,camera,"bearing");
        double speed=cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(CameraBearing.class,bearing,"velocity");
        double filtered=Math.toDegrees(Math.atan2(-(filter.x-mc.thePlayer.posX),filter.z-mc.thePlayer.posZ));
        System.out.println("P0_PAUSE_FRAME phase="+phase+" nano="+System.nanoTime()+" eventNano="+eventNano+" x="+(out.get(0)/mc.displayWidth-.5)+" feedSpeed="+speed+" yaw="+mc.thePlayer.rotationYaw+" actualBearing="+actual+" filteredBearing="+filtered+" error="+FacingHistory.difference(actual,mc.thePlayer.rotationYaw)+" filteredError="+FacingHistory.difference(filtered,mc.thePlayer.rotationYaw));
        if(sleep>0){int ms=sleep;sleep=0;long began=System.nanoTime();System.out.println("P0_PAUSE_BEGIN phase="+phase+" nano="+began);try{Thread.sleep(ms);}catch(InterruptedException ex){throw new IllegalStateException(ex);}System.out.println("P0_PAUSE_END phase="+phase+" nano="+System.nanoTime()+" durationNanos="+(System.nanoTime()-began));}
    }
}
