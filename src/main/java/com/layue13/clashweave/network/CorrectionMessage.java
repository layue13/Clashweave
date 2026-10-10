package com.layue13.clashweave.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Receipt-side marker; does not touch the world or defer onto the render thread. */
public final class CorrectionMessage implements IMessage {

    public long sequence;
    public double x, y, z;

    public CorrectionMessage() {}

    public CorrectionMessage(long sequence, double x, double y, double z) {
        this.sequence = sequence;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void toBytes(ByteBuf b) {
        b.writeLong(sequence)
            .writeDouble(x)
            .writeDouble(y)
            .writeDouble(z);
    }

    @Override
    public void fromBytes(ByteBuf b) {
        sequence = b.readLong();
        x = b.readDouble();
        y = b.readDouble();
        z = b.readDouble();
    }

    public static final class Handler implements IMessageHandler<CorrectionMessage, IMessage> {

        @Override
        public IMessage onMessage(CorrectionMessage m, MessageContext c) {
            // Observed by the connection interceptor before the following S08.
            return null;
        }
    }
}
