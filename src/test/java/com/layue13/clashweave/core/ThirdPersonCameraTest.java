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
    public void compositionRecomputesWithFovAndAspect() {
        assertEquals(9.38, ThirdPersonCamera.pitch(ThirdPersonCamera.Preset.GOLDEN, 70), .04);
        assertEquals(16.37, ThirdPersonCamera.bias(ThirdPersonCamera.Preset.GOLDEN, 70, 16.0 / 9), .04);
        assertTrue(
            ThirdPersonCamera.pitch(ThirdPersonCamera.Preset.GOLDEN, 90)
                > ThirdPersonCamera.pitch(ThirdPersonCamera.Preset.GOLDEN, 70));
        assertTrue(
            ThirdPersonCamera.bias(ThirdPersonCamera.Preset.GOLDEN, 70, 2)
                > ThirdPersonCamera.bias(ThirdPersonCamera.Preset.GOLDEN, 70, 1));
        assertEquals(1.0 / 3, ThirdPersonCamera.fraction(ThirdPersonCamera.Preset.THIRDS), 0);
        assertEquals(0, ThirdPersonCamera.pitch(ThirdPersonCamera.Preset.CENTER, 110), 0);
        assertEquals(0, ThirdPersonCamera.bias(ThirdPersonCamera.Preset.CENTER, 60, 2), 0);
    }

    @Test
    public void targetHeightIsBoundedAroundComposition() {
        double base = ThirdPersonCamera.pitch(ThirdPersonCamera.Preset.GOLDEN, 70);
        assertEquals(base + 8, ThirdPersonCamera.composedPitch(ThirdPersonCamera.Preset.GOLDEN, 70, 80, 8), 0);
        assertEquals(base - 8, ThirdPersonCamera.composedPitch(ThirdPersonCamera.Preset.GOLDEN, 70, -80, 8), 0);
        assertEquals(-20, ThirdPersonCamera.composedPitch(ThirdPersonCamera.Preset.OFF, 70, -20, 8), 0);
    }

    @Test
    public void horizonEnvelopeLimitsHeightWithinScreenTolerance() {
        for (ThirdPersonCamera.Preset p : new ThirdPersonCamera.Preset[] { ThirdPersonCamera.Preset.GOLDEN,
            ThirdPersonCamera.Preset.THIRDS, ThirdPersonCamera.Preset.CENTER }) {
            for (double fov : new double[] { 60, 70, 90 }) for (double height : new double[] { -80, 0, 80 }) {
                double pitch = ThirdPersonCamera.horizonBoundedPitch(p, fov, height, 8, .025);
                double horizon = .5 * (1 - Math.tan(Math.toRadians(pitch)) / Math.tan(Math.toRadians(fov) / 2));
                assertTrue(Math.abs(horizon - ThirdPersonCamera.fraction(p)) <= .0250001);
                assertTrue(Math.abs(pitch - ThirdPersonCamera.pitch(p, fov)) <= 8);
            }
        }
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
