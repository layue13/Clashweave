package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class LockSelectionTest {

    @Test
    public void balancesAngleAndDistanceAndStableTies() {
        assertTrue(LockSelection.score(4, 0, 16, 30) < LockSelection.score(8, 0, 16, 30));
        assertTrue(LockSelection.score(8, 0, 16, 30) < LockSelection.score(8, 10, 16, 30));
        assertEquals(LockSelection.score(8, 0, 16, 30), LockSelection.score(4, 7.5, 16, 30), 1e-12);
        assertTrue(LockSelection.better(.5, 1, .5, 2));
        assertFalse(LockSelection.better(.5, 2, .5, 1));
        assertFalse(LockSelection.better(Double.POSITIVE_INFINITY, 1, Double.POSITIVE_INFINITY, 2));
    }

    @Test
    public void coneAndRangeIncludeBoundaryOnly() {
        assertTrue(Double.isFinite(LockSelection.score(16, 30, 16, 30)));
        assertFalse(Double.isFinite(LockSelection.score(16, 30.0001, 16, 30)));
        assertFalse(Double.isFinite(LockSelection.score(16.0001, 0, 16, 30)));
        assertFalse(Double.isFinite(LockSelection.score(Double.NaN, 0, 16, 30)));
    }

    @Test
    public void acquireKeepHysteresis() {
        assertFalse(Double.isFinite(LockSelection.score(20, 0, 16, 30)));
        assertTrue(LockSelection.keep(20, 20, true));
        assertFalse(LockSelection.keep(22, 20, true));
        assertFalse(LockSelection.keep(4, 20, false));
    }
}
