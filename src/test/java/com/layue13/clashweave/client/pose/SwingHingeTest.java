package com.layue13.clashweave.client.pose;

import static org.junit.Assert.*;

import org.junit.Test;

/** The bend side of a limb follows its swing, and the hinge does not flip away from its undefined direction. */
public class SwingHingeTest {

    private static final float[] SHOULDER_R = { .29296875f, 0, 1.2890625f };

    private static Pose handAt(float dx, float dy, float dz) {
        Pose pose = new Pose();
        pose.handR[0] = SHOULDER_R[0] + dx;
        pose.handR[1] = SHOULDER_R[1] + dy;
        pose.handR[2] = SHOULDER_R[2] + dz;
        return pose;
    }

    @Test
    public void armHangingDownBendsTheForearmForward() {
        LimbAngles out = new LimbAngles();
        LimbSolver.solveArm(true, handAt(0, 0, -.5f), out);
        assertTrue(out.reachable);
        // Biped: front is -Z. The elbow sits behind the shoulder-hand line, the forearm points forward.
        assertTrue("upper z=" + out.upperDir[2], out.upperDir[2] > .3);
        assertTrue("lower z=" + out.lowerDir[2], out.lowerDir[2] < -.3);
    }

    @Test
    public void armRaisedForwardKeepsTheElbowBelowTheHand() {
        LimbAngles out = new LimbAngles();
        LimbSolver.solveArm(true, handAt(0, .5f, 0), out);
        assertTrue(out.reachable);
        // Biped: down is +Y. Elbow lower than the hand.
        assertTrue("upper y=" + out.upperDir[1], out.upperDir[1] > out.lowerDir[1] + .3);
    }

    @Test
    public void legUnderThePelvisBendsTheKneeForward() {
        Pose pose = new Pose();
        pose.pelvis[2] = -.12f;
        pose.footR[0] = .13f;
        LimbAngles out = new LimbAngles();
        LimbSolver.solveLeg(true, pose, out);
        assertTrue(out.reachable);
        assertTrue("thigh z=" + out.upperDir[2], out.upperDir[2] < -.2);
        assertTrue("shin z=" + out.lowerDir[2], out.lowerDir[2] > .2);
    }

    /** Down, forward and up again in the sagittal plane: the orientation turns about as fast as the input does. */
    @Test
    public void sweepingTheHandInTheSagittalPlaneTurnsTheHingeSmoothly() {
        LimbAngles out = new LimbAngles();
        double[] previous = null;
        double worst = 0;
        int worstDegrees = 0;
        for (int degrees = 0; degrees <= 360; degrees++) {
            double a = Math.toRadians(degrees);
            Pose pose = handAt(0, (float) (.5 * Math.sin(a)), (float) (-.5 * Math.cos(a)));
            LimbSolver.solveArm(true, pose, out);
            assertTrue(out.reachable);
            double[] matrix = rotation(out.upper);
            if (previous != null) {
                double step = angle(previous, matrix);
                if (step > worst) {
                    worst = step;
                    worstDegrees = degrees;
                }
            }
            previous = matrix;
        }
        System.out.println("C2B_SWING sagittal sweep worst 1-degree step=" + worst + " at " + worstDegrees);
        assertTrue("worst=" + worst + " at " + worstDegrees, worst < Math.toRadians(8));
    }

    private static double[] rotation(float[] euler) {
        double cx = Math.cos(euler[0]), sx = Math.sin(euler[0]);
        double cy = Math.cos(euler[1]), sy = Math.sin(euler[1]);
        double cz = Math.cos(euler[2]), sz = Math.sin(euler[2]);
        return new double[] { cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx, sz * cy, sz * sy * sx + cz * cx,
            sz * sy * cx - cz * sx, -sy, cy * sx, cy * cx };
    }

    private static double angle(double[] a, double[] b) {
        double trace = 0;
        for (int row = 0; row < 3; row++) for (int k = 0; k < 3; k++) trace += a[k * 3 + row] * b[k * 3 + row];
        return Math.acos(Math.max(-1, Math.min(1, (trace - 1) / 2)));
    }
}
