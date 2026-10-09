package com.layue13.clashweave.network;

import com.layue13.clashweave.Clashweave;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Entity snapshot and owner-only session/ack. Definitions travel on session establishment. */
public final class StateMessage implements IMessage {

    public int entity;
    public int revision;
    public int ack;
    public long tick;
    public long session;
    public long instance;
    public long start;
    public boolean engaged;
    public boolean sheathed;
    public boolean blocks;
    public float yaw;
    public String action = "";
    public String result = "";
    public String definitions = "";

    @Override
    public void fromBytes(ByteBuf buffer) {
        entity = buffer.readInt();
        revision = buffer.readInt();
        ack = buffer.readInt();
        tick = buffer.readLong();
        session = buffer.readLong();
        instance = buffer.readLong();
        start = buffer.readLong();
        engaged = buffer.readBoolean();
        sheathed = buffer.readBoolean();
        blocks = buffer.readBoolean();
        yaw = buffer.readFloat();
        action = ByteBufUtils.readUTF8String(buffer);
        result = ByteBufUtils.readUTF8String(buffer);
        definitions = ByteBufUtils.readUTF8String(buffer);
        if (action.length() > 32 || result.length() > 80 || definitions.length() > 24000) {
            throw new IllegalArgumentException("Snapshot size");
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(entity)
            .writeInt(revision)
            .writeInt(ack)
            .writeLong(tick)
            .writeLong(session)
            .writeLong(instance)
            .writeLong(start)
            .writeBoolean(engaged)
            .writeBoolean(sheathed)
            .writeBoolean(blocks)
            .writeFloat(yaw);
        ByteBufUtils.writeUTF8String(buffer, action);
        ByteBufUtils.writeUTF8String(buffer, result);
        ByteBufUtils.writeUTF8String(buffer, definitions);
    }

    public static final class Handler implements IMessageHandler<StateMessage, IMessage> {

        @Override
        public IMessage onMessage(StateMessage message, MessageContext context) {
            Clashweave.proxy.receive(message);
            return null;
        }
    }
}
