package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class LockSupportTest {

    private float aim(float yaw, double target, double cone, double maximum) {
        return LockAssist.yaw(yaw, -Math.sin(Math.toRadians(target)), Math.cos(Math.toRadians(target)), cone, maximum);
    }

    @Test
    public void assistanceHasConeCapWrapAndSafeDegenerateBehavior() {
        assertEquals(10, aim(0, 10, 45, 15), .0001);
        assertEquals(15, aim(0, 15, 45, 15), .0001);
        assertEquals(15, aim(0, 30, 45, 15), .0001);
        assertEquals(15, aim(0, 45, 45, 15), .0001);
        assertEquals(0, aim(0, 45.01, 45, 15), .0001);
        assertEquals(0, aim(0, 60, 45, 15), .0001);
        assertEquals(181, aim(179, -179, 45, 15), .0001);
        assertEquals(-181, aim(-179, 179, 45, 15), .0001);
        assertEquals(12, LockAssist.yaw(12, 0, 0, 45, 15), 0);
        assertEquals(12, LockAssist.yaw(12, Double.NaN, 1, 45, 15), 0);
        assertTrue(Float.isNaN(LockAssist.yaw(Float.NaN, 0, 1, 45, 15)));
        assertEquals(0, aim(0, 30, 45, 0), 0);
    }

    @Test
    public void markerRequiresArmedLiveInRangeAuthoritativeTarget() {
        assertTrue(LockAssist.valid(true, 7, true, 20, 20));
        assertFalse(LockAssist.valid(false, 7, true, 5, 20));
        assertFalse(LockAssist.valid(true, -1, true, 5, 20));
        assertFalse(LockAssist.valid(true, 7, false, 5, 20));
        assertFalse(LockAssist.valid(true, 7, true, 20.01, 20));
        assertFalse(LockAssist.valid(true, 7, true, Double.NaN, 20));
    }

    @Test
    public void weakYieldsImmediatelyAndRespectsActionDeadZoneBehindAndClockCaps() {
        LockFollow f = new LockFollow();
        LockFollow.Settings s = new LockFollow.Settings();
        LockFollow.Result r = f.step(LockFollow.Mode.WEAK, 1_000_000_000, .05, 0, 0, 30, 30, true, true, true, s);
        assertEquals(9, r.yaw, .0001);
        assertEquals(9, r.pitch, .0001);
        f.mouse(1_000_000_001, 4, s);
        r = f.step(LockFollow.Mode.WEAK, 1_000_000_001, .05, 9, 9, 30, 30, true, true, true, s);
        assertEquals("MOUSE", r.reason);
        assertEquals(9, r.yaw, 0);
        assertEquals(
            "COMMITTED",
            f.step(LockFollow.Mode.WEAK, 2_000_000_000, .05, 0, 0, 30, 0, true, true, false, s).reason);
        assertEquals(
            "BEHIND",
            f.step(LockFollow.Mode.WEAK, 2_000_000_000, .05, 0, 0, 180, 0, true, true, true, s).reason);
        assertEquals(0, f.step(LockFollow.Mode.WEAK, 2_000_000_000, .05, 0, 0, 8, 8, true, true, true, s).yaw, 0);
        assertEquals(9, f.step(LockFollow.Mode.WEAK, 2_000_000_000, 10, 0, 0, 60, 0, true, true, true, s).yaw, .0001);
    }

    @Test
    public void strongOffsetsDecayAndF5ModesTransitionWithCapsDuringCommitment() {
        LockFollow f = new LockFollow();
        LockFollow.Settings s = new LockFollow.Settings();
        f.mouse(1_000_000_000, 90, s);
        LockFollow.Result r = f.step(LockFollow.Mode.STRONG, 1_000_000_000, .05, 0, 0, 90, 90, true, true, false, s);
        assertEquals(30, r.offset, 0);
        assertEquals(36, r.yaw, .0001);
        assertEquals(36, r.pitch, .0001);
        r = f.step(LockFollow.Mode.STRONG, 1_050_000_000, .05, 36, 36, 90, 90, true, true, false, s);
        assertEquals(24, r.offset, .0001);
        assertEquals(72, r.yaw, .0001);
        assertEquals(60, r.pitch, .0001);
        r = f.step(LockFollow.Mode.WEAK, 2_000_000_000, .05, 0, 0, 90, 0, true, true, true, s);
        assertEquals(9, r.yaw, .0001);
        r = f.step(LockFollow.Mode.STRONG, 2_050_000_000, .05, r.yaw, 0, -170, -90, true, true, false, s);
        assertTrue(Math.abs(FacingHistory.difference(r.yaw, 9)) <= 36.0001);
        assertEquals(-36, r.pitch, .0001);
    }

    @Test
    public void followStopsForGuiDisabledAndOcclusionAndClearsOffset() {
        LockFollow f = new LockFollow();
        LockFollow.Settings s = new LockFollow.Settings();
        assertEquals("INACTIVE", f.step(LockFollow.Mode.STRONG, 0, .05, 0, 0, 90, 0, true, false, true, s).reason);
        assertEquals("OFF", f.step(LockFollow.Mode.OFF, 0, .05, 0, 0, 90, 0, true, true, true, s).reason);
        assertEquals("STRONG", f.step(LockFollow.Mode.STRONG, 0, .05, 0, 0, 90, 0, false, true, true, s).reason);
        assertEquals(
            "OCCLUDED",
            f.step(LockFollow.Mode.STRONG, 1_000_000_000, .05, 0, 0, 90, 0, false, true, true, s).reason);
        f.mouse(1_000_000_000, 15, s);
        f.clear();
        assertEquals(0, f.step(LockFollow.Mode.STRONG, 2_000_000_000, .05, 0, 0, 0, 0, true, true, true, s).offset, 0);
    }

    @Test
    public void automaticViewIsOneShotAndManualF5Wins() {
        LockView view = new LockView();
        assertEquals(1, view.update(true, 0, true, true));
        assertEquals(1, view.update(true, 1, true, true));
        assertEquals(0, view.update(false, 1, true, true));
        assertEquals(1, view.update(true, 2, true, true));
        assertEquals(0, view.update(true, 0, true, true));
        assertEquals(0, view.update(true, 0, true, true));
        assertEquals(0, view.update(false, 0, true, true));
        assertEquals(2, view.update(true, 2, false, true));
        assertEquals(2, view.update(false, 2, false, true));
        assertEquals(1, view.update(true, 0, true, false));
        assertEquals(1, view.update(false, 1, true, false));
        view.clear();
        assertEquals(2, view.update(false, 2, true, true));
    }

    @Test
    public void strongMouseCannotAddUnboundedTurnOnTopOfFollowCredit() {
        LockFollow f = new LockFollow();
        LockFollow.Settings s = new LockFollow.Settings();
        LockFollow.Result first = f.step(LockFollow.Mode.STRONG, 0, .05, 0, 0, 170, 0, true, true, false, s);
        f.mouse(50_000_000, 90, s);
        LockFollow.Result next = f
            .step(LockFollow.Mode.STRONG, 50_000_000, .05, first.yaw + 90, 80, 170, 0, true, true, false, s);
        assertEquals(30, next.offset, 0);
        assertTrue(Math.abs(FacingHistory.difference(next.yaw, first.yaw)) <= 36.0001);
        assertEquals(0, next.pitch, .0001);
    }

    @Test
    public void f5RoundTripWithinOneTickStillPreventsAutomaticRestore() {
        LockView view = new LockView();
        assertEquals(1, view.update(true, 0, true, true));
        view.manualChange();
        assertEquals(1, view.update(true, 1, true, true));
        assertEquals(1, view.update(false, 1, true, true));
    }
}
