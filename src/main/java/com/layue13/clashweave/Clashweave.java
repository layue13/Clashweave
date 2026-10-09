package com.layue13.clashweave;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = Clashweave.MODID, name = "Clashweave", version = Tags.VERSION, acceptedMinecraftVersions = "[1.7.10]")
public class Clashweave {

    public static final String MODID = "clashweave";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        event.getModLog()
            .info("Clashweave {} initialized", Tags.VERSION);
    }
}
