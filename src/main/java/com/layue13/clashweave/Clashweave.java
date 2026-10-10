package com.layue13.clashweave;

import net.minecraftforge.common.MinecraftForge;

import com.layue13.clashweave.forge.CombatConfig;
import com.layue13.clashweave.forge.CombatServer;
import com.layue13.clashweave.forge.CommonProxy;
import com.layue13.clashweave.forge.KatanaItem;
import com.layue13.clashweave.network.InputMessage;
import com.layue13.clashweave.network.StateMessage;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

@Mod(modid = Clashweave.MODID, name = "Clashweave", version = Tags.VERSION, acceptedMinecraftVersions = "[1.7.10]")
public class Clashweave {

    public static final String MODID = "clashweave";
    public static final CombatConfig config = new CombatConfig();
    public static final KatanaItem katana = new KatanaItem();
    public static SimpleNetworkWrapper network;
    public static CombatServer server;
    @SidedProxy(
        clientSide = "com.layue13.clashweave.client.ClientProxy",
        serverSide = "com.layue13.clashweave.forge.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void stopping(FMLServerStoppingEvent event) {
        server.stop();
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        try {
            config.load(new java.io.File(event.getModConfigurationDirectory(), MODID));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid combat configuration", exception);
        }
        GameRegistry.registerItem(katana, "katana");
        network = NetworkRegistry.INSTANCE.newSimpleChannel("clashweave");
        server = new CombatServer(config);
        network.registerMessage(InputMessage.Handler.class, InputMessage.class, 0, Side.SERVER);
        network.registerMessage(StateMessage.Handler.class, StateMessage.class, 1, Side.CLIENT);
        network.registerMessage(
            com.layue13.clashweave.network.SemanticMessage.Handler.class,
            com.layue13.clashweave.network.SemanticMessage.class,
            2,
            Side.CLIENT);
        network.registerMessage(
            com.layue13.clashweave.network.CorrectionMessage.Handler.class,
            com.layue13.clashweave.network.CorrectionMessage.class,
            3,
            Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(server);
        FMLCommonHandler.instance()
            .bus()
            .register(server);
        proxy.initialize();
        event.getModLog()
            .info("Clashweave {} initialized", Tags.VERSION);
    }
}
