package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class PostureTest {

    @Test
    public void blowsWearItDownAndTheLastOneBreaksIt() {
        Posture p = new Posture(100, 40, 2, 50);
        assertFalse(p.damage(30, 10, 16));
        assertFalse(p.damage(30, 12, 16));
        assertFalse(p.damage(30, 14, 16));
        assertEquals(10, p.value(), 1e-6);
        assertTrue("breaks exactly once", p.damage(30, 16, 16));
        assertTrue(p.broken(16));
        assertTrue(p.broken(31));
        assertFalse(p.broken(32));
    }

    @Test
    public void aBrokenEntityTakesNoFurtherPostureDamageAndComesBackHalfSteady() {
        Posture p = new Posture(100, 40, 2, 50);
        assertTrue(p.damage(100, 0, 16));
        assertFalse(p.damage(50, 5, 16));
        assertEquals(0, p.value(), 1e-6);
        for (long t = 1; t <= 16; t++) p.advance(t);
        assertFalse(p.broken(16));
        assertEquals(50, p.value(), 1e-6);
    }

    @Test
    public void recoveryWaitsForTheDelayAndNeverPassesTheMaximum() {
        Posture p = new Posture(100, 40, 2, 50);
        p.damage(30, 0, 16);
        for (long t = 1; t < 40; t++) p.advance(t);
        assertEquals(70, p.value(), 1e-6);
        for (long t = 40; t < 40 + 14; t++) p.advance(t);
        assertEquals(98, p.value(), 1e-6);
        for (long t = 54; t < 70; t++) p.advance(t);
        assertEquals(100, p.value(), 1e-6);
        assertTrue(p.settled(70));
    }

    @Test
    public void anotherBlowRestartsTheRecoveryDelay() {
        Posture p = new Posture(100, 10, 5, 50);
        p.damage(20, 0, 16);
        for (long t = 1; t <= 10; t++) p.advance(t);
        assertEquals(85, p.value(), 1e-6);
        p.damage(10, 11, 16);
        p.advance(12);
        p.advance(20);
        assertEquals(75, p.value(), 1e-6);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidConfigurationIsRejected() {
        new Posture(0, 40, 2, 50);
    }
}
