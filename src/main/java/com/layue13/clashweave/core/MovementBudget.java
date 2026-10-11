package com.layue13.clashweave.core;

/** Cumulative accepted movement. Vanilla collision still owns every individual position packet. */
public final class MovementBudget {

    private final long started;
    private final double speed;
    private final double total;
    private final double margin;
    private double path;
    private double vertical;

    private static final class Credit {

        double remaining;
        long expires;
    }

    private final java.util.List<Credit> credits = new java.util.ArrayList<>();
    private double spentLaunch;
    private long sampled;
    private final double jump;
    private final double epsilon;

    public MovementBudget(long started, double speed, double total, double margin) {
        this(started, speed, total, margin, 1.25, 0.03);
    }

    public MovementBudget(long started, double speed, double total, double margin, double jump, double epsilon) {
        this.started = started;
        this.speed = speed;
        this.total = total;
        this.margin = margin;
        sampled = started;
        this.jump = jump;
        this.epsilon = epsilon;
    }

    public boolean accept(long now, double horizontalDelta, double verticalDelta, double walkingSpeed,
        double externalHorizontal, double externalVertical) {
        return accept(
            now,
            Math.max(0, now - started) / 50_000_000L,
            horizontalDelta,
            verticalDelta,
            walkingSpeed,
            externalHorizontal,
            externalVertical);
    }

    public boolean accept(long now, long tick, double horizontalDelta, double verticalDelta, double walkingSpeed,
        double externalHorizontal, double externalVertical) {
        credits.removeIf(credit -> tick >= credit.expires);
        double available = credits.stream()
            .mapToDouble(credit -> credit.remaining)
            .sum();
        double ticks = Math.max(0, now - started) / 50_000_000.0;
        double root = Math.min(total, speed * (ticks + margin));
        double next = path + horizontalDelta;
        double nextY = vertical + Math.max(0, verticalDelta);
        double batchTicks = Math.max(0, now - sampled) / 50_000_000.0 + margin;
        // Walking/jumping and server knockback have separate finite allowances.
        if (horizontalDelta > (speed + walkingSpeed) * batchTicks + externalHorizontal + epsilon
            || next > root + walkingSpeed * (ticks + margin) + externalHorizontal + epsilon
            || nextY > jump + externalVertical + spentLaunch + available) return false;
        double needed = Math.max(0, nextY - jump - externalVertical - spentLaunch);
        for (Credit credit : credits) {
            double used = Math.min(needed, credit.remaining);
            credit.remaining -= used;
            needed -= used;
            spentLaunch += used;
        }
        path = next;
        vertical = nextY;
        sampled = now;
        return true;
    }

    /** Delayed pre-impulse C03 ground flags cannot revoke a new velocity authorization. */
    public static double remainingVertical(double credit, double previousY, double nextY, long tick, long expires) {
        return tick >= expires ? 0 : Math.max(0, credit - Math.max(0, nextY - previousY));
    }

    /** Finite server-authorized launch/lift credit; does not enlarge walking or root allowances. */
    public void creditVertical(double amount, long expires) {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("vertical credit");
        Credit credit = new Credit();
        credit.remaining = amount;
        credit.expires = expires;
        credits.add(credit);
    }

    public double path() {
        return path;
    }
}
