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
    public boolean handLFree;
    public boolean bladeDrawn;

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
        handLFree = source.handLFree;
        bladeDrawn = source.bladeDrawn;
    }

    /** Clamped blend, shortest quaternion arc, normalized directions; out may alias either input. */
    public static void blend(Pose a, Pose b, float w, Pose out) {
        if (!Float.isFinite(w)) throw new IllegalArgumentException("weight: not finite");
        w = Math.max(0, Math.min(1, w));
        if (w == 0) {
            out.copyFrom(a);
            return;
        }
        if (w == 1) {
            out.copyFrom(b);
            return;
        }
        mix(a.swordP, 0, b.swordP, 0, w, out.swordP, 3, false);
        quaternion(a.swordQ, 0, b.swordQ, 0, w, out.swordQ);
        mix(a.handR, 0, b.handR, 0, w, out.handR, 3, false);
        mix(a.handL, 0, b.handL, 0, w, out.handL, 3, false);
        mix(a.armR, 0, b.armR, 0, w, out.armR, 3, true);
        mix(a.armL, 0, b.armL, 0, w, out.armL, 3, true);
        mix(a.legR, 0, b.legR, 0, w, out.legR, 3, true);
        mix(a.legL, 0, b.legL, 0, w, out.legL, 3, true);
        mix(a.pelvis, 0, b.pelvis, 0, w, out.pelvis, 3, false);
        mix(a.footR, 0, b.footR, 0, w, out.footR, 2, false);
        mix(a.footL, 0, b.footL, 0, w, out.footL, 2, false);
        out.torsoYaw = a.torsoYaw + (b.torsoYaw - a.torsoYaw) * w;
        out.handLFree = w >= .5f ? b.handLFree : a.handLFree;
        out.bladeDrawn = w >= .5f ? b.bladeDrawn : a.bladeDrawn;
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
