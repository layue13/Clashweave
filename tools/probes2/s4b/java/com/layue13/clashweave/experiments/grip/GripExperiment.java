package com.layue13.clashweave.experiments.grip;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;

/** Opt-in rendering fixture: this jar is never part of the production source set. */
@Mod(modid="cwgrip", name="Clashweave S4b grip experiment", version="1")
public class GripExperiment {
    public static final Item BLADE = new Item().setUnlocalizedName("s4bBlade").setMaxStackSize(1);
    @SidedProxy(clientSide="com.layue13.clashweave.experiments.grip.GripClient", serverSide="com.layue13.clashweave.experiments.grip.GripProxy")
    public static GripProxy proxy;
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        GameRegistry.registerItem(BLADE,"calibrationBlade");
        proxy.init();
    }
    public static void log(String text) { System.out.println("CW2 S4B " + text); }
}
