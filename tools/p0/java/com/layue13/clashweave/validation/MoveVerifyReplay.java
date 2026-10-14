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
    private final java.util.List<EntityOtherPlayerMP> crowd=new java.util.ArrayList<EntityOtherPlayerMP>();
    private final long[] frames=new long[4000];
    private int frameCount;
    private long lastFrame;
    private EntityOtherPlayerMP dummy;

    public MoveVerifyReplay(){FMLCommonHandler.instance().bus().register(this);}

    private void key(KeyBinding binding,boolean down){KeyBinding.setKeyBindState(binding.getKeyCode(),down);}

    private void camera(){
        if(camera==null){camera=new EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Photo"));camera.setInvisible(true);}
        mc.renderViewEntity=camera;
        net.minecraft.entity.Entity focus=dummy!=null?dummy:mc.thePlayer;
        camera.setPosition(focus.posX,focus.boundingBox.minY,focus.posZ);
        camera.prevPosX=camera.lastTickPosX=camera.posX;camera.prevPosY=camera.lastTickPosY=camera.posY;camera.prevPosZ=camera.lastTickPosZ=camera.posZ;
        camera.rotationYaw=camera.prevRotationYaw=focus.rotationYaw+(angle>=800?angle-800:angle);camera.rotationPitch=camera.prevRotationPitch=0;
        mc.gameSettings.thirdPersonView=1;
    }

    private EntityOtherPlayerMP mannequin(double x,double y,double z){
        EntityOtherPlayerMP m=new EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Dummy"));
        m.setPosition(x,y,z);m.rotationYaw=m.prevRotationYaw=0;m.renderYawOffset=m.prevRenderYawOffset=0;m.rotationYawHead=m.prevRotationYawHead=0;
        mc.theWorld.spawnEntityInWorld(m);return m;
    }

    private void walkDummy(double dz){
        dummy.prevPosX=dummy.lastTickPosX=dummy.posX;dummy.prevPosY=dummy.lastTickPosY=dummy.posY;dummy.prevPosZ=dummy.lastTickPosZ=dummy.posZ;
        dummy.setPosition(dummy.posX,dummy.posY,dummy.posZ+dz);
    }

    private net.minecraft.entity.Entity mount;

    private void flag(Class<?> owner,String name,boolean value){
        try{java.lang.reflect.Field f=owner.getDeclaredField(name);f.setAccessible(true);f.setBoolean(dummy,value);}
        catch(Exception e){System.out.println("MOVE_FLAG_UNAVAILABLE "+name+" "+e);}
    }

    private void clearStage(){
        if(mount!=null){mc.theWorld.removeEntity(mount);mount=null;}
        if(dummy!=null){dummy.mountEntity(null);mc.theWorld.removeEntity(dummy);dummy=null;}
    }

    /** Staged scenes on a mannequin (client only): swimming with the legs in view, horse, minecart, flying, sleeping. */
    private void stages(){
        double x=baseX+6.5,y=baseY,z=baseZ+.5;
        if(ticks==650){dummy=mannequin(x,y+1.2,z);}
        if(ticks>650&&ticks<695){walkDummy(.1);flag(net.minecraft.entity.Entity.class,"inWater",true);}
        if(ticks>=664&&ticks<=676&&(ticks-664)%4==0)photo("swimair-"+((ticks-664)/4));
        if(ticks==695)clearStage();
        if(ticks==700){mount=new net.minecraft.entity.passive.EntityHorse(mc.theWorld);mount.setPosition(x,y,z);mc.theWorld.spawnEntityInWorld(mount);dummy=mannequin(x,y+1.2,z);dummy.mountEntity(mount);}
        if(ticks>=712&&ticks<=720&&(ticks-712)%4==0)photo("horse-"+((ticks-712)/4));
        if(ticks==725)clearStage();
        if(ticks==730){mount=new net.minecraft.entity.item.EntityMinecartEmpty(mc.theWorld,x,y+.5,z);mc.theWorld.spawnEntityInWorld(mount);dummy=mannequin(x,y+1.0,z);dummy.mountEntity(mount);}
        if(ticks>=740&&ticks<=744&&(ticks-740)%4==0)photo("cart-"+((ticks-740)/4));
        if(ticks==750)clearStage();
        if(ticks==755){dummy=mannequin(x,y+2.0,z);dummy.capabilities.isFlying=true;}
        if(ticks>755&&ticks<790){walkDummy(.15);dummy.capabilities.isFlying=true;}
        if(ticks>=768&&ticks<=780&&(ticks-768)%4==0)photo("fly-"+((ticks-768)/4));
        if(ticks==790)clearStage();
        if(ticks==795){dummy=mannequin(x,y+.1,z);flag(net.minecraft.entity.player.EntityPlayer.class,"sleeping",true);}
        if(ticks>=810&&ticks<=814&&(ticks-810)%4==0)photo("sleep-"+((ticks-810)/4));
        if(ticks==830)clearStage();
        // Arms owned by something else while the legs walk: a punch, a drawn bow, a meal.
        if(ticks==840){dummy=mannequin(x,y,z);}
        if(ticks>840&&ticks<880){walkDummy(.09);if(ticks%9==0)dummy.swingItem();}
        if(ticks>=855&&ticks<=867&&(ticks-855)%3==0)photo("punch-"+((ticks-855)/3));
        if(ticks==880)clearStage();
        if(ticks==885){dummy=mannequin(x,y,z);dummy.setCurrentItemOrArmor(0,new net.minecraft.item.ItemStack(net.minecraft.init.Items.bow));dummy.setItemInUse(dummy.getHeldItem(),72000);}
        if(ticks>885&&ticks<925)walkDummy(.09);
        if(ticks>=900&&ticks<=908&&(ticks-900)%4==0)photo("bow-"+((ticks-900)/4));
        if(ticks==925)clearStage();
        if(ticks==930){dummy=mannequin(x,y,z);dummy.setCurrentItemOrArmor(0,new net.minecraft.item.ItemStack(net.minecraft.init.Items.apple));dummy.setItemInUse(dummy.getHeldItem(),72000);}
        if(ticks>930&&ticks<970)walkDummy(.09);
        if(ticks>=945&&ticks<=953&&(ticks-945)%4==0)photo("eat-"+((ticks-945)/4));
        if(ticks==970)clearStage();
    }

    /** Angle 900 or more: not photographs but frame times with 40 walking mannequins in view, for before/after comparison. */
    private void crowd(){
        mc.gameSettings.hideGUI=true;mc.gameSettings.limitFramerate=260;mc.gameSettings.enableVsync=false;
        mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=0;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=0;
        if(ticks==110)for(int i=0;i<40;i++){
            double x=mc.thePlayer.posX+(i%8-3.5)*1.6,z=mc.thePlayer.posZ+4+(i/8)*1.6;
            EntityOtherPlayerMP m=new EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Crowd"+i));
            m.setPosition(x,mc.thePlayer.boundingBox.minY,z);mc.theWorld.spawnEntityInWorld(m);crowd.add(m);
        }
        if(ticks>110){int i=0;for(EntityOtherPlayerMP m:crowd){
            double a=ticks*.07+i*.7;m.prevPosX=m.lastTickPosX=m.posX;m.prevPosZ=m.lastTickPosZ=m.posZ;
            m.setPosition(m.posX+Math.cos(a)*.1,m.posY,m.posZ+Math.sin(a)*.1);m.rotationYaw=m.prevRotationYaw=(float)Math.toDegrees(a)-90;m.renderYawOffset=m.prevRenderYawOffset=m.rotationYaw;m.rotationYawHead=m.prevRotationYawHead=m.rotationYaw;i++;}}
        if(ticks==500){
            long[] sorted=java.util.Arrays.copyOf(frames,frameCount);java.util.Arrays.sort(sorted);long sum=0;for(long f:sorted)sum+=f;
            System.out.println("MOVE_CROWD frames="+frameCount+" meanMs="+(sum/1e6/Math.max(1,frameCount))+" p50Ms="+sorted[frameCount/2]/1e6+" p95Ms="+sorted[(int)(frameCount*.95)]/1e6+" maxMs="+sorted[frameCount-1]/1e6);
            done=true;System.out.println("MOVE_VERIFY_DONE");}
    }

    /** Angle 800 + yaw: draws the katana with one light attack, then walks with the blade out; photographs the ready stance walking. */
    private void stanceWalk(){
        mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=0;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=0;
        if(ticks==100)mc.thePlayer.inventory.currentItem=0;
        if(ticks==110)ClientProxy.instance.send(com.layue13.clashweave.core.Intent.LIGHT,-1);
        camera();
        // The server keeps a player engaged for 60 ticks after the last action: walk while it still is, then past it.
        key(mc.gameSettings.keyBindForward,ticks>=128&&ticks<290);
        if(ticks>=124&&ticks<=126)photo("stand-"+(ticks-124));
        if(ticks>=138&&ticks<=173&&(ticks-138)%5==0)photo("stancewalk-"+((ticks-138)/5));
        if(ticks>=215&&ticks<=239&&(ticks-215)%8==0)photo("drawnwalk-"+((ticks-215)/8));
        if(ticks==300){done=true;System.out.println("MOVE_VERIFY_DONE");}
    }

    private void photo(String name){if(shot==null)shot=name;}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||done||ClientProxy.instance.visuals.size()<2)return;
        mc.gameSettings.hideGUI=true;mc.gameSettings.limitFramerate=60;mc.gameSettings.pauseOnLostFocus=false;
        if(!mc.thePlayer.getCommandSenderName().equals("P0A"))return;
        if(++ticks<100)return;
        if(angle>=900){crowd();return;}
        if(angle>=800){stanceWalk();return;}
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
        stages();
        if(ticks==975){done=true;System.out.println("MOVE_VERIFY_DONE");}
    }

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event){
        if(angle>=900&&event.phase==TickEvent.Phase.END&&ticks>160&&ticks<500&&frameCount<frames.length){long n=System.nanoTime();if(lastFrame!=0)frames[frameCount++]=n-lastFrame;lastFrame=n;return;}
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||shot==null)return;
        ScreenShotHelper.saveScreenshot(new File("."),"move-"+shot+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        ClientProxy.Visual own=ClientProxy.instance.own();
        if(own!=null)System.out.println("MOVE_STATE engaged="+own.state.engaged+" sheathed="+own.state.sheathed+" action="+own.state.action);
        System.out.println("MOVE_CAPTURE name="+shot+" tick="+ticks+" onGround="+mc.thePlayer.onGround+" sprint="+mc.thePlayer.isSprinting()
            +" limbSwingAmount="+mc.thePlayer.limbSwingAmount+" motionY="+mc.thePlayer.motionY);
        shot=null;
    }
}
