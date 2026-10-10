package com.layue13.clashweave.core;

/** Render-only velocity estimate. Discontinuities discard prediction, never add an angular impulse. */
public final class CameraBearing {

    private boolean ready;
    private double previous, x, z, velocity, suppressed, rawSeconds, rawVx, rawVz;

    public void clear() {
        ready = false;
        velocity = 0;
        suppressed = 0;
        rawSeconds = rawVx = rawVz = 0;
    }

    public double lead(double bearing, double rawX, double rawZ, double seconds, double smooth, double filterTau,
        double maxSpeed, double teleportDistance) {
        double dt = ThirdPersonCamera.dt(seconds);
        double change = FacingHistory.difference((float) bearing, (float) previous);
        // Raw sample age is wall time, not the capped integration credit of a stalled render frame.
        rawSeconds += Double.isFinite(seconds) ? Math.max(0, seconds) : 0;
        boolean moved = Math.hypot(rawX - x, rawZ - z) > 1e-6;
        // Compare with the last observed velocity, allowing ordinary batched position samples.
        // An initially stationary target still has zero velocity and cannot explain a teleport.
        if (rawSeconds > .5) rawVx = rawVz = 0;
        boolean jump = !ready
            || moved && Math.hypot(rawX - x - rawVx * rawSeconds, rawZ - z - rawVz * rawSeconds) > teleportDistance
            || Math.abs(change) > Math.max(30, maxSpeed * dt * 2);
        if (jump && ready) suppressed = 3 * (smooth + filterTau);
        suppressed = Math.max(0, suppressed - dt);
        if (jump || suppressed > 0 || dt == 0 || !Double.isFinite(bearing)) velocity = 0;
        else {
            double rate = ThirdPersonCamera.clamp(change / dt, maxSpeed);
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
        previous = bearing;
        return jump || suppressed > 0 ? 0 : ThirdPersonCamera.clamp(velocity * (smooth + filterTau), 45);
    }
}
