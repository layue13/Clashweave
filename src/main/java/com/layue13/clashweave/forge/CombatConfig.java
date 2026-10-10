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
    public int futureTolerance;
    public int disengage;
    public double radius;
    public double lockAcquireRange;
    public double lockKeepRange;
    public double lockConeHalfAngle;
    public com.layue13.clashweave.core.ThirdPersonCamera.Preset lockCompositionPreset;
    public double lockStrongSmoothTime, lockStrongThirdPitchInfluence, lockTargetFilterTau;
    public double lockCompositionHorizonTolerance;
    public double cameraDistance, cameraHeightOffset, baselinePitchDown, lateralOffset, targetScreenY, playerScreenY;
    public double cameraDistanceAdjustment, cameraPitchAdjustment, cameraCollisionRadius, cameraCollisionPadding;
    public double cameraCollisionLookAhead, cameraHidePlayerDistance, cameraHidePlayerHysteresis;
    public double lockStrongThirdMaxOffset, lockStrongThirdOffsetDecay, lockFlickThresholdDegrees;
    public long lockFlickWindowMillis, lockFlickCooldownMillis;
    public boolean lockAssistEnabled;
    public double lockAssistCone;
    public double lockAssistMaxDegrees;
    public com.layue13.clashweave.core.LockFollow.Mode lockFollowFirstPerson;
    public com.layue13.clashweave.core.LockFollow.Mode lockFollowThirdPerson;
    public final com.layue13.clashweave.core.LockFollow.Settings lockFollow = new com.layue13.clashweave.core.LockFollow.Settings();
    public boolean lockAutoThirdPerson;
    public boolean lockRestoreView;
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
        futureTolerance = config
            .getInt("futureTolerance", "network", 2, 0, 4, "Future stamp allowance; clamp to receipt tick");
        disengage = config.getInt("disengageTicks", "combat", 60, 1, 400, "Exit hysteresis");
        radius = config.getFloat("engageRadius", "combat", 8, 1, 32, "Hostile target radius");
        lockAcquireRange = config.getFloat("lockAcquireRange", "lock", 16, 1, 64, "Visible target acquisition range");
        lockKeepRange = config.getFloat("lockKeepRange", "lock", 20, 1, 96, "Keep range; clamped above acquire range");
        lockKeepRange = Math.max(lockAcquireRange + 1, lockKeepRange);
        lockConeHalfAngle = config.getFloat("lockConeHalfAngle", "lock", 30, 1, 90, "Acquisition cone half angle");
        lockAssistEnabled = config.getBoolean("lockAssistEnabled", "lock", true, "Server commitment assistance");
        lockAssistCone = config.getFloat("lockAssistCone", "lock", 45, 0, 90, "Horizontal half cone degrees");
        lockAssistMaxDegrees = config
            .getFloat("lockAssistMaxDegrees", "lock", 15, 0, 30, "Maximum commitment correction");
        lockFollowFirstPerson = com.layue13.clashweave.core.LockFollow.Mode.valueOf(
            config.getString(
                "lockFollowFirstPerson",
                "camera",
                "WEAK",
                "Local camera mode",
                new String[] { "OFF", "WEAK", "STRONG" }));
        lockFollowThirdPerson = com.layue13.clashweave.core.LockFollow.Mode.valueOf(
            config.getString(
                "lockFollowThirdPerson",
                "camera",
                "STRONG",
                "Local camera mode",
                new String[] { "OFF", "WEAK", "STRONG" }));
        lockFollow.graceNanos = config.getInt("lockFollowGraceMillis", "camera", 250, 0, 2000, "Weak mouse grace")
            * 1_000_000L;
        lockFollow.lostNanos = config.getInt("lockLostGraceMillis", "camera", 1000, 0, 5000, "Occlusion grace")
            * 1_000_000L;
        lockFollow.weakSpeed = config
            .getFloat("lockWeakMaxDegreesPerSecond", "camera", 180, 0, 720, "Weak yaw speed cap");
        lockFollow.weakPitchSpeed = config
            .getFloat("lockWeakPitchMaxDegreesPerSecond", "camera", 180, 0, 720, "Weak pitch speed cap");
        lockFollow.deadZone = config.getFloat("lockFollowDeadZone", "camera", 8, 0, 45, "Weak composition tolerance");
        lockFollow.strongSpeed = config
            .getFloat("lockStrongMaxDegreesPerSecond", "camera", 720, 0, 1440, "Strong yaw and pitch speed cap");
        lockFollow.maxOffset = config
            .getFloat("lockStrongMaxOffset", "camera", 30, 0, 90, "Strong horizontal mouse offset cap");
        lockFollow.decay = config
            .getFloat("lockStrongOffsetDecay", "camera", 120, 0, 720, "Strong offset decay degrees per second");
        lockFollow.pitchMin = config.getFloat("lockStrongPitchMin", "camera", -45, -90, 90, "Strong lower pitch bound");
        lockFollow.pitchMax = Math.max(
            lockFollow.pitchMin,
            config.getFloat("lockStrongPitchMax", "camera", 60, -90, 90, "Strong upper pitch bound"));
        lockCompositionPreset = com.layue13.clashweave.core.ThirdPersonCamera.parse(
            config.getString(
                "lockCompositionPreset",
                "camera",
                "SOULS",
                "SOULS experimental centered raised lock camera; OFF leaves vanilla camera geometry",
                new String[] { "OFF", "SOULS" }));
        config.get("camera", "lockCompositionPreset", "SOULS")
            .set(lockCompositionPreset.name());
        cameraDistance = config.getFloat(
            "cameraDistance",
            "camera",
            4.2f,
            1,
            10,
            "Rear orbit distance in blocks; empirical starting value, pending playtest");
        cameraHeightOffset = config
            .getFloat("cameraHeightOffset", "camera", .6f, 0, 3, "Camera pivot height above player eye in blocks");
        baselinePitchDown = config
            .getFloat("baselinePitchDown", "camera", 18, -30, 60, "Baseline downward camera pitch in degrees");
        lateralOffset = config.getFloat(
            "lateralOffset",
            "camera",
            0,
            -2,
            2,
            "Local horizontal offset in blocks; default centered, no shoulder mode");
        targetScreenY = config.getFloat(
            "targetScreenY",
            "camera",
            .38f,
            .1f,
            .9f,
            "Desired target body center screen fraction measured from top");
        playerScreenY = config.getFloat(
            "playerScreenY",
            "camera",
            .65f,
            .1f,
            .9f,
            "Desired player body center screen fraction measured from top");
        cameraDistanceAdjustment = config.getFloat(
            "cameraDistanceAdjustment",
            "camera",
            2f,
            0,
            3,
            "Maximum adaptive distance change in blocks about cameraDistance");
        cameraPitchAdjustment = config.getFloat(
            "cameraPitchAdjustment",
            "camera",
            42,
            0,
            60,
            "Maximum adaptive pitch change in degrees about baselinePitchDown");
        cameraCollisionRadius = config.getFloat(
            "cameraCollisionRadius",
            "camera",
            .12f,
            .05f,
            .4f,
            "Camera swept cube radius in blocks including overhead rays");
        cameraCollisionPadding = config.getFloat(
            "cameraCollisionPadding",
            "camera",
            .3f,
            0,
            1,
            "Early obstruction margin in blocks; allows gradual approach before contact");
        cameraCollisionLookAhead = config.getFloat(
            "cameraCollisionLookAhead",
            "camera",
            .6f,
            0,
            2,
            "Horizontal anticipation radius for early smooth wall/ceiling retraction in blocks");
        cameraCollisionLookAhead = finite(cameraCollisionLookAhead, .6);
        cameraHidePlayerDistance = config.getFloat(
            "cameraHidePlayerDistance",
            "camera",
            1,
            0,
            2,
            "Hide only the local model when collision brings the SOULS camera this close to the eye; 0 disables");
        cameraHidePlayerHysteresis = config.getFloat(
            "cameraHidePlayerHysteresis",
            "camera",
            .2f,
            0,
            1,
            "Additional distance before showing the local model again; prevents flicker");
        cameraHidePlayerDistance = finite(cameraHidePlayerDistance, 1);
        cameraHidePlayerHysteresis = finite(cameraHidePlayerHysteresis, .2);
        cameraDistance = finite(cameraDistance, 4.2);
        cameraHeightOffset = finite(cameraHeightOffset, .6);
        baselinePitchDown = finite(baselinePitchDown, 18);
        lateralOffset = finite(lateralOffset, 0);
        targetScreenY = finite(targetScreenY, .38);
        playerScreenY = finite(playerScreenY, .65);
        cameraDistanceAdjustment = finite(cameraDistanceAdjustment, 2);
        cameraPitchAdjustment = finite(cameraPitchAdjustment, 42);
        cameraCollisionRadius = finite(cameraCollisionRadius, .12);
        cameraCollisionPadding = finite(cameraCollisionPadding, .3);
        lockCompositionHorizonTolerance = config.getFloat(
            "lockCompositionHorizonTolerance",
            "camera",
            .025f,
            0,
            .1f,
            "Additional horizon envelope in screen-height fractions");
        lockStrongSmoothTime = config
            .getFloat("lockStrongSmoothTime", "camera", .15f, .03f, .5f, "Critical damping smooth time seconds");
        lockStrongThirdPitchInfluence = config.getFloat(
            "lockStrongThirdPitchInfluence",
            "camera",
            8,
            0,
            30,
            "Third-person target height influence degrees");
        lockTargetFilterTau = config
            .getFloat("lockTargetFilterTau", "camera", .1f, 0, 1, "Third-person target position filter seconds");
        lockStrongThirdMaxOffset = config.getFloat(
            "lockStrongThirdMaxOffset",
            "camera",
            8,
            0,
            90,
            "Third-person mouse offset; first-person legacy config unchanged");
        lockStrongThirdOffsetDecay = config.getFloat(
            "lockStrongThirdOffsetDecay",
            "camera",
            240,
            0,
            720,
            "Third-person offset decay degrees per second");
        lockFlickWindowMillis = config
            .getInt("lockFlickWindowMillis", "camera", 150, 20, 500, "Flick accumulation window");
        lockFlickCooldownMillis = config
            .getInt("lockFlickCooldownMillis", "lock", 400, 100, 2000, "Server enforced target switch cooldown");
        lockFlickThresholdDegrees = config
            .getFloat("lockFlickThresholdDegrees", "camera", 25, 5, 90, "Flick threshold degrees");
        lockAutoThirdPerson = config
            .getBoolean("lockAutoThirdPerson", "camera", false, "Local lock enters rear view once");
        lockRestoreView = config.getBoolean("lockRestoreView", "camera", true, "Restore unless player used F5");
        config.getCategory("facing")
            .remove("maxDegreesPerTick");
        facingTolerance = config
            .getFloat("angleTolerance", "facing", 5, 0, 30, "C03 history matching tolerance degrees");
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

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
