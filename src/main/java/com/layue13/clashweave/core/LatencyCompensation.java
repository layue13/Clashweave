package com.layue13.clashweave.core;

/** Server latency bounds for compensation of late combo inputs. */
public final class LatencyCompensation {

    private LatencyCompensation() {}

    /** Unknown/nonfinite RTT and invalid negative inputs give no allowance. */
    public static int limit(long receiptAge, int configCap, double pingMillis, int jitterTicks) {
        if (receiptAge <= 0 || configCap <= 0 || !Double.isFinite(pingMillis) || pingMillis <= 0
            || jitterTicks < 0) return 0;
        // RTT / 2 / 50 ms per tick; clamp before narrowing to avoid overflow.
        double latencyCap = Math.ceil(pingMillis / 100.0) + Math.min(jitterTicks, 4);
        return (int) Math.min(Math.min(receiptAge, configCap), latencyCap);
    }
}
