package com.layue13.clashweave.core;

/** Ordered post-damage effects; platform policy and execution stay server-side. */
public final class HitEffect {

    public enum Type {
        /** Lifts the target: its upward velocity is raised to {@code vy}, spending control points. */
        LAUNCH,
        /** Drives an airborne target to the ground at {@code vy} and ends its chain: all remaining points are spent. */
        SLAM
    }

    public Type type;
    public double vy;

    public void validate(double maximum) {
        if (type == null || !Double.isFinite(vy) || vy < 0 || vy > maximum)
            throw new IllegalArgumentException("effects.type/vy: unknown effect or out-of-range velocity");
    }

    /** Preserves declaration order and runs only after the caller has submitted ordinary damage. */
    public static void submit(java.util.List<HitEffect> effects, boolean damageAccepted, int guard, boolean alive,
        boolean exempt, java.util.function.Consumer<HitEffect> apply) {
        if (allowed(damageAccepted, guard, alive, exempt)) for (HitEffect effect : effects) apply.accept(effect);
    }

    /** The velocity the {@code chain}th accepted launch in a row delivers: each one lifts a fixed share less. */
    public static double decayed(double vy, int chain, double decay) {
        return vy * Math.pow(decay, Math.max(0, chain));
    }

    public static boolean allowed(boolean damageAccepted, int guard, boolean alive, boolean exempt) {
        return damageAccepted && guard == 0 && alive && !exempt;
    }
}
