package com.layue13.clashweave.core;

/** Bounded horizontal commitment assistance, after authenticated press-time facing. */
public final class LockAssist {

    private LockAssist() {}

    public static float yaw(float pressed, double dx, double dz, double cone, double maximum) {
        if (!Float.isFinite(pressed) || !Double.isFinite(dx)
            || !Double.isFinite(dz)
            || !Double.isFinite(cone)
            || !Double.isFinite(maximum)
            || cone < 0
            || maximum < 0
            || Math.hypot(dx, dz) < 1e-8) return pressed;
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        double difference = FacingHistory.difference((float) target, pressed);
        if (Math.abs(difference) > cone) return pressed;
        return (float) (pressed + Math.max(-maximum, Math.min(maximum, difference)));
    }

    public static boolean valid(boolean armed, int target, boolean alive, double distance, double range) {
        return armed && target >= 0 && alive && Double.isFinite(distance) && distance <= range;
    }
}
