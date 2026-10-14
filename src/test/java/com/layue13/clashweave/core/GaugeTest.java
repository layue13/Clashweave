package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class GaugeTest {

    private static Gauge gauge() {
        return new Gauge(100, 0, 8, 8, 20);
    }

    @Test
    public void exchangesFillItAndItCapsAtTheMaximum() {
        Gauge g = gauge();
        assertTrue(g.gain(Gauge.Source.HIT, 60, 1));
        assertEquals(60, g.value(), 1e-6);
        assertTrue(g.gain(Gauge.Source.HIT, 60, 2));
        assertEquals(100, g.value(), 1e-6);
        assertTrue(g.full());
    }

    @Test
    public void aSourceIsRateLimitedButOthersAreNot() {
        Gauge g = gauge();
        assertTrue(g.gain(Gauge.Source.BLOCK, 4, 10));
        assertFalse("the same source again inside its interval", g.gain(Gauge.Source.BLOCK, 4, 12));
        assertTrue("another source is unaffected", g.gain(Gauge.Source.PARRY, 8, 12));
        assertTrue(g.gain(Gauge.Source.BLOCK, 4, 18));
        assertEquals(16, g.value(), 1e-6);
    }

    @Test
    public void activationNeedsAFullGaugeEmptiesItAndPausesFilling() {
        Gauge g = gauge();
        g.gain(Gauge.Source.HIT, 50, 1);
        assertFalse("not full", g.activate(2, 300));
        g.gain(Gauge.Source.HIT, 60, 3);
        assertTrue(g.activate(4, 300));
        assertEquals(0, g.value(), 1e-6);
        assertTrue(g.inMode(100));
        assertEquals(204, g.modeLeft(100));
        assertFalse("no filling during the mode", g.gain(Gauge.Source.HIT, 10, 50));
        assertFalse("and no second activation", g.activate(60, 300));
        assertFalse(g.inMode(304));
        assertEquals(0, g.modeLeft(304));
        assertTrue("filling resumes afterwards", g.gain(Gauge.Source.HIT, 10, 304));
    }

    @Test
    public void resetClearsTheGaugeAndTheMode() {
        Gauge g = gauge();
        g.gain(Gauge.Source.HIT, 100, 1);
        g.activate(2, 300);
        g.reset();
        assertEquals(0, g.value(), 1e-6);
        assertFalse(g.inMode(3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aWrongNumberOfIntervalsIsRejected() {
        new Gauge(100, 0, 0);
    }
}
