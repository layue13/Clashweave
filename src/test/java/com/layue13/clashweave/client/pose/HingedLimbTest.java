package com.layue13.clashweave.client.pose;

import static org.junit.Assert.*;

import org.junit.Test;

public class HingedLimbTest {

    @Test
    public void allTrackSamplesReconstruct() throws Exception {
        TrackData data = TrackDataTest.realData();
        Pose pose = new Pose();
        LimbAngles out = new LimbAngles();
        float[] pivot = new float[3], target = new float[3], end = new float[3];
        int[] unreachable = new int[4];
        float[] maxShortfall = new float[4];
        int samples = 0;
        double maxReachableError = 0;
        for (String id : data.actionIds()) {
            Track track = data.track(id);
            for (double t = 0; t <= track.duration(); t += .5) {
                track.sample(t, pose);
                for (int limb = 0; limb < 4; limb++) {
                    boolean arm = limb < 2, right = limb % 2 == 0;
                    if (arm) {
                        LimbSolver.solveArm(right, pose, out);
                        LimbSolver.shoulderPoint(right, pose.torsoYaw, pose.pelvis, pivot);
                        float[] hand = right ? pose.handR : pose.handL;
                        LimbSolver.bodyToBipedPoint(hand[0], hand[1], hand[2], target);
                    } else {
                        LimbSolver.solveLeg(right, pose, out);
                        LimbSolver.hipPoint(right, pose.pelvis, pivot);
                        float[] foot = right ? pose.footR : pose.footL;
                        LimbSolver.bodyToBipedPoint(foot[0], foot[1], 0, target);
                    }
                    for (int i = 0; i < 3; i++) pivot[i] /= 16;
                    LimbSolver.endPoint(pivot, out.upper, out.childAngleX, end);
                    double error = distance(end, target);
                    assertTrue(Float.isFinite(out.flex));
                    assertTrue(Float.isFinite(out.childAngleX));
                    assertTrue(Float.isFinite(out.shortfall));
                    assertTrue(out.flex >= 0 && out.flex <= Math.toRadians(145) + 1e-6);
                    assertEquals(-out.flex, out.childAngleX, 0);
                    if (out.reachable) {
                        maxReachableError = Math.max(maxReachableError, error);
                        assertTrue(id + " limb=" + limb + " t=" + t + " error=" + error, error < 2e-3);
                        assertEquals(0, out.shortfall, 0);
                    } else {
                        unreachable[limb]++;
                        maxShortfall[limb] = Math.max(maxShortfall[limb], out.shortfall);
                        assertTrue(out.shortfall > 0);
                        assertEquals(error, out.shortfall, 2e-5);
                    }
                    for (int i = 0; i < 3; i++) {
                        assertTrue(Float.isFinite(out.upper[i]));
                        assertTrue(Float.isFinite(end[i]));

                    }
                }
                samples++;
            }
        }
        System.out
            .println("C2B_IK samples=" + samples + " limbs=" + samples * 4 + " maxReachableError=" + maxReachableError);
        for (int limb = 0; limb < 4; limb++) System.out.println(
            "C2B_IK limb=" + limb + " unreachable=" + unreachable[limb] + " maxShortfall=" + maxShortfall[limb]);
    }

