package com.layue13.clashweave.client.pose;

/** Body-to-Biped conversion and rigid swing-only limbs; all output storage belongs to the caller. */
public final class LimbSolver {

    private static final float SCALE = .9375f;

    private static final float SEGMENT = .375f;
    private static final float MAX_FLEX = (float) Math.toRadians(145);
    /**
     * Each limb's hinge frame is the lateral axis carried by the shortest rotation from {@code -singular} to the
     * direction of the target, so the bend plane turns smoothly with the arm instead of pointing at a fixed pole. Such
     * a
     * frame is undefined exactly when the target lies along {@code singular}; the axes below sit as far as possible
     * from
     * every direction the katana tracks send the limb (45 degrees tilted from straight up, away from the sword arc).
     * They are Biped-space unit vectors; the tracks they were chosen against are in katana.tracks.json.
     */
    private static final float[] ARM_RIGHT_SINGULAR = unit(-.3f, -.71f, .64f);
    private static final float[] ARM_LEFT_SINGULAR = unit(.7f, -.71f, .06f);
    private static final float[] LEG_SINGULAR = { 0, -1, 0 };

    private LimbSolver() {}

    private static float[] unit(float x, float y, float z) {
        double n = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
        return new float[] { (float) (x / n), (float) (y / n), (float) (z / n) };
    }

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

    /** Local +Y follows upperDir, +X is unit(upperDir cross pole); Rz Ry Rx, radians. */
    public static void solveHinged(float[] upperDir, float[] pole, float[] outUpperXYZ) {
        double ux = upperDir[0], uy = upperDir[1], uz = upperDir[2];
        double norm = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (!(norm > 0) || !Double.isFinite(norm))
            throw new IllegalArgumentException("direction: expected finite nonzero vector");
        ux /= norm;
        uy /= norm;
        uz /= norm;
        double hx = uy * pole[2] - uz * pole[1];
        double hy = uz * pole[0] - ux * pole[2];
        double hz = ux * pole[1] - uy * pole[0];
        norm = Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (norm < 1e-10) {
            int axis = Math.abs(ux) <= Math.abs(uy) && Math.abs(ux) <= Math.abs(uz) ? 0
                : Math.abs(uy) <= Math.abs(uz) ? 1 : 2;
            hx = axis == 2 ? uy : axis == 1 ? -uz : 0;
            hy = axis == 0 ? uz : axis == 2 ? -ux : 0;
            hz = axis == 1 ? ux : axis == 0 ? -uy : 0;
            norm = Math.sqrt(hx * hx + hy * hy + hz * hz);
        }
        frame(ux, uy, uz, hx / norm, hy / norm, hz / norm, outUpperXYZ);
    }

    /** Rz Ry Rx for the frame with local +Y = u and local +X = h (h unit and perpendicular to u). */
    private static void frame(double ux, double uy, double uz, double hx, double hy, double hz, float[] outUpperXYZ) {
        double zz = hx * uy - hy * ux;
        double cosY = Math.hypot(hx, hy);
        outUpperXYZ[1] = (float) Math.atan2(-hz, cosY);
        if (cosY > 1e-10) {
            outUpperXYZ[0] = (float) Math.atan2(uz, zz);
            outUpperXYZ[2] = (float) Math.atan2(hy, hx);
        } else {
            outUpperXYZ[0] = 0;
            outUpperXYZ[2] = (float) Math.atan2(-ux, uy);
        }
        // Keep the knee rest angle inside the chart; a stateless Euler chart still has a seam.
        if (outUpperXYZ[0] < -Math.PI / 2) outUpperXYZ[0] += (float) (2 * Math.PI);
        if (outUpperXYZ[2] < -Math.PI / 2) outUpperXYZ[2] += (float) (2 * Math.PI);
    }

    /** Shoulder/hand IK with a hinge frame that follows the arm, and the anatomical flex limit; output owns scratch. */
    public static void solveArm(boolean right, Pose pose, LimbAngles out) {
        shoulderPoint(right, pose.torsoYaw, pose.pelvis, out.pivot);
        float[] hand = right ? pose.handR : pose.handL;
        bodyToBipedPoint(hand[0], hand[1], hand[2], out.target);
        // Elbows bend behind the upper arm's swing plane: forearm forward.
        solveSegmented(right ? ARM_RIGHT_SINGULAR : ARM_LEFT_SINGULAR, 1, out);
    }

    /** Hip/ground-foot IK; the existing hip anchor remains unchanged. */
    public static void solveLeg(boolean right, Pose pose, LimbAngles out) {
        hipPoint(right, pose.pelvis, out.pivot);
        float[] foot = right ? pose.footR : pose.footL;
        bodyToBipedPoint(foot[0], foot[1], 0, out.target);
        // Knees bend ahead of the thigh's swing plane: shin backward.
        solveSegmented(LEG_SINGULAR, -1, out);
    }

