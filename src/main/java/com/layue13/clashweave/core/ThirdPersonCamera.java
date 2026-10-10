package com.layue13.clashweave.core;

/** Local third-person composition primitives; no Minecraft or gameplay dependencies. */
public final class ThirdPersonCamera {

    public enum Preset {
        OFF,
        SOULS
    }

    private ThirdPersonCamera() {}

    /** Old composition names migrate to SOULS; no legacy preset remains selectable. */
    public static Preset parse(String text) {
        return "OFF".equalsIgnoreCase(text) ? Preset.OFF : Preset.SOULS;
    }

    public static double clamp(double value, double maximum) {
        return Math.max(-maximum, Math.min(maximum, value));
    }

    public static double dt(double seconds) {
        return Double.isFinite(seconds) ? Math.max(0, Math.min(.05, seconds)) : 0;
    }

    /** Exact critically damped spring under a piecewise constant goal, then a speed cap. */
    public static final class Spring {

        private double velocity;

        public void clear() {
            velocity = 0;
        }

        public double step(double current, double goal, double seconds, double smoothTime, double speed) {
            double dt = dt(seconds);
            if (dt == 0) return current;
            double omega = 2 / Math.max(.03, Math.min(.5, smoothTime));
            double error = current - goal;
            double temp = (velocity + omega * error) * dt;
            double decay = Math.exp(-omega * dt);
            double next = goal + (error + temp) * decay;
            velocity = (velocity - omega * temp) * decay;
            if ((goal - current) * (next - goal) > 0) {
                next = goal;
                velocity = 0;
            }
            double change = next - current;
            double bounded = clamp(change, Math.max(0, speed) * dt);
            if (bounded != change) velocity = bounded / dt;
            return current + bounded;
        }
    }

    public static final class Filter {

        private boolean initialized;
        public double x, y, z;

        public void clear() {
            initialized = false;
        }

        public void step(double nx, double ny, double nz, double seconds, double tau) {
            if (!initialized) {
                x = nx;
                y = ny;
                z = nz;
                initialized = true;
                return;
            }
            double alpha = tau <= 0 ? 1 : -Math.expm1(-dt(seconds) / tau);
            x += alpha * (nx - x);
            y += alpha * (ny - y);
            z += alpha * (nz - z);
        }
    }

    public static final class Flick {

        private long beginning = -1, last = Long.MIN_VALUE / 2;
        private double sum;

        public void clear() {
            beginning = -1;
            sum = 0;
        }

        public int add(long now, double delta, long window, double threshold, long cooldown) {
            if (!Double.isFinite(delta) || delta == 0) return 0;
            if (beginning < 0 || now - beginning > window || sum * delta < 0) {
                beginning = now;
                sum = 0;
            }
            sum += delta;
            if (Math.abs(sum) < threshold) return 0;
            int direction = sum > 0 ? 1 : -1;
            sum = 0;
            beginning = now;
            if (now - last < cooldown) return 0;
            last = now;
            return direction;
        }
    }

    /** Pick the nearest angular neighbour on the requested side, with deterministic ties. */
    public static double switchScore(float currentYaw, double dx, double dz, int direction, double distance) {
        if (direction != -1 && direction != 1 || Math.hypot(dx, dz) < 1e-8) return Double.POSITIVE_INFINITY;
        double delta = FacingHistory.difference((float) Math.toDegrees(Math.atan2(-dx, dz)), currentYaw) * direction;
        return delta > .01 && delta <= 180 ? delta + distance * .001 : Double.POSITIVE_INFINITY;
    }
}
