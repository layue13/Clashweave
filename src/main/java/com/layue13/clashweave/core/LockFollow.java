package com.layue13.clashweave.core;

/** Camera-only policy: no action state, movement, networking or authority side effects. */
public final class LockFollow {

    public enum Mode {
        OFF,
        WEAK,
        STRONG
    }

    public static final class Settings {

        public long graceNanos = 250_000_000L;
        public long lostNanos = 1_000_000_000L;
        public double weakSpeed = 180;
        public double weakPitchSpeed = 180;
        public double deadZone = 8;
        public double strongSpeed = 720;
        public double maxOffset = 30;
        public double decay = 120;
        public double pitchMin = -45;
        public double pitchMax = 60;
    }

    public static final class Result {

        public final float yaw;
        public final float pitch;
        public final double offset;
        public final String reason;

        Result(float yaw, float pitch, double offset, String reason) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.offset = offset;
            this.reason = reason;
        }
    }

    private long lastMouse = Long.MIN_VALUE / 2;
    private long hiddenSince = -1;
    private double offset;
    private boolean mousePending;
    private boolean strongControlled;
    private float strongYaw;
    private float strongPitch;

    public void mouse(long now, double yawDelta, Settings settings) {
        lastMouse = now;
        mousePending = true;
        if (Double.isFinite(yawDelta)) offset = clamp(offset + yawDelta, settings.maxOffset);
    }

    /** Preserve mouse grace when handing observation back from third-person control. */
    public void noteMouse(long now) {
        lastMouse = now;
    }

    public void clear() {
        strongControlled = false;
        offset = 0;
        hiddenSince = -1;
        mousePending = false;
    }

    public Result step(Mode mode, long now, double seconds, float yaw, float pitch, double targetYaw,
        double targetPitch, boolean visible, boolean enabled, boolean weakAllowed, Settings settings) {
        if (visible) hiddenSince = -1;
        else if (hiddenSince < 0) hiddenSince = now;
        boolean pending = mousePending;
        mousePending = false;
        String stop = !enabled ? "INACTIVE"
            : mode == Mode.OFF ? "OFF"
                : !visible && now - hiddenSince >= settings.lostNanos ? "OCCLUDED"
                    : !Float.isFinite(yaw) || !Float.isFinite(pitch)
                        || !Double.isFinite(targetYaw)
                        || !Double.isFinite(targetPitch) ? "INVALID"
                            : mode == Mode.WEAK && !weakAllowed ? "COMMITTED"
                                : mode == Mode.WEAK && now - lastMouse < settings.graceNanos ? "MOUSE" : "";
        if (!stop.isEmpty()) {
            strongControlled = false;
            return new Result(yaw, pitch, offset, stop);
        }
        // Cap elapsed credit to one tick: stalls and mode changes cannot accumulate a snap.
        double dt = Math.max(0, Math.min(.05, seconds));
        if (mode == Mode.WEAK) {
            strongControlled = false;
            double delta = FacingHistory.difference((float) targetYaw, yaw);
            double dot = Math.sin(Math.toRadians(pitch)) * Math.sin(Math.toRadians(targetPitch))
                + Math.cos(Math.toRadians(pitch)) * Math.cos(Math.toRadians(targetPitch))
                    * Math.cos(Math.toRadians(delta));
            double viewAngle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
            if (viewAngle > 120) return new Result(yaw, pitch, offset, "BEHIND");
            double dy = Math.abs(delta) <= settings.deadZone ? 0
                : clamp(delta - Math.copySign(settings.deadZone, delta), settings.weakSpeed * dt);
            double dp = targetPitch - pitch;
            dp = Math.abs(dp) <= settings.deadZone ? 0
                : clamp(dp - Math.copySign(settings.deadZone, dp), settings.weakPitchSpeed * dt);
            return new Result((float) (yaw + dy), (float) (pitch + dp), offset, "WEAK");
        }
        if (!pending) offset -= clamp(offset, settings.decay * dt);
        // Vanilla already applied mouse deltas. Use their bounded offset, rather than
        // granting another rotation credit on top of the previous controlled output.
        float baseYaw = strongControlled ? strongYaw : yaw;
        float basePitch = strongControlled ? strongPitch : pitch;
        double dy = FacingHistory.difference((float) (targetYaw + offset), baseYaw);
        double desiredPitch = Math.max(settings.pitchMin, Math.min(settings.pitchMax, targetPitch));
        strongYaw = (float) (baseYaw + clamp(dy, settings.strongSpeed * dt));
        strongPitch = (float) (basePitch + clamp(desiredPitch - basePitch, settings.strongSpeed * dt));
        strongControlled = true;
        return new Result(strongYaw, strongPitch, offset, "STRONG");
    }

    private static double clamp(double value, double maximum) {
        return Math.max(-maximum, Math.min(maximum, value));
    }
}
