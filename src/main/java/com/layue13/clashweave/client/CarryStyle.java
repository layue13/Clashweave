package com.layue13.clashweave.client;

import java.util.HashMap;
import java.util.Map;

/**
 * How a drawn weapon is carried one-handed while the other arm is busy (climbing, swimming): each weapon has its own,
 * because a katana, a spear and a shield do not hang the same way. Looked up by the weapon's skin id when a frame
 * needs it; unknown weapons get {@link #DEFAULT}.
 */
final class CarryStyle {

    /** Pitch of the blade about the hand, degrees: negative tips it forward of the arm, positive back along it. */
    final double pitch;

    private CarryStyle(double pitch) {
        this.pitch = pitch;
    }

    static final CarryStyle DEFAULT = new CarryStyle(-10);

    /**
     * The katana: carried low in the right hand with the point trailing back and down, the way a drawn blade is run
     * with, so on a ladder or in the water it stays clear of the wall and of the stroke.
     */
    static final CarryStyle KATANA = new CarryStyle(38);

    private static final Map<String, CarryStyle> STYLES = new HashMap<>();

    static {
        STYLES.put("katana:default", KATANA);
    }

    /** One HashMap probe on a String whose hash is cached. */
    static CarryStyle of(String skin) {
        CarryStyle style = STYLES.get(skin);
        return style == null ? DEFAULT : style;
    }
}
