package com.layue13.clashweave.core;

/** Session authentication and bounded stamps. A plausible backdated stamp is not cryptographic proof of a key press. */
public final class InputGate {

    private final long session;
    private int lastSequence;
    private long rateTick = -1;
    private int count;

    public InputGate(long session) {
        this.session = session;
    }

    public String validate(long token, int sequence, long stamp, long tick, int ageLimit) {
        if (token != session) return "SESSION";
        if (sequence <= lastSequence) return "REPLAY";
        lastSequence = sequence;
        if (stamp > tick) return "FUTURE";
        if (stamp < tick - ageLimit) return "STALE";
        if (rateTick != tick) {
            rateTick = tick;
            count = 0;
        }
        if (++count > 12) return "RATE";
        return "OK";
    }
}
