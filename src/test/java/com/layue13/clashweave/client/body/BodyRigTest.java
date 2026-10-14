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

    @Test
    public void channelsSeedFromTheFirstTargetAndThenChaseIndependentOfTheFrameRate() {
        BodyRig a = new BodyRig(), b = new BodyRig();
        for (BodyRig rig : new BodyRig[] { a, b }) {
            rig.beginFrame(1);
            rig.target()[Channels.R_ARM_X] = 1;
            rig.chase(TICK, TICK);
            assertEquals(1, rig.smoothed()[Channels.R_ARM_X], 0);
        }
        // Target jumps to 0: one tick in one frame against four quarter-tick frames.
        long now = TICK;
        a.beginFrame(2);
        a.target()[Channels.R_ARM_X] = 0;
        a.chase(now += TICK, TICK);
        long other = TICK;
        for (int i = 0; i < 4; i++) {
            b.beginFrame(10 + i);
            b.target()[Channels.R_ARM_X] = 0;
            b.chase(other += TICK / 4, TICK);
        }
        assertEquals(a.smoothed()[Channels.R_ARM_X], b.smoothed()[Channels.R_ARM_X], 1e-4f);
        assertTrue(a.smoothed()[Channels.R_ARM_X] > 0 && a.smoothed()[Channels.R_ARM_X] < 1);
    }

    @Test
    public void aSecondModelInTheSameFrameReusesTheStep() {
        BodyRig rig = new BodyRig();
        assertTrue(rig.beginFrame(5));
        assertTrue(rig.needsStep());
        rig.chase(TICK, TICK);
        assertFalse(rig.needsStep());
        assertTrue(rig.beginFrame(6));
    }

    @Test
    public void groundTimersCountTicksOnAndOffTheGround() {
        BodyRig rig = new BodyRig();
        rig.observeGround(true, TICK, TICK);
        rig.observeGround(false, 2 * TICK, TICK);
        rig.observeGround(false, 4 * TICK, TICK);
        assertEquals(3, rig.ticksAirborne(), 1e-4f);
        assertEquals(0, rig.ticksSinceTouchdown(), 0);
        rig.observeGround(true, 5 * TICK, TICK);
        assertEquals(1, rig.ticksSinceTouchdown(), 1e-4f);
        assertEquals(0, rig.ticksAirborne(), 0);
    }
}
