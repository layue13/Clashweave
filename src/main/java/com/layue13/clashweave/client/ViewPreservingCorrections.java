package com.layue13.clashweave.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/** Client-only adapter preserves local view at packet execution, never at Netty receipt. */
public final class ViewPreservingCorrections {

    private static final io.netty.util.AttributeKey<com.layue13.clashweave.core.CorrectionMarkers> MARKERS = new io.netty.util.AttributeKey<>(
        "clashweave_corrections");

    @SubscribeEvent
    public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        Channel channel = ReflectionHelper
            .getPrivateValue(NetworkManager.class, event.manager, "channel", "field_150746_k");
        channel.attr(MARKERS)
            .set(new com.layue13.clashweave.core.CorrectionMarkers());
        channel.pipeline()
            .addBefore("packet_handler", "clashweave_view", new ChannelInboundHandlerAdapter() {

                @Override
                public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                    com.layue13.clashweave.core.CorrectionMarkers markers = channel.attr(MARKERS)
                        .get();
                    if (message instanceof net.minecraft.network.play.server.S3FPacketCustomPayload) {
                        net.minecraft.network.play.server.S3FPacketCustomPayload packet = (net.minecraft.network.play.server.S3FPacketCustomPayload) message;
                        byte[] data = packet.func_149168_d();
                        if (com.layue13.clashweave.Clashweave.MODID.equals(packet.func_149169_c()) && data != null
                            && data.length == 33
                            && data[0] == 3) {
                            java.nio.ByteBuffer bytes = java.nio.ByteBuffer.wrap(data);
                            bytes.get();
                            long id = bytes.getLong();
                            boolean accepted = markers
                                .mark(id, System.nanoTime(), bytes.getDouble(), bytes.getDouble(), bytes.getDouble());
                            if (Boolean.getBoolean("clashweave.trace")) System.out.println(
                                "CW_CORRECTION_MARK sequence=" + id
                                    + " accepted="
                                    + accepted
                                    + " thread="
                                    + Thread.currentThread()
                                        .getName()
                                    + " nano="
                                    + System.nanoTime());
                        }
                    }
                    if (message instanceof cpw.mods.fml.common.network.internal.FMLProxyPacket) {
                        cpw.mods.fml.common.network.internal.FMLProxyPacket p = (cpw.mods.fml.common.network.internal.FMLProxyPacket) message;
                        io.netty.buffer.ByteBuf b = p.payload();
                        int at = b.readerIndex();
                        if (com.layue13.clashweave.Clashweave.MODID.equals(p.channel()) && b.readableBytes() == 33
                            && b.getByte(at) == 3) {
                            long id = b.getLong(at + 1);
                            boolean accepted = markers.mark(
                                id,
                                System.nanoTime(),
                                b.getDouble(at + 9),
                                b.getDouble(at + 17),
                                b.getDouble(at + 25));
                            if (Boolean.getBoolean("clashweave.trace")) System.out.println(
                                "CW_CORRECTION_MARK sequence=" + id
                                    + " accepted="
                                    + accepted
                                    + " thread="
                                    + Thread.currentThread()
                                        .getName()
                                    + " nano="
                                    + System.nanoTime());
                        }
                    }
                    if (message instanceof S08PacketPlayerPosLook) {
                        S08PacketPlayerPosLook packet = (S08PacketPlayerPosLook) message;
                        message = new Preserved(
                            packet,
                            markers.consume(
                                System.nanoTime(),
                                packet.func_148932_c(),
                                packet.func_148928_d(),
                                packet.func_148933_e()));
                    }
                    context.fireChannelRead(message);
                }
            });
    }

    private static final class Preserved extends S08PacketPlayerPosLook {

        private final long marker;

        private Preserved(S08PacketPlayerPosLook source, long marker) {
            super(
                source.func_148932_c(),
                source.func_148928_d(),
                source.func_148933_e(),
                source.func_148931_f(),
                source.func_148930_g(),
                source.func_148929_h());
            this.marker = marker;
        }

        @Override
        public void processPacket(INetHandlerPlayClient handler) {
            EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                super.processPacket(handler);
                return;
            }
            float yaw = player.rotationYaw;
            float pitch = player.rotationPitch;
            float previousYaw = player.prevRotationYaw;
            float previousPitch = player.prevRotationPitch;
            try {
                // Vanilla owns position, motion reset, terrain loading and the C06 acknowledgement.
                if (marker == 0) super.processPacket(handler);
                else handler.handlePlayerPosLook(
                    new S08PacketPlayerPosLook(
                        func_148932_c(),
                        func_148928_d(),
                        func_148933_e(),
                        yaw,
                        pitch,
                        func_148929_h()));
            } finally {
                if (marker != 0) {
                    player.rotationYaw = yaw;
                    player.rotationPitch = pitch;
                    player.prevRotationYaw = previousYaw;
                    player.prevRotationPitch = previousPitch;
                }
                if (Boolean.getBoolean("clashweave.trace")) System.out.println(
                    "CW_VIEW packet=1 marker=" + marker
                        + " protected="
                        + (marker != 0)
                        + " yawDelta="
                        + (player.rotationYaw - yaw)
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
