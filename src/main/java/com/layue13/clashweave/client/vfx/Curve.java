package com.layue13.clashweave.client.vfx;

/** A value over a lifetime from 0 to 1, resampled once into a fixed table so a lookup is two reads and a blend. */
public final class Curve {

    private static final int STEPS = 16;
    private final float[] table = new float[STEPS + 1];

    /** {@code points} are evenly spaced values over the lifetime; one value is a constant. */
    public Curve(float... points) {
        if (points == null || points.length == 0) throw new IllegalArgumentException("curve needs at least one value");
        for (int i = 0; i <= STEPS; i++) {
            if (points.length == 1) {
                table[i] = points[0];
                continue;
            }
            float position = (float) i / STEPS * (points.length - 1);
            int low = Math.min(points.length - 2, (int) position);
            float f = position - low;
            table[i] = points[low] + (points[low + 1] - points[low]) * f;
        }
    }

    public float at(float t) {
        if (t <= 0) return table[0];
        if (t >= 1) return table[STEPS];
        float position = t * STEPS;
        int low = (int) position;
        float f = position - low;
        return table[low] + (table[low + 1] - table[low]) * f;
    }
}
