package com.layue13.clashweave.network;

import com.layue13.clashweave.Clashweave;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Client to server, once per connection: the SHA-256 manifest hashes of the content the client loaded. */
public final class HelloMessage implements IMessage {

    public static final int HASH_CHARS = 64;
    public String gameplay = "", presentation = "";

    public HelloMessage() {}

    public HelloMessage(String gameplay, String presentation) {
        this.gameplay = gameplay;
        this.presentation = presentation;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        gameplay = ByteBufUtils.readUTF8String(buffer);
        presentation = ByteBufUtils.readUTF8String(buffer);
        if (gameplay.length() > HASH_CHARS || presentation.length() > HASH_CHARS)
            throw new IllegalArgumentException("Hello size");
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, gameplay);
        ByteBufUtils.writeUTF8String(buffer, presentation);
    }

    public static final class Handler implements IMessageHandler<HelloMessage, IMessage> {

        @Override
        public IMessage onMessage(HelloMessage message, MessageContext context) {
            Clashweave.server.hello(context.getServerHandler().playerEntity, message);
            return null;
        }
    }
}
