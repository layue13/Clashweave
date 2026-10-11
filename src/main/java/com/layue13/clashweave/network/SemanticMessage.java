package com.layue13.clashweave.network;

import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.core.SemanticEvents;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Authority-to-presentation only. There is no server-bound definition/event upload. */
public final class SemanticMessage implements IMessage {

    public long id;
    public SemanticEvents.Event event;

    public SemanticMessage() {}

    public SemanticMessage(long id, SemanticEvents.Event event) {
        this.id = id;
        this.event = event;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(id)
            .writeInt(event.kind.ordinal())
            .writeInt(event.actor)
            .writeInt(event.target)
            .writeLong(event.instance)
            .writeLong(event.tick)
            .writeLong(event.frozen);
        ByteBufUtils.writeUTF8String(buffer, event.action);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        id = buffer.readLong();
        int kind = buffer.readInt();
        if (id < 1 || kind < 0 || kind >= SemanticEvents.Kind.values().length)
            throw new IllegalArgumentException("Semantic event");
        int actor = buffer.readInt(), target = buffer.readInt();
        long instance = buffer.readLong(), tick = buffer.readLong(), frozen = buffer.readLong();
        String action = ByteBufUtils.readUTF8String(buffer);
        if (action.length() > 32) throw new IllegalArgumentException("Action size");
        event = new SemanticEvents.Event(
            SemanticEvents.Kind.values()[kind],
            actor,
            target,
            instance,
            tick,
            frozen,
            action);
    }

    public static final class Handler implements IMessageHandler<SemanticMessage, IMessage> {

        @Override
        public IMessage onMessage(SemanticMessage message, MessageContext context) {
            Clashweave.proxy.receive(message);
            return null;
        }
    }
}
