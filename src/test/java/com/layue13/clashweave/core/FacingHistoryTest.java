package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class FacingHistoryTest {

    private FacingHistory history() {
        FacingHistory h = new FacingHistory();
        h.observe(100_000_000, 0, 0, 0, 64, 0, true, true);
        return h;
    }

    private FacingHistory.Result choose(FacingHistory h, float yaw, float pitch, long time, double x) {
        return h.choose(yaw, pitch, time, 2, 1, x, 64, 0, 30, 5, 500_000_000, 4);
    }

    @Test
    public void snapshotUsesHistoryNotFutureSamples() {
        FacingHistory h = history();
        h.observe(200_000_000, 180, 0, 0, 64, 0, true, true);
        assertEquals(15, choose(h, 15, 3, 150_000_000, 0).yaw, 0);
        assertFalse(choose(h, 180, 0, 150_000_000, 0).accepted);
        assertEquals(2, choose(h, 180, 0, 150_000_000, 0).yaw, 0);
        assertEquals("HISTORY_TURN_RATE", choose(h, 180, 0, 200_000_000, 0).reason);
    }

    @Test
    public void forgedAnglesAndOldOrDistantHistoryFallBack() {
        assertEquals("TURN_RATE", choose(history(), 180, 0, 150_000_000, 0).reason);
        assertEquals("INVALID_ANGLE", choose(history(), Float.NaN, 0, 150_000_000, 0).reason);
        assertEquals("INVALID_ANGLE", choose(history(), 0, 91, 150_000_000, 0).reason);
        assertEquals("OLD_HISTORY", choose(history(), 0, 0, 700_000_001, 0).reason);
        assertEquals("POSITION_HISTORY", choose(history(), 0, 0, 150_000_000, 5).reason);
        assertEquals("NO_HISTORY", choose(new FacingHistory(), 0, 0, 150_000_000, 0).reason);
    }

    @Test
    public void wrappedAnglesAndPositionOnlyPacketsRetainLook() {
        FacingHistory h = new FacingHistory();
        h.observe(100_000_000, 179, 0, 0, 64, 0, true, true);
        h.observe(120_000_000, 0, 0, 1, 64, 0, false, true);
        assertTrue(h.choose(-179, 0, 150_000_000, 0, 0, 1, 64, 0, 30, 5, 500_000_000, 4).accepted);
        assertEquals(2, FacingHistory.difference(-179, 179), 0);
    }
}
