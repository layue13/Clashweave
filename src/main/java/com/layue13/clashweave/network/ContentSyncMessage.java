package com.layue13.clashweave.network;

import java.util.LinkedHashMap;
import java.util.Map;

import com.layue13.clashweave.Clashweave;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server to client, after the server accepted the client's hashes: the negotiated (post-override) rules hash, the
 * canonical overrides it was derived from, and the server's item to weapon table.
 */
public final class ContentSyncMessage implements IMessage {

    public static final int MAX_ITEMS = 64;
    public static final int MAX_TEXT = 256;
    public String effectiveHash = "", overrides = "";
    public Map<String, String> items = new LinkedHashMap<>();

    public ContentSyncMessage() {}

    public ContentSyncMessage(String effectiveHash, String overrides, Map<String, String> items) {
        this.effectiveHash = effectiveHash;
        this.overrides = overrides;
        this.items = new LinkedHashMap<>(items);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        effectiveHash = ByteBufUtils.readUTF8String(buffer);
        overrides = ByteBufUtils.readUTF8String(buffer);
        int count = buffer.readInt();
        if (effectiveHash.length() > HelloMessage.HASH_CHARS || overrides.length() > MAX_TEXT
            || count < 0
            || count > MAX_ITEMS) throw new IllegalArgumentException("Sync size");
        items = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String item = ByteBufUtils.readUTF8String(buffer), weapon = ByteBufUtils.readUTF8String(buffer);
            if (item.length() > MAX_TEXT || weapon.length() > MAX_TEXT) throw new IllegalArgumentException("Sync size");
            items.put(item, weapon);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, effectiveHash);
        ByteBufUtils.writeUTF8String(buffer, overrides);
        buffer.writeInt(items.size());
        for (Map.Entry<String, String> entry : items.entrySet()) {
            ByteBufUtils.writeUTF8String(buffer, entry.getKey());
            ByteBufUtils.writeUTF8String(buffer, entry.getValue());
        }
    }

    public static final class Handler implements IMessageHandler<ContentSyncMessage, IMessage> {

        @Override
        public IMessage onMessage(ContentSyncMessage message, MessageContext context) {
            Clashweave.proxy.receive(message);
            return null;
        }
    }
}
