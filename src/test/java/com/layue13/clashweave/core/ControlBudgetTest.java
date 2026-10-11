package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class ControlBudgetTest {

    private ControlBudget budget() {
        return new ControlBudget(3, 40, 20, 120);
    }

    @Test
    public void thirdControlSucceedsFourthDoesNotAndProtectionIsHalfOpen() {
        ControlBudget b = budget();
        assertTrue(b.consume(0, 1, false));
        assertTrue(b.consume(1, 1, false));
        assertTrue(b.consume(2, 1, false));
        assertEquals(0, b.remaining());
        assertEquals(42, b.protectedUntil());
        assertFalse(b.consume(3, 1, false));
        assertFalse(b.consume(41, 1, false));
    }

    @Test
    public void groundAndQuietAndProtectionAreAllRequiredForReset() {
        ControlBudget b = budget();
        for (int i = 0; i < 3; i++) assertTrue(b.consume(i, 1, false));
        b.advance(40, true);
        b.advance(59, true);
        assertEquals(0, b.remaining());
        b.advance(60, true);
        assertEquals(3, b.remaining());
        assertTrue(b.consume(60, 1, true));
    }

    @Test
    public void airborneExpiryCannotReplenishPursuit() {
        ControlBudget b = budget();
        for (int i = 0; i < 3; i++) b.consume(i, 1, false);
        b.advance(122, false);
        assertTrue(b.expired(122));
        assertEquals(0, b.remaining());
        assertFalse(b.consume(122, 1, false));
    }

    @Test
    public void attackerChangesDoNotCreateNewBudgetAndOtherTargetsAreIndependent() {
        ControlBudget a = budget(), other = budget();
        int accepted = 0;
        for (int attacker = 0; attacker < 4; attacker++) if (a.consume(5, 1, false)) accepted++;
        assertEquals(3, accepted);
        assertTrue(other.consume(5, 1, false));
        assertEquals(2, other.remaining());
    }

    @Test
    public void simultaneousAttackerPermutationsAlwaysShareThreePoints() {
        for (int offset = 0; offset < 4; offset++) {
            ControlBudget b = budget();
            int accepted = 0;
            java.util.Map<Integer, Boolean> outcomes = new java.util.LinkedHashMap<>();
            for (int index = 0; index < 4; index++) {
                int attacker = (index + offset) % 4;
                boolean result = b.consume(5, 1, false);
                outcomes.put(attacker, result);
                if (result) accepted++;
            }
            assertEquals(4, outcomes.size());
            assertEquals(0, b.remaining());
            assertEquals(3, accepted);
        }
    }

    @Test
    public void groundTimerRestartsWhenTargetLeavesGround() {
        ControlBudget b = budget();
        b.consume(0, 1, false);
        b.advance(10, true);
        b.advance(25, false);
        b.advance(30, true);
        b.advance(49, true);
        assertEquals(2, b.remaining());
        b.advance(50, true);
        assertEquals(3, b.remaining());
    }

    @Test
    public void invalidConfigurationAndCostsReject() {
        try {
            new ControlBudget(0, 40, 20, 120);
            fail();
        } catch (IllegalArgumentException expected) {}
        try {
            new ControlBudget(3, 40, 20, 10);
            fail();
        } catch (IllegalArgumentException expected) {}
        try {
            budget().consume(0, 0, true);
            fail();
        } catch (IllegalArgumentException expected) {}
    }
}
