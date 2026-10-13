package com.layue13.clashweave.client.pose;

import static org.junit.Assert.*;

import org.junit.Test;

public class LimbSolverTest {

    @Test
    public void basisPointsRoundTripAndHavePositiveDeterminant() {
        float[] out = new float[3];
        float[][] points = { { .293f, 0, 1.289f }, { 0, 0, 0 }, { 1, -2, 3 }, { -.4f, .7f, .9f } };
        for (float[] point : points) {
            LimbSolver.bodyToBipedPoint(point[0], point[1], point[2], out);
            assertArrayEquals(
                point,
                new float[] { -out[0] * .9375f, -out[2] * .9375f, (1.5f - out[1]) * .9375f },
                1e-6f);
            LimbSolver.bodyToBipedDir(point[0], point[1], point[2], out);
            assertArrayEquals(point, new float[] { -out[0], -out[2], -out[1] }, 0);
        }
        float[] x = new float[3], y = new float[3], z = new float[3];
        LimbSolver.bodyToBipedDir(1, 0, 0, x);
        LimbSolver.bodyToBipedDir(0, 1, 0, y);
        LimbSolver.bodyToBipedDir(0, 0, 1, z);
        double determinant = x[0] * (y[1] * z[2] - y[2] * z[1]) - y[0] * (x[1] * z[2] - x[2] * z[1])
            + z[0] * (x[1] * y[2] - x[2] * y[1]);
        assertEquals(1, determinant, 0);
        LimbSolver.bodyToBipedPoint(.293f, 0, 1.289f, out);
        assertEquals(-5 / 16f, out[0], 1e-4);
        assertEquals(2 / 16f, out[1], 1e-4);
        LimbSolver.bodyToBipedPoint(0, 0, 1.875f, out);
        assertEquals(-.5f, out[1], 0);
    }

    @Test
    public void everyBakedLimbMatchesDirectionAndFullSwingMatrix() throws Exception {
        TrackData data = TrackDataTest.realData();
        Pose pose = new Pose();
        int samples = 0;
        for (String id : data.actionIds()) {
            Track track = data.track(id);
            for (double time = 0; time <= track.duration(); time += .5) {
                track.sample(time, pose);
                for (float[] dir : new float[][] { pose.armR, pose.armL, pose.legR, pose.legL }) verifySwing(dir);
                samples++;
            }
        }
        System.out.println("C2A_SWING samples=" + samples + " limbs=" + samples * 4);
    }

    @Test
    public void axisAntipodeAndNearGimbalDirectionsAreStable() {
        for (float[] direction : new float[][] { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
            { 0, 0, -1 }, { 1e-8f, -1e-8f, 1 }, { 1e-8f, 1e-8f, -1 }, { 1, 1, 0 }, { -1, 1, 0 }, { 1e-8f, 0, 1 },
            { 0, 1e-8f, 1 } }) verifySwing(direction);
        // Equal X/Z components near Biped -Y make the swing's Euler beta approach +/- pi/2.
        for (float side : new float[] { -1, 1 })
            for (float epsilon : new float[] { 1e-2f, 1e-5f, 1e-8f, 1e-12f, 1e-15f })
                verifySwing(new float[] { epsilon, side * epsilon, 1 });
    }

    private static void verifySwing(float[] body) {
        float[] angles = new float[3];
        LimbSolver.solveLimb(body[0], body[1], body[2], angles);
        double length = Math.sqrt((double) body[0] * body[0] + (double) body[1] * body[1] + (double) body[2] * body[2]);
        double[] d = { -body[0] / length, -body[2] / length, -body[1] / length };
        double[] actual = euler(angles);
        for (int i = 0; i < 3; i++) assertEquals("limb direction", d[i], actual[i * 3 + 1], 1e-4);
        // Independent axis-angle Rodrigues reference, not the solver's quaternion construction.
        double cross = Math.hypot(d[0], d[2]);
        double ax = cross == 0 ? 1 : d[2] / cross, az = cross == 0 ? 0 : -d[0] / cross;
        double c = d[1], s = cross, v = 1 - c;
        double[] swing = { c + ax * ax * v, -az * s, ax * az * v, az * s, c, -ax * s, ax * az * v, ax * s,
            c + az * az * v };
        for (int i = 0; i < 9; i++) {
            assertTrue("finite Euler angle", Float.isFinite(angles[i % 3]));
            assertEquals("full swing matrix[" + i + "]", swing[i], actual[i], 1e-4);
        }
    }

    private static double[] euler(float[] angles) {
        double cx = Math.cos(angles[0]), sx = Math.sin(angles[0]);
        double cy = Math.cos(angles[1]), sy = Math.sin(angles[1]);
        double cz = Math.cos(angles[2]), sz = Math.sin(angles[2]);
        return new double[] { cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx, sz * cy, sz * sy * sx + cz * cx,
            sz * sy * cx - cz * sx, -sy, cy * sx, cy * cx };
    }

