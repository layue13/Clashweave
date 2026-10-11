package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class LatencyCompensationTest {

    @Test
    public void zeroOrUnknownPingDisablesCompensationEvenWithJitter() {
        assertEquals(0, LatencyCompensation.limit(8, 4, 0, 1));
        assertEquals(0, LatencyCompensation.limit(8, 4, -1, 1));
        assertEquals(0, LatencyCompensation.limit(8, 4, Double.NaN, 4));
    }

    @Test
    public void hundredMillisRoundTripAllowsTwoTicks() {
        assertEquals(2, LatencyCompensation.limit(6, 4, 100, 1));
    }

    @Test
    public void higherPingStillRespectsConfiguredCap() {
        assertEquals(3, LatencyCompensation.limit(6, 4, 200, 1));
        assertEquals(4, LatencyCompensation.limit(6, 4, 400, 1));
        assertEquals(5, LatencyCompensation.limit(8, 8, 400, 1));
    }

    @Test
    public void forgedOldStampCannotGetFullAllowanceAtLowPing() {
        assertEquals(2, LatencyCompensation.limit(8, 4, 40, 1));
    }

    @Test
    public void honestYoungerReceiptUsesItsActualAge() {
        assertEquals(1, LatencyCompensation.limit(1, 4, 400, 1));
        assertEquals(0, LatencyCompensation.limit(0, 4, 400, 1));
    }

    @Test
    public void disabledCapAlwaysGivesZero() {
        for (long age = 0; age <= 8; age++) {
            assertEquals(0, LatencyCompensation.limit(age, 0, 400, 4));
        }
        assertEquals(0, LatencyCompensation.limit(Long.MAX_VALUE, 0, Double.MAX_VALUE, 4));
    }

    @Test
    public void negativeInputsFailClosed() {
        assertEquals(0, LatencyCompensation.limit(-1, 4, 100, 1));
        assertEquals(0, LatencyCompensation.limit(Long.MIN_VALUE, 4, 100, 1));
        assertEquals(0, LatencyCompensation.limit(8, -1, 100, 1));
        assertEquals(0, LatencyCompensation.limit(8, 4, -100, 1));
        assertEquals(0, LatencyCompensation.limit(8, 4, 100, -1));
    }

    @Test
    public void nonfinitePingFailsClosed() {
        assertEquals(0, LatencyCompensation.limit(8, 4, Double.NaN, 1));
        assertEquals(0, LatencyCompensation.limit(8, 4, Double.POSITIVE_INFINITY, 1));
        assertEquals(0, LatencyCompensation.limit(8, 4, Double.NEGATIVE_INFINITY, 1));
    }

    @Test
    public void oneWayTicksRoundUpAtExactBoundaries() {
        assertEquals(1, LatencyCompensation.limit(8, 8, 1, 0));
        assertEquals(1, LatencyCompensation.limit(8, 8, 99, 0));
        assertEquals(1, LatencyCompensation.limit(8, 8, 100, 0));
        assertEquals(2, LatencyCompensation.limit(8, 8, 101, 0));
        assertEquals(2, LatencyCompensation.limit(8, 8, 200, 0));
        assertEquals(3, LatencyCompensation.limit(8, 8, 201, 0));
    }

    @Test
    public void extremeInputsCannotOverflowOrExceedJitterRange() {
        assertEquals(4, LatencyCompensation.limit(Long.MAX_VALUE, 4, Double.MAX_VALUE, 4));
        assertEquals(5, LatencyCompensation.limit(8, 8, 40, Integer.MAX_VALUE));
        assertEquals(
            Integer.MAX_VALUE,
            LatencyCompensation.limit(Long.MAX_VALUE, Integer.MAX_VALUE, Double.MAX_VALUE, 4));
    }
}
