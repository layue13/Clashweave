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
                    context.fireChannelRead(message);
                }
            });
    }
}
