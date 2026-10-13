package com.layue13.clashweave.client.pose;

/** Immutable packed samples. No per-frame lookup, iterator or scratch object is needed. */
public final class Track {

    /** Offsets of one packed sample; TrackData writes through these, sample() reads through them. */
    static final int SWORD_P = 0, SWORD_Q = 3, HAND_R = 7, HAND_L = 10, ARM_R = 13, ARM_L = 16, LEG_R = 19, LEG_L = 22,
        PELVIS = 25, FOOT_R = 28, FOOT_L = 30, TORSO_YAW = 32, HAND_L_FREE = 33, BLADE_DRAWN = 34, TORSO_LEAN = 35,
        TORSO_SIDE = 36, STRIDE = 37;
    private final float[] times;
    private final float[] values;

    Track(float[] times, float[] values) {
        this.times = times;
        this.values = values;
    }

    public float duration() {
        return times[times.length - 1];
    }

    /** Clamps time; discrete values stay at the preceding sample until the next timestamp. */
    public void sample(double ticks, Pose out) {
        if (Double.isNaN(ticks)) throw new IllegalArgumentException("ticks: not finite");
        int low = 0, high = times.length - 1;
        if (ticks >= times[high]) low = high;
        else if (ticks > 0) {
            while (high - low > 1) {
                int middle = (low + high) >>> 1;
                if (ticks >= times[middle]) low = middle;
                else high = middle;
            }
        }
        int next = Math.min(low + 1, times.length - 1);
        float w = ticks <= times[low] || low == next ? 0 : (float) ((ticks - times[low]) / (times[next] - times[low]));
        int a = low * STRIDE, b = next * STRIDE;
        if (w == 0) System.arraycopy(values, a + SWORD_P, out.swordP, 0, 3);
        else Pose.mix(values, a + SWORD_P, values, b + SWORD_P, w, out.swordP, 3, false);
        if (w == 0) System.arraycopy(values, a + SWORD_Q, out.swordQ, 0, 4);
        else Pose.quaternion(values, a + SWORD_Q, values, b + SWORD_Q, w, out.swordQ);
        if (w == 0) System.arraycopy(values, a + HAND_R, out.handR, 0, 3);
        else Pose.mix(values, a + HAND_R, values, b + HAND_R, w, out.handR, 3, false);
        if (w == 0) System.arraycopy(values, a + HAND_L, out.handL, 0, 3);
        else Pose.mix(values, a + HAND_L, values, b + HAND_L, w, out.handL, 3, false);
        if (w == 0) System.arraycopy(values, a + ARM_R, out.armR, 0, 3);
        else Pose.mix(values, a + ARM_R, values, b + ARM_R, w, out.armR, 3, true);
        if (w == 0) System.arraycopy(values, a + ARM_L, out.armL, 0, 3);
        else Pose.mix(values, a + ARM_L, values, b + ARM_L, w, out.armL, 3, true);
        if (w == 0) System.arraycopy(values, a + LEG_R, out.legR, 0, 3);
        else Pose.mix(values, a + LEG_R, values, b + LEG_R, w, out.legR, 3, true);
        if (w == 0) System.arraycopy(values, a + LEG_L, out.legL, 0, 3);
        else Pose.mix(values, a + LEG_L, values, b + LEG_L, w, out.legL, 3, true);
        if (w == 0) System.arraycopy(values, a + PELVIS, out.pelvis, 0, 3);
        else Pose.mix(values, a + PELVIS, values, b + PELVIS, w, out.pelvis, 3, false);
        if (w == 0) System.arraycopy(values, a + FOOT_R, out.footR, 0, 2);
        else Pose.mix(values, a + FOOT_R, values, b + FOOT_R, w, out.footR, 2, false);
        if (w == 0) System.arraycopy(values, a + FOOT_L, out.footL, 0, 2);
        else Pose.mix(values, a + FOOT_L, values, b + FOOT_L, w, out.footL, 2, false);
        out.torsoYaw = values[a + TORSO_YAW] + (values[b + TORSO_YAW] - values[a + TORSO_YAW]) * w;
        out.torsoLean = values[a + TORSO_LEAN] + (values[b + TORSO_LEAN] - values[a + TORSO_LEAN]) * w;
        out.torsoSide = values[a + TORSO_SIDE] + (values[b + TORSO_SIDE] - values[a + TORSO_SIDE]) * w;
        out.handLFree = values[a + HAND_L_FREE] != 0;
        out.bladeDrawn = values[a + BLADE_DRAWN] != 0;
    }
}
