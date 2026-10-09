package com.layue13.clashweave.probe;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Disposable measurements, not a combat API. Normal launches register nothing. */
public final class ProbeConfig {

    public static boolean enabled;
    public static int segmentTicks;
    public static int guardWindow;
    public static int rewindLimit;
    public static int disengageTicks;
    public static double engageRadius;
    public static double stepSpeed;
    public static int stepTicks;
    public static int swingTicks;
    public static boolean protectFriendly;
    public static boolean allowBlocks;

    private ProbeConfig() {}

    public static void load(File file) {
        Configuration c = new Configuration(file);
        c.load();
        enabled = Boolean.getBoolean("clashweave.probes");
        segmentTicks = c.getInt("segmentTicks", "probes", 7, 1, 100, "S1 interval; not balance");
        guardWindow = c.getInt("guardWindow", "probes", 5, 1, 20, "S3 half-open window");
        rewindLimit = c.getInt("rewindLimit", "probes", 4, 0, 10, "Maximum accepted input age");
        disengageTicks = c.getInt("disengageTicks", "probes", 60, 0, 200, "S5 hysteresis");
        engageRadius = c.getFloat("engageRadius", "probes", 8, 0, 64, "Hostile targeting radius");
        stepSpeed = c.getFloat("stepSpeed", "probes", 0.4F, 0.01F, 1, "S2 blocks per tick");
        stepTicks = c.getInt("stepTicks", "probes", 5, 1, 20, "S2 envelope duration");
        swingTicks = c.getInt("swingTicks", "probes", 13, 1, 100, "S4 visual duration only");
        protectFriendly = c.getBoolean("protectFriendly", "probes", true, "Provisional default; user review pending");
        allowBlocks = c.getBoolean("allowPeacefulBlockUse", "probes", true, "Provisional default; user review pending");
        c.save();
    }
}
