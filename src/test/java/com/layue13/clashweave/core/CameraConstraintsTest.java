package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class CameraConstraintsTest {

    @Test
    public void sizeLimitsAndFallbackNeverEscapeHardBounds() {
        for (double h : new double[] { .3, .5, .9, 1, 1.5, 1.8, 3 }) {
            double blend = SoulsComposition.sizeBlend(h, .8, 1.5);
            double cap = 45 - 10 * blend, aim = .85 - .35 * blend;
            for (double r : new double[] { 1.5, 2, 2.5, 4, 8 }) {
                SoulsComposition.Pose p = SoulsComposition
                    .solve(r, h * aim, .9, 2.22, 70, 3.5, 5, 8, cap, .45, .68, 1.8, .15);
                assertTrue(p.distance >= 3.5 && p.distance <= 5);
                assertTrue(p.pitch >= 8 && p.pitch <= cap);
                if (h >= 1.5) assertTrue(p.pitch <= 35);
            }
        }
        assertEquals(0, SoulsComposition.sizeBlend(.8, .8, 1.5), 0);
        assertEquals(1, SoulsComposition.sizeBlend(1.5, .8, 1.5), 0);
        assertEquals(.5, SoulsComposition.sizeBlend(1.15, .8, 1.5), 1e-9);
    }

    @Test
    public void openFieldNeverHidesEvenWithPreviousHiddenState() {
        for (double r : new double[] { 1.5, 2.5, 4, 8 }) for (double h : new double[] { .3, 1, 1.8, 3 }) {
            SoulsComposition.Pose p = SoulsComposition.solve(r, h / 2, .9, 2.22, 70, 3.5, 5, 8, 35, .45, .68, 1.8, .15);
            assertFalse(SoulsComposition.hideModel(false, true, p.distance, 1, .2));
            assertFalse(SoulsComposition.hideModel(true, true, p.distance, 1, .2));
        }
    }

    @Test
    public void projectionFovIncludesDynamicScale() {
        for (double f : new double[] { 50, 70, 90, 110 })
            assertEquals(f, SoulsComposition.verticalFov(1 / Math.tan(Math.toRadians(f / 2))), 1e-9);
        assertEquals(70, SoulsComposition.verticalFov(Double.NaN), 0);
    }

    @Test
    public void bearingPredictionConvergesAtNinetyAndRejectsTeleport() {
        CameraBearing b = new CameraBearing();
        double lead = 0;
        for (int i = 0; i < 400; i++) {
            double a = i * .9;
            lead = b.lead(a, Math.sin(Math.toRadians(a)) * 8, Math.cos(Math.toRadians(a)) * 8, .01, .15, .1, 180, 3);
        }
        assertEquals(22.5, lead, .02);
        assertEquals(0, b.lead(20, 100, 100, .01, .15, .1, 180, 3), 0);
        assertEquals(0, b.lead(20, 100, 100, .01, .15, .1, 180, 3), 0);
    }

    @Test
    public void steadyOrbitLagIsLessThanThreeDegrees() {
        for (double speed : new double[] { 45, 90 }) {
            CameraBearing b = new CameraBearing();
            ThirdPersonCamera.Spring s = new ThirdPersonCamera.Spring();
            double yaw = 0;
            for (int i = 0; i < 1000; i++) {
                double goal = i * speed * .01;
                double lead = b
                    .lead(goal, Math.sin(Math.toRadians(goal)), Math.cos(Math.toRadians(goal)), .01, .15, 0, 180, 3);
                yaw = s.step(yaw, goal + lead, .01, .15, 720);
                if (i > 500) assertTrue(Math.abs(yaw - goal) < 3);
            }
        }
    }

    @Test
    public void batchedContinuousPositionsDoNotRemainInTeleportSuppression() {
        CameraBearing b = new CameraBearing();
        double lead = 0, x = 0, z = 8;
        for (int i = 0; i < 800; i++) {
            double angle = i * .9;
            if (i % 30 == 0) {
                x = Math.sin(Math.toRadians(angle)) * 8;
                z = Math.cos(Math.toRadians(angle)) * 8;
            }
            lead = b.lead(angle, x, z, .01, .15, .1, 180, 3);
            if (i > 500) assertTrue(lead > 20 && lead <= 45);
        }
        assertEquals(0, b.lead(20, 100, 100, .01, .15, .1, 180, 3), 0);
    }

    @Test
    public void stalledRenderUsesWallSampleAgeButKeepsIntegrationCapped() {
        CameraBearing b = new CameraBearing();
        for (int i = 0; i < 100; i++) b.lead(i * .9, i * .24, 0, .01, .15, .1, 180, 3);
        // 24 blocks/s continues across a 200ms frame stall: this is not a teleport.
        assertEquals(0, b.lead(107.1, 28.56, 0, .2, .15, .1, 180, 3), 0);
        for (int i = 1; i <= 29; i++)
            assertEquals(0, b.lead(107.1 + i * .9, 28.56 + i * .24, 0, .01, .15, .1, 180, 3), 0);
        // Ordinary raw displacement was not classified as a teleport (which suppresses for .75s).
        assertTrue(b.lead(135, 36, 0, .01, .15, .1, 180, 3) > 0);
        assertEquals(.05, ThirdPersonCamera.dt(.2), 0);
        assertEquals(0, b.lead(107.1, 128.56, 0, .01, .15, .1, 180, 3), 0);
    }
}
