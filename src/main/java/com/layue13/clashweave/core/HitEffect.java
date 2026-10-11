package com.layue13.clashweave.core;

/** Ordered post-damage effects; platform policy and execution stay server-side. */
public final class HitEffect {

    public enum Type {
        LAUNCH
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

    public static boolean allowed(boolean damageAccepted, int guard, boolean alive, boolean exempt) {
        return damageAccepted && guard == 0 && alive && !exempt;
    }
}
