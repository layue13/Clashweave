package com.layue13.clashweave.client.pose;

/** Immutable packed samples. No per-frame lookup, iterator or scratch object is needed. */
public final class Track {

    static final int STRIDE = 35;
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
        if (w == 0) System.arraycopy(values, a + 0, out.swordP, 0, 3);
        else Pose.mix(values, a + 0, values, b + 0, w, out.swordP, 3, false);
        if (w == 0) System.arraycopy(values, a + 3, out.swordQ, 0, 4);
        else Pose.quaternion(values, a + 3, values, b + 3, w, out.swordQ);
        if (w == 0) System.arraycopy(values, a + 7, out.handR, 0, 3);
        else Pose.mix(values, a + 7, values, b + 7, w, out.handR, 3, false);
        if (w == 0) System.arraycopy(values, a + 10, out.handL, 0, 3);
        else Pose.mix(values, a + 10, values, b + 10, w, out.handL, 3, false);
        if (w == 0) System.arraycopy(values, a + 13, out.armR, 0, 3);
        else Pose.mix(values, a + 13, values, b + 13, w, out.armR, 3, true);
        if (w == 0) System.arraycopy(values, a + 16, out.armL, 0, 3);
        else Pose.mix(values, a + 16, values, b + 16, w, out.armL, 3, true);
        if (w == 0) System.arraycopy(values, a + 19, out.legR, 0, 3);
        else Pose.mix(values, a + 19, values, b + 19, w, out.legR, 3, true);
        if (w == 0) System.arraycopy(values, a + 22, out.legL, 0, 3);
        else Pose.mix(values, a + 22, values, b + 22, w, out.legL, 3, true);
        if (w == 0) System.arraycopy(values, a + 25, out.pelvis, 0, 3);
        else Pose.mix(values, a + 25, values, b + 25, w, out.pelvis, 3, false);
        if (w == 0) System.arraycopy(values, a + 28, out.footR, 0, 2);
        else Pose.mix(values, a + 28, values, b + 28, w, out.footR, 2, false);
        if (w == 0) System.arraycopy(values, a + 30, out.footL, 0, 2);
        else Pose.mix(values, a + 30, values, b + 30, w, out.footL, 2, false);
        out.torsoYaw = values[a + 32] + (values[b + 32] - values[a + 32]) * w;
        out.handLFree = values[a + 33] != 0;
        out.bladeDrawn = values[a + 34] != 0;
    }
}
