package com.layue13.clashweave.validation;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderZombie;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.client.SoulsCamera;
import com.layue13.clashweave.core.SoulsComposition;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
/** Fixture-only geometry and model scale; never part of the production jar. */
public final class SoulsReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final FloatBuffer model=BufferUtils.createFloatBuffer(16),projection=BufferUtils.createFloatBuffer(16),point=BufferUtils.createFloatBuffer(3);
    private final IntBuffer viewport=BufferUtils.createIntBuffer(16);
    public SoulsReplay(){MinecraftForge.EVENT_BUS.register(this);RenderingRegistry.registerEntityRenderingHandler(EntityZombie.class,new RenderZombie(){
        @Override protected void preRenderCallback(EntityLivingBase living,float partial){super.preRenderCallback(living,partial);float ratio=(float)(living.height/1.95);GL11.glScalef(ratio,ratio,ratio);}
    });}
    private double[] project(double x,double y,double z){point.clear();model.rewind();projection.rewind();viewport.rewind();boolean ok=GLU.gluProject((float)(x-RenderManager.renderPosX),(float)(y-RenderManager.renderPosY),(float)(z-RenderManager.renderPosZ),model,projection,viewport,point);return new double[]{ok?point.get(0)/mc.displayWidth:Double.NaN,ok?1-point.get(1)/mc.displayHeight:Double.NaN,point.get(2)};}
    @SubscribeEvent public void render(RenderWorldLastEvent event){
        if(mc.thePlayer==null || !mc.thePlayer.getCommandSenderName().equals("P0A") || ClientProxy.instance==null)return;
        EntityLivingBase target=ClientProxy.instance.lockCamera.target();if(target==null)return;
        model.clear();projection.clear();viewport.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,model);GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,projection);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        AxisAlignedBB box=mc.thePlayer.boundingBox;double[] bounds={1e9,1e9,-1e9,-1e9};
        for(int i=0;i<8;i++){double[] p=project((i&1)==0?box.minX:box.maxX,(i&2)==0?box.minY:box.maxY,(i&4)==0?box.minZ:box.maxZ);bounds[0]=Math.min(bounds[0],p[0]);bounds[1]=Math.min(bounds[1],p[1]);bounds[2]=Math.max(bounds[2],p[0]);bounds[3]=Math.max(bounds[3],p[1]);}
        double[] player=project(mc.thePlayer.posX,(box.minY+box.maxY)/2,mc.thePlayer.posZ);
        double[] torso=project(target.posX,target.boundingBox.minY+target.height*.5,target.posZ);
        double[] head=project(target.posX,target.boundingBox.minY+target.height*.9,target.posZ);
        boolean torsoCovered=SoulsComposition.covered(bounds[0],bounds[1],bounds[2],bounds[3],torso[0],torso[1],torso[2]);
        boolean headCovered=SoulsComposition.covered(bounds[0],bounds[1],bounds[2],bounds[3],head[0],head[1],head[2]);
        boolean inside=SoulsCamera.active && mc.theWorld.getBlock((int)Math.floor(SoulsCamera.cameraX),(int)Math.floor(SoulsCamera.cameraY),(int)Math.floor(SoulsCamera.cameraZ)).getMaterial().isSolid();
        System.out.println("P0_SOULS_PROJECTION phase="+CompositionReplay.phase+" nano="+System.nanoTime()+" active="+SoulsCamera.active+" playerX="+player[0]+" playerY="+player[1]+" targetX="+torso[0]+" targetY="+torso[1]+" headX="+head[0]+" headY="+head[1]+" hidden="+SoulsCamera.hidden+" headCovered="+headCovered+" torsoCovered="+torsoCovered+" targetHeight="+target.height+" distance="+SoulsCamera.distance+" pitch="+SoulsCamera.pitch+" fraction="+SoulsCamera.fraction+" collisionDelta="+SoulsCamera.delta+" insideBlock="+inside);
    }
}
