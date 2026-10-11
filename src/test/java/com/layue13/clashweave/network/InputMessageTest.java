package com.layue13.clashweave.network;

import static org.junit.Assert.*;

import org.junit.Test;

import com.layue13.clashweave.core.Intent;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class InputMessageTest {

    @Test
    public void encodedLayoutMatchesReceiptObserverConstants() {
        InputMessage message = new InputMessage(11, 22, 0x12345678, Intent.LIGHT, 44);
        message.origin = 33;
        message.yaw = 90;
        message.pitch = -20;
        ByteBuf wire = Unpooled.buffer();
        try {
            wire.writeByte(InputMessage.DISCRIMINATOR);
            message.toBytes(wire);
            assertEquals(InputMessage.WIRE_BYTES, wire.readableBytes());
            assertEquals(InputMessage.DISCRIMINATOR, wire.readByte());
            assertEquals(InputMessage.BODY_BYTES, wire.readableBytes());
            assertEquals(message.sequence, wire.getInt(InputMessage.SEQUENCE_WIRE_OFFSET));
            assertEquals(
                message.sequence,
                wire.slice(1, InputMessage.BODY_BYTES)
                    .getInt(InputMessage.SEQUENCE_BODY_OFFSET));
            InputMessage decoded = new InputMessage();
            decoded.fromBytes(wire);
            assertEquals(message.session, decoded.session);
            assertEquals(message.stamp, decoded.stamp);
            assertEquals(message.origin, decoded.origin);
            assertEquals(message.yaw, decoded.yaw, 0);
            assertEquals(message.pitch, decoded.pitch, 0);
            assertEquals(message.sequence, decoded.sequence);
            assertEquals(message.kind, decoded.kind);
            assertEquals(message.target, decoded.target);
            assertEquals(0, wire.readableBytes());
        } finally {
            wire.release();
        }
    }
}
