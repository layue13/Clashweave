package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class CameraBearingTest {

    @Test
    public void uncappedIntervalDoesNotInflateVelocity() {
        CameraBearing b = new CameraBearing();
        b.lead(0, 0, 8, .01, .15, .1, 180, 3, 1, .3);
        for (int i = 1; i <= 100; i++) {
            double speed = b.lead(i * 36, 0, 8, .4, .15, .1, 180, 3, 1, .3) / .25;
            assertTrue(speed > 0 && speed <= 90.001);
        }
        assertEquals(.05, ThirdPersonCamera.dt(.2), 0);
    }

    @Test
    public void stallThresholdAndRecoveryUseWallSeconds() {
        CameraBearing b = new CameraBearing();
        b.lead(0, 0, 8, .01, .15, .1, 180, 3);
        assertTrue(b.lead(9, 0, 8, .1, .15, .1, 180, 3) > 0);
        assertEquals(0, b.lead(18.001, 0, 8, .10001, .15, .1, 180, 3), 0);
        assertEquals(0, b.lead(25.201, 0, 8, .08, .15, .1, 180, 3), 0);
        assertEquals(0, b.lead(32.401, 0, 8, .08, .15, .1, 180, 3), 0);
        assertEquals(0, b.lead(39.601, 0, 8, .08, .15, .1, 180, 3), 0);
        assertTrue(b.lead(46.801, 0, 8, .08, .15, .1, 180, 3) > 0);
    }

    @Test
    public void initialSampleDoesNotIntroduceSwitchRecovery() {
        CameraBearing b = new CameraBearing();
        assertEquals(0, b.lead(0, 0, 8, .01, .15, .1, 180, 3), 0);
        assertTrue(b.lead(.9, 0, 8, .01, .15, .1, 180, 3) > 0);
        b.clear();
        assertEquals(0, b.lead(30, 0, 8, .01, .15, .1, 180, 3), 0);
        assertEquals(0, b.lead(30.9, 0, 8, .01, .15, .1, 180, 3), 0);
    }

    @Test
    public void invalidIntervalsCannotCreateVelocity() {
        CameraBearing b = new CameraBearing();
        for (double t : new double[] { 0, -1, Double.NaN, Double.POSITIVE_INFINITY })
            assertEquals(0, b.lead(0, 0, 8, t, .15, .1, 180, 3), 0);
    }

    @Test
    public void orbitReplay() {
        replay(false, false);
    }

    @Test
    public void pauseReplay() {
        replay(true, false);
    }

    @Test
    public void switchReplay() {
        replay(false, true);
    }

    @Test
    public void switchPauseReplay() {
        replay(true, true);
    }

    private static void replay(boolean pause, boolean changeTarget) {
        CameraBearing bearing = new CameraBearing();
        ThirdPersonCamera.Filter filter = new ThirdPersonCamera.Filter();
        ThirdPersonCamera.Spring spring = new ThirdPersonCamera.Spring();
        double time = 0, yaw = 0, steady = 0, minimum = Double.POSITIVE_INFINITY, peak = 0;
        for (int i = 0; i < 1000; i++) {
            double dt = i == 500 && pause ? .2 : .01;
            time += dt;
            double angle = time * 90 + (changeTarget && i >= 500 ? 30 : 0);
            double x = -8 * Math.sin(Math.toRadians(angle)), z = 8 * Math.cos(Math.toRadians(angle));
            filter.step(x, 0, z, dt, .1);
            double goal = Math.toDegrees(Math.atan2(-filter.x, filter.z));
            if (i == 500 && changeTarget) bearing.clear();
            double lead = bearing.lead(goal, x, z, dt, .15, .1, 180, 3);
            yaw = spring.step(yaw, yaw + FacingHistory.difference((float) goal, (float) yaw) + lead, dt, .15, 720);
            double error = FacingHistory.difference((float) angle, (float) yaw);
            if (i >= 450 && i < 500) steady += error / 50;
            if (i >= 500) {
                minimum = Math.min(minimum, error);
                peak = Math.max(peak, Math.abs(lead / .25));
            }
        }
        String context = "pause=" + pause + " switch=" + changeTarget;
        System.out
            .println("CAMERA_REPLAY " + context + " peak=" + peak + " minimumError=" + minimum + " steady=" + steady);
        assertTrue(context + " velocity=" + peak, peak <= 99);
        assertTrue(context + " error=" + minimum + " steady=" + steady, minimum >= steady - 1);
    }
}
