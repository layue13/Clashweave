package com.layue13.clashweave.validation;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ScreenShotHelper;
import com.layue13.clashweave.client.ClientProxy;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * B2: P0A, unarmed, stands, walks, sprints, jumps and lands while an orbit camera at a fixed angle to its facing
 * photographs it at fixed ticks. Ordinary keys only; nothing here changes a model or a pose. Opt-in.
 */
public final class MoveVerifyReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final float angle=Float.parseFloat(System.getProperty("cw.b2.moveVerify"));
    private EntityOtherPlayerMP camera;
    private int ticks;
    private String shot;
    private boolean done;

    public MoveVerifyReplay(){FMLCommonHandler.instance().bus().register(this);}

    private void key(KeyBinding binding,boolean down){KeyBinding.setKeyBindState(binding.getKeyCode(),down);}

    private void camera(){
        if(camera==null){camera=new EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Photo"));camera.setInvisible(true);}
        mc.renderViewEntity=camera;
        camera.setPosition(mc.thePlayer.posX,mc.thePlayer.boundingBox.minY,mc.thePlayer.posZ);
        camera.prevPosX=camera.lastTickPosX=camera.posX;camera.prevPosY=camera.lastTickPosY=camera.posY;camera.prevPosZ=camera.lastTickPosZ=camera.posZ;
        camera.rotationYaw=camera.prevRotationYaw=mc.thePlayer.rotationYaw+angle;camera.rotationPitch=camera.prevRotationPitch=0;
        mc.gameSettings.thirdPersonView=1;
    }

    private void photo(String name){if(shot==null)shot=name;}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||done||ClientProxy.instance.visuals.size()<2)return;
        mc.gameSettings.hideGUI=true;mc.gameSettings.limitFramerate=60;mc.gameSettings.pauseOnLostFocus=false;
        if(!mc.thePlayer.getCommandSenderName().equals("P0A"))return;
        if(++ticks<100)return;
        if(ticks==100)mc.thePlayer.inventory.currentItem=1;
        mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=0;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=0;
        camera();
        // Timeline: 110-170 walk, 175-200 stand, 205-265 sprint, 275 jump, 300 stop.
        key(mc.gameSettings.keyBindForward,(ticks>=110&&ticks<170)||(ticks>=205&&ticks<265));
        key(mc.gameSettings.keyBindSprint,ticks>=205&&ticks<265);
        key(mc.gameSettings.keyBindJump,ticks>=275&&ticks<277);
        if(ticks==105)photo("idle-0");
        if(ticks==190)photo("idle-1");
        if(ticks>=125&&ticks<=149&&(ticks-125)%3==0)photo("walk-"+((ticks-125)/3));
        if(ticks>=220&&ticks<=240&&(ticks-220)%3==0)photo("sprint-"+((ticks-220)/3));
        if(ticks>=276&&ticks<=294&&(ticks-276)%2==0)photo("jump-"+((ticks-276)/2));
        if(ticks==320){done=true;System.out.println("MOVE_VERIFY_DONE");}
    }

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||shot==null)return;
        ScreenShotHelper.saveScreenshot(new File("."),"move-"+shot+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        System.out.println("MOVE_CAPTURE name="+shot+" tick="+ticks+" onGround="+mc.thePlayer.onGround+" sprint="+mc.thePlayer.isSprinting()
            +" limbSwingAmount="+mc.thePlayer.limbSwingAmount+" motionY="+mc.thePlayer.motionY);
        shot=null;
    }
}
