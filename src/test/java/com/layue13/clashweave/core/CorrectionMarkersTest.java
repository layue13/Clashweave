package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class CorrectionMarkersTest {

    @Test
    public void oneShotAndTimeoutBoundary() {
        CorrectionMarkers m = new CorrectionMarkers();
        assertEquals(0, m.consume(0, 0, 64, 0));
        assertTrue(m.mark(1, 0, 0, 64, 0));
        assertEquals(1, m.consume(250_000_000, 0, 64, 0));
        assertEquals(0, m.consume(250_000_000, 0, 64, 0));
        m.mark(2, 0, 0, 64, 0);
        assertEquals(0, m.consume(250_000_001, 0, 64, 0));
    }

    @Test
    public void sequenceAndPositionDoNotCrossMatch() {
        CorrectionMarkers m = new CorrectionMarkers();
        m.mark(3, 0, 0, 64, 0);
        assertFalse(m.mark(2, 1, 1, 64, 0));
        assertFalse(m.mark(3, 1, 1, 64, 0));
        assertEquals(3, m.consume(2, 0, 64, 0));
        m.mark(4, 3, 0, 64, 0);
        assertEquals(0, m.consume(4, 1, 64, 0));
        assertEquals(0, m.consume(5, 0, 64, 0));
        m.mark(5, 6, 0, 64, 0);
        m.mark(6, 7, 2, 64, 0);
        assertEquals(6, m.consume(8, 2, 64, 0));
    }

    @Test
    public void reconnectHasIndependentSequenceAndCodec() {
        CorrectionMarkers m = new CorrectionMarkers();
        assertFalse(m.mark(1, 0, Double.NaN, 64, 0));
        com.layue13.clashweave.network.CorrectionMessage original = new com.layue13.clashweave.network.CorrectionMessage(
            9,
            1,
            64,
            -2);
        io.netty.buffer.ByteBuf b = io.netty.buffer.Unpooled.buffer();
        original.toBytes(b);
        com.layue13.clashweave.network.CorrectionMessage decoded = new com.layue13.clashweave.network.CorrectionMessage();
        decoded.fromBytes(b);
        assertEquals(9, decoded.sequence);
        assertEquals(-2, decoded.z, 0);
        b.release();
        m.mark(9, 0, 1, 64, -2);
        assertTrue(new CorrectionMarkers().mark(1, 0, 1, 64, -2));
    }
}
