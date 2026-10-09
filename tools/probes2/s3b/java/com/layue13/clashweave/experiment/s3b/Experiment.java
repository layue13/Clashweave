package com.layue13.clashweave.experiment.s3b;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

@Mod(modid="cw_s3b",name="Clashweave S3b experiment",version="1",acceptedMinecraftVersions="[1.7.10]")
public class Experiment {
    public static SimpleNetworkWrapper wire;
    @SidedProxy(clientSide="com.layue13.clashweave.experiment.s3b.Client",serverSide="com.layue13.clashweave.experiment.s3b.Proxy")
    public static Proxy proxy;
    @Mod.EventHandler public void init(FMLInitializationEvent e) {
        wire = NetworkRegistry.INSTANCE.newSimpleChannel("cw_s3b");
        wire.registerMessage(Server.Inbound.class,Packet.class,0,Side.SERVER);
        wire.registerMessage(Proxy.Inbound.class,Packet.class,1,Side.CLIENT);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new Server());
        proxy.init();
    }
    public static void log(String s) { System.out.println("CW3 " + s); }
}
