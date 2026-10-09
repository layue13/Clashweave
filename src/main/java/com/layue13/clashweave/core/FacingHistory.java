package com.layue13.clashweave.core;

import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded read-only packet history; no world or Minecraft dependency. */
public final class FacingHistory {

    private static final class Sample {

        long time;
        long angularTime, previousAngularTime;
        float previousYaw, previousPitch;
        float yaw, pitch;
        double x, y, z;
    }

    public static final class Result {

        public final boolean accepted;
        public final float yaw, pitch, requestedYaw, requestedPitch;
        public final String reason;

        Result(boolean accepted, float yaw, float pitch, String reason, float requestedYaw, float requestedPitch) {
            this.accepted = accepted;
            this.yaw = yaw;
            this.pitch = pitch;
            this.reason = reason;
            this.requestedYaw = requestedYaw;
            this.requestedPitch = requestedPitch;
        }
    }

    private final Deque<Sample> samples = new ArrayDeque<>();

    public synchronized void observe(long time, float yaw, float pitch, double x, double y, double z, boolean rotating,
        boolean moving) {
        Sample old = samples.peekLast();
        if (!rotating && old == null) return;
        Sample s = new Sample();
        s.time = time;
        s.angularTime = rotating ? time : old.angularTime;
        s.previousAngularTime = rotating ? old == null ? time : old.angularTime : old.previousAngularTime;
        s.previousYaw = rotating ? old == null ? yaw : old.yaw : old.previousYaw;
        s.previousPitch = rotating ? old == null ? pitch : old.pitch : old.previousPitch;
        s.yaw = rotating ? yaw : old.yaw;
        s.pitch = rotating ? pitch : old.pitch;
        s.x = moving ? x : old == null ? 0 : old.x;
        s.y = moving ? y : old == null ? 0 : old.y;
        s.z = moving ? z : old == null ? 0 : old.z;
        if (!Float.isFinite(s.yaw) || !Float.isFinite(s.pitch)
            || !Double.isFinite(s.x)
            || !Double.isFinite(s.y)
            || !Double.isFinite(s.z)) return;
        samples.addLast(s);
        while (samples.size() > 64) samples.removeFirst();
    }

    public static double difference(double a, double b) {
        return ((a - b) % 360 + 540) % 360 - 180;
    }

    public synchronized Result choose(float yaw, float pitch, long arrival, float knownYaw, float knownPitch, double x,
        double y, double z, double maxDegreesPerTick, double tolerance, long maxAge, double drift) {
        String reason = "NO_HISTORY";
        Sample s = null;
        Sample turnBase = null;
        for (Sample candidate : samples) {
            if (turnBase == null) turnBase = candidate;
            if (candidate.time <= arrival - 50_000_000L) turnBase = candidate;
        }
        for (Sample candidate : samples) if (candidate.time <= arrival) s = candidate;
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(pitch) > 90) reason = "INVALID_ANGLE";
        else if (s != null) {
            if (arrival - s.time > maxAge) reason = "OLD_HISTORY";
            else if (Math.sqrt((x - s.x) * (x - s.x) + (y - s.y) * (y - s.y) + (z - s.z) * (z - s.z)) > drift)
                reason = "POSITION_HISTORY";
            else if (turnBase != null && (Math.abs(difference(s.yaw, turnBase.yaw))
                > maxDegreesPerTick * Math.max(1, (arrival - turnBase.time) / 50_000_000.0) + tolerance
                || Math.abs(s.pitch - turnBase.pitch)
                    > maxDegreesPerTick * Math.max(1, (arrival - turnBase.time) / 50_000_000.0) + tolerance))
                reason = "HISTORY_TURN_RATE";
            else if (Math.abs(difference(s.yaw, s.previousYaw))
                > maxDegreesPerTick * Math.max(1, (s.angularTime - s.previousAngularTime) / 50_000_000.0) + tolerance
                || Math.abs(s.pitch - s.previousPitch)
                    > maxDegreesPerTick * Math.max(1, (s.angularTime - s.previousAngularTime) / 50_000_000.0)
                        + tolerance)
                reason = "HISTORY_TURN_RATE";
            else {
                double allowance = maxDegreesPerTick * Math.max(1, (arrival - s.time) / 50_000_000.0) + tolerance;
                if (Math.abs(difference(yaw, s.yaw)) <= allowance && Math.abs(pitch - s.pitch) <= allowance)
                    return new Result(true, yaw, pitch, "OK", yaw, pitch);
                reason = "TURN_RATE";
            }
        }
        return new Result(false, knownYaw, knownPitch, reason, yaw, pitch);
    }
}
