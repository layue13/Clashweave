package com.layue13.clashweave.client.pose;

import static org.junit.Assert.*;

import org.junit.Test;

public class TwoBoneIkTest {

    @Test
    public void reachableTargetsReconstructAndBendTowardPole() {
        float[] pivot = { .1f, -.2f, .3f }, pole = { 0, 0, 1 };
        float[] upper = new float[3], lower = new float[3];
        for (int i = 1; i < 100; i++) {
            float[] target = { pivot[0] + i * .006f, pivot[1] + .1f, pivot[2] };
            TwoBoneIk.solve(pivot, target, pole, .375f, .375f, upper, lower);
            for (int axis = 0; axis < 3; axis++)
                assertEquals(target[axis], pivot[axis] + .375f * (upper[axis] + lower[axis]), 1e-4);
            assertTrue(upper[2] > 0);
            unit(upper);
            unit(lower);
        }
    }

    @Test
    public void unequalSegmentsReconstructAcrossTheirReachableInterval() {
        float[] pivot = { -.2f, .3f, .1f }, pole = { 1, 0, 0 };
        float[] upper = new float[3], lower = new float[3];
        for (int i = 0; i <= 100; i++) {
            float radius = .25f + .5f * i / 100;
            float[] target = { pivot[0], pivot[1], pivot[2] - radius };
            TwoBoneIk.solve(pivot, target, pole, .5f, .25f, upper, lower);
            unit(upper);
            unit(lower);
            for (int axis = 0; axis < 3; axis++)
                assertEquals(target[axis], pivot[axis] + .5f * upper[axis] + .25f * lower[axis], 1e-4);
        }
    }

    @Test
    public void unreachableTargetsExtendAlongRay() {
        float[] upper = new float[3], lower = new float[3];
        TwoBoneIk.solve(new float[3], new float[] { 2, 0, 0 }, new float[] { 0, 1, 0 }, .375f, .375f, upper, lower);
        assertArrayEquals(new float[] { 1, 0, 0 }, upper, 1e-6f);
        assertArrayEquals(upper, lower, 1e-6f);
    }

    @Test
    public void degeneraciesAreFiniteUnitAndDeterministic() {
        float[] upper = new float[3], lower = new float[3];
        for (float[] target : new float[][] { { 0, 0, 0 }, { 0, .75f, 0 }, { 0, .000001f, 0 }, { 0, .25f, 0 } }) {
            for (float[] pole : new float[][] { { 0, 1, 0 }, { 0, 0, 0 }, { 0, 1, 1e-12f } }) {
                for (float l2 : new float[] { .375f, .125f, .625f }) {
                    TwoBoneIk.solve(new float[3], target, pole, .375f, l2, upper, lower);
                    unit(upper);
                    unit(lower);
                    float radius = Math.max(Math.abs(.375f - l2), Math.min(.375f + l2, target[1]));
                    assertEquals(radius, .375f * upper[1] + l2 * lower[1], 1e-5);
                    assertEquals(0, .375f * upper[0] + l2 * lower[0], 1e-5);
                    assertEquals(0, .375f * upper[2] + l2 * lower[2], 1e-5);
                }
            }
        }
    }

    private static void unit(float[] dir) {
        double squared = 0;
        for (float v : dir) {
            assertTrue(Float.isFinite(v));
            squared += v * v;
        }
        assertEquals(1, squared, 1e-5);
    }
}
