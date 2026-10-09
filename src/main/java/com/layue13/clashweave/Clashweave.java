package com.layue13.clashweave;

import com.layue13.clashweave.probe.ProbeBootstrap;
import com.layue13.clashweave.probe.ProbeConfig;
import com.layue13.clashweave.probe.ProbeProxy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = Clashweave.MODID, name = "Clashweave", version = Tags.VERSION, acceptedMinecraftVersions = "[1.7.10]")
public class Clashweave {

    public static final String MODID = "clashweave";

    @SidedProxy(
        clientSide = "com.layue13.clashweave.probe.client.ProbeClient",
        serverSide = "com.layue13.clashweave.probe.ProbeProxy")
    public static ProbeProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ProbeConfig.load(event.getSuggestedConfigurationFile());
        ProbeBootstrap.init();
        proxy.init();
        event.getModLog()
            .info("Clashweave {} initialized", Tags.VERSION);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        if (ProbeConfig.enabled) event.registerServerCommand(new com.layue13.clashweave.probe.ProbeCommand());
    }
}
