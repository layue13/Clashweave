package com.layue13.clashweave.validation;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.entity.item.EntityBoat;
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
    private int baseX,baseY,baseZ;
    private EntityBoat boat;
    private EntityOtherPlayerMP dummy;

    public MoveVerifyReplay(){FMLCommonHandler.instance().bus().register(this);}

    private void key(KeyBinding binding,boolean down){KeyBinding.setKeyBindState(binding.getKeyCode(),down);}

    private void camera(){
        if(camera==null){camera=new EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Photo"));camera.setInvisible(true);}
        mc.renderViewEntity=camera;
        net.minecraft.entity.Entity focus=dummy!=null?dummy:mc.thePlayer;
        camera.setPosition(focus.posX,focus.boundingBox.minY,focus.posZ);
        camera.prevPosX=camera.lastTickPosX=camera.posX;camera.prevPosY=camera.lastTickPosY=camera.posY;camera.prevPosZ=camera.lastTickPosZ=camera.posZ;
        camera.rotationYaw=camera.prevRotationYaw=focus.rotationYaw+angle;camera.rotationPitch=camera.prevRotationPitch=0;
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
        // Timeline: 110-170 walk, 175-200 stand, 205-265 sprint, 275 jump, 300-370 sneak (walking from 310), 390 stop.
        key(mc.gameSettings.keyBindSprint,ticks>=205&&ticks<265);
        key(mc.gameSettings.keyBindSneak,ticks>=300&&ticks<370);
        key(mc.gameSettings.keyBindJump,ticks>=275&&ticks<277);
        if(ticks==105)photo("idle-0");
        if(ticks==190)photo("idle-1");
        if(ticks>=125&&ticks<=149&&(ticks-125)%3==0)photo("walk-"+((ticks-125)/3));
        if(ticks>=220&&ticks<=240&&(ticks-220)%3==0)photo("sprint-"+((ticks-220)/3));
        if(ticks>=276&&ticks<=294&&(ticks-276)%2==0)photo("jump-"+((ticks-276)/2));
        if(ticks>=330&&ticks<=354&&(ticks-330)%3==0)photo("sneak-"+((ticks-330)/3));
        // Terrain, water, a ladder and a boat exist on this client only: they stage poses, not the server's rules.
        if(ticks==380){baseX=(int)Math.floor(mc.thePlayer.posX);baseY=(int)Math.floor(mc.thePlayer.boundingBox.minY);baseZ=(int)Math.floor(mc.thePlayer.posZ);}
        if(ticks==385)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=8;dz++)mc.theWorld.setBlock(baseX+dx,baseY,baseZ+dz,Blocks.water);
        key(mc.gameSettings.keyBindForward,(ticks>=110&&ticks<170)||(ticks>=205&&ticks<265)||(ticks>=310&&ticks<370)||(ticks>=400&&ticks<440)||(ticks>=500&&ticks<560));
        if(ticks>=392&&ticks<=396&&(ticks-392)%4==0)photo("swim-idle");
        if(ticks>=410&&ticks<=434&&(ticks-410)%4==0)photo("swim-"+((ticks-410)/4));
        if(ticks==450){for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=8;dz++)mc.theWorld.setBlock(baseX+dx,baseY,baseZ+dz,Blocks.air);
            // A mannequin climbs: the player's own position belongs to the server, which would snap it off a client-only ladder.
            for(int y=0;y<9;y++){mc.theWorld.setBlock(baseX+3,baseY+y,baseZ+2,Blocks.stone);mc.theWorld.setBlock(baseX+3,baseY+y,baseZ+1,Blocks.ladder,2,2);}
            dummy=new EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Dummy"));
            dummy.setPosition(baseX+3.5,baseY+1.0,baseZ+1.55);mc.theWorld.spawnEntityInWorld(dummy);}
        if(dummy!=null&&ticks>450&&ticks<580){dummy.prevPosX=dummy.lastTickPosX=dummy.posX;dummy.prevPosY=dummy.lastTickPosY=dummy.posY;dummy.prevPosZ=dummy.lastTickPosZ=dummy.posZ;
            double y=dummy.posY+.06;if(y>baseY+7)y=baseY+1;dummy.setPosition(baseX+3.5,y,baseZ+1.55);dummy.onGround=false;
            dummy.rotationYaw=dummy.prevRotationYaw=0;dummy.renderYawOffset=dummy.prevRenderYawOffset=0;dummy.rotationYawHead=dummy.prevRotationYawHead=0;}
        if(ticks>=520&&ticks<=544&&(ticks-520)%4==0)photo("climb-"+((ticks-520)/4));
        if(ticks==580){for(int y=0;y<9;y++){mc.theWorld.setBlock(baseX+3,baseY+y,baseZ+2,Blocks.air);mc.theWorld.setBlock(baseX+3,baseY+y,baseZ+1,Blocks.air);}
            mc.theWorld.removeEntity(dummy);dummy=null;}
        if(ticks==600){boat=new EntityBoat(mc.theWorld,mc.thePlayer.posX,mc.thePlayer.posY-.3,mc.thePlayer.posZ);mc.theWorld.spawnEntityInWorld(boat);mc.thePlayer.mountEntity(boat);}
        if(ticks>=610&&ticks<=622&&(ticks-610)%4==0)photo("ride-"+((ticks-610)/4));
        if(ticks==640){done=true;System.out.println("MOVE_VERIFY_DONE");}
    }

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||shot==null)return;
        ScreenShotHelper.saveScreenshot(new File("."),"move-"+shot+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        System.out.println("MOVE_CAPTURE name="+shot+" tick="+ticks+" onGround="+mc.thePlayer.onGround+" sprint="+mc.thePlayer.isSprinting()
            +" limbSwingAmount="+mc.thePlayer.limbSwingAmount+" motionY="+mc.thePlayer.motionY);
        shot=null;
    }
}
