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

    /** Clearance has lexicographic priority; screen anchors are preferences, never permissions. */
    public static Pose solve(double targetRange, double targetHeight, double playerHeight, double pivotHeight,
        double fov, double distanceMin, double distanceMax, double pitchMin, double pitchMax, double targetY,
        double playerY, double playerTop, double margin) {
        double best = Double.POSITIVE_INFINITY, deficit = Double.POSITIVE_INFINITY;
        double bd = distanceMin, bp = pitchMin;
        for (int i = 0; i <= 40; i++) {
            double d = distanceMin + (distanceMax - distanceMin) * i / 40;
            for (int j = 0; j <= 60; j++) {
                double pitch = pitchMin + (pitchMax - pitchMin) * j / 60;
                double ty = screenY(targetRange, targetHeight, pivotHeight, d, pitch, fov);
                double py = screenY(0, playerHeight, pivotHeight, d, pitch, fov);
                double radians = Math.toRadians(pitch);
                double back = d * Math.cos(radians), cameraY = pivotHeight + d * Math.sin(radians);
                // Check the front edge of the player's .6-wide body, not only its center plane.
                double t = Math.min(1, (back + .3) / Math.max(1e-6, back + targetRange));
                double missing = Math.max(0, playerTop + margin - (cameraY + t * (targetHeight - cameraY)));
                double score = (ty - targetY) * (ty - targetY) + (py - playerY) * (py - playerY);
                if (missing < deficit - 1e-9 || Math.abs(missing - deficit) < 1e-9 && score < best) {
                    deficit = missing;
                    best = score;
                    bd = d;
                    bp = pitch;
                }
            }
        }
        return new Pose(bd, bp);
    }

    public static double sizeBlend(double height, double small, double large) {
        return Math.max(0, Math.min(1, (height - small) / Math.max(1e-6, large - small)));
    }

    public static double verticalFov(double projectionYScale) {
        return Double.isFinite(projectionYScale) && projectionYScale > 0
            ? Math.toDegrees(2 * Math.atan(1 / projectionYScale))
            : 70;
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
