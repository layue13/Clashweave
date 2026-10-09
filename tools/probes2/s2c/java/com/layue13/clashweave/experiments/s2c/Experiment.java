package com.layue13.clashweave.experiments.s2c;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

@Mod(modid="cw_s2c_experiment", name="S2b S5b independent experiment", version="round3")
public class Experiment {
    @SidedProxy(clientSide="com.layue13.clashweave.experiments.s2c.client.Client", serverSide="com.layue13.clashweave.experiments.s2c.Proxy")
    public static Proxy proxy;
    public static SimpleNetworkWrapper wire;
    public static final Server server = new Server();
    public static double radius, step;
    public static int disengage, duration, phaseCredit;
    public static boolean protectFriendly, peacefulBlocks;
    @Mod.EventHandler public void init(FMLPreInitializationEvent event) {
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        radius=config.get("state", "engageRadius", 8.0).getDouble();
        disengage=config.get("state", "disengageTicks", 60).getInt();
        step=config.get("movement", "stepPerTick", 0.4).getDouble();
        duration=config.get("movement", "duration", 5).getInt();
        phaseCredit=config.get("movement", "marginTicks", 2).getInt();
        protectFriendly=config.get("interaction", "protectFriendly", true).getBoolean();
        peacefulBlocks=config.get("interaction", "peacefulBlocks", true).getBoolean();
        config.save();
        wire=NetworkRegistry.INSTANCE.newSimpleChannel("cw_s2c2");
        wire.registerMessage(ToServer.class, Record.class, 0, Side.SERVER);
        wire.registerMessage(ToClient.class, Record.class, 1, Side.CLIENT);
        FMLCommonHandler.instance().bus().register(server);
        MinecraftForge.EVENT_BUS.register(server);
        proxy.init();
    }
    public static class ToServer implements IMessageHandler<Record,IMessage> {
        public IMessage onMessage(Record record, MessageContext context) {
            server.enqueue(context.getServerHandler().playerEntity, record); return null;
        }
    }
    public static class ToClient implements IMessageHandler<Record,IMessage> {
        public IMessage onMessage(Record record, MessageContext context) { proxy.accept(record); return null; }
    }
}
