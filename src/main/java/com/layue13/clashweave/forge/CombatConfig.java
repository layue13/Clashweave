package com.layue13.clashweave.forge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import net.minecraftforge.common.config.Configuration;

import com.layue13.clashweave.core.ActionCatalog;

public final class CombatConfig {

    public double presentationRange;
    public double multiplierMinimum;
    public double multiplierMaximum;
    public int maxResistance;
    public int defer;
    public int perfectWindow;
    public int guardCooldown;
    public int counterWindow;
    public int stampAge;
    public int disengage;
    public double radius;
    public double lockAcquireRange;
    public double lockKeepRange;
    public double lockConeHalfAngle;
    public double facingMaxTurn;
    public double facingTolerance;
    public int facingHistoryAge;
    public double facingHistoryDrift;
    public int sweepLayers;
    public double sweepBottom;
    public double sweepTop;
    public double guardArc;
    public double blockDamage;
    public double movementMargin;
    public double walkAllowance;
    public double sprintAllowance;
    public double flyingAllowance;
    public double jumpAllowance;
    public double impulseMultiplier;
    public double movementEpsilon;
    public boolean friendly;
    public boolean blocks;
    public ActionCatalog actions;

    public void load(File directory) throws Exception {
        directory.mkdirs();
        Configuration config = new Configuration(new File(directory, "combat.cfg"));
        config.load();
        presentationRange = config.getFloat("eventRange", "presentation", 64, 8, 256, "Semantic event observer range");
        multiplierMinimum = config
            .getFloat("multiplierMinimum", "weapon", .5f, .1f, 1, "Server numerical multiplier lower bound");
        multiplierMaximum = config
            .getFloat("multiplierMaximum", "weapon", 2, 1, 8, "Server numerical multiplier upper bound");
        maxResistance = config.getInt("maxHurtResistantTime", "combat", 8, 1, 40, "Persistent engaged-target timer");
        defer = config.getInt("confirmationDelay", "combat", 3, 1, 8, "Tick and wall-clock minimum");
        perfectWindow = config.getInt("perfectWindow", "combat", 5, 1, 8, "Half-open press window");
        guardCooldown = config.getInt("guardCooldown", "combat", 6, 1, 40, "Repeat press restriction");
        counterWindow = config.getInt(
            "counterWindowTicks",
            "combat",
            8,
            1,
            40,
            "P0 provisional ordinary counter entry; original timing missing");
        stampAge = config.getInt("stampAgeLimit", "network", 8, 1, 20, "Maximum accepted input age");
        disengage = config.getInt("disengageTicks", "combat", 60, 1, 400, "Exit hysteresis");
        radius = config.getFloat("engageRadius", "combat", 8, 1, 32, "Hostile target radius");
        lockAcquireRange = config.getFloat("lockAcquireRange", "lock", 16, 1, 64, "Visible target acquisition range");
        lockKeepRange = config.getFloat("lockKeepRange", "lock", 20, 1, 96, "Keep range; clamped above acquire range");
        lockKeepRange = Math.max(lockAcquireRange + 1, lockKeepRange);
        lockConeHalfAngle = config.getFloat("lockConeHalfAngle", "lock", 30, 1, 90, "Acquisition cone half angle");
        facingMaxTurn = config.getFloat("maxDegreesPerTick", "facing", 30, 1, 180, "Snapshot turn-rate limit");
        facingTolerance = config.getFloat("angleTolerance", "facing", 5, 0, 30, "Packet-phase tolerance degrees");
        facingHistoryAge = config.getInt("historyAgeMillis", "facing", 500, 50, 2000, "Maximum packet history age");
        facingHistoryDrift = config
            .getFloat("historyPositionDrift", "facing", 4, 0.1f, 16, "Maximum distance from recent position packet");
        sweepLayers = config.getInt("layers", "sweep", 4, 2, 8, "Body-height samples");
        sweepBottom = config.getFloat("bottomFraction", "sweep", 0.1f, 0, 1, "Lowest body-height fraction");
        sweepTop = config.getFloat("topFraction", "sweep", 0.95f, 0, 1.5f, "Highest body-height fraction");
        guardArc = config.getFloat("guardArc", "combat", 140, 1, 180, "Degrees");
        blockDamage = config.getFloat("ordinaryGuardDamage", "combat", 0.25f, 0, 1, "Damage multiplier");
        movementMargin = config.getFloat("movementMarginTicks", "movement", 2, 0, 4, "Batch allowance");
        walkAllowance = config.getFloat("walkAllowance", "movement", 0.28f, 0, 2, "Blocks per 50ms");
        sprintAllowance = config.getFloat("sprintAllowance", "movement", 0.36f, 0, 2, "Blocks per 50ms");
        flyingAllowance = config.getFloat("flyingAllowance", "movement", 0.6f, 0, 4, "Blocks per 50ms");
        jumpAllowance = config
            .getFloat("jumpAllowance", "movement", 1.25f, 0, 8, "Cumulative upward allowance per verified jump");
        impulseMultiplier = config
            .getFloat("impulseMultiplier", "movement", 4, 1, 20, "Server velocity distance credit");
        movementEpsilon = config.getFloat("movementEpsilon", "movement", 0.03f, 0, 0.5f, "Position tolerance");
        friendly = config.getBoolean("protectFriendly", "combat", true, "Villagers, tamed pets, team players");
        blocks = config.getBoolean("peacefulBlockInteraction", "input", true, "Block right click outside engagement");
        config.save();
        File data = new File(directory, CombatWeapons.DEFAULT.actionData + ".json");
        if (!data.exists()) {
            try (
                InputStream input = getClass()
                    .getResourceAsStream("/assets/clashweave/combat/" + CombatWeapons.DEFAULT.actionData + ".json");
                FileOutputStream output = new FileOutputStream(data)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            }
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(data), StandardCharsets.UTF_8)) {
            actions = new ActionCatalog(reader);
        }
    }
}
