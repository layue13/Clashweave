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
        return h.choose(yaw, pitch, time, 2, 1, x, 64, 0, 5, 500_000_000, 4);
    }

    @Test
    public void snapshotUsesHistoryNotFutureSamples() {
        FacingHistory h = history();
        h.observe(200_000_000, 180, 0, 0, 64, 0, true, true);
        assertEquals(4, choose(h, 4, 3, 150_000_000, 0).yaw, 0);
        assertEquals(3, choose(h, 4, 3, 150_000_000, 0).pitch, 0);
        assertFalse(choose(h, 180, 0, 150_000_000, 0).accepted);
        assertEquals(2, choose(h, 180, 0, 150_000_000, 0).yaw, 0);
        assertTrue(choose(h, 180, 0, 200_000_000, 0).accepted);
    }

    @Test
    public void forgedAnglesAndOldOrDistantHistoryFallBack() {
        assertEquals("HISTORY_MISMATCH", choose(history(), 180, 0, 150_000_000, 0).reason);
        assertEquals("INVALID_ANGLE", choose(history(), Float.NaN, 0, 150_000_000, 0).reason);
        assertEquals("INVALID_ANGLE", choose(history(), 0, 91, 150_000_000, 0).reason);
        assertEquals("OLD_HISTORY", choose(history(), 0, 0, 700_000_001, 0).reason);
        assertEquals("POSITION_HISTORY", choose(history(), 0, 0, 150_000_000, 5).reason);
        assertEquals("NO_HISTORY", choose(new FacingHistory(), 0, 0, 150_000_000, 0).reason);
    }

    @Test
    public void fastTurnsAndOlderMatchingSampleAreAccepted() {
        FacingHistory h = history();
        h.observe(150_000_000, 90, 3, 0, 64, 0, true, true);
        h.observe(151_000_000, 180, 6, 0, 64, 0, true, true);
        assertTrue(choose(h, 90, 3, 160_000_000, 0).accepted);
        assertTrue(choose(h, 180, 6, 160_000_000, 0).accepted);
        assertFalse(choose(h, 45, 0, 160_000_000, 0).accepted);
        // Position-only packets cannot refresh an old angular sample's age.
        h.observe(700_000_000, 0, 0, 0, 64, 0, false, true);
        assertEquals("OLD_HISTORY", choose(h, 180, 6, 700_000_001, 0).reason);
    }

    @Test
    public void wrappedAnglesAndPositionOnlyPacketsRetainLook() {
        FacingHistory h = new FacingHistory();
        h.observe(100_000_000, 179, 0, 0, 64, 0, true, true);
        h.observe(120_000_000, 0, 0, 1, 64, 0, false, true);
        assertTrue(h.choose(-179, 0, 150_000_000, 0, 0, 1, 64, 0, 5, 500_000_000, 4).accepted);
        assertEquals(2, FacingHistory.difference(-179, 179), 0);
    }

    @Test
    public void receiveTimestampExcludesLaterPacketsEvenIfDispatchIsDelayed() {
        FacingHistory h = history();
        h.requestReceived(7, 150_000_000);
        h.requestReceived(7, 250_000_000);
        h.observe(200_000_000, 90, 0, 0, 64, 0, true, true);
        assertFalse(choose(h, 90, 0, h.requestArrival(7), 0).accepted);
        assertEquals(0, h.requestArrival(7));
    }
}
