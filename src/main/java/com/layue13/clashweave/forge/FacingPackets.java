package com.layue13.clashweave.forge;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C03PacketPlayer;

import com.layue13.clashweave.core.FacingHistory;

import cpw.mods.fml.relauncher.ReflectionHelper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/** Observes decoded C03 without consuming, modifying or replacing vanilla movement processing. */
final class FacingPackets {

    private FacingPackets() {}

    static void install(NetworkManager manager, final FacingHistory history) {
        Channel channel = ReflectionHelper.getPrivateValue(NetworkManager.class, manager, "channel", "field_150746_k");
        if (channel.pipeline()
            .get("clashweave_facing") != null)
            channel.pipeline()
                .remove("clashweave_facing");
        channel.pipeline()
            .addBefore("packet_handler", "clashweave_facing", new ChannelInboundHandlerAdapter() {

                @Override
                public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                    if (message instanceof C03PacketPlayer) {
                        C03PacketPlayer p = (C03PacketPlayer) message;
                        history.observe(
                            System.nanoTime(),
                            p.func_149462_g(),
                            p.func_149470_h(),
                            p.func_149464_c(),
                            p.func_149467_d(),
                            p.func_149472_e(),
                            p.func_149463_k(),
                            p.func_149466_j());
                    }
                    if (message instanceof net.minecraft.network.play.client.C17PacketCustomPayload) {
                        net.minecraft.network.play.client.C17PacketCustomPayload p = (net.minecraft.network.play.client.C17PacketCustomPayload) message;
                        byte[] bytes = p.func_149558_e();
                        if (com.layue13.clashweave.Clashweave.MODID.equals(p.func_149559_c()) && bytes != null
                            && bytes.length == 45
                            && bytes[0] == 0)
                            history.requestReceived(
                                java.nio.ByteBuffer.wrap(bytes)
                                    .getInt(33),
                                System.nanoTime());
                    }
                    if (message instanceof cpw.mods.fml.common.network.internal.FMLProxyPacket) {
                        cpw.mods.fml.common.network.internal.FMLProxyPacket p = (cpw.mods.fml.common.network.internal.FMLProxyPacket) message;
                        io.netty.buffer.ByteBuf b = p.payload();
                        int at = b.readerIndex();
                        if (com.layue13.clashweave.Clashweave.MODID.equals(p.channel()) && b.readableBytes() == 45
                            && b.getByte(at) == 0) history.requestReceived(b.getInt(at + 33), System.nanoTime());
                    }
                    context.fireChannelRead(message);
                }
            });
    }
}
