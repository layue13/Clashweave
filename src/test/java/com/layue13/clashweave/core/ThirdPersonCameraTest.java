package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class ThirdPersonCameraTest {

    @Test
    public void thirdPersonHandoffPreservesFirstPersonMouseGraceAndCurrentView() {
        LockFollow f = new LockFollow();
        LockFollow.Settings settings = new LockFollow.Settings();
        f.step(LockFollow.Mode.STRONG, 0, .05, 0, 0, 90, 0, true, true, true, settings);
        f.noteMouse(50_000_000);
        f.clear();
        assertEquals(
            "MOUSE",
            f.step(LockFollow.Mode.WEAK, 100_000_000, .05, 45, 0, 90, 0, true, true, true, settings).reason);
        LockFollow.Result next = f
            .step(LockFollow.Mode.STRONG, 400_000_000, .05, 120, 0, 90, 0, true, true, true, settings);
        assertEquals(90, next.yaw, 0);
    }

    @Test
    public void springConvergesWithoutOvershootAndRespectsSpeed() {
        ThirdPersonCamera.Spring s = new ThirdPersonCamera.Spring();
        double v = 0;
        for (int i = 0; i < 300; i++) {
            double next = s.step(v, 90, .016, .15, 720);
            assertTrue(next >= v && next <= 90);
            assertTrue(next - v <= 720 * .016 + .00001);
            v = next;
        }
        assertEquals(90, v, .00001);
    }

    @Test
    public void springFrameRateAndCreditCap() {
        double[] ends = new double[3];
        double[] rates = { .01, .02, .05 };
        for (int i = 0; i < 3; i++) {
            ThirdPersonCamera.Spring s = new ThirdPersonCamera.Spring();
            for (int k = 0; k < (int) (2 / rates[i]); k++) ends[i] = s.step(ends[i], 45, rates[i], .15, 720);
        }
        assertEquals(ends[0], ends[2], .000001);
        assertEquals(
            new ThirdPersonCamera.Spring().step(0, 180, .05, .15, 10),
            new ThirdPersonCamera.Spring().step(0, 180, 2, .15, 10),
            0);
        assertEquals(0, new ThirdPersonCamera.Spring().step(0, 90, Double.NaN, .15, 720), 0);
    }

    @Test
    public void compositionHasOnlyOffAndSoulsWithLegacyMigration() {
        assertEquals(2, ThirdPersonCamera.Preset.values().length);
        assertEquals(ThirdPersonCamera.Preset.OFF, ThirdPersonCamera.parse("off"));
        for (String old : new String[] { "GOLDEN", "THIRDS", "CENTER", "unknown", "SOULS" })
            assertEquals(ThirdPersonCamera.Preset.SOULS, ThirdPersonCamera.parse(old));
    }

    @Test
    public void compositionBoundsAndFovAreExplicit() {
        for (double fov : new double[] { 60, 70, 90 }) for (double range : new double[] { 2, 4, 8 }) {
            SoulsComposition.Pose p = SoulsComposition.solve(range, .9, .9, 2.22, fov, 4.2, 1.2, 10, 12, .38, .65);
            assertTrue(p.distance >= 3 && p.distance <= 5.4);
            assertTrue(p.pitch >= -2 && p.pitch <= 22);
            assertTrue(Double.isFinite(SoulsComposition.screenY(range, .9, 2.22, p.distance, p.pitch, fov)));
        }
        assertTrue(
            SoulsComposition.screenY(0, .9, 2.22, 4.2, 10, 60) > SoulsComposition.screenY(0, .9, 2.22, 4.2, 10, 90));
    }

    @Test
    public void projectedBodyOnlyCoversFiniteVisiblePoints() {
        assertTrue(SoulsComposition.covered(.4, .5, .6, .8, .5, .65, .99));
        assertFalse(SoulsComposition.covered(.4, .5, .6, .8, .5, .38, .99));
        assertFalse(SoulsComposition.covered(.4, .5, .6, .8, .5, .65, 1.1));
        assertFalse(SoulsComposition.covered(.4, .5, .6, .8, Double.NaN, .65, .99));
        assertTrue(SoulsComposition.covered(.4, .5, .6, .8, .4, .5, 0));
    }

    @Test
    public void exponentialFilterFrameRateAndAttenuation() {
        ThirdPersonCamera.Filter a = new ThirdPersonCamera.Filter(), b = new ThirdPersonCamera.Filter();
        a.step(0, 0, 0, 0, .1);
        b.step(0, 0, 0, 0, .1);
        for (int i = 0; i < 10; i++) a.step(1, 2, 3, .01, .1);
        for (int i = 0; i < 5; i++) b.step(1, 2, 3, .02, .1);
        assertEquals(a.x, b.x, .0000001);
        assertEquals(1 - Math.exp(-1), a.x, .0000001);
        assertTrue(a.x < 1);
        a.clear();
        a.step(3, 2, 1, .01, .1);
        assertEquals(3, a.x, 0);
    }

    @Test
    public void flickWindowCooldownAndTwentySmallMotions() {
        ThirdPersonCamera.Flick f = new ThirdPersonCamera.Flick();
        long w = 150_000_000, c = 400_000_000;
        for (int i = 0; i < 20; i++) {
            assertEquals(0, f.add(i * 200_000_000L, 0, w, 25, c));
            assertEquals(0, f.add(i * 200_000_000L, 2, w, 25, c));
        }
        assertEquals(0, f.add(5_000_000_000L, 15, w, 25, c));
        assertEquals(1, f.add(5_100_000_000L, 15, w, 25, c));
        assertEquals(0, f.add(5_200_000_000L, -30, w, 25, c));
        assertEquals(-1, f.add(5_600_000_000L, -30, w, 25, c));
        assertEquals(0, f.add(6_000_000_000L, 15, w, 25, c));
        assertEquals(0, f.add(6_200_000_000L, 15, w, 25, c));
    }

    @Test
    public void switchUsesSideRelativeToLockedTarget() {
        assertTrue(Double.isFinite(ThirdPersonCamera.switchScore(0, -1, 2, 1, 2)));
        assertEquals(Double.POSITIVE_INFINITY, ThirdPersonCamera.switchScore(0, -1, 2, -1, 2), 0);
        assertTrue(Double.isFinite(ThirdPersonCamera.switchScore(179, .03, -2, 1, 2)));
        assertEquals(Double.POSITIVE_INFINITY, ThirdPersonCamera.switchScore(0, 0, 0, 1, 0), 0);
    }
}
