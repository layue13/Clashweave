package com.layue13.clashweave;

import net.minecraftforge.common.MinecraftForge;

import com.layue13.clashweave.forge.CombatConfig;
import com.layue13.clashweave.forge.CombatServer;
import com.layue13.clashweave.forge.CombatWeapons;
import com.layue13.clashweave.forge.CommonProxy;
import com.layue13.clashweave.forge.ContentRuntime;
import com.layue13.clashweave.forge.KatanaItem;
import com.layue13.clashweave.network.ContentSyncMessage;
import com.layue13.clashweave.network.HelloMessage;
import com.layue13.clashweave.network.InputMessage;
import com.layue13.clashweave.network.StateMessage;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
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
    public static org.apache.logging.log4j.Logger log;
    public static ContentRuntime content;
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

    /** All items exist by now, so the server-authoritative item table can be resolved. */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        try {
            CombatWeapons.install(
                CombatWeapons.build(
                    java.util.Arrays.asList(config.weaponItems),
                    content.rules,
                    config.multiplierMinimum,
                    config.multiplierMaximum,
                    name -> (net.minecraft.item.Item) net.minecraft.item.Item.itemRegistry.getObject(name)));
        } catch (IllegalArgumentException exception) {
            log.error("Clashweave weapon table rejected: {}", exception.getMessage());
            throw new IllegalStateException(
                "Clashweave refuses to start: invalid weapons.items in combat.cfg: " + exception.getMessage(),
                exception);
        }
        log.info(
            "Clashweave weapon table: {}",
            CombatWeapons.table()
                .names());
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        log = event.getModLog();
        java.io.File directory = new java.io.File(event.getModConfigurationDirectory(), MODID);
        try {
            config.load(directory);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid combat configuration", exception);
        }
        content = ContentRuntime.load(directory, config, log);
        GameRegistry.registerItem(katana, "katana");
        network = NetworkRegistry.INSTANCE.newSimpleChannel("clashweave");
        server = new CombatServer(config, content.rules);
        network
            .registerMessage(InputMessage.Handler.class, InputMessage.class, InputMessage.DISCRIMINATOR, Side.SERVER);
        network.registerMessage(StateMessage.Handler.class, StateMessage.class, 1, Side.CLIENT);
        network.registerMessage(HelloMessage.Handler.class, HelloMessage.class, 4, Side.SERVER);
        network.registerMessage(ContentSyncMessage.Handler.class, ContentSyncMessage.class, 5, Side.CLIENT);
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
