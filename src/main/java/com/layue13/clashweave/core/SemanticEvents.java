package com.layue13.clashweave.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Ordered semantic results. Listeners are presentation-only and cannot alter settlement. */
public final class SemanticEvents {

    public enum Kind {
        SWING,
        HIT,
        BLOCK,
        PARRY,
        SHEATHE,
        DRAW,
        HURT
    }

    public static final class Event {

        public final Kind kind;
        public final int actor, target;
        public final long instance, tick, frozen;
        public final String action;

        public Event(Kind kind, int actor, int target, long instance, long tick, long frozen, String action) {
            this.kind = java.util.Objects.requireNonNull(kind);
            this.actor = actor;
            this.target = target;
            this.instance = instance;
            this.tick = tick;
            this.frozen = frozen;
            this.action = action;
        }
    }

    public int listenerFailures;
    private final List<Consumer<Event>> listeners = new ArrayList<>();

    public void subscribe(Consumer<Event> listener) {
        listeners.add(listener);
    }

    public void emit(Event event) {
        for (Consumer<Event> listener : listeners) try {
            listener.accept(event);
        } catch (RuntimeException failure) {
            listenerFailures++;
            System.err.println(
                "CW_PRESENTATION_FAILURE " + failure.getClass()
                    .getSimpleName());
        }
    }

    public void actionStarted(int actor, long instance, long tick, String action, boolean draw, boolean attack) {
        if (draw) emit(new Event(Kind.DRAW, actor, -1, instance, tick, 0, action));
        if (attack) emit(new Event(Kind.SWING, actor, -1, instance, tick, 0, action));
    }

    public void contact(int attacker, int defender, long instance, long tick, long frozen, String action, boolean hit,
        int guard) {
        if (guard > 0)
            emit(new Event(guard == 2 ? Kind.PARRY : Kind.BLOCK, defender, attacker, instance, tick, frozen, action));
        if (hit) {
            emit(new Event(Kind.HIT, attacker, defender, instance, tick, frozen, action));
            emit(new Event(Kind.HURT, defender, attacker, instance, tick, frozen, action));
        }
    }
}
