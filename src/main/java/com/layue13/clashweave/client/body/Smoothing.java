package com.layue13.clashweave.client.body;

/**
 * Frame-rate independent chasing: a value moves toward its target by a fixed fraction per tick, so one 50 ms frame and
 * two 25 ms frames land in the same place. Targets are recomputed every frame and the value only ever chases them,
 * which
 * is what lets one pose melt into another instead of cutting.
 */
public final class Smoothing {

    private Smoothing() {}

    /** Fraction of the remaining distance covered in {@code ticks}, when {@code perTick} of it is covered per tick. */
    public static float alpha(float perTick, double ticks) {
        if (perTick >= 1 || ticks >= 8) return 1;
        if (perTick <= 0 || ticks <= 0) return 0;
        return (float) (1 - Math.pow(1 - perTick, ticks));
    }

    public static float toward(float from, float to, float alpha) {
        return from + (to - from) * alpha;
    }

    /** Along the shorter way around, so a chase never spins a limb through a full turn. */
    public static float angleToward(float from, float to, float alpha) {
        float delta = (float) (to - from - Math.PI * 2 * Math.floor((to - from + Math.PI) / (Math.PI * 2)));
        return from + delta * alpha;
    }
}
