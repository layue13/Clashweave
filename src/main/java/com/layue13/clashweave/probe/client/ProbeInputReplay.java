package com.layue13.clashweave.probe.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovementInputFromOptions;

import com.layue13.clashweave.probe.ProbeServer;

final class ProbeInputReplay {

    private ProbeInputReplay() {}

    static void run(Minecraft mc) {
        ProbeMovementInput input = new ProbeMovementInput(new MovementInputFromOptions(mc.gameSettings));
        ProbeEdges edges = new ProbeEdges();
        int assertions = 0;
        try {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            input.suppressSneak = false;
            input.updatePlayerMoveState();
            check(input.sneak && Math.abs(input.moveForward - 0.3F) < 0.001F, "peace sneaks at 0.3");
            assertions++;
            edges.sample(true, false, false);
            input.suppressSneak = true;
            input.updatePlayerMoveState();
            check(
                !input.sneak && Math.abs(input.moveForward - 1) < 0.001F && input.jump,
                "engage restores movement and jump");
            assertions++;
            check(!edges.sample(true, true, false), "held Shift on engagement creates no press");
            assertions++;
            edges.sample(false, true, false);
            check(edges.sample(true, true, false), "real rising edge creates press");
            assertions++;
            check(!edges.sample(true, true, false), "hold creates no repeat");
            assertions++;
            edges.sample(true, false, true);
            check(!edges.sample(true, true, false), "GUI close while held creates no press");
            assertions++;
            edges.sample(true, false, false);
            check(!edges.sample(true, true, false), "equip while held creates no press");
            assertions++;
            edges.sample(false, true, false);
            check(edges.sample(true, true, false), "release then press survives transitions");
            assertions++;
            input.suppressSneak = false;
            input.updatePlayerMoveState();
            check(input.sneak && input.jump && Math.abs(input.moveForward - 0.3F) < 0.001F, "peace mapping restored");
            assertions++;
            check(edges.presses == 2, "exactly two genuine presses");
            assertions++;
            ProbeServer.log(
                "S5_REPLAY assertions=" + assertions
                    + " presses="
                    + edges.presses
                    + " releases="
                    + edges.releases
                    + " passed=true provider=MovementInputFromOptions");
        } finally {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        }
    }

    private static void check(boolean condition, String message) {
        ProbeServer.log("S5_CHECK " + message + " passed=" + condition);
        if (!condition) throw new IllegalStateException("S5: " + message);
    }
}
