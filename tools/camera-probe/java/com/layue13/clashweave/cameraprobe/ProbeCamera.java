package com.layue13.clashweave.cameraprobe;
import java.nio.FloatBuffer;import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;import org.lwjgl.opengl.GL11;import org.lwjgl.util.glu.GLU;
import net.minecraft.client.Minecraft;import net.minecraft.entity.EntityLivingBase;import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.client.event.RenderWorldLastEvent;import net.minecraftforge.common.MinecraftForge;
import com.layue13.clashweave.Clashweave;import com.layue13.clashweave.client.ClientProxy;import com.layue13.clashweave.core.ThirdPersonCamera;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
/** Experimental matrix hook only; no main-jar dependency on this class. */
public final class ProbeCamera {
 private static final Minecraft mc=Minecraft.getMinecraft();private static boolean registered,active;private static double yaw,pitch,shift;
 private static final FloatBuffer base=BufferUtils.createFloatBuffer(16),projection=BufferUtils.createFloatBuffer(16),matrix=BufferUtils.createFloatBuffer(16),out=BufferUtils.createFloatBuffer(3);
 private static final IntBuffer viewport=BufferUtils.createIntBuffer(16);
 private static final float[] nativeMatrix=new float[16];
 private static double[] player,target;private static double distance=4,height=.5;
 public static void apply(float partial){
  if(!registered){MinecraftForge.EVENT_BUS.register(new ProbeCamera());registered=true;}
  active=false;matrix.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);for(int i=0;i<16;i++)nativeMatrix[i]=matrix.get(i);
  if(mc.thePlayer==null || mc.currentScreen!=null || mc.gameSettings.thirdPersonView!=1 || !Boolean.parseBoolean(System.getProperty("cw.p0.probeEnabled","true")) || ClientProxy.instance==null)return;
  EntityLivingBase t=ClientProxy.instance.lockCamera.target();if(t==null)return;
  if(Clashweave.config.lockCompositionPreset==ThirdPersonCamera.Preset.OFF)return;
  distance=Double.parseDouble(System.getProperty("cw.p0.probeDistance","4"));height=Double.parseDouble(System.getProperty("cw.p0.probeHeight",".5"));
  double x=mc.thePlayer.prevPosX+(mc.thePlayer.posX-mc.thePlayer.prevPosX)*partial,y=mc.thePlayer.prevPosY+(mc.thePlayer.posY-mc.thePlayer.prevPosY)*partial,z=mc.thePlayer.prevPosZ+(mc.thePlayer.posZ-mc.thePlayer.prevPosZ)*partial;
  player=new double[]{mc.thePlayer.posX-x,(mc.thePlayer.boundingBox.minY+mc.thePlayer.boundingBox.maxY)/2-y,mc.thePlayer.posZ-z};
  target=new double[]{t.posX-x,(t.boundingBox.minY+t.boundingBox.maxY)/2-y,t.posZ-z};
  base.clear();projection.clear();viewport.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,base);GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,projection);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
  double f=ThirdPersonCamera.fraction(Clashweave.config.lockCompositionPreset);
  for(int iteration=0;iteration<8;iteration++){
   double[] err=errors(f);if(Math.abs(err[0])+Math.abs(err[1])+Math.abs(err[2])<.000001)break;
   double[][] a=new double[3][4];double[] steps={.01,.01,.001};
   for(int k=0;k<3;k++){double save=k==0?yaw:k==1?pitch:shift;if(k==0)yaw+=steps[k];else if(k==1)pitch+=steps[k];else shift+=steps[k];double[] e=errors(f);if(k==0)yaw=save;else if(k==1)pitch=save;else shift=save;for(int row=0;row<3;row++)a[row][k]=(e[row]-err[row])/steps[k];}
   for(int row=0;row<3;row++)a[row][3]=-err[row];
   boolean good=true;for(int k=0;k<3;k++){int pivot=k;for(int j=k+1;j<3;j++)if(Math.abs(a[j][k])>Math.abs(a[pivot][k]))pivot=j;double[] swap=a[k];a[k]=a[pivot];a[pivot]=swap;if(Math.abs(a[k][k])<1e-8){good=false;break;}double v=a[k][k];for(int col=k;col<4;col++)a[k][col]/=v;for(int row=0;row<3;row++)if(row!=k){v=a[row][k];for(int col=k;col<4;col++)a[row][col]-=v*a[k][col];}}
   if(!good)break; yaw=Math.max(-70,Math.min(70,yaw+a[0][3]));pitch=Math.max(-40,Math.min(40,pitch+a[1][3]));shift=Math.max(-Double.parseDouble(System.getProperty("cw.p0.probeMaxShoulderOffset","4")),Math.min(Double.parseDouble(System.getProperty("cw.p0.probeMaxShoulderOffset","4")),shift+a[2][3]));
  }
  transform();active=true;
 }
 private static void transform(){GL11.glLoadIdentity();GL11.glTranslated(shift,-height,4-distance);GL11.glRotated(pitch,1,0,0);base.rewind();GL11.glMultMatrix(base);GL11.glRotated(yaw,0,1,0);matrix.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);}
 private static double[] project(double[] xyz){out.clear();matrix.rewind();projection.rewind();viewport.rewind();boolean ok=GLU.gluProject((float)xyz[0],(float)xyz[1],(float)xyz[2],matrix,projection,viewport,out);return new double[]{ok?out.get(0)/mc.displayWidth:Double.NaN,ok?1-out.get(1)/mc.displayHeight:Double.NaN,out.get(2)};}
 private static double horizon(){double x=-matrix.get(2),z=-matrix.get(10),r=Math.hypot(x,z);return project(new double[]{10000*x/r,0,10000*z/r})[1];}
 private static double[] errors(double f){transform();return new double[]{project(player)[0]-f,project(target)[0]-(1-f),horizon()-f};}
 @SubscribeEvent public void capture(RenderWorldLastEvent event){
  if(mc.thePlayer==null || !mc.thePlayer.getCommandSenderName().equals("P0A"))return;
  EntityLivingBase t=ClientProxy.instance.lockCamera.target();if(t==null)return;
  matrix.clear();projection.clear();viewport.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,projection);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
  double[] p=project(new double[]{mc.thePlayer.posX-RenderManager.renderPosX,(mc.thePlayer.boundingBox.minY+mc.thePlayer.boundingBox.maxY)/2-RenderManager.renderPosY,mc.thePlayer.posZ-RenderManager.renderPosZ});
  double[] q=project(new double[]{t.posX-RenderManager.renderPosX,(t.boundingBox.minY+t.boundingBox.maxY)/2-RenderManager.renderPosY,t.posZ-RenderManager.renderPosZ});
  double[] a=bounds(mc.thePlayer.boundingBox.expand(.3,.1,.3)),b=bounds(t.boundingBox.expand(.8,.1,.8));
  boolean overlap=Math.max(a[0],b[0])<Math.min(a[2],b[2]) && Math.max(a[1],b[1])<Math.min(a[3],b[3]);
  double nativeError=0;for(int i=0;i<16;i++)nativeError=Math.max(nativeError,Math.abs(matrix.get(i)-nativeMatrix[i]));
  double hx=-matrix.get(2),hz=-matrix.get(10),hr=Math.hypot(hx,hz);
  double[] left=project(new double[]{10000*(hx-hz)/hr,0,10000*(hz+hx)/hr}),right=project(new double[]{10000*(hx+hz)/hr,0,10000*(hz-hx)/hr});
  double roll=Math.toDegrees(Math.atan((right[1]-left[1])*mc.displayHeight/((right[0]-left[0])*mc.displayWidth)));
  System.out.println("CAMERA_PROBE_FRAME nano="+System.nanoTime()+" active="+active+" view="+mc.gameSettings.thirdPersonView+" preset="+Clashweave.config.lockCompositionPreset+" playerX="+p[0]+" playerY="+p[1]+" targetX="+q[0]+" targetY="+q[1]+" horizon="+horizon()+" boxesOverlap="+overlap+" yawAdjust="+yaw+" pitchAdjust="+pitch+" lateral="+shift+" height="+height+" distance="+distance+" roll="+roll+" nativeMatrixError="+nativeError);
 }
 private static double[] bounds(net.minecraft.util.AxisAlignedBB b){double[] r={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};for(int i=0;i<8;i++){double[] p=project(new double[]{((i&1)==0?b.minX:b.maxX)-RenderManager.renderPosX,((i&2)==0?b.minY:b.maxY)-RenderManager.renderPosY,((i&4)==0?b.minZ:b.maxZ)-RenderManager.renderPosZ});r[0]=Math.min(r[0],p[0]);r[1]=Math.min(r[1],p[1]);r[2]=Math.max(r[2],p[0]);r[3]=Math.max(r[3],p[1]);}return r;}
}
