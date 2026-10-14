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
    private final String mode=System.getProperty("cw.b2.moveMode","player");
    private EntityOtherPlayerMP camera;
    private int ticks;
    private String shot;
    private boolean done;
    private int baseX,baseY,baseZ;
    private EntityBoat boat;
    private net.minecraft.entity.EntityLiving mob;
    private final java.util.List<net.minecraft.entity.EntityLivingBase> crowd=new java.util.ArrayList<net.minecraft.entity.EntityLivingBase>();
    private final long[] frames=new long[4000];
    private int frameCount;
    private long lastFrame;
    private EntityOtherPlayerMP dummy;

    public MoveVerifyReplay(){FMLCommonHandler.instance().bus().register(this);}

    private void key(KeyBinding binding,boolean down){KeyBinding.setKeyBindState(binding.getKeyCode(),down);}

    private void camera(){
        if(camera==null){camera=new EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Photo"));camera.setInvisible(true);}
        mc.renderViewEntity=camera;
        net.minecraft.entity.Entity focus=mob!=null?mob:dummy!=null?dummy:mc.thePlayer;
        camera.setPosition(focus.posX,focus.boundingBox.minY,focus.posZ);
        camera.prevPosX=camera.lastTickPosX=camera.posX;camera.prevPosY=camera.lastTickPosY=camera.posY;camera.prevPosZ=camera.lastTickPosZ=camera.posZ;
        camera.rotationYaw=camera.prevRotationYaw=focus.rotationYaw+angle;camera.rotationPitch=camera.prevRotationPitch=0;
        mc.gameSettings.thirdPersonView=1;
    }

    private EntityOtherPlayerMP mannequin(double x,double y,double z){
        EntityOtherPlayerMP m=new EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Dummy"));
        m.setPosition(x,y,z);m.rotationYaw=m.prevRotationYaw=0;m.renderYawOffset=m.prevRenderYawOffset=0;m.rotationYawHead=m.prevRotationYawHead=0;
        if(Boolean.getBoolean("cw.b6.armed"))m.setCurrentItemOrArmor(0,new net.minecraft.item.ItemStack(com.layue13.clashweave.Clashweave.katana));
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
        if(ticks>650&&ticks<695){walkDummy(.1);flag(net.minecraft.entity.Entity.class,"inWater",true);dummy.prevLimbSwingAmount=dummy.limbSwingAmount;dummy.limbSwingAmount=.8f;dummy.limbSwing+=.5f;}
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
        if(ticks==795){dummy=mannequin(x,y+.1,z);
            // A real bed under it, so the body is laid down by the bed's own orientation.
            int bx=(int)Math.floor(dummy.posX),by=(int)Math.floor(y),bz=(int)Math.floor(dummy.posZ);
            mc.theWorld.setBlock(bx,by,bz,net.minecraft.init.Blocks.bed,0,2);mc.theWorld.setBlock(bx,by,bz+1,net.minecraft.init.Blocks.bed,8,2);
            dummy.sleepInBedAt(bx,by,bz+1);bed=new int[]{bx,by,bz};}
        if(ticks>=810&&ticks<=814&&(ticks-810)%4==0)photo("sleep-"+((ticks-810)/4));
        if(ticks==830){clearStage();if(bed!=null){mc.theWorld.setBlockToAir(bed[0],bed[1],bed[2]);mc.theWorld.setBlockToAir(bed[0],bed[1],bed[2]+1);bed=null;}}
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
        mc.gameSettings.hideGUI=true;mc.gameSettings.limitFramerate=260;mc.gameSettings.enableVsync=false;org.lwjgl.opengl.Display.setVSyncEnabled(false);
        mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=0;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=0;
        if(ticks==110)for(int i=0;i<40;i++){
            double x=mc.thePlayer.posX+(i%8-3.5)*1.6,z=mc.thePlayer.posZ+4+(i/8)*1.6;
            net.minecraft.entity.EntityLivingBase m=mode.equals("crowdmobs")?new net.minecraft.entity.monster.EntityZombie(mc.theWorld)
                :new EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Crowd"+i));
            m.setPosition(x,mc.thePlayer.boundingBox.minY,z);mc.theWorld.spawnEntityInWorld(m);crowd.add(m);
        }
        if(ticks>110){int i=0;for(net.minecraft.entity.EntityLivingBase m:crowd){
            double a=ticks*.07+i*.7;m.prevPosX=m.lastTickPosX=m.posX;m.prevPosZ=m.lastTickPosZ=m.posZ;
            m.setPosition(m.posX+Math.cos(a)*.1,m.posY,m.posZ+Math.sin(a)*.1);m.rotationYaw=m.prevRotationYaw=(float)Math.toDegrees(a)-90;m.renderYawOffset=m.prevRenderYawOffset=m.rotationYaw;m.rotationYawHead=m.prevRotationYawHead=m.rotationYaw;m.prevLimbSwingAmount=m.limbSwingAmount;m.limbSwingAmount=.7f;m.limbSwing+=.55f;i++;}}
        if(ticks==500){
            long[] sorted=java.util.Arrays.copyOf(frames,frameCount);java.util.Arrays.sort(sorted);long sum=0;for(long f:sorted)sum+=f;
            System.out.println("MOVE_CROWD frames="+frameCount+" meanMs="+(sum/1e6/Math.max(1,frameCount))+" p50Ms="+sorted[frameCount/2]/1e6+" p95Ms="+sorted[(int)(frameCount*.95)]/1e6+" maxMs="+sorted[frameCount-1]/1e6);
            done=true;System.out.println("MOVE_VERIFY_DONE");}
    }

    /** Angle 800 + yaw: draws the katana with one light attack, then walks with the blade out; photographs the ready stance walking. */
    private void stanceWalk(){
        // Walk out and back: the floor is only 100 blocks long, and sprinting covers it.
        mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=ticks<215?0:180;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=0;
        if(ticks==100)mc.thePlayer.inventory.currentItem=0;
        if(ticks==110)ClientProxy.instance.send(com.layue13.clashweave.core.Intent.LIGHT,-1);
        camera();
        // The server keeps a player engaged for 60 ticks after the last action: walk while it still is, then past it.
        key(mc.gameSettings.keyBindForward,ticks>=128&&ticks<340);
        key(mc.gameSettings.keyBindSprint,ticks>=245&&ticks<275);
        key(mc.gameSettings.keyBindSneak,ticks>=280&&ticks<310);
        key(mc.gameSettings.keyBindJump,ticks>=315&&ticks<320);
        if(ticks>=250&&ticks<=272&&(ticks-250)%6==0)photo("stancesprint-"+((ticks-250)/6));
        if(ticks>=285&&ticks<=306&&(ticks-285)%6==0)photo("stancesneak-"+((ticks-285)/6));
        if(ticks>=316&&ticks<=334&&(ticks-316)%6==0)photo("stancejump-"+((ticks-316)/6));
        if(ticks>=124&&ticks<=126)photo("stand-"+(ticks-124));
        if(ticks>=138&&ticks<=173&&(ticks-138)%5==0)photo("stancewalk-"+((ticks-138)/5));
        if(ticks>=215&&ticks<=239&&(ticks-215)%8==0)photo("drawnwalk-"+((ticks-215)/8));
        if(ticks==345){done=true;System.out.println("MOVE_VERIFY_DONE");}
    }

    /** Real input in the player's own eyes: sheathed, drawing, ready stance, walking, sprinting, sneaking, jumping, looking up and down, then an empty hand. */
    private void firstPerson(){
        mc.renderViewEntity=mc.thePlayer;mc.gameSettings.thirdPersonView=0;mc.gameSettings.hideGUI=false;
        if(System.getProperty("cw.b6.fov")!=null)mc.gameSettings.fovSetting=Float.parseFloat(System.getProperty("cw.b6.fov"));
        mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=0;
        boolean down=ticks>=340&&ticks<352,up=ticks>=360&&ticks<372;
        mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=down?60:up?-60:0;
        if(ticks==100)mc.thePlayer.inventory.currentItem=0;
        if(ticks==160)ClientProxy.instance.send(com.layue13.clashweave.core.Intent.LIGHT,-1);
        key(mc.gameSettings.keyBindForward,(ticks>=120&&ticks<150)||(ticks>=190&&ticks<310));
        key(mc.gameSettings.keyBindSprint,ticks>=245&&ticks<275);
        key(mc.gameSettings.keyBindSneak,ticks>=280&&ticks<310);
        key(mc.gameSettings.keyBindJump,ticks>=315&&ticks<320);
        if(ticks==380)mc.thePlayer.inventory.currentItem=1;
        if(ticks>=110&&ticks<=112)photo("fp-sheathed-idle-"+(ticks-110));
        if(ticks>=130&&ticks<=142&&(ticks-130)%6==0)photo("fp-sheathed-walk-"+((ticks-130)/6));
        if(ticks>=163&&ticks<=181&&(ticks-163)%3==0)photo("fp-draw-"+((ticks-163)/3));
        if(ticks>=186&&ticks<=188)photo("fp-stance-"+(ticks-186));
        if(ticks>=200&&ticks<=236&&(ticks-200)%6==0)photo("fp-walk-"+((ticks-200)/6));
        if(ticks>=250&&ticks<=272&&(ticks-250)%6==0)photo("fp-sprint-"+((ticks-250)/6));
        if(ticks>=285&&ticks<=306&&(ticks-285)%6==0)photo("fp-sneak-"+((ticks-285)/6));
        if(ticks>=316&&ticks<=334&&(ticks-316)%6==0)photo("fp-jump-"+((ticks-316)/6));
        if(ticks==346||ticks==347)photo("fp-down-"+(ticks-346));
        if(ticks==366||ticks==367)photo("fp-up-"+(ticks-366));
        if(ticks>=398&&ticks<=400)photo("fp-empty-"+(ticks-398));
        if(ticks==410){done=true;System.out.println("MOVE_VERIFY_DONE");}
    }

    /** First person in water, on a ladder and on a pig; the server moves the player between the three places. */
    private void fpStates(){
        mc.renderViewEntity=mc.thePlayer;mc.gameSettings.thirdPersonView=Boolean.getBoolean("cw.b6.third")?(angle>90?2:1):0;mc.gameSettings.hideGUI=false;
        if(ticks==100)mc.thePlayer.inventory.currentItem=0;
        if(ticks==105&&!Boolean.getBoolean("cw.b6.nodraw"))ClientProxy.instance.send(com.layue13.clashweave.core.Intent.LIGHT,-1);
        if(ticks%30==0)System.out.println("MOVE_FP2 tick="+ticks+" pos="+mc.thePlayer.posX+","+mc.thePlayer.posY+","+mc.thePlayer.posZ+" inWater="+mc.thePlayer.isInWater()+" riding="+mc.thePlayer.isRiding());
        boolean ladder=ticks>=225&&ticks<330,ride=ticks>=350&&ticks<415,fly=ticks>=440;
        mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=ladder?180:0;
        mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=ticks>=200&&ticks<220?40:0;
        key(mc.gameSettings.keyBindForward,(ticks>=130&&ticks<200)||(ladder&&ticks>=230)||(ride&&ticks<380)||(fly&&ticks<470));
        key(mc.gameSettings.keyBindJump,ticks>=125&&ticks<200&&ticks%7<3);
        if(ticks%10==0&&ticks>=120&&ticks<=200)System.out.println("MOVE_FP2_SWIM tick="+ticks+" amount="+mc.thePlayer.limbSwingAmount+" y="+mc.thePlayer.posY+" inWater="+mc.thePlayer.isInWater());
        if(ticks>=140&&ticks<=190&&(ticks-140)%10==0)photo("fp2-swim-"+((ticks-140)/10));
        if(ticks>=205&&ticks<=215&&(ticks-205)%5==0)photo("fp2-swimdown-"+((ticks-205)/5));
        if(ticks>=240&&ticks<=300&&(ticks-240)%10==0)photo("fp2-climb-"+((ticks-240)/10));
        if(ticks>=360&&ticks<=400&&(ticks-360)%10==0)photo("fp2-ride-"+((ticks-360)/10));
        if(ticks>=445&&ticks<=475&&(ticks-445)%10==0)photo("fp2-fly-"+((ticks-445)/10));
        if(ticks==490){done=true;System.out.println("MOVE_VERIFY_DONE");}
    }

    /** Photographs the other real player (P0B, put to bed by the server) from five yaws around the bed. */
    private void sleepReal(){
        net.minecraft.entity.Entity other=null;
        for(Object o:mc.theWorld.loadedEntityList)if(o instanceof EntityOtherPlayerMP&&((EntityOtherPlayerMP)o).getCommandSenderName().equals("P0B"))other=(net.minecraft.entity.Entity)o;
        if(other==null)return;
        if(camera==null){camera=new EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Photo"));camera.setInvisible(true);}
        mc.renderViewEntity=camera;
        float[] yaws={0,45,90,135,180};
        int phase=(ticks-110)/20;
        if(phase>=0&&phase<5){
            camera.setPosition(other.posX,other.boundingBox.minY,other.posZ);
            camera.prevPosX=camera.lastTickPosX=camera.posX;camera.prevPosY=camera.lastTickPosY=camera.posY;camera.prevPosZ=camera.lastTickPosZ=camera.posZ;
            camera.rotationYaw=camera.prevRotationYaw=yaws[phase];camera.rotationPitch=camera.prevRotationPitch=20;
            mc.gameSettings.thirdPersonView=1;
            if((ticks-110)%20==10)photo("sleepreal-"+(int)yaws[phase]+"-sleeping"+((EntityOtherPlayerMP)other).isPlayerSleeping());
        }
        if(ticks>=215){done=true;System.out.println("MOVE_VERIFY_DONE");}
    }

    /** Photographs whichever server-driven monster is nearest as they walk at P0A; the orbit camera follows it. */
    private void walkers(){
        mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=0;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=0;
        if(ticks<120)return;
        net.minecraft.entity.EntityLiving best=null;double bestD=1e9;
        for(Object o:mc.theWorld.loadedEntityList){
            if(!(o instanceof net.minecraft.entity.monster.EntityZombie||o instanceof net.minecraft.entity.monster.EntitySkeleton||o instanceof net.minecraft.entity.monster.EntityEnderman))continue;
            if(!System.getProperty("cw.b4.pick","").isEmpty()&&!o.getClass().getSimpleName().equals(System.getProperty("cw.b4.pick")))continue;
            net.minecraft.entity.EntityLiving l=(net.minecraft.entity.EntityLiving)o;
            double d=l.getDistanceSqToEntity(mc.thePlayer);
            if(d>(System.getProperty("cw.b4.pick","").isEmpty()?16:2)&&d<bestD){bestD=d;best=l;}
        }
        mob=best;
        if(ticks%40==0)for(Object o:mc.theWorld.loadedEntityList)if(o instanceof net.minecraft.entity.monster.EntityEnderman){net.minecraft.entity.monster.EntityEnderman en=(net.minecraft.entity.monster.EntityEnderman)o;System.out.println("MOVE_ENDERMAN tick="+ticks+" pos="+en.posX+","+en.posY+","+en.posZ+" distSq="+en.getDistanceSqToEntity(mc.thePlayer)+" dead="+en.isDead);}
        if(mob!=null)camera();
        int local=ticks-120;
        if(mob!=null&&local>=0&&local<=360&&local%12==0)photo("walker-"+(local/12)+"-"+mob.getClass().getSimpleName());
        if(local>400){done=true;System.out.println("MOVE_VERIFY_DONE");}
    }

    private static final String[] SCENES=System.getProperty("cw.b2.scenes","zombie,skeleton,pigman,armored,bow,child,water,chickenjockey,spiderjockey,modded,zvillager,armoredfull,skelarmor,enderman,endercarry").split(",");
    private int[] bed;
    private net.minecraft.entity.Entity mobMount;
    private final java.util.List<net.minecraft.entity.Entity> packMates=new java.util.ArrayList<net.minecraft.entity.Entity>();
    private net.minecraft.client.renderer.entity.Render savedRender;

    /** A model a mod might ship: its own class extending the vanilla one, which must keep its own posing. */
    private static final class ModdedZombie extends net.minecraft.client.model.ModelZombie {}

    private net.minecraft.entity.EntityLiving make(String scene,double x,double y,double z){
        net.minecraft.world.World w=mc.theWorld;
        net.minecraft.entity.EntityLiving e;
        if(scene.equals("skelhurt")||scene.equals("skeleton")||scene.equals("bow")||scene.equals("spiderjockey")||scene.equals("skelarmor"))e=new net.minecraft.entity.monster.EntitySkeleton(w);
        else if(scene.equals("pighurt"))e=new net.minecraft.entity.monster.EntityPigZombie(w);
        else if(scene.equals("pigman")||scene.equals("pigaxe"))e=new net.minecraft.entity.monster.EntityPigZombie(w);
        else if(scene.equals("enderman")||scene.equals("endercarry")||scene.equals("enderscream"))e=new net.minecraft.entity.monster.EntityEnderman(w);
        else e=new net.minecraft.entity.monster.EntityZombie(w);
        e.setPosition(x,y,z);e.rotationYaw=e.prevRotationYaw=0;e.renderYawOffset=e.prevRenderYawOffset=0;e.rotationYawHead=e.prevRotationYawHead=0;
        if(scene.equals("armored")){e.setCurrentItemOrArmor(4,new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_helmet));e.setCurrentItemOrArmor(3,new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_chestplate));e.setCurrentItemOrArmor(1,new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_boots));}
        if(scene.equals("armoredfull")||scene.equals("skelarmor")){
            e.setCurrentItemOrArmor(4,new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_helmet));e.setCurrentItemOrArmor(3,new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_chestplate));
            e.setCurrentItemOrArmor(2,new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_leggings));e.setCurrentItemOrArmor(1,new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_boots));}
        if(scene.equals("leather")||scene.equals("gold")||scene.equals("chain")){
            net.minecraft.item.Item[] set=scene.equals("leather")?new net.minecraft.item.Item[]{net.minecraft.init.Items.leather_boots,net.minecraft.init.Items.leather_leggings,net.minecraft.init.Items.leather_chestplate,net.minecraft.init.Items.leather_helmet}
                :scene.equals("gold")?new net.minecraft.item.Item[]{net.minecraft.init.Items.golden_boots,net.minecraft.init.Items.golden_leggings,net.minecraft.init.Items.golden_chestplate,net.minecraft.init.Items.golden_helmet}
                :new net.minecraft.item.Item[]{net.minecraft.init.Items.chainmail_boots,net.minecraft.init.Items.chainmail_leggings,net.minecraft.init.Items.chainmail_chestplate,net.minecraft.init.Items.chainmail_helmet};
            for(int slot=1;slot<=4;slot++)e.setCurrentItemOrArmor(slot,new net.minecraft.item.ItemStack(set[slot-1]));}
        if(scene.equals("pigaxe"))e.setCurrentItemOrArmor(0,new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_axe));
        if(scene.endsWith("hurt")){e.hurtTime=e.maxHurtTime=10;}
        if(scene.equals("villhurt")&&e instanceof net.minecraft.entity.monster.EntityZombie)((net.minecraft.entity.monster.EntityZombie)e).setVillager(true);
        if(scene.equals("armhurt")){for(int slot=1;slot<=4;slot++)e.setCurrentItemOrArmor(slot,new net.minecraft.item.ItemStack(slot==1?net.minecraft.init.Items.iron_boots:slot==2?net.minecraft.init.Items.iron_leggings:slot==3?net.minecraft.init.Items.iron_chestplate:net.minecraft.init.Items.iron_helmet));}
        if(scene.equals("dying")){e.setHealth(0);}
        if(scene.equals("pack"))for(int i=0;i<3;i++){net.minecraft.entity.monster.EntityZombie mate=new net.minecraft.entity.monster.EntityZombie(w);mate.setPosition(x+(i==0?.55:i==1?-.5:.1),y,z+(i==0?.3:i==1?-.4:.7));mc.theWorld.spawnEntityInWorld(mate);packMates.add(mate);}
        if(scene.equals("zvillager")&&e instanceof net.minecraft.entity.monster.EntityZombie)((net.minecraft.entity.monster.EntityZombie)e).setVillager(true);
        if(scene.equals("enderscream"))e.getDataWatcher().updateObject(18,(byte)1);
        if(scene.equals("endercarry"))((net.minecraft.entity.monster.EntityEnderman)e).func_146081_a(net.minecraft.init.Blocks.dirt);
        if(scene.equals("bow"))e.setCurrentItemOrArmor(0,new net.minecraft.item.ItemStack(net.minecraft.init.Items.bow));
        if(scene.equals("pigman"))e.setCurrentItemOrArmor(0,new net.minecraft.item.ItemStack(net.minecraft.init.Items.golden_sword));
        if(scene.equals("child")&&e instanceof net.minecraft.entity.monster.EntityZombie)((net.minecraft.entity.monster.EntityZombie)e).setChild(true);
        mc.theWorld.spawnEntityInWorld(e);
        return e;
    }

    private void setFlag(net.minecraft.entity.Entity target,String name,boolean value){
        try{java.lang.reflect.Field f=net.minecraft.entity.Entity.class.getDeclaredField(name);f.setAccessible(true);f.setBoolean(target,value);}
        catch(Exception e){System.out.println("MOVE_FLAG_UNAVAILABLE "+name+" "+e);}
    }

    private void step(net.minecraft.entity.Entity e,double dz){
        e.prevPosX=e.lastTickPosX=e.posX;e.prevPosY=e.lastTickPosY=e.posY;e.prevPosZ=e.lastTickPosZ=e.posZ;
        e.setPosition(e.posX,e.posY,e.posZ+dz);e.onGround=e.ridingEntity==null;
        e.rotationYaw=e.prevRotationYaw=0;
        if(e instanceof net.minecraft.entity.EntityLivingBase){
            net.minecraft.entity.EntityLivingBase l=(net.minecraft.entity.EntityLivingBase)e;
            l.prevLimbSwingAmount=l.limbSwingAmount;l.limbSwingAmount=.7f;l.limbSwing+=.55f;
            l.renderYawOffset=l.prevRenderYawOffset=0;l.rotationYawHead=l.prevRotationYawHead=0;
        }
    }

    /** Monster scenes, client-only, one after another: each walks past the camera and is photographed. */
    private void mobs(){
        mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=0;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=0;
        double x=mc.thePlayer.posX+3,y=mc.thePlayer.boundingBox.minY,z=mc.thePlayer.posZ;
        if(ticks<110)return;
        int local=(ticks-110)%80,which=(ticks-110)/80;
        if(which>=SCENES.length){done=true;System.out.println("MOVE_VERIFY_DONE");return;}
        String scene=SCENES[which];
        if(local==0){
            if(scene.equals("modded")){
                // Swap the zombie renderer for one whose model is a mod-style subclass: it must be left alone.
                java.util.Map map=net.minecraft.client.renderer.entity.RenderManager.instance.entityRenderMap;
                savedRender=(net.minecraft.client.renderer.entity.Render)map.get(net.minecraft.entity.monster.EntityZombie.class);
                net.minecraft.client.renderer.entity.RenderBiped r=new net.minecraft.client.renderer.entity.RenderBiped(new ModdedZombie(),.5f){protected net.minecraft.util.ResourceLocation getEntityTexture(net.minecraft.entity.Entity e){return new net.minecraft.util.ResourceLocation("textures/entity/zombie/zombie.png");}};
                r.setRenderManager(net.minecraft.client.renderer.entity.RenderManager.instance);
                map.put(net.minecraft.entity.monster.EntityZombie.class,r);
            }
            mob=make(scene,x,y,z);
            if(scene.equals("chickenjockey")){mobMount=new net.minecraft.entity.passive.EntityChicken(mc.theWorld);mobMount.setPosition(x,y,z);mc.theWorld.spawnEntityInWorld(mobMount);mob.mountEntity(mobMount);}
            if(scene.equals("spiderjockey")){mobMount=new net.minecraft.entity.monster.EntitySpider(mc.theWorld);mobMount.setPosition(x,y,z);mc.theWorld.spawnEntityInWorld(mobMount);mob.mountEntity(mobMount);}
        }
        if(mob!=null&&local>0){
            step(mob,mobMount==null?.13:0);
            if(mobMount!=null)step(mobMount,.13);
            for(net.minecraft.entity.Entity mate:packMates)step(mate,.13);
            if(scene.equals("water"))setFlag(mob,"inWater",true);
            if(scene.endsWith("hurt"))mob.hurtTime=6;
            if(scene.equals("dying")){mob.setHealth(0);mob.deathTime=12;}
        }
        camera();
        if(local>=30&&local<=66&&(local-30)%6==0)photo("mob-"+scene+"-"+((local-30)/6));
        if(local==75){
            if(mob!=null){mc.theWorld.removeEntity(mob);mob=null;}
            if(mobMount!=null){mc.theWorld.removeEntity(mobMount);mobMount=null;}
            for(net.minecraft.entity.Entity mate:packMates)mc.theWorld.removeEntity(mate);packMates.clear();
            if(scene.equals("modded")&&savedRender!=null)net.minecraft.client.renderer.entity.RenderManager.instance.entityRenderMap.put(net.minecraft.entity.monster.EntityZombie.class,savedRender);
        }
    }

    private void photo(String name){if(shot==null)shot=name;}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||done||ClientProxy.instance.visuals.size()<2)return;
        mc.gameSettings.hideGUI=true;mc.gameSettings.limitFramerate=60;mc.gameSettings.pauseOnLostFocus=false;
        if(!mc.thePlayer.getCommandSenderName().equals("P0A"))return;
        if(++ticks<100)return;
        if(mode.equals("crowd")||mode.equals("crowdmobs")){crowd();return;}
        if(mode.equals("stance")){stanceWalk();return;}
        if(mode.equals("mobs")){mobs();return;}
        if(mode.equals("walkers")){walkers();return;}
        if(mode.equals("firstperson")){firstPerson();return;}
        if(mode.equals("fpstates")){fpStates();return;}
        if(mode.equals("sleepreal")){sleepReal();return;}
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
        if(mode.startsWith("crowd")&&event.phase==TickEvent.Phase.END&&ticks>160&&ticks<500&&frameCount<frames.length){long n=System.nanoTime();if(lastFrame!=0)frames[frameCount++]=n-lastFrame;lastFrame=n;return;}
        if(event.phase!=TickEvent.Phase.END||mc.thePlayer==null||shot==null)return;
        ScreenShotHelper.saveScreenshot(new File("."),"move-"+shot+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        ClientProxy.Visual own=ClientProxy.instance.own();
        if(own!=null)System.out.println("MOVE_STATE engaged="+own.state.engaged+" sheathed="+own.state.sheathed+" action="+own.state.action);
        System.out.println("MOVE_CAPTURE name="+shot+" tick="+ticks+" onGround="+mc.thePlayer.onGround+" sprint="+mc.thePlayer.isSprinting()
            +" limbSwingAmount="+mc.thePlayer.limbSwingAmount+" motionY="+mc.thePlayer.motionY);
        shot=null;
    }
}
