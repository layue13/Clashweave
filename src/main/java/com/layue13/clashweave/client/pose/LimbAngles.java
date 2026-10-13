package com.layue13.clashweave.client.pose;

/** Reusable hinged-limb result. Angles are radians; distances are Biped units. */
public final class LimbAngles {

    public final float[] upper = new float[3];
    public float childAngleX;
    public float flex;
    public boolean reachable;
    public float shortfall;

    final float[] pivot = new float[3];
    final float[] target = new float[3];
    final float[] upperDir = new float[3];
    final float[] lowerDir = new float[3];
}
