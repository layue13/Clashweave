package com.layue13.clashweave.client.body;

import static com.layue13.clashweave.client.body.Channels.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class LocomotionTest {

    private static LocomotionInputs walking(float swing, float amount) {
        LocomotionInputs in = new LocomotionInputs();
        in.limbSwing = swing;
        in.limbSwingAmount = amount;
        in.onGround = true;
        in.ticksSinceTouchdown = 100;
        in.age = 10;
        return in;
    }

    private static float[] compute(LocomotionInputs in) {
        float[] t = new float[COUNT];
        java.util.Arrays.fill(t, 123f);
        Locomotion.compute(in, t);
        return t;
    }

    @Test
    public void armsAndLegsSwingInAntiphase() {
        float[] t = compute(walking(1.2f, .8f));
        assertEquals(-t[L_ARM_X], t[R_ARM_X], 1e-5f);
        assertTrue("arm and leg of one side swing against each other", t[R_ARM_X] * t[R_LEG_X] < 0);
        assertTrue(t[R_ARM_X] * t[L_ARM_X] < 0);
    }

    @Test
    public void aKneeBendsOnlyWhileItsLegSwingsForwardAndNeverBackward() {
        float maxBend = 0;
        for (float swing = 0; swing < 12; swing += .05f) {
            float[] t = compute(walking(swing, .8f));
            assertTrue(t[R_SHIN] >= 0 && t[L_SHIN] >= 0);
            // The two knees never bend together while walking.
            assertTrue(t[R_SHIN] == 0 || t[L_SHIN] == 0);
            maxBend = Math.max(maxBend, Math.max(t[R_SHIN], t[L_SHIN]));
        }
        assertTrue(maxBend > Math.toRadians(25) && maxBend < Math.toRadians(60));
    }

    @Test
    public void thePoseIsContinuousInTheWalkCycle() {
        float[] last = null;
        for (float swing = 0; swing < 12; swing += .02f) {
            float[] t = compute(walking(swing, .8f));
            if (last != null) for (int c = 0; c < COUNT; c++) assertTrue(
                "channel " + c + " jumped " + Math.abs(t[c] - last[c]) + " at " + swing,
                Math.abs(t[c] - last[c]) < .08f);
            last = t;
        }
    }

    @Test
    public void standingStillSwingsNothingAndFadesInFromTheWalk() {
        float[] still = compute(walking(5, 0));
        assertEquals(0, still[R_LEG_X], 1e-6f);
        assertEquals(0, still[R_SHIN], 0);
        assertEquals(0, still[ROOT_Y], 1e-6f);
        float[] slow = compute(walking(5, .1f)), fast = compute(walking(5, .8f));
        assertTrue(Math.abs(slow[R_LEG_X]) < Math.abs(fast[R_LEG_X]));
    }

    @Test
    public void theHeadKeepsLookingWhereThePlayerLooksWhateverTheChestDoes() {
        for (float swing = 0; swing < 7; swing += .3f) {
            LocomotionInputs in = walking(swing, .9f);
            in.headYaw = 25;
            in.headPitch = -10;
            float[] t = compute(in);
            assertEquals(Math.toRadians(25), t[HEAD_Y] + t[BODY_Y], 1e-5);
            assertEquals(Math.toRadians(-10), t[HEAD_X] + t[BODY_X], 1e-5);
        }
    }

    @Test
    public void sprintingLeansForwardAndBendsTheElbowsHarder() {
        LocomotionInputs run = walking(2, .9f);
        run.sprinting = true;
        float[] r = compute(run), w = compute(walking(2, .9f));
        assertTrue(r[BODY_X] > w[BODY_X] + Math.toRadians(10));
        assertTrue(r[R_FORE] > w[R_FORE] + Math.toRadians(40));
    }

    @Test
    public void airborneRaisesTheArmsOutAndBendsTheKnees() {
        LocomotionInputs jump = walking(0, 0);
        jump.onGround = false;
        jump.ticksAirborne = 8;
        float[] t = compute(jump);
        assertTrue(t[R_ARM_Z] > Math.toRadians(30) && t[L_ARM_Z] < -Math.toRadians(30));
        assertTrue(t[R_SHIN] > Math.toRadians(30));
    }

    @Test
    public void landingFoldsForwardAndGivesAtTheKneesThenRecovers() {
        float last = Float.MAX_VALUE;
        for (float ticks = 0; ticks <= 6; ticks += .5f) {
            LocomotionInputs in = walking(0, 0);
            in.ticksSinceTouchdown = ticks;
            float[] t = compute(in);
            float fold = t[BODY_X];
            if (ticks > 0) assertTrue(fold <= last + 1e-6f);
            last = fold;
            if (ticks >= 5) assertEquals(0, t[R_SHIN], 1e-6f);
        }
        LocomotionInputs now = walking(0, 0);
        now.ticksSinceTouchdown = 0;
        assertTrue(compute(now)[R_SHIN] > Math.toRadians(25));
    }

    @Test
    public void sneakingAndABusyArmAreLeftToVanilla() {
        LocomotionInputs in = walking(2, .8f);
        in.sneaking = true;
        float[] t = compute(in);
        for (float v : t) assertEquals(123f, v, 0);
        in.sneaking = false;
        in.armsBusy = true;
        t = compute(in);
        assertEquals(123f, t[R_ARM_X], 0);
        assertEquals(123f, t[L_FORE], 0);
        assertNotEquals(123f, t[R_LEG_X], 0);
    }
}
