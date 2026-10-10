package com.layue13.clashweave.core;

/** Pure lock policy: equal normalized angle/range weights, stable numeric identity tie break. */
public final class LockSelection {

    private LockSelection() {}

    public static double score(double distance, double angle, double range, double halfAngle) {
        if (!Double.isFinite(distance) || !Double.isFinite(angle)
            || distance < 0
            || angle < 0
            || distance > range
            || angle > halfAngle + 1e-5) return Double.POSITIVE_INFINITY;
        return distance / range + angle / halfAngle;
    }

    public static boolean better(double score, int identity, double best, int bestIdentity) {
        return Double.isFinite(score)
            && (Double.compare(score, best) < 0 || Double.compare(score, best) == 0 && identity < bestIdentity);
    }

    public static boolean keep(double distance, double range, boolean alive) {
        return alive && Double.isFinite(distance) && distance <= range;
    }
}
