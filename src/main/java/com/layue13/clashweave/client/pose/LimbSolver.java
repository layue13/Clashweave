package com.layue13.clashweave.client.pose;

/** Body-to-Biped conversion and rigid swing-only limbs; all output storage belongs to the caller. */
public final class LimbSolver {

    private static final float SCALE = .9375f;

    private LimbSolver() {}

    /** Block-space body point to Biped units (px/16), including ground-origin translation. */
    public static void bodyToBipedPoint(float x, float y, float z, float[] out3) {
        out3[0] = -x / SCALE;
        out3[1] = 1.5f - z / SCALE;
        out3[2] = -y / SCALE;
    }

    /** Rotation only: preserves direction length and handedness. */
    public static void bodyToBipedDir(float x, float y, float z, float[] out3) {
        out3[0] = -x;
        out3[1] = -z;
        out3[2] = -y;
    }

    /** Shortest swing from Biped +Y, decomposed as Rz Ry Rx in radians. Input must be finite and nonzero. */
    public static void solveLimb(float dx, float dy, float dz, float[] outXYZ) {
        double length = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
        if (!(length > 0) || !Double.isFinite(length))
            throw new IllegalArgumentException("direction: expected finite nonzero vector");
        double x = -dx / length, y = -dz / length, z = -dy / length;
        double cross = Math.hypot(x, z);
        double qx, qz, qw;
        if (cross == 0) {
            // At the antipode the swing axis is ambiguous; consistently choose Biped +X.
            qx = y < 0 ? 1 : 0;
            qz = 0;
            qw = y < 0 ? 0 : 1;
        } else {
            double half = .5 * Math.atan2(cross, y);
            double sine = Math.sin(half);
            qx = z / cross * sine;
            qz = -x / cross * sine;
            qw = Math.cos(half);
        }
        double r00 = 1 - 2 * qz * qz, r10 = 2 * qz * qw, r20 = 2 * qx * qz;
        double r21 = 2 * qx * qw, r22 = 1 - 2 * qx * qx;
        double cosY = Math.hypot(r00, r10);
        outXYZ[1] = (float) Math.atan2(-r20, cosY);
        if (cosY > 1e-10) {
            outXYZ[0] = (float) Math.atan2(r21, r22);
            outXYZ[2] = (float) Math.atan2(r10, r00);
        } else {
            outXYZ[0] = 0;
            outXYZ[2] = (float) Math.atan2(2 * qz * qw, 1 - 2 * (qx * qx + qz * qz));
        }
    }

    /** Shoulder rotation point in px. Exact ModelBiped anchors calibrate the rounded bake coordinates. */
    public static void shoulderPoint(boolean right, float torsoYawDeg, float[] pelvis3, float[] outBipedPx3) {
        double angle = Math.toRadians(torsoYawDeg);
        float x = right ? -5 : 5;
        outBipedPx3[0] = (float) (x * Math.cos(angle)) - pelvis3[0] * 16 / SCALE;
        outBipedPx3[1] = 2 - pelvis3[2] * 16 / SCALE;
        outBipedPx3[2] = (float) (x * Math.sin(angle)) - pelvis3[1] * 16 / SCALE;
    }

    /** Independent leg rotation point in px; pelvis translates but does not rotate it. */
    public static void hipPoint(boolean right, float[] pelvis3, float[] outBipedPx3) {
        outBipedPx3[0] = (right ? -1.9f : 1.9f) - pelvis3[0] * 16 / SCALE;
        outBipedPx3[1] = 12 - pelvis3[2] * 16 / SCALE;
        outBipedPx3[2] = -pelvis3[1] * 16 / SCALE;
    }

    /** Body +Z maps to Biped -Y, so the right-handed yaw changes sign. */
    public static float torsoAngleY(float torsoYawDeg) {
        return (float) -Math.toRadians(torsoYawDeg);
    }

    /** Column-major affine matrix from sword-local blocks to Biped units, including scale 1/S. */
    public static void swordMatrix(Pose pose, float[] out16) {
        double x = pose.swordQ[0], y = pose.swordQ[1], z = pose.swordQ[2], w = pose.swordQ[3];
        // Left-multiply the quaternion matrix by the body-to-Biped basis (-x, -z, -y).
        out16[0] = (float) (-(1 - 2 * (y * y + z * z)) / SCALE);
        out16[1] = (float) (-2 * (x * z - y * w) / SCALE);
        out16[2] = (float) (-2 * (x * y + z * w) / SCALE);
        out16[3] = 0;
        out16[4] = (float) (-2 * (x * y - z * w) / SCALE);
        out16[5] = (float) (-2 * (y * z + x * w) / SCALE);
        out16[6] = (float) (-(1 - 2 * (x * x + z * z)) / SCALE);
        out16[7] = 0;
        out16[8] = (float) (-2 * (x * z + y * w) / SCALE);
        out16[9] = (float) (-(1 - 2 * (x * x + y * y)) / SCALE);
        out16[10] = (float) (-2 * (y * z - x * w) / SCALE);
        out16[11] = 0;
        out16[12] = -pose.swordP[0] / SCALE;
        out16[13] = 1.5f - pose.swordP[2] / SCALE;
        out16[14] = -pose.swordP[1] / SCALE;
        out16[15] = 1;
    }
}
