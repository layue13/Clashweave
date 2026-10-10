package com.layue13.clashweave.presentation;

import com.layue13.clashweave.core.SemanticEvents;

/** Default P0 cue policy: partial block emits semantic HIT but retains one guard sound. */
public final class FeedbackPolicy {

    public enum Sound {
        NONE,
        HIT,
        GUARD
    }

    private SemanticEvents.Event lastGuard;

    public Sound sound(SemanticEvents.Event event, int owner) {
        if (event.instance != 0 && (event.kind == SemanticEvents.Kind.BLOCK || event.kind == SemanticEvents.Kind.PARRY)
            && (event.actor == owner || event.target == owner)) {
            lastGuard = event;
            return Sound.GUARD;
        }
        if (event.kind != SemanticEvents.Kind.HIT || event.actor != owner) return Sound.NONE;
        if (lastGuard != null && lastGuard.actor == event.target
            && lastGuard.target == event.actor
            && lastGuard.instance == event.instance
            && lastGuard.frozen == event.frozen) return Sound.NONE;
        return Sound.HIT;
    }
}
