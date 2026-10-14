package com.layue13.clashweave.client.body;

/** What the locomotion layer reads about an entity this frame. Reused, never reallocated. */
public final class LocomotionInputs {

    /** Vanilla's walk cycle position and speed (ModelBase's limbSwing and limbSwingAmount). */
    public float limbSwing, limbSwingAmount;
    /** Entity age in ticks with the partial tick, for the idle sway. */
    public float age;
    /** Head look relative to the body, degrees. */
    public float headYaw, headPitch;
    public boolean onGround, sprinting, sneaking;
    /**
     * Which family of poses applies: {@link #GROUND}, {@link #SWIM}, {@link #RIDE} or {@link #CLIMB} or {@link #FLY}.
     */
    public int mode;
    public static final int GROUND = 0, SWIM = 1, RIDE = 2, CLIMB = 3, FLY = 4;
    /** Radians of climbing cycle: advances with height gained or lost on a ladder. */
    public float climbPhase;
    /** Ticks since the feet last touched the ground and since they last left it. */
    public float ticksSinceTouchdown, ticksAirborne;
    /** The right arm belongs to something else (a swing, a drawn bow, eating). */
    public boolean armsBusy;
    /** The left arm too (a drawn bow holds with both). */
    public boolean leftBusy;
    /** The busy arm is using an item (bow, food), not just swinging. */
    public boolean usingItem;
    /** The body's own walk character. */
    public Gait gait = Gait.PLAYER;
}
