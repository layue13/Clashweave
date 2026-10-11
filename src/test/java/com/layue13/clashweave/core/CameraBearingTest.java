package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class CameraBearingTest {

    @Test
    public void delayedSampleSmallerThanVelocityPredictionIsNotJump() {
        CameraBearing b = new CameraBearing();
        b.lead(0, 0, 8, .01, .15, .1, 180, 3);
        // Establish 39.6 blocks/s without reflection or a discontinuity.
        assertTrue(b.lead(.9, .396, 8, .01, .15, .1, 180, 3) > 0);
        assertTrue(b.lead(8.352, .396, 8, .0828, .15, .1, 180, 3) > 0);
        // Sample age .101s, actual displacement .613, render interval 18.2ms,
        // bearing change 1.012695 degrees: the old residual is 3.3866 blocks.
        assertTrue(b.lead(9.364695, 1.009, 8, .0182, .15, .1, 180, 3) > 0);
        assertTrue(b.lead(10.264695, 1.009, 8, .01, .15, .1, 180, 3) > 0);
    }

    @Test
    public void teleportsSuppressRegardlessOfPreviousSampleSpeed() {
        for (double speed : new double[] { 0, 12.57, 39.6, 500 }) {
            for (double distance : new double[] { 5, 10, 30 }) {
                for (int direction = 0; direction < 4; direction++) {
                    CameraBearing b = new CameraBearing();
                    double x = speed * .001;
                    b.lead(0, 0, 8, .01, .15, .1, 180, 3);
                    assertTrue(b.lead(.09, x, 8, .001, .15, .1, 180, 3) > 0);
                    assertTrue(b.lead(7.542, x, 8, .0828, .15, .1, 180, 3) > 0);
                    double nextX = x + (direction == 0 ? distance : direction == 1 ? -distance : 0);
                    double nextZ = 8 + (direction == 2 ? distance : direction == 3 ? -distance : 0);
                    String context = "speed=" + speed + " distance=" + distance + " direction=" + direction;
                    assertEquals(context, 0, b.lead(8.554695, nextX, nextZ, .0182, .15, .1, 180, 3), 0);
                    assertEquals(context, 0, b.lead(9.454695, nextX, nextZ, .01, .15, .1, 180, 3), 0);
                    assertTrue(context, b.lead(76.954695, nextX, nextZ, .75, .15, .1, 180, 3, 1, .3) > 0);
                }
            }
        }
    }

    @Test
    public void batchedOrbitSamplesDoNotSuppressPrediction() {
        CameraBearing b = new CameraBearing();
        ThirdPersonCamera.Filter filter = new ThirdPersonCamera.Filter();
        double angle = 0, x = 0, z = 8;
        b.lead(0, x, z, .01, .15, .1, 180, 3);
        // 90 degrees/s on radius 8: ten unchanged render samples, then two
        // adjacent batches, each containing twice a 50ms server-tick displacement.
        for (int i = 0; i < 1200; i++) {
            int frame = i % 12;
            double dt = frame < 10 ? .018 : .01;
            if (frame >= 10) {
                angle += 9;
                x = -8 * Math.sin(Math.toRadians(angle));
                z = 8 * Math.cos(Math.toRadians(angle));
            }
            filter.step(x, 0, z, dt, .1);
            double goal = Math.toDegrees(Math.atan2(-filter.x, filter.z));
            double lead = b.lead(goal, x, z, dt, .15, .1, 180, 3);
            if (i >= 10) assertTrue("frame=" + i + " lead=" + lead, lead > 0);
            assertTrue(Math.abs(lead) <= 45);
        }
    }

    @Test
    public void displacementThresholdRemainsStrict() {
        for (double distance : new double[] { 3, 3.0001 }) {
            CameraBearing b = new CameraBearing();
            b.lead(0, 0, 8, .01, .15, .1, 180, 3);
            double lead = b.lead(.9, distance, 8, .01, .15, .1, 180, 3);
            if (distance == 3) assertTrue(lead > 0);
            else assertEquals(0, lead, 0);
        }
    }

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
