package com.layue13.clashweave.core;

/** One-shot, connection-owned marker. All times are receipt-side monotonic nanoseconds. */
public final class CorrectionMarkers {

    public static final long LIFETIME = 250_000_000L;
    private long latest, pending, deadline;
    private double x, y, z;

    public synchronized boolean mark(long sequence, long now, double x, double y, double z) {
        if (sequence <= latest || sequence <= 0 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            return false;
        latest = pending = sequence;
        deadline = now + LIFETIME;
        this.x = x;
        this.y = y;
        this.z = z;
        return true;
    }

    public synchronized long consume(long now, double x, double y, double z) {
        long id = pending;
        pending = 0;
        return id != 0 && now <= deadline
            && Math.abs(this.x - x) <= 1e-7
            && Math.abs(this.y - y) <= 1e-7
            && Math.abs(this.z - z) <= 1e-7 ? id : 0;
    }
}
