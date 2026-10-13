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
        LimbSolver.shoulderPoint(true, new Pose(), out);
        assertArrayEquals(new float[] { -5, 2, 0 }, out, 0);
        LimbSolver.shoulderPoint(false, new Pose(), out);
        assertArrayEquals(new float[] { 5, 2, 0 }, out, 0);
        LimbSolver.hipPoint(true, zero, out);
        assertArrayEquals(new float[] { -1.9f, 12, 0 }, out, 0);
        LimbSolver.hipPoint(false, zero, out);
        assertArrayEquals(new float[] { 1.9f, 12, 0 }, out, 0);
        float[] pelvis = { .1f, .2f, .3f };
        for (boolean right : new boolean[] { true, false }) {
            float x = right ? -5 : 5;
            Pose turned = new Pose();
            turned.torsoYaw = 30;
            System.arraycopy(pelvis, 0, turned.pelvis, 0, 3);
            LimbSolver.shoulderPoint(right, turned, out);
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
        Pose turned = new Pose();
        turned.torsoYaw = 30;
        float[] torso = new float[3];
        LimbSolver.torsoAngles(turned, torso);
        double bipedAngle = torso[1];
        assertEquals(-bodyAngle, bipedAngle, 1e-7);
        assertArrayEquals(
            after,
            new float[] { (float) (before[0] * Math.cos(bipedAngle) + before[2] * Math.sin(bipedAngle)), before[1],
                (float) (-before[0] * Math.sin(bipedAngle) + before[2] * Math.cos(bipedAngle)) },
            1e-6f);
    }

    /** motion2.torso(): lean about x (negative angle), then yaw about z, then side about y, about the waist. */
    private static float[] bodyTorso(float[] p, double yaw, double lean, double side, float[] pel) {
        double x = p[0], y = p[1], z = p[2] - .703125;
        double a = Math.toRadians(-lean);
        double y1 = y * Math.cos(a) - z * Math.sin(a), z1 = y * Math.sin(a) + z * Math.cos(a);
        double b = Math.toRadians(yaw);
        double x2 = x * Math.cos(b) - y1 * Math.sin(b), y2 = x * Math.sin(b) + y1 * Math.cos(b);
        double g = Math.toRadians(side);
        double x3 = x2 * Math.cos(g) + z1 * Math.sin(g), z3 = -x2 * Math.sin(g) + z1 * Math.cos(g);
        return new float[] { (float) x3 + pel[0], (float) y2 + pel[1], (float) z3 + .703125f + pel[2] };
    }

    /** The game's torso and the bake's torso are the same transform, and the shoulders ride the body box. */
    @Test
    public void torsoTransformMatchesTheBakeAndCarriesShouldersWithTheBody() {
        Pose pose = new Pose();
        pose.torsoYaw = 23;
        pose.torsoLean = 18;
        pose.torsoSide = -6;
        pose.pelvis[0] = .04f;
        pose.pelvis[1] = .3f;
        pose.pelvis[2] = -.11f;
        // The exact ModelBiped right shoulder in body space: (5, 2) px.
        float[] shoulderBody = { 5 * .9375f / 16, 0, (1.5f - 2 / 16f) * .9375f };
        float[] expected = new float[3], actual = new float[3];
        float[] baked = bodyTorso(shoulderBody, pose.torsoYaw, pose.torsoLean, pose.torsoSide, pose.pelvis);
        LimbSolver.bodyToBipedPoint(baked[0], baked[1], baked[2], expected);
        LimbSolver.shoulderPoint(true, pose, actual);
        for (int i = 0; i < 3; i++) assertEquals(expected[i] * 16, actual[i], 1e-3);
        // Forward lean moves the shoulders toward the front (-Z in Biped).
        Pose upright = new Pose();
        upright.torsoLean = 0;
        Pose leaning = new Pose();
        leaning.torsoLean = 20;
        float[] a = new float[3], b = new float[3];
        LimbSolver.shoulderPoint(true, upright, a);
        LimbSolver.shoulderPoint(true, leaning, b);
        assertTrue(b[2] < a[2] - 3);
        // ModelRenderer draws the body box at neck + Rz Ry Rx * v; the shoulder anchor of that box is where the arm
        // hangs.
        float[] neck = new float[3], angles = new float[3];
        LimbSolver.neckPoint(pose, neck);
        LimbSolver.torsoAngles(pose, angles);
        double[] v = { -5, 2, 0 };
        double cx = Math.cos(angles[0]), sx = Math.sin(angles[0]), cy = Math.cos(angles[1]), sy = Math.sin(angles[1]);
        double cz = Math.cos(angles[2]), sz = Math.sin(angles[2]);
        double y1 = v[1] * cx - v[2] * sx, z1 = v[1] * sx + v[2] * cx;
        double x2 = v[0] * cy + z1 * sy, z2 = -v[0] * sy + z1 * cy;
        double x3 = x2 * cz - y1 * sz, y3 = x2 * sz + y1 * cz;
        assertEquals(actual[0], neck[0] + x3, 1e-3);
        assertEquals(actual[1], neck[1] + y3, 1e-3);
        assertEquals(actual[2], neck[2] + z2, 1e-3);
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