    @Test
    public void shouldersHipsAndPelvisOffsetsUsePixels() {
        float[] zero = new float[3], out = new float[3];
        LimbSolver.shoulderPoint(true, 0, zero, out);
        assertArrayEquals(new float[] { -5, 2, 0 }, out, 0);
        LimbSolver.shoulderPoint(false, 0, zero, out);
        assertArrayEquals(new float[] { 5, 2, 0 }, out, 0);
        LimbSolver.hipPoint(true, zero, out);
        assertArrayEquals(new float[] { -1.9f, 12, 0 }, out, 0);
        LimbSolver.hipPoint(false, zero, out);
        assertArrayEquals(new float[] { 1.9f, 12, 0 }, out, 0);
        float[] pelvis = { .1f, .2f, .3f };
        for (boolean right : new boolean[] { true, false }) {
            float x = right ? -5 : 5;
            LimbSolver.shoulderPoint(right, 30, pelvis, out);
            assertArrayEquals(
                new float[] { (float) (x * Math.cos(Math.PI / 6)) - .1f * 16 / .9375f, 2 - .3f * 16 / .9375f,
                    (float) (x * Math.sin(Math.PI / 6)) - .2f * 16 / .9375f },
                out,
                1e-6f);
            LimbSolver.hipPoint(right, pelvis, out);
            assertArrayEquals(
                new float[] { (right ? -1.9f : 1.9f) - .1f * 16 / .9375f, 12 - .3f * 16 / .9375f, -.2f * 16 / .9375f },
                out,
                1e-6f);
        }
    }

    @Test
    public void positiveBodyYawIsNegativeBipedYaw() {
        float x = .3f, y = .2f, z = 1.2f;
        double bodyAngle = Math.PI / 6;
        float[] before = new float[3], after = new float[3];
        LimbSolver.bodyToBipedPoint(x, y, z, before);
        LimbSolver.bodyToBipedPoint(
            (float) (x * Math.cos(bodyAngle) - y * Math.sin(bodyAngle)),
            (float) (x * Math.sin(bodyAngle) + y * Math.cos(bodyAngle)),
            z,
            after);
        double bipedAngle = LimbSolver.torsoAngleY(30);
        assertEquals(-bodyAngle, bipedAngle, 1e-7);
        assertArrayEquals(
            after,
            new float[] { (float) (before[0] * Math.cos(bipedAngle) + before[2] * Math.sin(bipedAngle)), before[1],
                (float) (-before[0] * Math.sin(bipedAngle) + before[2] * Math.cos(bipedAngle)) },
            1e-6f);
    }

    @Test
    public void swordMatrixMatchesIndependentQuaternionPointRotation() throws Exception {
        TrackData data = TrackDataTest.realData();
        Pose pose = new Pose();
        float[] matrix = new float[16];
        for (String id : data.actionIds()) {
            Track track = data.track(id);
            for (double t = 0; t <= track.duration(); t += .5) {
                track.sample(t, pose);
                LimbSolver.swordMatrix(pose, matrix);
                for (float[] point : new float[][] { { 0, 0, 0 }, { 0, .3f, 0 }, { .1f, 0, 0 }, { 0, 0, .2f } }) {
                    double qx = pose.swordQ[0], qy = pose.swordQ[1], qz = pose.swordQ[2], qw = pose.swordQ[3];
                    // q*v*q^-1 via two cross products, independently of matrix entry formulas.
                    double tx = 2 * (qy * point[2] - qz * point[1]);
                    double ty = 2 * (qz * point[0] - qx * point[2]);
                    double tz = 2 * (qx * point[1] - qy * point[0]);
                    float bx = (float) (pose.swordP[0] + point[0] + qw * tx + qy * tz - qz * ty);
                    float by = (float) (pose.swordP[1] + point[1] + qw * ty + qz * tx - qx * tz);
                    float bz = (float) (pose.swordP[2] + point[2] + qw * tz + qx * ty - qy * tx);
                    float[] expected = new float[3];
                    LimbSolver.bodyToBipedPoint(bx, by, bz, expected);
                    for (int i = 0; i < 3; i++) assertEquals(
                        expected[i],
                        matrix[i] * point[0] + matrix[4 + i] * point[1] + matrix[8 + i] * point[2] + matrix[12 + i],
                        1e-6);
                }
                assertEquals(0, matrix[3], 0);
                assertEquals(0, matrix[7], 0);
                assertEquals(0, matrix[11], 0);
                assertEquals(1, matrix[15], 0);
            }
        }
    }
}
