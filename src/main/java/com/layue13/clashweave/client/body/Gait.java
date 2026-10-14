package com.layue13.clashweave.client.body;

/**
 * The numbers that make one body's walk its own: how far the chest twists and leans, how high the bob, how wide the
 * legs swing and how far the knees fold, how the arms carry. {@link #PLAYER} holds the values the player's walk was
 * tuned with; the monsters differ from it, not from each other's copy of it. Angles are degrees, offsets pixels.
 * Instances are shared and never changed after construction.
 */
public final class Gait {

    /** Chest twist against the hips, and forward lean while walking and while idle, plus the rock twice a stride. */
    public float twist = 14, lean = 3, idleLean = 1.5f, rock = 5;
    /** Vertical bob, thigh swing and knee fold per stride, and a knee that stays a little bent while walking. */
    public float bob = .6f, legSwing = 40, knee = 35, drag = 0;
    /** Free arms: swing, resting elbow and the elbow's swing with the stride. */
    public float armSwing = 28.6f, elbowRest = 8, elbowSwing = 10;
    /** Head sagging forward of the chest. */
    public float headDroop = 0;
    /** An arm the model holds up itself: elbow added when it holds nothing, and a slow sway about its pose. */
    public float busyElbow = 25, sway = 0;

    public static final Gait PLAYER = new Gait();

    /** Slouched, leaning into the walk, dragging a foot, head lolling, arms swaying out in front. */
    public static final Gait ZOMBIE = new Gait();

    /** Upright and brisk: short twist, sharp knees, no droop. */
    public static final Gait SKELETON = new Gait();

    /** Tall and unhurried: long legs swing little, knees fold slowly, thin arms trail loosely. */
    public static final Gait ENDERMAN = new Gait();

    static {
        ENDERMAN.twist = 6;
        ENDERMAN.lean = 2;
        ENDERMAN.idleLean = 0;
        ENDERMAN.rock = 2;
        ENDERMAN.bob = .3f;
        ENDERMAN.legSwing = 22;
        ENDERMAN.knee = 26;
        ENDERMAN.armSwing = 16;
        ENDERMAN.elbowRest = 5;
        ENDERMAN.elbowSwing = 12;
        ENDERMAN.busyElbow = 0;

        ZOMBIE.twist = 7;
        ZOMBIE.lean = 9;
        ZOMBIE.idleLean = 6;
        ZOMBIE.rock = 3;
        ZOMBIE.bob = .35f;
        ZOMBIE.legSwing = 30;
        ZOMBIE.knee = 22;
        ZOMBIE.drag = 8;
        ZOMBIE.headDroop = 12;
        ZOMBIE.busyElbow = 14;
        ZOMBIE.sway = 6;

        SKELETON.twist = 5;
        SKELETON.lean = 1;
        SKELETON.idleLean = 0;
        SKELETON.rock = 2;
        SKELETON.bob = .25f;
        SKELETON.legSwing = 38;
        SKELETON.knee = 55;
        SKELETON.busyElbow = 0;
    }
}
