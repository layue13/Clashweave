package com.layue13.clashweave.validation;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import com.layue13.clashweave.Clashweave;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Actual server world, visibility, friendly protection and END keep-distance checks. */
public final class LockScenario {
    private int ticks;
    private int assertions;
    private EntityPlayerMP a;
    private EntityPlayerMP b;
    private void check(boolean result, String label) {
        System.out.println("P0_LOCK_ASSERT " + label + "=" + result);
        assertions++;
        if (!result) throw new AssertionError(label);
    }
    private void place(double distance, double angle) {
        a.setPosition(0,64,0); a.rotationYaw=0; a.rotationPitch=0;
        b.setPosition(Math.sin(Math.toRadians(angle))*distance,64,Math.cos(Math.toRadians(angle))*distance);
        b.setHealth(200);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("cw.p0.lockTest") || event.phase != TickEvent.Phase.END) return;
        if (MinecraftServer.getServer().getCurrentPlayerCount()<2) return;
        ticks++;
        if (ticks<30) return;
        if (a==null) for (Object object: MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p=(EntityPlayerMP)object; if(p.getCommandSenderName().equals("P0A")) a=p; else b=p;
        }
        if(ticks==30) {
            check(Clashweave.config.radius==8 && Clashweave.config.lockAcquireRange==16 && Clashweave.config.lockKeepRange==20,"independentDefaults");
            for (int distance: new int[]{4,8,12,16,20,22}) {
                place(distance,0);
                check((Clashweave.server.acquireLock(a)==b)==(distance<=16),"acquire"+distance);
            }
            for(double angle: new double[]{-30,30,29.99,30.01,-30.01}) {
                place(12,angle);
                check((Clashweave.server.acquireLock(a)==b)==(Math.abs(angle)<=30),"cone"+angle);
            }
            place(12,0);
            for(int x=-1;x<=1;x++)for(int y=64;y<=67;y++) a.worldObj.setBlock(x,y,2,Blocks.stone);
            check(Clashweave.server.acquireLock(a)==null,"occluded");
            for(int x=-1;x<=1;x++)for(int y=64;y<=67;y++) a.worldObj.setBlock(x,y,2,Blocks.air);
            place(22,0);
            EntityVillager friend=new EntityVillager(a.worldObj);friend.setPosition(0,64,4);a.worldObj.spawnEntityInWorld(friend);
            check(Clashweave.server.acquireLock(a)==null,"protectedVillager");
            friend.setDead();
            net.minecraft.entity.passive.EntityWolf pet=new net.minecraft.entity.passive.EntityWolf(a.worldObj);
            pet.setTamed(true);pet.setPosition(0,64,4);a.worldObj.spawnEntityInWorld(pet);
            check(Clashweave.server.acquireLock(a)==null,"protectedPet");pet.setDead();
            net.minecraft.scoreboard.ScorePlayerTeam team=a.worldObj.getScoreboard().createTeam("locktest");
            a.worldObj.getScoreboard().func_151392_a(a.getCommandSenderName(),team.getRegisteredName());
            a.worldObj.getScoreboard().func_151392_a(b.getCommandSenderName(),team.getRegisteredName());
            place(4,0);check(Clashweave.server.acquireLock(a)==null,"protectedTeamPlayer");
            a.worldObj.getScoreboard().removePlayerFromTeams(a.getCommandSenderName());
            a.worldObj.getScoreboard().removePlayerFromTeams(b.getCommandSenderName());
            place(22,0);
            check(Clashweave.server.acquireLock(a)==null,"deadAndSelfExcluded");
            place(16,0);Clashweave.server.state(a).lock=b.getEntityId();
            System.out.println("P0_LOCK_KEEP scheduled=16");
        } else if(ticks==31) {
            check(Clashweave.server.state(a).lock==b.getEntityId(),"keep16");
            place(20,0);
        } else if(ticks==32) {
            check(Clashweave.server.state(a).lock==b.getEntityId(),"keep20");
            place(22,0);
        } else if(ticks==33) {
            check(Clashweave.server.state(a).lock==-1,"release22");
            System.out.println("P0_LOCK COMPLETE assertions="+assertions);
            System.out.println("P0_NETWORK COMPLETE");
            MinecraftServer.getServer().initiateShutdown();
        }
    }
}
