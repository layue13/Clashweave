package com.layue13.clashweave.core;

/**
 * The places on the body a weapon's scabbard can hang, named in the resource data ({@code mounts[].slot}, and
 * {@code stowedSlot} to say which one the stowed model uses). Each slot is a fixed anchor on the skeleton; the data
 * gives
 * the offset and angle from it. A new anchor needs code, a new mount on an existing anchor is only data.
 */
public final class MountSlots {

    public static final String LEFT_HIP = "left_hip", RIGHT_HIP = "right_hip", BACK = "back";

    /** The slot a resource without {@code stowedSlot} uses. */
    public static final String DEFAULT = LEFT_HIP;

    private MountSlots() {}

    public static boolean known(String slot) {
        return LEFT_HIP.equals(slot) || RIGHT_HIP.equals(slot) || BACK.equals(slot);
    }
}
