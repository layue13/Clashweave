package com.layue13.clashweave.core;

/**
 * The shapes an action's blow can take, declared by data ({@code shape} in the action) and tested by the server:
 * a {@link #SECTOR} (the blade swings through an arc), a {@link #THRUST} (a straight line of some width that reaches
 * out as the blow goes on) and a {@link #BURST} (a circle on the ground ahead of the attacker). This class holds the
 * flat geometry only; heights and walls are the server's business.
 */
public final class HitShapes {

    public static final String SECTOR = "sector", THRUST = "thrust", BURST = "burst";

    private HitShapes() {}

    public static boolean known(String shape) {
        return SECTOR.equals(shape) || THRUST.equals(shape) || BURST.equals(shape);
    }

    /** Horizontal distance from the point to the box; 0 when it is inside. */
    public static double pointToBox(double px, double pz, double minX, double minZ, double maxX, double maxZ) {
        double dx = Math.max(Math.max(minX - px, 0), px - maxX);
        double dz = Math.max(Math.max(minZ - pz, 0), pz - maxZ);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Shortest horizontal distance from the segment that starts at (ox, oz), runs along the unit direction (dx, dz) for
     * {@code length}, to the box. The distance from a point moving along a line to a convex box is convex, so a
     * ternary search finds the minimum; no allocation.
     */
    public static double segmentToBox(double ox, double oz, double dx, double dz, double length, double minX,
        double minZ, double maxX, double maxZ) {
        double low = 0, high = Math.max(0, length);
        for (int i = 0; i < 48; i++) {
            double a = low + (high - low) / 3, b = high - (high - low) / 3;
            double da = pointToBox(ox + dx * a, oz + dz * a, minX, minZ, maxX, maxZ);
            double db = pointToBox(ox + dx * b, oz + dz * b, minX, minZ, maxX, maxZ);
            if (da < db) high = b;
            else low = a;
        }
        return pointToBox(ox + dx * low, oz + dz * low, minX, minZ, maxX, maxZ);
    }

    /**
     * A thrust of the given width has reached the box when the line it has drawn so far passes within half the width.
     */
    public static boolean thrustHits(double ox, double oz, double dx, double dz, double reach, double progress,
        double width, double minX, double minZ, double maxX, double maxZ) {
        double length = reach * Math.max(0, Math.min(1, progress));
        return segmentToBox(ox, oz, dx, dz, length, minX, minZ, maxX, maxZ) <= width / 2;
    }

    /** A burst centred {@code offset} ahead of the attacker has reached the box when it lies within {@code radius}. */
    public static boolean burstHits(double ox, double oz, double dx, double dz, double offset, double radius,
        double minX, double minZ, double maxX, double maxZ) {
        return pointToBox(ox + dx * offset, oz + dz * offset, minX, minZ, maxX, maxZ) <= radius;
    }
}
