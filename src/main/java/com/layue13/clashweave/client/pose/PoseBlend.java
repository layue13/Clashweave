package com.layue13.clashweave.client.pose;

/** One preallocated snapshot, blended toward the live target using elapsed tick advances. */
public final class PoseBlend {

    private final Pose from = new Pose();
    private double elapsed;
    private int duration;

    public void start(Pose source, int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks: negative");
        from.copyFrom(source);
        elapsed = 0;
        duration = ticks;
    }

    /** Advances before sampling. Pass zero to obtain the starting pose; target may alias out. */
    public void update(Pose target, double partialTicksAdvance, Pose out) {
        if (!Double.isFinite(partialTicksAdvance) || partialTicksAdvance < 0)
            throw new IllegalArgumentException("partialTicksAdvance: expected finite nonnegative value");
        if (!active()) {
            out.copyFrom(target);
            return;
        }
        elapsed = Math.min(duration, elapsed + partialTicksAdvance);
        float u = (float) (elapsed / duration);
        Pose.blend(from, target, u * u * (3 - 2 * u), out);
    }

    public boolean active() {
        return elapsed < duration;
    }

    public void reset() {
        duration = 0;
        elapsed = 0;
    }
}
