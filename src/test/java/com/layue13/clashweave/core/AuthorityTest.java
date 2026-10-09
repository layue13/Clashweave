package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class AuthorityTest {

    @Test
    public void guardRequiresBothClocksAndStrictArrivalBoundary() {
        assertFalse(GuardTimeline.ready(103, 149_999_999, 100, 0, 3));
        assertFalse(GuardTimeline.ready(102, 150_000_000, 100, 0, 3));
        assertTrue(GuardTimeline.ready(103, 150_000_000, 100, 0, 3));
        GuardTimeline guard = new GuardTimeline();
        assertTrue(guard.press(100, 149_999_999, 6));
        assertEquals(2, guard.defend(100, 150_000_000, 5, true));
        assertEquals(0, guard.defend(100, 149_999_999, 5, true));
        assertFalse(guard.press(101, 149_999_999, 6));
        guard.release(101);
        assertFalse(guard.press(102, 149_999_999, 6));
        assertEquals(0, guard.defend(100, 150_000_000, 5, false));
    }

    @Test
    public void sessionReplayFutureAndStaleRejectWithoutFallback() {
        InputGate gate = new InputGate(123);
        assertEquals("SESSION", gate.validate(321, 1, 100, 100, 8));
        assertEquals("OK", gate.validate(123, 1, 98, 100, 8));
        assertEquals("REPLAY", gate.validate(123, 1, 98, 100, 8));
        assertEquals("FUTURE", gate.validate(123, 2, 101, 100, 8));
        assertEquals("STALE", gate.validate(123, 3, 91, 100, 8));
    }

    @Test
    public void cumulativeBudgetAllowsBatchAfterLongTickButRejectsTeleportAndTotalOverflow() {
        MovementBudget budget = new MovementBudget(0, 0.2, 1.2, 2);
        assertTrue(budget.accept(200_000_000, 1.0, 0, 0, 0, 0));
        assertFalse(budget.accept(250_000_000, 0.4, 0, 0, 0, 0));
        assertFalse(budget.accept(250_000_000, 5, 0, 0, 0, 0));
        assertFalse(budget.accept(250_000_000, 0, 5, 0, 0, 0));
        assertEquals(1, budget.path(), 0);
    }

    @Test
    public void shortEnvelopeCannotIdentifyModerateOverspeedWithinItsMargin() {
        for (double multiplier : new double[] { 1.2, 1.5 }) {
            MovementBudget budget = new MovementBudget(0, 0.2, 1.2, 2);
            double remaining = 1.2;
            for (int tick = 1; remaining > 0; tick++) {
                double step = Math.min(remaining, 0.2 * multiplier);
                assertTrue(budget.accept(tick * 50_000_000L, step, 0, 0.28, 0, 0));
                remaining -= step;
            }
        }
    }
}