    /**
     * What a frame shows is the orientation, not its Euler triple: near gimbal lock or across the 2*pi cut the three
     * angles can jump while the rotation barely moves. A flipped hinge shows as a rotation step near pi. A step is only
     * excused where the TARGET itself jumped (a discontinuity in the baked data, listed in the output), never by the
     * solver: the rotation must stay below 0.9 rad for every target step under 0.25 block.
     */
    @Test
    public void adjacentTrackRotationsStayContinuousUnlessTheTargetJumped() throws Exception {
        TrackData data = TrackDataTest.realData();
        Pose pose = new Pose();
        LimbAngles out = new LimbAngles();
        double[] rotationMax = new double[4];
        StringBuilder excused = new StringBuilder();
        String worst = "";
        double worstStep = 0;
        for (String id : data.actionIds()) {
            Track track = data.track(id);
            double[][] previous = new double[4][];
            float[][] previousTarget = new float[4][3];
            for (double t = 0; t <= track.duration(); t += .5) {
                track.sample(t, pose);
                for (int limb = 0; limb < 4; limb++) {
                    if (limb < 2) LimbSolver.solveArm(limb % 2 == 0, pose, out);
                    else LimbSolver.solveLeg(limb % 2 == 0, pose, out);
                    double[] matrix = matrix(out.upper);
                    float[] raw = new float[3];
                    if (limb < 2) {
                        float[] hand = limb == 0 ? pose.handR : pose.handL;
                        LimbSolver.bodyToBipedPoint(hand[0], hand[1], hand[2], raw);
                    } else {
                        float[] foot = limb == 2 ? pose.footR : pose.footL;
                        LimbSolver.bodyToBipedPoint(foot[0], foot[1], 0, raw);
                    }
                    if (t > 0) {
                        double step = rotationAngle(previous[limb], matrix);
                        double moved = distance(raw, previousTarget[limb]);
                        rotationMax[limb] = Math.max(rotationMax[limb], step);
                        if (step >= .9) {
                            excused.append(" ")
                                .append(id)
                                .append(" limb=")
                                .append(limb)
                                .append(" t=")
                                .append(t)
                                .append(" rotation=")
                                .append(step)
                                .append(" targetStep=")
                                .append(moved)
                                .append(";");
                            assertTrue(
                                id + " limb=" + limb + " t=" + t + " rotation=" + step + " targetStep=" + moved,
                                moved > .25 / .9375);
                        }
                        if (step > worstStep) {
                            worstStep = step;
                            worst = id + " limb=" + limb + " t=" + t;
                        }
                    }
                    previous[limb] = matrix;
                    System.arraycopy(raw, 0, previousTarget[limb], 0, 3);
                }
            }
        }
        for (int limb = 0; limb < 4; limb++)
            System.out.println("C2B_CONTINUITY limb=" + limb + " maxRotationStep=" + rotationMax[limb]);
        System.out.println("C2B_CONTINUITY worst " + worst + " step=" + worstStep);
        System.out.println("C2B_CONTINUITY excused by data jumps:" + excused);
    }

    private static double[] matrix(float[] euler) {
        double cx = Math.cos(euler[0]), sx = Math.sin(euler[0]);
        double cy = Math.cos(euler[1]), sy = Math.sin(euler[1]);
        double cz = Math.cos(euler[2]), sz = Math.sin(euler[2]);
        // Rz * Ry * Rx, row major.
        return new double[] { cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx, sz * cy, sz * sy * sx + cz * cx,
            sz * sy * cx - cz * sx, -sy, cy * sx, cy * cx };
    }

    private static double rotationAngle(double[] a, double[] b) {
        double trace = 0;
        for (int row = 0; row < 3; row++) for (int k = 0; k < 3; k++) trace += a[k * 3 + row] * b[k * 3 + row];
        return Math.acos(Math.max(-1, Math.min(1, (trace - 1) / 2)));
    }

    @Test
    public void anatomicalClampKeepsClosestAllowedEndpoint() {
        Pose pose = new Pose();
        // Hand exactly at the right shoulder in body coordinates.
        pose.handR[0] = .29296875f;
        pose.handR[2] = 1.2890625f;
        LimbAngles out = new LimbAngles();
        LimbSolver.solveArm(true, pose, out);
        assertFalse(out.reachable);
        assertEquals(Math.toRadians(145), out.flex, 1e-6);
        float[] end = new float[3];
        LimbSolver.endPoint(out.pivot, out.upper, out.childAngleX, end);
        assertEquals(.75 * Math.cos(Math.toRadians(145) / 2), distance(end, out.pivot), 1e-6);
        assertEquals(distance(end, out.pivot), out.shortfall, 1e-6);
    }

    @Test
    public void handAtTheScabbardMouthIsReachableWithABentElbow() throws Exception {
        Pose pose = new Pose();
        TrackDataTest.realData()
            .track("iai")
            .sample(0, pose);
        LimbAngles out = new LimbAngles();
        LimbSolver.solveArm(false, pose, out);
        // The old fixed pole made this pose a hinge flip that was clamped into a false 0.7 shortfall.
        assertTrue(out.reachable);
        assertEquals(0, out.shortfall, 0);
        assertTrue(out.flex > Math.toRadians(20));
    }

    @Test
    public void fkConfirmsElbowForwardAndKneeBackwardLowerDirection() {
        float[] upper = { 0, 1, 0 }, angles = new float[3], end = new float[3];
        for (float side : new float[] { -1, 1 }) {
            LimbSolver.solveHinged(upper, new float[] { side * .35f, 1, .2f }, angles);
            LimbSolver.endPoint(new float[3], angles, -.7f, end);
            assertTrue("elbow lower segment forward", end[2] < 0);
            LimbSolver.solveHinged(upper, new float[] { side * .2f, 0, -1 }, angles);
            LimbSolver.endPoint(new float[3], angles, -.7f, end);
            assertTrue("knee lower segment backward", end[2] > 0);
        }
    }

    private static double distance(float[] a, float[] b) {
        double squared = 0;
        for (int i = 0; i < 3; i++) squared += (double) (a[i] - b[i]) * (a[i] - b[i]);
        return Math.sqrt(squared);
    }
}
