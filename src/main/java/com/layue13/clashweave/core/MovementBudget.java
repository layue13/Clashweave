package com.layue13.clashweave.core;

/** Cumulative accepted movement. Vanilla collision still owns every individual position packet. */
public final class MovementBudget {

    private final long started;
    private final double speed;
    private final double total;
    private final double margin;
    private double path;
    private double vertical;
    private long sampled;

    public MovementBudget(long started, double speed, double total, double margin) {
        this.started = started;
        this.speed = speed;
        this.total = total;
        this.margin = margin;
        sampled = started;
    }

    public boolean accept(long now, double horizontalDelta, double verticalDelta, double walkingSpeed,
        double externalHorizontal, double externalVertical) {
        double ticks = Math.max(0, now - started) / 50_000_000.0;
        double root = Math.min(total, speed * (ticks + margin));
        double next = path + horizontalDelta;
        double nextY = vertical + Math.max(0, verticalDelta);
        double batchTicks = Math.max(0, now - sampled) / 50_000_000.0 + margin;
        // Walking/jumping and server knockback have separate finite allowances.
        if (horizontalDelta > (speed + walkingSpeed) * batchTicks + externalHorizontal + 0.03
            || next > root + walkingSpeed * (ticks + margin) + externalHorizontal + 0.03
            || nextY > 1.25 + 0.42 * ticks + externalVertical) return false;
        path = next;
        vertical = nextY;
        sampled = now;
        return true;
    }

    public double path() {
        return path;
    }
}
