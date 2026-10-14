package com.layue13.clashweave.client.body;

/** The animated channels of a biped body, angles in radians and the root bob in model pixels. */
public final class Channels {

    public static final int HEAD_X = 0, HEAD_Y = 1;
    public static final int BODY_X = 2, BODY_Y = 3, BODY_Z = 4;
    public static final int R_ARM_X = 5, R_ARM_Y = 6, R_ARM_Z = 7;
    public static final int L_ARM_X = 8, L_ARM_Y = 9, L_ARM_Z = 10;
    /** Forearm and shin: how far the lower segment is bent, never negative. */
    public static final int R_FORE = 11, L_FORE = 12, R_SHIN = 13, L_SHIN = 14;
    public static final int R_LEG_X = 15, R_LEG_Y = 16, R_LEG_Z = 17;
    public static final int L_LEG_X = 18, L_LEG_Y = 19, L_LEG_Z = 20;
    /** Body, head and arms rise and sink together; model pixels, positive down. */
    public static final int ROOT_Y = 21;
    public static final int COUNT = 22;

    /** Regions an animation layer can take over from vanilla's own pose. */
    public static final int HEAD = 1, BODY = 2, ARMS = 4, LEGS = 8, ALL = HEAD | BODY | ARMS | LEGS;

    /**
     * Fraction of the distance to the target each channel covers per tick. Legs and arms follow closely, the torso and
     * head
     * lag a little: the lag between a stepping leg and the chest above it is most of what makes a walk read as soft.
     */
    static final float[] SMOOTHNESS = new float[COUNT];

    static {
        SMOOTHNESS[HEAD_X] = SMOOTHNESS[HEAD_Y] = .5f;
        SMOOTHNESS[BODY_X] = SMOOTHNESS[BODY_Y] = SMOOTHNESS[BODY_Z] = .5f;
        for (int c : new int[] { R_ARM_X, R_ARM_Y, R_ARM_Z, L_ARM_X, L_ARM_Y, L_ARM_Z, R_FORE, L_FORE })
            SMOOTHNESS[c] = .8f;
        for (int c : new int[] { R_LEG_X, R_LEG_Y, R_LEG_Z, L_LEG_X, L_LEG_Y, L_LEG_Z }) SMOOTHNESS[c] = .9f;
        SMOOTHNESS[R_SHIN] = SMOOTHNESS[L_SHIN] = .6f;
        SMOOTHNESS[ROOT_Y] = .6f;
    }

    private Channels() {}
}
