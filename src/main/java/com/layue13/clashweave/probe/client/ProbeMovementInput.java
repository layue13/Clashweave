package com.layue13.clashweave.probe.client;

import net.minecraft.util.MovementInput;

/** S5 wraps the original provider; remove both sneaking and its 0.3 movement multiplier. */
final class ProbeMovementInput extends MovementInput {

    final MovementInput original;
    boolean suppressSneak;

    ProbeMovementInput(MovementInput original) {
        this.original = original;
    }

    @Override
    public void updatePlayerMoveState() {
        original.updatePlayerMoveState();
        moveStrafe = original.moveStrafe;
        moveForward = original.moveForward;
        jump = original.jump;
        sneak = original.sneak;
        if (suppressSneak && sneak) {
            moveStrafe /= 0.3F;
            moveForward /= 0.3F;
            sneak = false;
        }
    }
}
