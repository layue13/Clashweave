package com.layue13.clashweave.core;

/** Pure projection geometry. Values are experimental starts, not measured Souls game data. */
public final class SoulsComposition {

    private SoulsComposition() {}

    public static final class Pose {

        public final double distance, pitch;

        Pose(double distance, double pitch) {
            this.distance = distance;
            this.pitch = pitch;
        }
    }

    /** Bounded least-squares search keeps composition subordinate to configured camera limits. */
    public static Pose solve(double targetRange, double targetHeight, double playerHeight, double pivotHeight,
        double fov, double distance, double distanceAdjustment, double baselinePitch, double pitchAdjustment,
        double targetY, double playerY) {
        double best = Double.POSITIVE_INFINITY, bd = distance, bp = baselinePitch;
        for (int i = 0; i <= 40; i++) {
            double d = Math.max(.2, distance - distanceAdjustment + 2 * distanceAdjustment * i / 40);
            for (int j = 0; j <= 60; j++) {
                double pitch = Math
                    .max(-60, Math.min(75, baselinePitch - pitchAdjustment + 2 * pitchAdjustment * j / 60));
                double ty = screenY(targetRange, targetHeight, pivotHeight, d, pitch, fov);
                double py = screenY(0, playerHeight, pivotHeight, d, pitch, fov);
                double score = (ty - targetY) * (ty - targetY) + (py - playerY) * (py - playerY);
                if (score < best) {
                    best = score;
                    bd = d;
                    bp = pitch;
                }
            }
        }
        return new Pose(bd, bp);
    }

    /** Camera orbits the raised pivot. Screen y grows downward. */
    public static double screenY(double range, double pointHeight, double pivotHeight, double distance,
        double pitchDegrees, double verticalFov) {
        double pitch = Math.toRadians(pitchDegrees), a = pivotHeight - pointHeight;
        double depth = distance + range * Math.cos(pitch) + a * Math.sin(pitch);
        if (depth <= 1e-6) return Double.NaN;
        return .5
            + (a * Math.cos(pitch) - range * Math.sin(pitch)) / (2 * Math.tan(Math.toRadians(verticalFov) / 2) * depth);
    }

    /** True only for a finite, visible-depth point covered by a projected body rectangle. */
    public static boolean covered(double minX, double minY, double maxX, double maxY, double x, double y,
        double depth) {
        return Double.isFinite(minX) && Double.isFinite(minY)
            && Double.isFinite(maxX)
            && Double.isFinite(maxY)
            && Double.isFinite(x)
            && Double.isFinite(y)
            && Double.isFinite(depth)
            && depth >= 0
            && depth <= 1
            && x >= minX
            && x <= maxX
            && y >= minY
            && y <= maxY;
    }

    /** Safety is stronger than smoothing when a new obstruction suddenly appears. */
    public static double safeFraction(double previous, double desired, double seconds, double smooth,
        ThirdPersonCamera.Spring spring) {
        return Math.max(0, Math.min(desired, spring.step(previous, desired, seconds, smooth, 4)));
    }

    public static boolean hideModel(boolean active, boolean hidden, double distance, double threshold,
        double hysteresis) {
        return active && Double.isFinite(distance)
            && distance >= 0
            && threshold > 0
            && distance < (hidden ? threshold + Math.max(0, hysteresis) : threshold);
    }
}
