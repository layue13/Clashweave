package com.layue13.clashweave.validation;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.forge.CombatServer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class MovementScenario {
    private long instance;
    private int started;
    private int corrections;
    private int ticks;
    private double baselineY;
    private double maxY;

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) throws InterruptedException {
        if (!Boolean.getBoolean("cw.p0.movement")) return;
        if (event.phase == TickEvent.Phase.START) {
            if (Boolean.getBoolean("cw.p0.lowTps")) Thread.sleep(150);
            return;
        }
        if (MinecraftServer.getServer().getCurrentPlayerCount() < 2) return;
        ticks++;
        for (Object object : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) object;
            if (!player.getCommandSenderName().equals("P0A")) continue;
            CombatServer.PlayerState state = Clashweave.server.state(player);
            if (state.scheduler.current() != null && state.instance != instance) {
                if (started>0) System.out.println("P0_MOVEMENT finish="+started+" maxRise="+(maxY-baselineY)+" corrections="+state.corrections);
                instance = state.instance;
                started++;
                baselineY=player.posY;
                maxY=player.posY;
                if (started == 9) for (int x = (int) Math.floor(player.posX)-2; x <= (int) Math.floor(player.posX)+2; x++) for (int y=64;y<=66;y++) player.worldObj.setBlock(x,y,(int)Math.floor(player.posZ)+1,Blocks.stone);
                if (started == 10) {
                    for (int x=-2;x<=2;x++) for (int y=64;y<=66;y++) player.worldObj.setBlock(x,y,1,Blocks.air);
                    player.worldObj.setBlock(0,64,0,Blocks.water);
                    player.worldObj.setBlock(0,64,1,Blocks.water);
                }
                if (started == 11) {
                    // Remove the whole seven-block water-flow range before testing solid stairs and jumping.
                    for(int x=-10;x<=10;x++) for(int z=-10;z<=10;z++) player.worldObj.setBlock(x,64,z,Blocks.air);
                    player.worldObj.setBlock(0,64,1,Blocks.stone_stairs,2,3);
                }
                if (started == 12) player.worldObj.setBlock(0,64,1,Blocks.air);
                System.out.println("P0_MOVEMENT start=" + started + " instance=" + instance + " corrections=" + state.corrections + " tick=" + Clashweave.server.tick);
                if (started == 4) {
                    for (Object other : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
                        EntityPlayerMP attacker = (EntityPlayerMP) other;
                        if (attacker != player) player.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(attacker), 2);
                    }
                }
            }
            maxY=Math.max(maxY,player.posY);
            if (state.corrections != corrections) {
                corrections = state.corrections;
                System.out.println("P0_MOVEMENT corrected=" + corrections + " start=" + started + " tick=" + Clashweave.server.tick);
            }
        }
        if (ticks == 500) {
            System.out.println("P0_MOVEMENT COMPLETE starts=" + started + " corrections=" + corrections);
            MinecraftServer.getServer().initiateShutdown();
        }
    }
}
