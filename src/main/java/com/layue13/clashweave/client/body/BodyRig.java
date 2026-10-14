package com.layue13.clashweave.client.body;

/**
 * The part of an entity's body animation that outlives a frame: how far the combat pose has taken the body over and the
 * pose it was last showing, so a release has something to melt from. One per entity, owned by its Visual. Time comes in
 * as nanoseconds, so rendering speed never changes how fast the body moves.
 */
public final class BodyRig {

    /** Per tick fractions: the body is taken over quickly and handed back a little more slowly. */
    static final float RISE = .4f, FALL = .28f;

    /** 0 = vanilla, 1 = fully the combat pose. */
    private float takeover;
    private long lastNanos;

    public float takeover() {
        return takeover;
    }

    /** Eases toward 1 while the combat pose is wanted and toward 0 otherwise; returns the new takeover. */
    public float advance(boolean wanted, long nanos, long tickPeriod) {
        double ticks = lastNanos == 0 ? 0 : (nanos - lastNanos) / (double) tickPeriod;
        lastNanos = nanos;
        float alpha = Smoothing.alpha(wanted ? RISE : FALL, ticks);
        takeover = Smoothing.toward(takeover, wanted ? 1 : 0, alpha);
        // Settle the last sliver so a finished fade is exactly 0 or 1 and costs nothing.
        if (takeover > .999f) takeover = 1;
        else if (takeover < .001f) takeover = 0;
        return takeover;
    }
}
