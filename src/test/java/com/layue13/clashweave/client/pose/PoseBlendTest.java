package com.layue13.clashweave.client.pose;

import static org.junit.Assert.*;

import org.junit.Test;

public class PoseBlendTest {

    @Test
    public void smoothstepStartsEndsAndIsMonotonic() {
        Pose from = new Pose(), target = new Pose(), out = new Pose();
        from.swordP[0] = 2;
        target.swordP[0] = 10;
        PoseBlend blend = new PoseBlend();
        blend.start(from, 4);
        from.swordP[0] = 99;
        blend.update(target, 0, out);
        assertEquals(2, out.swordP[0], 0);
        assertTrue(blend.active());
        float previous = out.swordP[0];
        for (int i = 1; i <= 16; i++) {
            blend.update(target, .25, out);
            double u = i / 16.0;
            assertEquals(2 + 8 * u * u * (3 - 2 * u), out.swordP[0], 1e-6);
            assertTrue(out.swordP[0] >= previous);
            previous = out.swordP[0];
        }
        assertFalse(blend.active());
        assertEquals(10, out.swordP[0], 0);
        target.swordP[0] = 20;
        blend.update(target, .5, out);
        assertEquals(20, out.swordP[0], 0);
    }

    @Test
    public void restartResetZeroDurationAndMovingTarget() {
        Pose from = new Pose(), target = new Pose(), out = new Pose();
        target.swordP[0] = 10;
        PoseBlend blend = new PoseBlend();
        blend.start(from, 2);
        blend.update(target, 1, out);
        assertEquals(5, out.swordP[0], 0);
        target.swordP[0] = 20;
        blend.update(target, 0, target);
        assertEquals(10, target.swordP[0], 0);
        blend.start(out, 2);
        blend.update(target, 0, out);
        assertEquals(5, out.swordP[0], 0);
        blend.reset();
        assertFalse(blend.active());
        blend.update(target, 0, out);
        assertEquals(10, out.swordP[0], 0);
        blend.start(from, 0);
        assertFalse(blend.active());
        blend.update(target, 0, out);
        assertEquals(10, out.swordP[0], 0);
        blend.start(from, 1);
        blend.update(target, 100, out);
        assertFalse(blend.active());
    }

    @Test
    public void poseCopyBlendBooleansAndAliasing() {
        Pose a = new Pose(), b = new Pose(), expected = new Pose(), out = new Pose();
        b.swordP[0] = 2;
        b.handR[1] = 4;
        b.handL[2] = 6;
        b.pelvis[0] = 8;
        b.footR[0] = 10;
        b.footL[1] = 12;
        b.torsoYaw = 30;
        b.handLFree = true;
        b.bladeDrawn = true;
        b.swordQ[2] = (float) Math.sqrt(.5);
        b.swordQ[3] = (float) Math.sqrt(.5);
        b.armR[0] = 1;
        b.armR[2] = 0;
        Pose.blend(a, b, .5f, expected);
        assertEquals(1, expected.swordP[0], 0);
        assertEquals(2, expected.handR[1], 0);
        assertEquals(3, expected.handL[2], 0);
        assertEquals(4, expected.pelvis[0], 0);
        assertEquals(5, expected.footR[0], 0);
        assertEquals(6, expected.footL[1], 0);
        assertEquals(15, expected.torsoYaw, 0);
        assertTrue(expected.handLFree);
        assertTrue(expected.bladeDrawn);
        out.copyFrom(a);
        Pose.blend(out, b, .5f, out);
        same(expected, out);
        out.copyFrom(b);
        Pose.blend(a, out, .5f, out);
        same(expected, out);
        Pose.blend(a, b, 0, out);
        same(a, out);
        Pose.blend(a, b, 1, out);
        same(b, out);
        Pose.blend(a, b, .499f, out);
        assertFalse(out.handLFree);
        assertFalse(out.bladeDrawn);
        out.copyFrom(b);
        out.copyFrom(out);
        same(b, out);
    }

    private static void same(Pose expected, Pose actual) {
        float[][] a = { expected.swordP, expected.swordQ, expected.handR, expected.handL, expected.armR, expected.armL,
            expected.legR, expected.legL, expected.pelvis, expected.footR, expected.footL };
        float[][] b = { actual.swordP, actual.swordQ, actual.handR, actual.handL, actual.armR, actual.armL, actual.legR,
            actual.legL, actual.pelvis, actual.footR, actual.footL };
        for (int i = 0; i < a.length; i++) assertArrayEquals(a[i], b[i], 0);
        assertEquals(expected.torsoYaw, actual.torsoYaw, 0);
        assertEquals(expected.handLFree, actual.handLFree);
        assertEquals(expected.bladeDrawn, actual.bladeDrawn);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeDurationIsRejected() {
        new PoseBlend().start(new Pose(), -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeAdvanceIsRejected() {
        new PoseBlend().update(new Pose(), -1, new Pose());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonfiniteAdvanceIsRejected() {
        new PoseBlend().update(new Pose(), Double.NaN, new Pose());
    }
}
