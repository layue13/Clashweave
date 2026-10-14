package com.layue13.clashweave.core;

/**
 * The mode gauge of one player: it fills from exchanges (blows that land, guarded blows, damage taken), each source
 * rate-limited, and spends itself in one go to start a timed mode, during which it does not fill. Pure logic: the
 * caller
 * supplies the tick and decides what counts as one exchange.
 */
public final class Gauge {

    public enum Source {
        HIT,
        BLOCK,
        PARRY,
        HURT
    }

    private final int max;
    private final long[] interval = new long[Source.values().length];
    private final long[] last = new long[Source.values().length];
    private float value;
    private long modeUntil = -1;

    /** {@code intervals} are the minimum ticks between two credits of each source, in {@link Source} order. */
    public Gauge(int max, long... intervals) {
        if (max < 1 || intervals.length != interval.length) throw new IllegalArgumentException("gauge configuration");
        this.max = max;
        for (int i = 0; i < interval.length; i++) {
            if (intervals[i] < 0) throw new IllegalArgumentException("gauge configuration");
            interval[i] = intervals[i];
            last[i] = Long.MIN_VALUE / 2;
        }
    }

    public boolean inMode(long tick) {
        return tick < modeUntil;
    }

    public int modeLeft(long tick) {
        return inMode(tick) ? (int) (modeUntil - tick) : 0;
    }

    public float value() {
        return value;
    }

    public boolean full() {
        return value >= max;
    }

    /** Credits an exchange. Refused during a mode and inside the source's minimum interval. */
    public boolean gain(Source source, float amount, long tick) {
        if (amount <= 0 || inMode(tick)) return false;
        int i = source.ordinal();
        if (tick - last[i] < interval[i]) return false;
        last[i] = tick;
        value = Math.min(max, value + amount);
        return true;
    }

    /** Starts the mode when the gauge is full and no mode runs; the gauge empties. */
    public boolean activate(long tick, int duration) {
        if (!full() || inMode(tick) || duration < 1) return false;
        value = 0;
        modeUntil = tick + duration;
        return true;
    }

    public void reset() {
        value = 0;
        modeUntil = -1;
    }
}
