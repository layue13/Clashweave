package com.layue13.clashweave.core;

/**
 * One entity's stance stability, 0 to {@code max}. Blows and blocked blows wear it down; left alone for a while it
 * recovers; at zero the entity is broken for a limited time, during which it takes no further posture damage, and
 * then comes back at a fraction of its stability. Pure logic: the caller supplies the tick.
 */
public final class Posture {

    private final int max, recoveryDelay, recoveryPerTick, resetPercent;
    private float value;
    private long lastDamage = Long.MIN_VALUE / 2, brokenUntil = -1;

    public Posture(int max, int recoveryDelay, int recoveryPerTick, int resetPercent) {
        if (max < 1 || recoveryDelay < 0 || recoveryPerTick < 0 || resetPercent < 1 || resetPercent > 100)
            throw new IllegalArgumentException("posture configuration");
        this.max = max;
        this.recoveryDelay = recoveryDelay;
        this.recoveryPerTick = recoveryPerTick;
        this.resetPercent = resetPercent;
        value = max;
    }

    /** Whether the entity is broken at {@code tick}. */
    public boolean broken(long tick) {
        return tick < brokenUntil;
    }

    public float value() {
        return value;
    }

    /**
     * Wears the stability down. Returns true when this blow breaks it (the caller announces that once). Ignored while
     * broken.
     */
    public boolean damage(float amount, long tick, int breakTicks) {
        if (amount <= 0 || broken(tick)) return false;
        lastDamage = tick;
        value -= amount;
        if (value > 0) return false;
        value = 0;
        brokenUntil = tick + breakTicks;
        return true;
    }

    /** Recovers stability once undisturbed; a finished break restores a share of it. Call once per tick. */
    public void advance(long tick) {
        if (brokenUntil >= 0 && tick >= brokenUntil) {
            brokenUntil = -1;
            value = max * resetPercent / 100f;
            lastDamage = tick;
        }
        if (broken(tick) || value >= max || tick - lastDamage < recoveryDelay) return;
        value = Math.min(max, value + recoveryPerTick);
    }

    /** Ends a running break now: the next {@link #advance} restores the share of stability a finished break gives. */
    public void endBreak(long tick) {
        if (broken(tick)) brokenUntil = tick;
    }

    /** At full stability and not broken: nothing to keep. */
    public boolean settled(long tick) {
        return value >= max && !broken(tick);
    }
}
