package com.layue13.clashweave.experiment.s3supp;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
public class Proxy {
    public void init() {}
    public void accept(Packet p,long receipt) {}
    public static class Inbound implements IMessageHandler<Packet,IMessage> {
        public IMessage onMessage(Packet p,MessageContext c) { Experiment.proxy.accept(p,System.currentTimeMillis());return null; }
    }
}
