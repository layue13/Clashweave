package com.layue13.clashweave.probe;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;

import com.layue13.clashweave.Clashweave;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

public final class ProbeBootstrap {

    public static SimpleNetworkWrapper channel;
    public static Item blade;
    public static ProbeServer server;

    private ProbeBootstrap() {}

    public static void init() {
        if (!ProbeConfig.enabled) return;
        blade = new Item().setUnlocalizedName("clashweave.probe_blade")
            .setTextureName("minecraft:iron_sword")
            .setMaxStackSize(1)
            .setCreativeTab(CreativeTabs.tabCombat);
        GameRegistry.registerItem(blade, "probe_blade");
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("cw_probe");
        channel.registerMessage(ProbeServer.Inbound.class, ProbePacket.class, 0, Side.SERVER);
        channel.registerMessage(ClientInbound.class, ProbePacket.class, 1, Side.CLIENT);
        server = new ProbeServer();
        FMLCommonHandler.instance()
            .bus()
            .register(server);
        MinecraftForge.EVENT_BUS.register(server);
    }

    public static class ClientInbound implements IMessageHandler<ProbePacket, IMessage> {

        @Override
        public IMessage onMessage(ProbePacket packet, MessageContext context) {
            Clashweave.proxy.accept(packet);
            return null;
        }
    }
}
