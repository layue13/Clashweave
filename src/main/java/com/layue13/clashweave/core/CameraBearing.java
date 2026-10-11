package com.layue13.clashweave.core;

/** Render-only velocity estimate. Discontinuities discard prediction, never add an angular impulse. */
public final class CameraBearing {

    private boolean ready, lostHistory;
    private double previous, x, z, velocity, suppressed, rawSeconds, rawVx, rawVz;

    public void clear() {
        lostHistory |= ready;
        ready = false;
        velocity = 0;
        suppressed = 0;
        rawSeconds = rawVx = rawVz = 0;
    }

    public double lead(double bearing, double rawX, double rawZ, double seconds, double smooth, double filterTau,
        double maxSpeed, double teleportDistance) {
        return lead(bearing, rawX, rawZ, seconds, smooth, filterTau, maxSpeed, teleportDistance, .1, .3);
    }

    public double lead(double bearing, double rawX, double rawZ, double seconds, double smooth, double filterTau,
        double maxSpeed, double teleportDistance, double stallSeconds, double stallRecovery) {
        double realSeconds = Double.isFinite(seconds) ? Math.max(0, seconds) : 0;
        double dt = ThirdPersonCamera.dt(seconds);
        double change = FacingHistory.difference((float) bearing, (float) previous);
        // Raw sample age is wall time, not the capped integration credit of a stalled render frame.
        rawSeconds += realSeconds;
        boolean moved = Math.hypot(rawX - x, rawZ - z) > 1e-6;
        // Compare with the last observed velocity, allowing ordinary batched position samples.
        // An initially stationary target still has zero velocity and cannot explain a teleport.
        if (rawSeconds > .5) rawVx = rawVz = 0;
        boolean jump = !ready
            || moved && Math.hypot(rawX - x - rawVx * rawSeconds, rawZ - z - rawVz * rawSeconds) > teleportDistance
            || Math.abs(change) > Math.max(30, maxSpeed * realSeconds * 2);
        suppressed = Math.max(0, suppressed - realSeconds);
        // A switched target keeps the position filter; its catch-up is not target velocity.
        // The very first sample needs no recovery window because that filter starts at the target.
        if (jump && (ready || lostHistory)) suppressed = Math.max(suppressed, 3 * (smooth + filterTau));
        boolean stalled = realSeconds > stallSeconds;
        // Recovery starts after the stalled frame; it is a wall-clock duration, not integration credit.
        if (stalled) suppressed = Math.max(suppressed, stallRecovery);
        if (jump || stalled || suppressed > 0 || dt == 0 || !Double.isFinite(bearing)) velocity = 0;
        else {
            double rate = ThirdPersonCamera.clamp(change / Math.max(1e-6, realSeconds), maxSpeed);
            velocity += (rate - velocity) * -Math.expm1(-dt / .05);
        }
        if (moved && ready && rawSeconds > 0) {
            rawVx = (rawX - x) / rawSeconds;
            rawVz = (rawZ - z) / rawSeconds;
        }
        if (moved || !ready) {
            x = rawX;
            z = rawZ;
            rawSeconds = 0;
        }
        ready = Double.isFinite(bearing);
        lostHistory = false;
        previous = bearing;
        return jump || stalled || suppressed > 0 ? 0 : ThirdPersonCamera.clamp(velocity * (smooth + filterTau), 45);
    }
}