    /**
     * The hinge axis is {@code lateral * X} carried by the shortest rotation from {@code -singular} to the target
     * direction t; it is perpendicular to t, so the bend plane holds t and the offset w = h x t. Segments of equal
     * length l and reach r: upper = (r/2 t + height w) / l, lower = (r/2 t - height w) / l. Closed form, no iteration.
     */
    private static void solveSegmented(float[] singular, int lateral, LimbAngles out) {
        for (int i = 0; i < 3; i++) out.pivot[i] /= 16;
        double rx = -singular[0], ry = -singular[1], rz = -singular[2];
        double dx = (double) out.target[0] - out.pivot[0], dy = (double) out.target[1] - out.pivot[1];
        double dz = (double) out.target[2] - out.pivot[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double minimum = 2 * SEGMENT * Math.cos(MAX_FLEX / 2);
        double radius = Math.max(minimum, Math.min(2 * SEGMENT, distance));
        out.reachable = distance >= minimum && distance <= 2 * SEGMENT;
        out.shortfall = (float) Math.abs(radius - distance);
        if (distance == 0) {
            // Any ray is equally close; the rest direction keeps the hinge frame defined.
            dx = rx;
            dy = ry;
            dz = rz;
        } else {
            dx /= distance;
            dy /= distance;
            dz /= distance;
        }
        // h0: the lateral axis perpendicular to rest.
        double dotX = rx;
        double h0x = 1 - dotX * rx, h0y = -dotX * ry, h0z = -dotX * rz;
        double h0 = Math.sqrt(h0x * h0x + h0y * h0y + h0z * h0z);
        h0x *= lateral / h0;
        h0y *= lateral / h0;
        h0z *= lateral / h0;
        // Rodrigues rotation of h0 about rest x t by the angle between them.
        double ax = ry * dz - rz * dy, ay = rz * dx - rx * dz, az = rx * dy - ry * dx;
        double sine = Math.sqrt(ax * ax + ay * ay + az * az);
        double cosine = rx * dx + ry * dy + rz * dz;
        double hx = h0x, hy = h0y, hz = h0z;
        if (sine > 1e-9) {
            ax /= sine;
            ay /= sine;
            az /= sine;
            double kh = ax * h0x + ay * h0y + az * h0z;
            double cx = ay * h0z - az * h0y, cy = az * h0x - ax * h0z, cz = ax * h0y - ay * h0x;
            hx = h0x * cosine + cx * sine + ax * kh * (1 - cosine);
            hy = h0y * cosine + cy * sine + ay * kh * (1 - cosine);
            hz = h0z * cosine + cz * sine + az * kh * (1 - cosine);
        }
        // Offset direction of the joint from the line pivot-target.
        double wx = hy * dz - hz * dy, wy = hz * dx - hx * dz, wz = hx * dy - hy * dx;
        double along = radius / 2;
        double height = Math.sqrt(Math.max(0, (double) SEGMENT * SEGMENT - along * along));
        double ux = (along * dx + height * wx) / SEGMENT, uy = (along * dy + height * wy) / SEGMENT;
        double uz = (along * dz + height * wz) / SEGMENT;
        double lx = ((radius - along) * dx - height * wx) / SEGMENT,
            ly = ((radius - along) * dy - height * wy) / SEGMENT;
        double lz = ((radius - along) * dz - height * wz) / SEGMENT;
        out.upperDir[0] = (float) ux;
        out.upperDir[1] = (float) uy;
        out.upperDir[2] = (float) uz;
        out.lowerDir[0] = (float) lx;
        out.lowerDir[1] = (float) ly;
        out.lowerDir[2] = (float) lz;
        frame(ux, uy, uz, hx, hy, hz, out.upper);
        out.flex = (float) Math.min(MAX_FLEX, 2 * Math.acos(Math.min(1, radius / (2 * SEGMENT))));
        // The lower segment always lies on the -w side of the upper one, so the child angle is the negative flexion.
        out.childAngleX = -out.flex;
    }

    /** FK for two 6 px segments: pivot + Rz Ry Rx * (0, l + l*cos(child), l*sin(child)). */
    public static void endPoint(float[] pivot, float[] upperXYZ, float childAngleX, float[] out3) {
        double y = SEGMENT * (1 + Math.cos(childAngleX)), z = SEGMENT * Math.sin(childAngleX);
        double cx = Math.cos(upperXYZ[0]), sx = Math.sin(upperXYZ[0]);
        double cy = Math.cos(upperXYZ[1]), sy = Math.sin(upperXYZ[1]);
        double cz = Math.cos(upperXYZ[2]), sz = Math.sin(upperXYZ[2]);
        double ry = cx * y - sx * z, rz = sx * y + cx * z;
        double rx = sy * rz;
        out3[0] = (float) (pivot[0] + cz * rx - sz * ry);
        out3[1] = (float) (pivot[1] + sz * rx + cz * ry);
        out3[2] = (float) (pivot[2] + cy * rz);
    }
}
