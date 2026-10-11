package com.layue13.clashweave.core;

import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded read-only packet history; no world or Minecraft dependency. */
public final class FacingHistory {

    private static final class Sample {

        long time;
        long angularTime;
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

    private final java.util.LinkedHashMap<Integer, Long> requests = new java.util.LinkedHashMap<>();

    public synchronized void requestReceived(int sequence, long time) {
        if (!requests.containsKey(sequence)) requests.put(sequence, time);
        while (requests.size() > 512) requests.remove(
            requests.keySet()
                .iterator()
                .next());
    }

    public synchronized long requestArrival(int sequence) {
        Long time = requests.remove(sequence);
        return time == null ? 0 : time;
    }

    private final Deque<Sample> samples = new ArrayDeque<>();

    public synchronized void observe(long time, float yaw, float pitch, double x, double y, double z, boolean rotating,
        boolean moving) {
        Sample old = samples.peekLast();
        if (!rotating && old == null) return;
        Sample s = new Sample();
        s.time = time;
        s.angularTime = rotating ? time : old.angularTime;
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
        double y, double z, double tolerance, long maxAge, double drift) {
        String reason = "NO_HISTORY";
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(pitch) > 90)
            return new Result(false, knownYaw, knownPitch, "INVALID_ANGLE", yaw, pitch);
        boolean past = false, recent = false, nearby = false;
        for (Sample s : samples) {
            if (s.time > arrival) continue;
            past = true;
            if (arrival - s.angularTime > maxAge) continue;
            recent = true;
            if (Math.sqrt((x - s.x) * (x - s.x) + (y - s.y) * (y - s.y) + (z - s.z) * (z - s.z)) > drift) continue;
            nearby = true;
            if (Math.abs(s.pitch) <= 90 && Math.abs(difference(yaw, s.yaw)) <= tolerance
                && Math.abs(pitch - s.pitch) <= tolerance) return new Result(true, yaw, pitch, "OK", yaw, pitch);
        }
        if (past) reason = !recent ? "OLD_HISTORY" : !nearby ? "POSITION_HISTORY" : "HISTORY_MISMATCH";
        return new Result(false, knownYaw, knownPitch, reason, yaw, pitch);
    }
}
