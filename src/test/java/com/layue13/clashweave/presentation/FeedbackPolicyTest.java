package com.layue13.clashweave.presentation;

import static org.junit.Assert.*;

import org.junit.Test;

import com.layue13.clashweave.core.SemanticEvents;

public class FeedbackPolicyTest {

    private SemanticEvents.Event event(SemanticEvents.Kind kind, int actor, int target, long frozen) {
        return new SemanticEvents.Event(kind, actor, target, 3, 4, frozen, "heavy");
    }

    @Test
    public void partialBlockKeepsOneGuardCueAndUnblockedHitStillPlays() {
        FeedbackPolicy policy = new FeedbackPolicy();
        assertEquals(FeedbackPolicy.Sound.GUARD, policy.sound(event(SemanticEvents.Kind.BLOCK, 2, 1, 5), 1));
        assertEquals(FeedbackPolicy.Sound.NONE, policy.sound(event(SemanticEvents.Kind.HIT, 1, 2, 5), 1));
        assertEquals(FeedbackPolicy.Sound.HIT, policy.sound(event(SemanticEvents.Kind.HIT, 1, 2, 6), 1));
        assertEquals(FeedbackPolicy.Sound.NONE, policy.sound(event(SemanticEvents.Kind.HIT, 1, 2, 6), 2));
        assertEquals(FeedbackPolicy.Sound.GUARD, policy.sound(event(SemanticEvents.Kind.PARRY, 2, 1, 7), 2));
        assertEquals(FeedbackPolicy.Sound.NONE, policy.sound(event(SemanticEvents.Kind.HURT, 2, 1, 7), 2));
    }
}
