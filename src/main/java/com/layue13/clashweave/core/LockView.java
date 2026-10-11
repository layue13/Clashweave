package com.layue13.clashweave.core;

/** One camera transition per lock episode; manual F5 wins until the episode ends. */
public final class LockView {

    private boolean locked;
    private int original;
    private int expected;
    private boolean manual;
    private boolean switched;

    public int update(boolean active, int current, boolean automatic, boolean restore) {
        if (!locked && active) {
            locked = true;
            original = current;
            manual = false;
            switched = automatic;
            expected = automatic ? 1 : current;
            return expected;
        }
        if (locked && current != expected) manual = true;
        expected = current;
        if (locked && !active) {
            locked = false;
            return switched && restore && !manual ? original : current;
        }
        return current;
    }

    public void manualChange() {
        if (locked) manual = true;
    }

    public void clear() {
        locked = false;
        manual = false;
    }
}
