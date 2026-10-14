package com.layue13.clashweave.core;

/** Ordered post-damage effects; platform policy and execution stay server-side. */
public final class HitEffect {

    public enum Type {
        /** Lifts the target: its upward velocity is raised to {@code vy}, spending control points. */
        LAUNCH,
        /** Drives an airborne target to the ground at {@code vy} and ends its chain: all remaining points are spent. */
        SLAM,
        /** Strikes the same target again {@code delay} ticks after the hit, for {@code damage}. */
        DELAYED_SLASH
    }

    public Type type;
    public double vy;
    /** DELAYED_SLASH only: ticks until the second strike, its damage and its posture damage. */
    public int delay;
    public float damage, posture;

    public void validate(double maximum) {
        if (type == null || !Double.isFinite(vy) || vy < 0 || vy > maximum)
            throw new IllegalArgumentException("effects.type/vy: unknown effect or out-of-range velocity");
        if (type == Type.DELAYED_SLASH) {
            if (vy != 0 || delay < 1
                || delay > 60
                || !Float.isFinite(damage)
                || damage < 0
                || damage > 100
                || !Float.isFinite(posture)
                || posture < 0
                || posture > 1000)
                throw new IllegalArgumentException(
                    "effects.DELAYED_SLASH: delay 1-60, damage 0-100, posture 0-1000, no vy");
        } else if (delay != 0 || damage != 0 || posture != 0)
            throw new IllegalArgumentException("effects: delay, damage and posture belong to DELAYED_SLASH only");
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
