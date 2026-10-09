package com.layue13.clashweave.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

import com.layue13.clashweave.forge.CombatServer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/** Client-only adapter preserves local view at packet execution, never at Netty receipt. */
public final class ViewPreservingCorrections {

    @SubscribeEvent
    public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        Channel channel = ReflectionHelper
            .getPrivateValue(NetworkManager.class, event.manager, "channel", "field_150746_k");
        channel.pipeline()
            .addBefore("packet_handler", "clashweave_view", new ChannelInboundHandlerAdapter() {

                @Override
                public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                    context.fireChannelRead(
                        message instanceof S08PacketPlayerPosLook ? new Preserved((S08PacketPlayerPosLook) message)
                            : message);
                }
            });
    }

    private static final class Preserved extends S08PacketPlayerPosLook {

        private Preserved(S08PacketPlayerPosLook source) {
            super(
                source.func_148932_c(),
                source.func_148928_d(),
                source.func_148933_e(),
                source.func_148931_f(),
                source.func_148930_g(),
                source.func_148929_h());
        }

        @Override
        public void processPacket(INetHandlerPlayClient handler) {
            EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
            if (player == null || !CombatServer.armed(player)) {
                super.processPacket(handler);
                return;
            }
            float yaw = player.rotationYaw;
            float pitch = player.rotationPitch;
            float previousYaw = player.prevRotationYaw;
            float previousPitch = player.prevRotationPitch;
            try {
                // Vanilla owns position, motion reset, terrain loading and the C06 acknowledgement.
                handler.handlePlayerPosLook(
                    new S08PacketPlayerPosLook(
                        func_148932_c(),
                        func_148928_d(),
                        func_148933_e(),
                        yaw,
                        pitch,
                        func_148929_h()));
            } finally {
                player.rotationYaw = yaw;
                player.rotationPitch = pitch;
                player.prevRotationYaw = previousYaw;
                player.prevRotationPitch = previousPitch;
                if (Boolean.getBoolean("clashweave.trace")) System.out.println(
                    "CW_VIEW packet=1 yawDelta=" + (player.rotationYaw - yaw)
                        + " pitchDelta="
                        + (player.rotationPitch - pitch)
                        + " requestedYawDelta="
                        + (func_148931_f() - yaw)
                        + " requestedPitchDelta="
                        + (func_148930_g() - pitch)
                        + " nano="
                        + System.nanoTime());
            }
        }
    }
}
