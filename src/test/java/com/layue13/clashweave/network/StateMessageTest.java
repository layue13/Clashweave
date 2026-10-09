package com.layue13.clashweave.network;

import static org.junit.Assert.*;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class StateMessageTest {

    @Test
    public void inputEncodingPreservesObservedOriginAndRejectsTruncatedPacket() {
        InputMessage input = new InputMessage(123, 456, 7, com.layue13.clashweave.core.Intent.LIGHT, -1);
        input.origin = 9876543210L;
        input.yaw = 32.5f;
        input.pitch = -12.25f;
        ByteBuf buffer = Unpooled.buffer();
        ByteBuf truncated = Unpooled.buffer(28)
            .writeZero(28);
        try {
            input.toBytes(buffer);
            assertEquals(44, buffer.readableBytes());
            InputMessage decoded = new InputMessage();
            decoded.fromBytes(buffer);
            assertEquals(input.origin, decoded.origin);
            assertEquals(input.yaw, decoded.yaw, 0);
            assertEquals(input.pitch, decoded.pitch, 0);
            assertEquals(input.session, decoded.session);
            assertThrows(IllegalArgumentException.class, () -> new InputMessage().fromBytes(truncated));
        } finally {
            buffer.release();
            truncated.release();
        }
    }

    @Test
    public void observerEncodingCannotMutateOrExposeOwnerCredentials() {
        StateMessage owner = new StateMessage();
        owner.session = 123456;
        owner.definitions = "configuration";
        owner.action = "light_2";
        owner.confirmed = true;
        owner.budgetNano = 987654321;
        owner.budgetTick = 321;
        owner.budgetX = 1.25;
        owner.budgetY = 64;
        owner.budgetZ = -2.75;
        StateMessage observer = new StateMessage(owner);
        ByteBuf remoteBuffer = Unpooled.buffer();
        ByteBuf ownerBuffer = Unpooled.buffer();
        try {
            observer.toBytes(remoteBuffer);
            owner.toBytes(ownerBuffer);
            StateMessage remote = new StateMessage();
            StateMessage local = new StateMessage();
            remote.fromBytes(remoteBuffer);
            local.fromBytes(ownerBuffer);
            assertEquals(0, remote.session);
            assertEquals("", remote.definitions);
            assertEquals(123456, local.session);
            assertEquals("configuration", local.definitions);
            assertEquals("light_2", remote.action);
            assertEquals(owner.budgetNano, local.budgetNano);
            assertEquals(owner.budgetTick, local.budgetTick);
            assertEquals(owner.budgetX, local.budgetX, 0);
            assertEquals(owner.budgetY, local.budgetY, 0);
            assertEquals(owner.budgetZ, local.budgetZ, 0);
            assertEquals(0, remote.budgetNano);
            assertTrue(remote.confirmed);
        } finally {
            remoteBuffer.release();
            ownerBuffer.release();
        }
    }
}
