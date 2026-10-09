package com.layue13.clashweave.network;

import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.core.Intent;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class InputMessage implements IMessage {

    public long session;
    public long stamp;
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
        if (buffer.readableBytes() != 28) throw new IllegalArgumentException("Input size");
        session = buffer.readLong();
        stamp = buffer.readLong();
        sequence = buffer.readInt();
        kind = buffer.readInt();
        target = buffer.readInt();
        if (kind < -1 || kind >= Intent.values().length) throw new IllegalArgumentException("Input intent");
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(session)
            .writeLong(stamp)
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
