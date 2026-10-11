package com.layue13.clashweave.core;

import java.util.Objects;

/** Internal presentation IDs only; never consulted by hit geometry or scheduling. */
public final class AppearanceState {

    public static final AppearanceState DEFAULT = new AppearanceState(
        "clashweave:katana",
        "clashweave:default",
        "clashweave:katana");
    public final String skin, effects, animations;

    public AppearanceState(String skin, String effects, String animations) {
        this.skin = id(skin);
        this.effects = id(effects);
        this.animations = id(animations);
    }

    public static String id(String value) {
        if (value == null || !value.matches("[a-z0-9_.:/-]{1,64}"))
            throw new IllegalArgumentException("Invalid content ID");
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AppearanceState)) return false;
        AppearanceState a = (AppearanceState) o;
        return skin.equals(a.skin) && effects.equals(a.effects) && animations.equals(a.animations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(skin, effects, animations);
    }

    public static final class Pending {

        private AppearanceState current = DEFAULT, pending;

        public AppearanceState current() {
            return current;
        }

        public void request(AppearanceState next) {
            pending = Objects.requireNonNull(next);
        }

        public boolean apply(boolean betweenActions) {
            if (!betweenActions || pending == null) return false;
            boolean changed = !current.equals(pending);
            current = pending;
            pending = null;
            return changed;
        }
    }
}
