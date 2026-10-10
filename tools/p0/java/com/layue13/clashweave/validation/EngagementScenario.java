package com.layue13.clashweave.validation;

import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import com.layue13.clashweave.Clashweave;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class EngagementScenario {
    private int ticks;
    private int wrong;
    private int chest;
    private int sneak;

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("cw.p0.engagement") || event.phase != TickEvent.Phase.END || MinecraftServer.getServer().getCurrentPlayerCount()<2) return;
        ticks++;
        EntityPlayerMP a=null;
        EntityPlayerMP b=null;
        for (Object object:MinecraftServer.getServer().getConfigurationManager().playerEntityList) if (((EntityPlayerMP)object).getCommandSenderName().equals("P0A")) a=(EntityPlayerMP)object;
        for (Object object:MinecraftServer.getServer().getConfigurationManager().playerEntityList) if (((EntityPlayerMP)object).getCommandSenderName().equals("P0B")) b=(EntityPlayerMP)object;
        if (a==null) return;
        if (b!=null && b.getDistanceSq(4,64,0)>.01) b.playerNetServerHandler.setPlayerLocation(4,64,0,180,0);
        a.worldObj.setBlock(0,64,1,Blocks.chest);
        for (Object object:a.worldObj.loadedEntityList) {
            if (!(object instanceof EntityZombie)) continue;
            EntityZombie mob=(EntityZombie)object;
            if (ticks==1 || ticks==120) { mob.setPosition(0,64,4); mob.setAttackTarget(a); }
            if (ticks==40) mob.setPosition(0,64,9);
            if (ticks==140) {
                for (Object task : new java.util.ArrayList<>(mob.tasks.taskEntries)) mob.tasks.removeTask(((net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry)task).action);
                for (Object task : new java.util.ArrayList<>(mob.targetTasks.taskEntries)) mob.targetTasks.removeTask(((net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry)task).action);
                mob.setAttackTarget(null); mob.setRevengeTarget(null);
            }
        }
        if (ticks==220) a.attackEntityFrom(net.minecraft.util.DamageSource.generic,2);
        boolean engaged=Clashweave.server.state(a).engaged;
        Boolean expected=ticks==20||ticks==130||ticks==225||ticks==320||ticks==450 ? Boolean.TRUE : ticks==110||ticks==210||ticks==290||ticks==430||ticks==530 ? Boolean.FALSE : null;
        if (expected!=null) {
            if (engaged!=expected) wrong++;
            System.out.println("P0_ENGAGEMENT sample="+ticks+" expected="+expected+" actual="+engaged+" wrong="+wrong+" guard="+Clashweave.server.state(a).guard.held()+" lock="+Clashweave.server.state(a).lock+" x="+a.posX+" y="+a.posY+" z="+a.posZ);
        }
        if (engaged && a.openContainer!=a.inventoryContainer) { chest++; a.closeScreen(); }
        if (engaged && a.isSneaking()) sneak++;
        if (ticks==540) {
            System.out.println("P0_ENGAGEMENT COMPLETE wrong="+wrong+" combatChestTicks="+chest+" combatSneakTicks="+sneak);
            MinecraftServer.getServer().initiateShutdown();
        }
    }
}
