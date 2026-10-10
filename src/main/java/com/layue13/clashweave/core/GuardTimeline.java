package com.layue13.clashweave.core;

import java.util.ArrayList;
import java.util.List;

/** Guard press/release history; history expires by tick, never refreshes on hold. */
public final class GuardTimeline {

    private static final class Span {

        long press;
        long release = Long.MAX_VALUE;
        long arrival;
    }

    private final List<Span> spans = new ArrayList<>();
    private boolean held;
    private long lastPress = Long.MIN_VALUE / 2;

    public boolean press(long stamp, long arrival, int cooldown) {
        if (held || stamp - lastPress < cooldown) return false;
        Span span = new Span();
        span.press = stamp;
        span.arrival = arrival;
        spans.add(span);
        held = true;
        lastPress = stamp;
        return true;
    }

    public void release(long stamp) {
        if (held) spans.get(spans.size() - 1).release = stamp;
        held = false;
    }

    public int defend(long hitTick, long cutoff, int window, boolean facing) {
        if (!facing) return 0;
        for (int index = spans.size() - 1; index >= 0; index--) {
            Span span = spans.get(index);
            if (span.arrival < cutoff && span.press <= hitTick && span.release > hitTick) {
                return hitTick - span.press < window ? 2 : 1;
            }
        }
        return 0;
    }

    public boolean held() {
        return held;
    }

    public void prune(long before) {
        spans.removeIf(span -> span.release < before);
    }

    public static boolean ready(long tick, long now, long hitTick, long frozen, int defer) {
        return tick >= hitTick + defer && now - frozen >= defer * 50_000_000L;
    }
}
