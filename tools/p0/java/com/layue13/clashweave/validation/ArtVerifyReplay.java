package com.layue13.clashweave.validation;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ScreenShotHelper;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Opt-in framebuffer evidence; ordinary keys/intents, no model or mount mutations. */
public final class ArtVerifyReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final Set<String> captured=new HashSet<>();
    private int ticks;
    private String shot;
    private long lastFrame;
    private long frames;
    private long maximum;
    private boolean orbit;
    private boolean textureReported;
    private String observerStance="";
    private int observerStable;

    public ArtVerifyReplay(){FMLCommonHandler.instance().bus().register(this);}
    private void key(KeyBinding binding,boolean down){KeyBinding.setKeyBindState(binding.getKeyCode(),down);}
    private void photo(String name,int view,float angle){
        if(captured.contains(name)||shot!=null)return;
        mc.gameSettings.thirdPersonView=view;
        mc.thePlayer.rotationPitch=0;
        if(view!=0){
            EntityOtherPlayerMP camera=new EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Photo"));
            camera.setPosition(mc.thePlayer.posX,mc.thePlayer.boundingBox.minY,mc.thePlayer.posZ);
            camera.prevPosX=camera.lastTickPosX=camera.posX;camera.prevPosY=camera.lastTickPosY=camera.posY;camera.prevPosZ=camera.lastTickPosZ=camera.posZ;
            camera.rotationYaw=camera.prevRotationYaw=angle;
            mc.renderViewEntity=camera;orbit=true;
        }
        shot=name;
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||ClientProxy.instance.visuals.size()<2)return;
        ClientProxy p=ClientProxy.instance;ClientProxy.Visual own=p.own();if(own==null)return;
        mc.gameSettings.hideGUI=true;mc.gameSettings.limitFramerate=60;mc.gameSettings.pauseOnLostFocus=false;
        if(++ticks<100)return;
        boolean a=mc.thePlayer.getCommandSenderName().equals("P0A");
        if(!a){
            for(Object o:mc.theWorld.playerEntities){net.minecraft.entity.player.EntityPlayer remote=(net.minecraft.entity.player.EntityPlayer)o;
                if(!remote.getCommandSenderName().equals("P0A"))continue;
                ClientProxy.Visual v=p.visuals.get(remote.getEntityId());if(v==null)return;
                mc.gameSettings.thirdPersonView=1;
                mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=20+(float)Math.toDegrees(Math.atan2(-(remote.posX-mc.thePlayer.posX),remote.posZ-mc.thePlayer.posZ));
                mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=8;
                if(v.state.action.isEmpty()){
                    String name=v.state.sheathed?"observer-sheathed":"observer-drawn";
                    if(!name.equals(observerStance)){observerStance=name;observerStable=0;}
                    if(++observerStable>=10&&!captured.contains(name)&&shot==null)shot=name;
                }else observerStable=0;
            }return;
        }
        mc.thePlayer.rotationYaw=0;mc.thePlayer.rotationPitch="sheathed-first".equals(shot)?65:0;
        key(mc.gameSettings.keyBindSneak,ticks>=160&&ticks<180);
        key(mc.gameSettings.keyBindForward,ticks>=190&&ticks<215);
        key(mc.gameSettings.keyBindSprint,ticks>=190&&ticks<215);
        if(ticks==110)photo("sheathed-front",2,0);
        if(ticks==125)photo("sheathed-side",1,90);
        if(ticks==132){mc.gameSettings.fovSetting=30;photo("sheathed-detail",1,35);}
        if(ticks==140)photo("sheathed-back",1,0);
        if(ticks==150){photo("sheathed-first",0,0);mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=65;}
        if(ticks==172)photo("sheathed-crouch",1,90);
        if(ticks==205)photo("sheathed-run",1,90);
        if(ticks==230)p.send(Intent.LIGHT,-1);
        if(ticks>260&&ticks<310&&own.state.action.isEmpty()&&!own.state.sheathed){
            if(ticks==270)photo("drawn-first",0,0);
            if(ticks==285)photo("drawn-third",2,0);
            if(ticks==300)photo("drawn-side",1,90);
        }
        if(ticks==310){mc.gameSettings.fovSetting=30;photo("drawn-detail",1,90);}
        if(ticks==325)p.send(Intent.HEAVY,-1);
        if(Ids.local(own.state.action).equals("heavy")&&p.elapsed(own,0)>=5&&p.elapsed(own,0)<8)photo("swing-first",0,0);
    }
    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null)return;
        long now=System.nanoTime();if(lastFrame!=0){maximum=Math.max(maximum,now-lastFrame);frames++;}lastFrame=now;
        if(shot==null)return;
        if(!textureReported){
            net.minecraft.client.renderer.texture.ITextureObject texture=mc.getTextureManager().getTexture(new net.minecraft.util.ResourceLocation("clashweave","textures/models/katana.png"));
            if(texture!=null){org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_TEXTURE_BIT);
                org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,texture.getGlTextureId());
                System.out.println("ART_TEXTURE min="+org.lwjgl.opengl.GL11.glGetTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER)
                    +" mag="+org.lwjgl.opengl.GL11.glGetTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER));
                org.lwjgl.opengl.GL11.glPopAttrib();textureReported=true;}
        }
        ScreenShotHelper.saveScreenshot(new File("."),"art-"+shot+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        ClientProxy.Visual v=ClientProxy.instance.own();
        System.out.println("ART_CAPTURE name="+shot+" tick="+ticks+" action="+v.state.action+" sheathed="+v.state.sheathed
            +" view="+mc.gameSettings.thirdPersonView+" pitch="+mc.thePlayer.rotationPitch
            +" sneak="+mc.thePlayer.isSneaking()+" sprint="+mc.thePlayer.isSprinting()+" onGround="+mc.thePlayer.onGround
            +" feet="+mc.thePlayer.boundingBox.minY+" frames="+frames+" maximumFrameNanos="+maximum);
        captured.add(shot);shot=null;
        mc.gameSettings.fovSetting=70;
        if(orbit){mc.renderViewEntity=mc.thePlayer;orbit=false;}
    }
}
