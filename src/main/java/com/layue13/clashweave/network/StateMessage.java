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
    public long feedbackFrozen;
    public long budgetNano;
    public long budgetTick;
    public double budgetX;
    public double budgetY;
    public double budgetZ;
    public boolean engaged;
    public boolean sheathed;
    public boolean blocks;
    public boolean confirmed;
    public float yaw;
    public String action = "";
    public String result = "";
    public String definitions = "";

    public StateMessage() {}

    public StateMessage(StateMessage source) {
        entity = source.entity;
        revision = source.revision;
        ack = source.ack;
        tick = source.tick;
        instance = source.instance;
        start = source.start;
        feedbackFrozen = source.feedbackFrozen;
        engaged = source.engaged;
        sheathed = source.sheathed;
        blocks = source.blocks;
        confirmed = source.confirmed;
        yaw = source.yaw;
        action = source.action;
        result = source.result;
        // Observers receive neither the owner's authentication token nor its configuration payload.
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        entity = buffer.readInt();
        revision = buffer.readInt();
        ack = buffer.readInt();
        tick = buffer.readLong();
        session = buffer.readLong();
        instance = buffer.readLong();
        start = buffer.readLong();
        feedbackFrozen = buffer.readLong();
        budgetNano = buffer.readLong();
        budgetTick = buffer.readLong();
        budgetX = buffer.readDouble();
        budgetY = buffer.readDouble();
        budgetZ = buffer.readDouble();
        engaged = buffer.readBoolean();
        sheathed = buffer.readBoolean();
        blocks = buffer.readBoolean();
        confirmed = buffer.readBoolean();
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
            .writeLong(feedbackFrozen)
            .writeLong(budgetNano)
            .writeLong(budgetTick)
            .writeDouble(budgetX)
            .writeDouble(budgetY)
            .writeDouble(budgetZ)
            .writeBoolean(engaged)
            .writeBoolean(sheathed)
            .writeBoolean(blocks)
            .writeBoolean(confirmed)
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
