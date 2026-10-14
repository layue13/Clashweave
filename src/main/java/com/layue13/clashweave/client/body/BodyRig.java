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

    /** The base pose, chased target by target every frame; see Channels. */
    private final float[] smooth = new float[Channels.COUNT];
    private final float[] target = new float[Channels.COUNT];
    private boolean seeded;
    private long chaseNanos;
    /** Frame stamps: the main model steps the rig once, the armor models drawn after it reuse the result. */
    private long stamp, steppedStamp;
    private float touchTicks = 100, airTicks;
    private long groundNanos;

    /**
     * The channels the next step chases toward: the caller fills them with vanilla's pose and then the layers' targets.
     */
    public float[] target() {
        return target;
    }

    public float[] smoothed() {
        return smooth;
    }

    public float ticksSinceTouchdown() {
        return touchTicks;
    }

    public float ticksAirborne() {
        return airTicks;
    }

    /** Marks a new frame for this entity; returns whether the base pose has to be stepped for it. */
    public boolean beginFrame(long nanos) {
        stamp = nanos;
        return steppedStamp != stamp;
    }

    public boolean needsStep() {
        return steppedStamp != stamp;
    }

    /** Counts the time on and off the ground in ticks. */
    public void observeGround(boolean onGround, long nanos, long tickPeriod) {
        double ticks = groundNanos == 0 ? 0 : (nanos - groundNanos) / (double) tickPeriod;
        groundNanos = nanos;
        if (onGround) {
            touchTicks += (float) ticks;
            airTicks = 0;
        } else {
            airTicks += (float) ticks;
            touchTicks = 0;
        }
    }

    /** Moves every channel toward its target by its own per-tick fraction of the time since the last step. */
    public void chase(long nanos, long tickPeriod) {
        double ticks = chaseNanos == 0 ? 0 : (nanos - chaseNanos) / (double) tickPeriod;
        chaseNanos = nanos;
        for (int i = 0; i < Channels.COUNT; i++) {
            if (!seeded) smooth[i] = target[i];
            else smooth[i] = Smoothing.toward(smooth[i], target[i], Smoothing.alpha(Channels.SMOOTHNESS[i], ticks));
        }
        seeded = true;
        steppedStamp = stamp;
    }

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
