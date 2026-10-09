package com.layue13.clashweave.probe.client;

/** Tracks physical hold across mapping changes and GUI. A hold never becomes a press. */
final class ProbeEdges {

    boolean previousPhysical;
    boolean previousActive;
    boolean blocked;
    int presses;
    int releases;

    boolean sample(boolean physical, boolean active, boolean gui) {
        boolean rising = physical && !previousPhysical;
        if (gui || (!previousActive && active && previousPhysical)) blocked = physical;
        if (!physical) blocked = false;
        if (previousActive && (!active || !physical)) releases++;
        boolean press = active && rising && !blocked;
        if (press) presses++;
        previousPhysical = physical;
        previousActive = active;
        return press;
    }
}
