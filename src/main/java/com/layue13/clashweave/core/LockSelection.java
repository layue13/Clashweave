package com.layue13.clashweave.core;

/** Pure lock policy: horizontal angle/range dominate, stable numeric identity tie break. */
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

    /** Vertical angle is admission first, with only a one-percent normalized scoring contribution. */
    public static double acquisitionScore(double distance, double horizontal, double vertical, double heightDelta,
        double range, double horizontalCone, double verticalCone) {
        if (!Double.isFinite(vertical) || !Double.isFinite(heightDelta)
            || !Double.isFinite(range)
            || !Double.isFinite(horizontalCone)
            || !Double.isFinite(verticalCone)
            || range <= 0
            || horizontalCone <= 0
            || verticalCone <= 0
            || vertical < 0
            || vertical > verticalCone + 1e-5
            || Math.abs(heightDelta) > range / 2) return Double.POSITIVE_INFINITY;
        return score(distance, horizontal, range, horizontalCone) + .01 * vertical / verticalCone;
    }

    /** Wrapped yaw and elevation are independent; elevation uses the target bounding-box center. */
    public static double[] angles(double dx, double centerDy, double dz, float yaw, float pitch) {
        double horizontalRange = Math.hypot(dx, dz);
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));
        double horizontal = horizontalRange < 1e-9 ? 0 : Math.abs(FacingHistory.difference(bearing, yaw));
        double vertical = Math.abs(Math.toDegrees(Math.atan2(centerDy, horizontalRange)) + pitch);
        return new double[] { horizontal, vertical };
    }

    public static boolean keep(double distance, double range, boolean alive) {
        return alive && Double.isFinite(distance) && distance <= range;
    }
}
