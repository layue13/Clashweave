package com.layue13.clashweave.client.body;

import static com.layue13.clashweave.client.body.Channels.*;

/**
 * Standing, walking, sprinting, jumping and landing as targets for the body channels. The shapes follow the way Mo'
 * Bends
 * (MIT) coordinates a walk: arms and legs swing in antiphase, the knee bends only while the leg swings forward, the
 * chest
 * counter-twists against the hips and the head cancels the chest's movement so the eyes stay level, the body bobs twice
 * per stride and folds forward on landing. Rewritten for this skeleton's axes and conventions; nothing here is a copy.
 *
 * <p>
 * It only overwrites what it animates. The caller fills the targets with vanilla's pose first, so a state this class
 * does not know (sneaking, and for now swimming, riding, climbing, sleeping) keeps vanilla's pose.
 */
public final class Locomotion {

    private static final float DEG = (float) (Math.PI / 180);

    private Locomotion() {}

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    public static void compute(LocomotionInputs in, float[] t) {
        if (in.sneaking) return;
        float amount = clamp(in.limbSwingAmount, 0, 1);
        float walk = Math.min(1, amount * 3);
        float run = in.sprinting ? Math.min(1, amount * 2) : 0;
        float cycle = in.limbSwing * .6662f;
        float kneel = in.onGround ? Math.max(0, 1 - in.ticksSinceTouchdown / 5) : 0;
        float air = in.onGround ? 0 : Math.min(1, in.ticksAirborne / 5);

        float breathing = (float) Math.sin(in.age * .067f);
        float sway = (float) Math.cos(in.age * .09f);
        float swing = (float) Math.cos(cycle);

        // Torso: twists against the hips, rocks twice a stride, leans into a run, folds forward on landing.
        float bodyY = -swing * (14 + 5 * run) * DEG * walk;
        float bodyX = ((1.5f + .8f * breathing) * (1 - walk) + (3 + 5 * (float) Math.cos(2 * cycle)) * walk) * DEG
            + (12 * run + 8 * air + 18 * kneel) * DEG;
        t[BODY_X] = bodyX;
        t[BODY_Y] = bodyY;
        t[BODY_Z] = -clamp(in.headYaw * .1f, -10, 10) * DEG * walk;
        // The head cancels what the chest does, so the eyes keep looking where the player looks.
        t[HEAD_X] = in.headPitch * DEG - bodyX;
        t[HEAD_Y] = in.headYaw * DEG - bodyY;
        t[ROOT_Y] = (float) Math.cos(2 * cycle) * (.6f + .7f * run) * walk * (1 - air)
            + 2.5f * (float) Math.sin(kneel * Math.PI);

        // Legs: antiphase swing; the knee bends only while its leg swings forward.
        float legSwing = amount * 40 * (1 + .35f * run) * DEG;
        float forward = (35 + 40 * run) * walk * DEG;
        float rightSwing = (float) Math.sin(cycle), leftSwing = -rightSwing;
        t[R_LEG_X] = swing * legSwing - 5 * walk * DEG;
        t[L_LEG_X] = -swing * legSwing - 5 * walk * DEG;
        t[R_LEG_Z] = 2 * DEG;
        t[L_LEG_Z] = -2 * DEG;
        t[R_LEG_Y] = t[L_LEG_Y] = 0;
        // A run kicks the heel up soon after toe-off, while the leg is still behind: its bend peaks a little earlier in
        // the cycle than a walk's, and the planted knee gives a little under the weight.
        float kick = (float) Math.sin(cycle + .6f), kickL = -kick;
        t[R_SHIN] = lerp(Math.max(0, rightSwing) * forward, Math.max(0, kick) * 95 * DEG + 12 * DEG * run, run);
        t[L_SHIN] = lerp(Math.max(0, leftSwing) * forward, Math.max(0, kickL) * 95 * DEG + 12 * DEG * run, run);

        if (!in.armsBusy) {
            float armSwing = amount * 28.6f * (1 + 1.1f * run) * DEG;
            float idle = breathing * 2.5f * DEG * (1 - walk);
            t[R_ARM_X] = -swing * armSwing + idle;
            t[L_ARM_X] = swing * armSwing - idle;
            t[R_ARM_Y] = t[L_ARM_Y] = 0;
            t[R_ARM_Z] = (5 + 2 * sway * (1 - walk)) * DEG;
            t[L_ARM_Z] = -(5 + 2 * sway * (1 - walk)) * DEG;
            float rest = 8 * DEG;
            // Running pumps the arms: the elbow stays near a right angle and closes further as the hand comes forward.
            t[R_FORE] = rest + (10 * (.5f + .5f * swing) * walk + (68 + 22 * swing) * run) * DEG;
            t[L_FORE] = rest + (10 * (.5f - .5f * swing) * walk + (68 - 22 * swing) * run) * DEG;
        }

        if (!in.onGround || kneel > 0) {
            // Airborne: arms out and a little forward, one knee drawn up; landing: both knees give and the arms drop.
            float a = air, k = kneel;
            t[L_LEG_X] = lerp(t[L_LEG_X], 20 * DEG, a);
            t[R_LEG_X] = lerp(t[R_LEG_X], -30 * DEG, a);
            t[R_SHIN] = lerp(t[R_SHIN], 50 * DEG, a);
            t[L_SHIN] = lerp(t[L_SHIN], 25 * DEG, a);
            t[R_LEG_X] -= 25 * DEG * k;
            t[L_LEG_X] -= 25 * DEG * k;
            t[R_SHIN] += 35 * DEG * k;
            t[L_SHIN] += 35 * DEG * k;
            if (!in.armsBusy) {
                t[R_ARM_X] = lerp(t[R_ARM_X], -25 * DEG, a);
                t[L_ARM_X] = lerp(t[L_ARM_X], -25 * DEG, a);
                t[R_ARM_Z] = lerp(t[R_ARM_Z], 40 * DEG, a);
                t[L_ARM_Z] = lerp(t[L_ARM_Z], -40 * DEG, a);
                t[R_FORE] = lerp(t[R_FORE], 25 * DEG, a);
                t[L_FORE] = lerp(t[L_FORE], 25 * DEG, a);
            }
        }
    }

    private static float lerp(float a, float b, float w) {
        return a + (b - a) * w;
    }
}
