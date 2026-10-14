package com.layue13.clashweave.core;

/** One target, shared by all attackers. No caller can replenish an active pursuit. */
public final class ControlBudget {

    private final int capacity, protectionTicks, quietTicks, expiryTicks;
    private int remaining;
    private long protectedUntil, lastControl = Long.MIN_VALUE / 2, groundedSince = -1;

    public ControlBudget(int capacity, int protectionTicks, int quietTicks, int expiryTicks) {
        if (capacity < 1 || protectionTicks < 1 || quietTicks < 1 || expiryTicks < quietTicks)
            throw new IllegalArgumentException("control budget configuration");
        this.capacity = capacity;
        this.protectionTicks = protectionTicks;
        this.quietTicks = quietTicks;
        this.expiryTicks = expiryTicks;
        remaining = capacity;
    }

    public void advance(long tick, boolean grounded) {
        if (!grounded) groundedSince = -1;
        else if (groundedSince < 0) groundedSince = tick;
        if (remaining < capacity && tick >= protectedUntil
            && groundedSince >= 0
            && tick - groundedSince >= quietTicks
            && tick - lastControl >= quietTicks) remaining = capacity;
    }

    public boolean consume(long tick, int cost, boolean grounded) {
        if (cost < 1) throw new IllegalArgumentException("control cost");
        advance(tick, grounded);
        if (tick < protectedUntil) return false;
        if (remaining < cost) {
            protectedUntil = tick + protectionTicks;
            return false;
        }
        remaining -= cost;
        lastControl = tick;
        if (remaining == 0) protectedUntil = tick + protectionTicks;
        return true;
    }

    /**
     * Ends the chain: spends every remaining point and starts the protection, as a finisher does. A target whose
     * points ran out on the way up is already protected; the finisher may still bring it down, and neither extends
     * nor shortens that protection.
     */
    public boolean exhaust(long tick, boolean grounded) {
        advance(tick, grounded);
        if (tick < protectedUntil) return true;
        remaining = 0;
        lastControl = tick;
        protectedUntil = tick + protectionTicks;
        return true;
    }

    public int remaining() {
        return remaining;
    }

    public long protectedUntil() {
        return protectedUntil;
    }

    public boolean expired(long tick) {
        return tick - lastControl >= expiryTicks && tick >= protectedUntil;
    }
}
