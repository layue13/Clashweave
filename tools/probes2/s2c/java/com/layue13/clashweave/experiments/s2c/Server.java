package com.layue13.clashweave.experiments.s2c;

import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class Server {
    private static final class Input {
        final EntityPlayerMP player; final Record record;
        Input(EntityPlayerMP p, Record r) { player=p; record=r; }
    }
    private final ConcurrentLinkedQueue<Input> inputs=new ConcurrentLinkedQueue<Input>();
    private EntityPlayerMP player;
    private EntityZombie zombie;
    private long tick, moveFinish=-1, nextMove=-1, maliciousNotice=-1;
    private int trial=-1, mode, rejects, revision, stateCase=-1, openCount, sneakCount, wrong, slow;
    private double speed, credit, travelled, lastX, lastY, lastZ;
    private long actionStartNano; private boolean stallRequested;
    private AxisAlignedBB lastBox;
    private boolean active, engaged, lock, action, stateTesting;
    private long lastThreat=-10000, caseAt, snapshotNano;
    private volatile long noticeHandlerTick=-1;
    private volatile int noticeTrial=-1;
    public void enqueue(EntityPlayerMP p, Record r) {
        if(r.kind==4) { noticeHandlerTick=tick; noticeTrial=r.id; log("S2C_NOTICE_HANDLER trial="+r.id+" tick="+tick); }
        inputs.add(new Input(p,r));
    }
    private void send(Record r) { Experiment.wire.sendTo(r,player); }
    private void log(String text) { System.out.println("CWMC " + text); }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase==TickEvent.Phase.START) { tick++;
            if(stallRequested){stallRequested=false;long start=System.nanoTime();try{Thread.sleep(Integer.getInteger("cw.s2c.stallMillis",50));}catch(InterruptedException x){Thread.currentThread().interrupt();}log("S2C_STALL tick="+tick+" elapsedMs="+((System.nanoTime()-start)/1e6));}
            return; }
        Input input;
        while ((input=inputs.poll())!=null) receive(input.player,input.record);
        if (player==null) return;
        if (active) validate();
        if (moveFinish>=0 && tick>=moveFinish) {
            log("S2C_END trial="+trial+" mode="+mode+" tick="+tick+" rejects="+rejects
                +" travelled="+travelled+" serverX="+player.posX+" serverY="+player.posY+" serverZ="+player.posZ);
            active=false; moveFinish=-1; nextMove=tick+5;
        }
        if (nextMove>=0 && tick>=nextMove) { nextMove=-1; setupMove(); }
        if (stateTesting) stateFixture();
        updateState();
        if (stateTesting && engaged) {
            if (player.openContainer!=player.inventoryContainer) { openCount++; player.closeScreen(); }
            if (player.isSneaking()) sneakCount++;
        }
    }
    private void receive(EntityPlayerMP p, Record r) {
        if (r.kind==0 && player==null) {
            player=p; player.theItemInWorldManager.setGameType(GameType.SURVIVAL);
            player.capabilities.disableDamage=false; player.noClip=false;
            player.inventory.mainInventory[0]=new ItemStack(Items.iron_sword); player.inventory.currentItem=0;
            observeOutbound();
            log("LOGIN name="+p.getCommandSenderName()); nextMove=tick+10;
            if(Boolean.getBoolean("cw.s2c.stateOnly")) trial=41;
            if(Boolean.getBoolean("cw.s2c.extraOnly"))trial=39;
        } else if(r.kind==10 && r.id==trial){
            actionStartNano=System.nanoTime();active=true;travelled=0;lastX=player.posX;lastY=player.posY;lastZ=player.posZ;
            send(new Record(11,trial,tick,0,0,0,0,0));log("S2C_START trial="+trial+" tick="+tick+" nano="+actionStartNano);
        } else if(r.kind==12 && r.id==trial){stallRequested=true;}
        else if (r.kind==2 && r.id==trial) {
            log("S2C_CLIENT trial="+trial+" mode="+mode+" S08="+(int)r.a+" x="+r.x+" y="+r.y+" z="+r.z+" tick="+tick);
            moveFinish=tick+3;
        } else if (r.kind==4 && r.id==trial) {
            maliciousNotice=noticeHandlerTick;
            log("S2C_MALICIOUS_NOTICE trial="+trial+" tick="+tick+" targetX="+r.x);
        } else if (r.kind==6) {
            lock=r.a==1; action=r.a==2;
        } else if (r.kind==7) {
            double ms=(r.nano-(long)r.x)/1.0e6;
            boolean expected=r.a==1;
            boolean got=r.b==1;
            if (expected!=got) wrong++;
            if (ms>100.0) slow++;
            log("S5B_APPLIED revision="+r.id+" expected="+expected+" got="+got+" elapsedMs="+ms
                +" serverTick="+tick+" clientLocalTick="+r.tick+" stale="+(r.y==1));
        } else if (r.kind==8) {
            log("S5B_CLIENT_CHECK case="+r.id+" engaged="+(r.a==1)+" sneak="+(r.b==1)
                +" forward="+r.x+" nativeChestOpened="+(r.y==1));
            if (r.a==1 && (r.b==1 || r.y==1)) wrong++;
        }
    }
    private void observeOutbound() {
        Object manager=player.playerNetServerHandler.netManager;
        try {
            for(java.lang.reflect.Field f:manager.getClass().getDeclaredFields()) if(io.netty.channel.Channel.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true); io.netty.channel.Channel channel=(io.netty.channel.Channel)f.get(manager);
                channel.pipeline().addBefore("packet_handler","cw-s2c-inbound",new io.netty.channel.ChannelInboundHandlerAdapter(){
                    public void channelRead(io.netty.channel.ChannelHandlerContext c,Object o)throws Exception {
                        if(o instanceof net.minecraft.network.play.client.C03PacketPlayer && ((net.minecraft.network.play.client.C03PacketPlayer)o).func_149466_j()) {
                            net.minecraft.network.play.client.C03PacketPlayer p=(net.minecraft.network.play.client.C03PacketPlayer)o;
                            log("S2C_NATIVE_WIRE trial="+trial+" tick="+tick+" x="+p.func_149464_c()+" z="+p.func_149472_e()+" nano="+System.nanoTime());
                        }super.channelRead(c,o);
                    }
                });
                channel.pipeline().addBefore("packet_handler","cw-ms-outbound-observer",new io.netty.channel.ChannelOutboundHandlerAdapter() {
                    public void write(io.netty.channel.ChannelHandlerContext context,Object packet,io.netty.channel.ChannelPromise promise) throws Exception {
                        if(packet instanceof net.minecraft.network.play.server.S08PacketPlayerPosLook) {
                            log("S2C_S08_SENT trial="+trial+" tick="+tick+" noticeTrial="+noticeTrial+" noticeDelta="+(tick-noticeHandlerTick));
                        }
                        super.write(context,packet,promise);
                    }
                }); return;
            }
            throw new IllegalStateException("Server network channel absent");
        } catch(IllegalAccessException error) { throw new IllegalStateException(error); }
    }
    private void setupMove() {
        trial++;
        if (trial>=44) { send(new Record(9,0,tick,0,0,0,0,0));return; }
        mode=trial<40 ? trial%7 : trial==40 ? 7 : trial==41?8:trial==42?9:10;
        speed=(mode==1 || mode==2 ? 2 : 1)*Experiment.step;
        WorldServer world=(WorldServer)player.worldObj;
        for (int x=-2;x<=10;x++) for(int z=-3;z<=8;z++) {
            world.setBlock(x,63,z,Blocks.stone);
            for (int y=64;y<=71;y++) world.setBlockToAir(x,y,z);
        }
        if (mode==2 || mode==8 || mode==9) for(int z=-3;z<=8;z++) for(int y=64;y<=67;y++) world.setBlock(3,y,z,Blocks.stone);
        if (mode==5) {
            world.setBlock(2,64,0,Blocks.stone_stairs,0,3);
            world.setBlock(3,64,0,Blocks.stone); world.setBlock(4,64,0,Blocks.stone);
        }
        if (mode==6) for(int x=-1;x<=7;x++) for(int z=-1;z<=3;z++) world.setBlock(x,64,z,Blocks.water);
        player.playerNetServerHandler.setPlayerLocation(0.5, mode==4?67:64,0.5,-90,0);
        player.motionX=player.motionY=player.motionZ=0;
        lastX=player.posX; lastY=player.posY; lastZ=player.posZ; lastBox=player.boundingBox.copy();
        travelled=0; credit=Experiment.phaseCredit*speed; rejects=0; maliciousNotice=-1; active=false;
        send(new Record(1,trial,tick,lastX,lastY,lastZ,speed,mode));
        log("S2C_BEGIN trial="+trial+" mode="+mode+" tick="+tick+" speed="+speed+" total="+(speed*Experiment.duration));
    }
    private void validate() {
        double dx=player.posX-lastX,dy=player.posY-lastY,dz=player.posZ-lastZ;
        double distance=Math.sqrt(dx*dx+dz*dz);
        double elapsed=(System.nanoTime()-actionStartNano)/50000000.0;
        double budget=Math.min(speed*Experiment.duration,speed*(elapsed+Experiment.phaseCredit));
        boolean excess=travelled+distance>budget+1e-4;
        if(distance>1e-6)log("S2C_OBSERVED trial="+trial+" tick="+tick+" dx="+dx+" dy="+dy+" dz="+dz+" elapsedTicks="+elapsed+" travelled="+travelled+" budget="+budget+" inWater="+player.isInWater());
        if(excess){rejects++;log("S2C_CORRECT trial="+trial+" tick="+tick+" reason=budget noticeDelta="+(maliciousNotice<0?-1:tick-maliciousNotice)+" illegalX="+player.posX+" rollbackX="+lastX);
            player.playerNetServerHandler.setPlayerLocation(lastX,lastY,lastZ,player.rotationYaw,player.rotationPitch);
        }else travelled+=distance;
        lastX=player.posX; lastY=player.posY; lastZ=player.posZ; lastBox=player.boundingBox.copy();
    }
    private void setupState() {
        active=false; stateTesting=true; stateCase=-1; caseAt=tick;
        for(int x=-2;x<=12;x++) for(int z=-3;z<=8;z++) {
            player.worldObj.setBlock(x,63,z,Blocks.stone);
            for(int y=64;y<=71;y++) player.worldObj.setBlockToAir(x,y,z);
        }
        player.setHealth(20); player.fallDistance=0;
        player.motionX=player.motionY=player.motionZ=0;
        player.playerNetServerHandler.setPlayerLocation(0.5,64,0.5,0,0);
        player.worldObj.setBlock(1,64,1,Blocks.chest);
        zombie=new EntityZombie(player.worldObj);
        zombie.tasks.taskEntries.clear(); zombie.targetTasks.taskEntries.clear();
        zombie.setPosition(9.5,64,0.5); player.worldObj.spawnEntityInWorld(zombie);
        zombie.setAttackTarget(null); lastThreat=tick-Experiment.disengage-1;
        send(new Record(5,0,tick,1,64,1,0,0));
        log("S5B_START hysteresis="+Experiment.disengage+" radius="+Experiment.radius);
    }
    private void stateFixture() {
        // Each scripted trigger remains long enough to observe entry and configured hysteresis exit.
        if (tick-caseAt>=90 || stateCase<0) {
            stateCase++; caseAt=tick; lock=false; action=false;
            zombie.setAttackTarget(null); zombie.setPosition(9.5,64,0.5);
            if (stateCase==0) { zombie.setPosition(4.5,64,0.5); zombie.setAttackTarget(player); }
            if (stateCase==2) zombie.setAttackTarget(player);
            if (stateCase==3) {
                player.hurtResistantTime=0;
                float before=player.getHealth();
                boolean accepted=player.attackEntityFrom(DamageSource.generic,2);
                log("S5B_HIT accepted="+accepted+" hpBefore="+before+" hpAfter="+player.getHealth()+" hurtTime="+player.hurtTime);
            }
            if (stateCase==4 || stateCase==5) send(new Record(6,stateCase,tick,0,0,0,stateCase==4?1:2,0));
            log("S5B_CASE id="+stateCase+" tick="+tick+" monsterDistance="+zombie.getDistanceToEntity(player)+" target="+(zombie.getAttackTarget()==player));
            if (stateCase>=6) {
                log("S5B_SUMMARY wrong="+wrong+" slow="+slow+" openTicks="+openCount+" sneakTicks="+sneakCount);
                send(new Record(9,0,tick,0,0,0,0,0)); stateTesting=false;
            }
        }
        if (!stateTesting) return;
        if (stateCase==1 || stateCase==2) { lock=false; action=false; }
        if (tick-caseAt==75) {
            boolean expected=stateCase==0 || stateCase==4 || stateCase==5;
            if (engaged!=expected) wrong++;
            log("S5B_ORACLE case="+stateCase+" expected="+expected+" server="+engaged);
            send(new Record(8,stateCase,tick,1,64,1,engaged?1:0,0));
        }
    }
    private void updateState() {
        boolean threat=lock || action || player.hurtTime>0;
        for(Object object:player.worldObj.loadedEntityList) if(object instanceof net.minecraft.entity.monster.EntityMob) {
            net.minecraft.entity.monster.EntityMob mob=(net.minecraft.entity.monster.EntityMob)object;
            if (mob.getAttackTarget()==player && !mob.isDead && mob.getDistanceToEntity(player)<=Experiment.radius) threat=true;
        }
        if (threat) lastThreat=tick;
        boolean next=threat || tick-lastThreat<=Experiment.disengage;
        if(next!=engaged) {
            engaged=next; revision++;
            Record snapshot=new Record(3,revision,tick,0,0,0,engaged?1:0,0);
            snapshotNano=snapshot.nano;
            send(snapshot); log("S5B_CHANGE revision="+revision+" tick="+tick+" engaged="+engaged+" nano="+snapshotNano);
        }
    }
    @SubscribeEvent public void interact(PlayerInteractEvent event) {
        if (event.entityPlayer!=player || event.world.isRemote) return;
        if (event.action==PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) event.setCanceled(true);
        if (event.action==PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK && (engaged || !Experiment.peacefulBlocks)) event.setCanceled(true);
    }
    @SubscribeEvent public void entityInteraction(net.minecraftforge.event.entity.player.EntityInteractEvent event) {
        if(event.entityPlayer==player) event.setCanceled(true);
    }
    @SubscribeEvent public void friendly(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if(Experiment.protectFriendly && event.source.getEntity()==player
            && (event.entityLiving instanceof net.minecraft.entity.passive.EntityVillager
                || (event.entityLiving instanceof net.minecraft.entity.player.EntityPlayer && player.isOnSameTeam(event.entityLiving))
                || (event.entityLiving instanceof net.minecraft.entity.passive.EntityTameable
                    && ((net.minecraft.entity.passive.EntityTameable)event.entityLiving).isTamed()))) event.setCanceled(true);
    }
}
