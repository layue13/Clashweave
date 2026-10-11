package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class SemanticEventsTest {

    @Test
    public void authoritativeEventsHaveStableOrderAndDoNotInventHits() {
        SemanticEvents bus = new SemanticEvents();
        List<SemanticEvents.Kind> first = new ArrayList<>(), second = new ArrayList<>();
        bus.subscribe(e -> first.add(e.kind));
        bus.subscribe(e -> second.add(e.kind));
        bus.actionStarted(1, 3, 10, "iai", true, true);
        bus.contact(1, 2, 3, 12, 99, "iai", true, 0);
        bus.contact(1, 2, 4, 20, 100, "heavy", false, 2);
        bus.contact(1, 2, 5, 30, 101, "heavy", true, 1);
        bus.emit(new SemanticEvents.Event(SemanticEvents.Kind.SHEATHE, 1, -1, 6, 40, 0, "sheathe"));
        assertEquals(
            Arrays.asList(
                SemanticEvents.Kind.DRAW,
                SemanticEvents.Kind.SWING,
                SemanticEvents.Kind.HIT,
                SemanticEvents.Kind.HURT,
                SemanticEvents.Kind.PARRY,
                SemanticEvents.Kind.BLOCK,
                SemanticEvents.Kind.HIT,
                SemanticEvents.Kind.HURT,
                SemanticEvents.Kind.SHEATHE),
            first);
        assertEquals(first, second);
    }

    @Test
    public void presentationFailureCannotStopOtherSubscribers() {
        SemanticEvents bus = new SemanticEvents();
        List<SemanticEvents.Kind> seen = new ArrayList<>();
        bus.subscribe(e -> { throw new IllegalStateException("presentation only"); });
        bus.subscribe(e -> seen.add(e.kind));
        bus.contact(1, 2, 3, 4, 5, "light_1", true, 0);
        assertEquals(2, bus.listenerFailures);
        assertEquals(Arrays.asList(SemanticEvents.Kind.HIT, SemanticEvents.Kind.HURT), seen);
    }
}
