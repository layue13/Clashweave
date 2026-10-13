package com.layue13.clashweave.client.pose;

import static org.junit.Assert.*;

import org.junit.Test;

public class HingeAxisTest {

    @Test
    public void flexSweepHasStableAxisAndContinuousEulerAngles() {
        float[] pivot = new float[3], target = new float[3], pole = { 0, 0, -1 };
        float[] upper = new float[3], lower = new float[3], angles = new float[3], previous = new float[3];
        double[] priorAxis = null;
        for (int degrees = 0; degrees <= 1400; degrees++) {
            target[1] = (float) (.75 * Math.cos(Math.toRadians(degrees / 10.0) / 2));
            TwoBoneIk.solve(pivot, target, pole, .375f, .375f, upper, lower);
            LimbSolver.solveHinged(upper, pole, angles);
            double[] matrix = matrix(angles);
            double[] axis = { matrix[0], matrix[3], matrix[6] };
            if (priorAxis != null) {
                assertTrue(axis[0] * priorAxis[0] + axis[1] * priorAxis[1] + axis[2] * priorAxis[2] > .999);
                for (int i = 0; i < 3; i++) assertTrue(Math.abs(angles[i] - previous[i]) < .01);
            }
            for (int i = 0; i < 3; i++) previous[i] = angles[i];
            priorAxis = axis;
        }
    }

    @Test
    public void frameReconstructsBothAxesIncludingGimbalLockAndParallelPole() {
        for (float[] upper : new float[][] { { 0, 1, 0 }, { 1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 1e-8f, 0, 1 },
            { 1e-8f, 0, -1 }, { .3f, .8f, -.5f } }) {
            for (float[] pole : new float[][] { { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 0 } }) {
                float[] angles = new float[3];
                LimbSolver.solveHinged(upper, pole, angles);
                double[] m = matrix(angles);
                double norm = Math.sqrt(upper[0] * upper[0] + upper[1] * upper[1] + upper[2] * upper[2]);
                double[] u = { upper[0] / norm, upper[1] / norm, upper[2] / norm };
                double[] h = { u[1] * pole[2] - u[2] * pole[1], u[2] * pole[0] - u[0] * pole[2],
                    u[0] * pole[1] - u[1] * pole[0] };
                double hn = Math.sqrt(h[0] * h[0] + h[1] * h[1] + h[2] * h[2]);
                for (int i = 0; i < 3; i++) {
                    assertTrue(Float.isFinite(angles[i]));
                    assertEquals(u[i], m[i * 3 + 1], 1e-5);
                    if (hn > 1e-10) assertEquals(h[i] / hn, m[i * 3], 1e-5);
                }
                assertEquals(
                    1,
                    m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6])
                        + m[2] * (m[3] * m[7] - m[4] * m[6]),
                    1e-6);
            }
        }
    }

    static double[] matrix(float[] angles) {
        double cx = Math.cos(angles[0]), sx = Math.sin(angles[0]);
        double cy = Math.cos(angles[1]), sy = Math.sin(angles[1]);
        double cz = Math.cos(angles[2]), sz = Math.sin(angles[2]);
        return new double[] { cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx, sz * cy, sz * sy * sx + cz * cx,
            sz * sy * cx - cz * sx, -sy, cy * sx, cy * cx };
    }
}
