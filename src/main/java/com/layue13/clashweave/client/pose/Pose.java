package com.layue13.clashweave.client.pose;

/** Reusable body-space pose. Arrays are owned by this instance; rotations use xyzw quaternions. */
public final class Pose {

    public final float[] swordP = new float[3];
    public final float[] swordQ = { 0, 0, 0, 1 };
    public final float[] handR = new float[3];
    public final float[] handL = new float[3];
    public final float[] armR = { 0, 0, -1 };
    public final float[] armL = { 0, 0, -1 };
    public final float[] legR = { 0, 0, -1 };
    public final float[] legL = { 0, 0, -1 };
    public final float[] pelvis = new float[3];
    public final float[] footR = new float[2];
    public final float[] footL = new float[2];
    public float torsoYaw;
    /** Degrees about the waist: lean positive forward, side positive toward the player's right. */
    public float torsoLean, torsoSide;
    public boolean handLFree;
    public boolean bladeDrawn;
    /** Degrees of extra upper-body twist {@link #twistTo} has already applied to this sample. */
    public float twistApplied;

    /** Copies all values without allocating; self-copy is supported. */
    public void copyFrom(Pose source) {
        System.arraycopy(source.swordP, 0, swordP, 0, 3);
        System.arraycopy(source.swordQ, 0, swordQ, 0, 4);
        System.arraycopy(source.handR, 0, handR, 0, 3);
        System.arraycopy(source.handL, 0, handL, 0, 3);
        System.arraycopy(source.armR, 0, armR, 0, 3);
        System.arraycopy(source.armL, 0, armL, 0, 3);
        System.arraycopy(source.legR, 0, legR, 0, 3);
        System.arraycopy(source.legL, 0, legL, 0, 3);
        System.arraycopy(source.pelvis, 0, pelvis, 0, 3);
        System.arraycopy(source.footR, 0, footR, 0, 2);
        System.arraycopy(source.footL, 0, footL, 0, 2);
        torsoYaw = source.torsoYaw;
        torsoLean = source.torsoLean;
        torsoSide = source.torsoSide;
        handLFree = source.handLFree;
        bladeDrawn = source.bladeDrawn;
        twistApplied = source.twistApplied;
    }

    /**
     * Turns everything the torso carries (blade, hands, arm directions, torso yaw) about the vertical through the waist
     * so that the total extra twist on this sample is {@code degrees}; the legs stay. Idempotent per sample: several
     * models posed from one pose in a frame all ask for the same value.
     */
    public void twistTo(float degrees) {
        float delta = degrees - twistApplied;
        if (delta == 0) return;
        twistApplied = degrees;
        double r = Math.toRadians(delta), c = Math.cos(r), s = Math.sin(r);
        turn(swordP, c, s);
        turn(handR, c, s);
        turn(handL, c, s);
        turn(armR, c, s);
        turn(armL, c, s);
        double h = r / 2, sz = Math.sin(h), cz = Math.cos(h);
        float qx = swordQ[0], qy = swordQ[1], qz = swordQ[2], qw = swordQ[3];
        swordQ[0] = (float) (cz * qx - sz * qy);
        swordQ[1] = (float) (cz * qy + sz * qx);
        swordQ[2] = (float) (cz * qz + sz * qw);
        swordQ[3] = (float) (cz * qw - sz * qz);
        torsoYaw += delta;
    }

    private static void turn(float[] v, double c, double s) {
        float x = v[0], y = v[1];
        v[0] = (float) (x * c - y * s);
        v[1] = (float) (x * s + y * c);
    }

    static void mix(float[] a, int ai, float[] b, int bi, float w, float[] out, int n, boolean unit) {
        double length = 0;
        // Select the nearer endpoint if antipodal directions cancel exactly.
        double dot = 0;
        if (unit) for (int i = 0; i < n; i++) {
            double v = a[ai + i] + (b[bi + i] - a[ai + i]) * (double) w;
            dot += v * v;
        }
        for (int i = 0; i < n; i++) {
            float v = unit && dot < 1e-20 ? (w < .5f ? a[ai + i] : b[bi + i]) : a[ai + i] + (b[bi + i] - a[ai + i]) * w;
            out[i] = v;
            length += (double) v * v;
        }
        if (unit && length > 0) {
            float inverse = (float) (1 / Math.sqrt(length));
            for (int i = 0; i < n; i++) out[i] *= inverse;
        }
    }

    static void quaternion(float[] a, int ai, float[] b, int bi, float w, float[] out) {
        double dot = 0;
        for (int i = 0; i < 4; i++) dot += (double) a[ai + i] * b[bi + i];
        double sign = dot < 0 ? -1 : 1;
        dot = Math.min(1, Math.abs(dot));
        double wa = 1 - w, wb = w;
        if (dot < .9995) {
            double angle = Math.acos(dot), divisor = Math.sin(angle);
            wa = Math.sin((1 - w) * angle) / divisor;
            wb = Math.sin(w * angle) / divisor;
        }
        double length = 0;
        for (int i = 0; i < 4; i++) {
            out[i] = (float) (wa * a[ai + i] + sign * wb * b[bi + i]);
            length += (double) out[i] * out[i];
        }
        float inverse = (float) (1 / Math.sqrt(length));
        for (int i = 0; i < 4; i++) out[i] *= inverse;
    }
}
