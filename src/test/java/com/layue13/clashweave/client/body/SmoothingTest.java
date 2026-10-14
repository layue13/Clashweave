package com.layue13.clashweave.client.body;

import static org.junit.Assert.*;

import org.junit.Test;

public class SmoothingTest {

    @Test
    public void oneFrameAndTwoHalfFramesLandInTheSamePlace() {
        for (float perTick : new float[] { .1f, .4f, .8f }) {
            float whole = Smoothing.toward(0, 1, Smoothing.alpha(perTick, 1));
            float half = 0;
            for (int i = 0; i < 2; i++) half = Smoothing.toward(half, 1, Smoothing.alpha(perTick, .5));
            assertEquals(whole, half, 1e-6f);
        }
    }

    @Test
    public void alphaIsBoundedAndMonotonic() {
        assertEquals(1, Smoothing.alpha(1, .01), 0);
        assertEquals(0, Smoothing.alpha(0, 5), 0);
        assertEquals(0, Smoothing.alpha(.5f, 0), 0);
        assertEquals(1, Smoothing.alpha(.5f, 50), 0);
        float last = 0;
        for (double t = .1; t < 6; t += .1) {
            float a = Smoothing.alpha(.3f, t);
            assertTrue(a >= last && a <= 1);
            last = a;
        }
    }

    @Test
    public void angleChaseTakesTheShortWayAcrossTheCut() {
        float from = (float) Math.toRadians(170), to = (float) Math.toRadians(-170);
        float mid = Smoothing.angleToward(from, to, .5f);
        assertEquals(Math.PI, Math.abs(mid), 1e-5);
    }
}
