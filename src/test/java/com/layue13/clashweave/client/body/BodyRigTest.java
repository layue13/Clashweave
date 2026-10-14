package com.layue13.clashweave.client.body;

import static org.junit.Assert.*;

import org.junit.Test;

public class BodyRigTest {

    private static final long TICK = 50_000_000L;

    private static float run(BodyRig rig, boolean wanted, int frames, long frameNanos, long start) {
        long now = start;
        for (int i = 0; i < frames; i++) {
            now += frameNanos;
            rig.advance(wanted, now, TICK);
        }
        return rig.takeover();
    }

    @Test
    public void takeoverRisesToExactlyOneAndFallsToExactlyZero() {
        BodyRig rig = new BodyRig();
        rig.advance(true, TICK, TICK);
        assertEquals(1, run(rig, true, 60, TICK / 2, TICK), 0);
        assertEquals(0, run(rig, false, 120, TICK / 2, TICK * 100), 0);
    }

    @Test
    public void takeoverDoesNotDependOnTheFrameRate() {
        BodyRig slow = new BodyRig(), fast = new BodyRig();
        slow.advance(true, TICK, TICK);
        fast.advance(true, TICK, TICK);
        // Two ticks of wall time: 2 frames of 1 tick against 8 frames of a quarter tick.
        float a = run(slow, true, 2, TICK, TICK);
        float b = run(fast, true, 8, TICK / 4, TICK);
        assertEquals(a, b, 1e-4f);
        assertTrue(a > 0 && a < 1);
    }

    @Test
    public void aFrameWithNoElapsedTimeMovesNothing() {
        BodyRig rig = new BodyRig();
        rig.advance(true, TICK, TICK);
        rig.advance(true, 2 * TICK, TICK);
        float before = rig.takeover();
        rig.advance(true, 2 * TICK, TICK);
        assertEquals(before, rig.takeover(), 0);
    }
}
