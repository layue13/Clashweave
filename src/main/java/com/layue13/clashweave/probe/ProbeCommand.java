package com.layue13.clashweave.probe;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

public class ProbeCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "cwprobe";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/cwprobe s1 | s2 [open|wall|invalid] | s3 | swing | engage | peace";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }
        if ("s1".equals(args[0])) {
            ProbeBootstrap.server.startDamage();
        } else if (sender instanceof EntityPlayerMP) {
            EntityPlayerMP p = (EntityPlayerMP) sender;
            if ("s2".equals(args[0])) ProbeBootstrap.server.startMove(p, args.length > 1 ? args[1] : "open", 0);
            if ("s3".equals(args[0])) ProbeBootstrap.server.startGuard(p);
            if ("swing".equals(args[0])) ProbeBootstrap.server.swing(p);
            if ("engage".equals(args[0]) || "peace".equals(args[0])) {
                p.getEntityData()
                    .setBoolean("cwEngage", "engage".equals(args[0]));
                ProbeBootstrap.channel.sendTo(new ProbePacket(5, "engage".equals(args[0]) ? 1 : 0, 0, 0, 0), p);
            }
        }
    }
}
