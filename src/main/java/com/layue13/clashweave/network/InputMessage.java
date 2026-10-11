package com.layue13.clashweave.network;

import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.core.Intent;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class InputMessage implements IMessage {

    public static final int DISCRIMINATOR = 0;
    public static final int BODY_BYTES = 44;
    public static final int WIRE_BYTES = BODY_BYTES + 1;
    public static final int SEQUENCE_BODY_OFFSET = 32;
    public static final int SEQUENCE_WIRE_OFFSET = SEQUENCE_BODY_OFFSET + 1;

    public long session;
    public long stamp;
    public long origin;
    public float yaw;
    public float pitch;
    public int sequence;
    public int kind;
    public int target;

    public InputMessage() {}

    public InputMessage(long session, long stamp, int sequence, Intent intent, int target) {
        this.session = session;
        this.stamp = stamp;
        this.sequence = sequence;
        this.kind = intent == null ? -1 : intent.ordinal();
        this.target = target;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        if (buffer.readableBytes() != BODY_BYTES) throw new IllegalArgumentException("Input size");
        session = buffer.readLong();
        stamp = buffer.readLong();
        origin = buffer.readLong();
        yaw = buffer.readFloat();
        pitch = buffer.readFloat();
        sequence = buffer.readInt();
        kind = buffer.readInt();
        target = buffer.readInt();
        if (kind < -2 || kind >= Intent.values().length) throw new IllegalArgumentException("Input intent");
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(session)
            .writeLong(stamp)
            .writeLong(origin)
            .writeFloat(yaw)
            .writeFloat(pitch)
            .writeInt(sequence)
            .writeInt(kind)
            .writeInt(target);
    }

    public static final class Handler implements IMessageHandler<InputMessage, IMessage> {

        @Override
        public IMessage onMessage(InputMessage message, MessageContext context) {
            Clashweave.server.enqueue(context.getServerHandler().playerEntity, message);
            return null;
        }
    }
}
