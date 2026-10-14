package com.layue13.clashweave.forge;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.layue13.clashweave.content.EffectiveRules;

public final class CombatConfig {

    public double presentationRange;
    public double effectVelocityLimit;
    public int controlCapacity, launchCost, launchProtectionTicks, controlResetTicks, controlExpiryTicks,
        launchCreditTicks;
    public boolean launchRequiresHit, launchRequiresGround, launchExcludeSwimming;
    public String[] launchExemptEntities;
    public float chainDecay;
    public boolean slamRequiresAir;
    public float blockRecoil, parryRecoil;
    public int slamGraceTicks;
    public int gaugeMax, gaugeStreakCap, gaugeStreakTicks, gaugeBlockInterval, gaugeHurtInterval, modeTicks;
    public float gaugeHit, gaugeStreak, gaugeBlock, gaugeParry, gaugeHurt;
    public int dodgeCooldown;
    public int postureMax, postureRecoveryDelay, postureRecoveryPerTick, postureResetPercent;
    public int breakTicksMonster, breakTicksPlayer;
    public float finisherMultiplier, finisherPlayerCap, finisherRange, gaugeFinisher;
    public float breakDamageBonus, postureBlockedShare, posturePrecise, postureScale;
    public double multiplierMinimum;
    public double multiplierMaximum;
    public int maxResistance;
    public int defer;
    public int perfectWindow;
    public int guardCooldown;
    public int counterWindow;
    public int stampAge;
    public int inputEdgeCompensationTicks;
    public int inputEdgeCompensationJitterTicks;
    public int futureTolerance;
    public int disengage;
    public double radius;
    public double lockAcquireRange;
    public double lockKeepRange;
    public double lockConeHalfAngle;
    public double lockConeVerticalHalfAngle;
    public com.layue13.clashweave.core.ThirdPersonCamera.Preset lockCompositionPreset;
    public double lockStrongSmoothTime, lockStrongThirdPitchInfluence, lockTargetFilterTau;
    public double lockCompositionHorizonTolerance;
    public double cameraDistance, cameraHeightOffset, baselinePitchDown, lateralOffset, targetScreenY, playerScreenY;
    public double cameraDistanceMin, cameraDistanceMax, cameraPitchMin, cameraPitchMax, cameraHeadClearance;
    public double smallTargetHeight, largeTargetHeight, smallTargetPitchMax, smallTargetAimFraction;
    public double cameraFeedForwardMaxSpeed, cameraFeedForwardTeleportDistance;
    public double cameraFeedForwardStallSeconds, cameraFeedForwardStallRecovery;
    public double cameraCollisionRadius, cameraCollisionPadding, cameraCollisionMaxSpeed;
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
    public boolean legacyKatanaJson;
    public String[] weaponItems;
    public int helloTimeoutTicks;

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
        inputEdgeCompensationTicks = Math.min(
            stampAge,
            config.getInt(
                "inputEdgeCompensationTicks",
                "network",
                4,
                0,
                8,
                "Late combo edge allowance from validated receipt age; 0 disables. Also capped by server RTT "
                    + "plus jitter allowance; unknown or zero RTT disables compensation; must not exceed stampAgeLimit"));
        inputEdgeCompensationJitterTicks = config.getInt(
            "inputEdgeCompensationJitterTicks",
            "network",
            1,
            0,
            4,
            "Extra ticks for jitter beyond ceil(server keepalive RTT / 2 / 50 ms); only used with positive RTT");
        futureTolerance = config
            .getInt("futureTolerance", "network", 2, 0, 4, "Future stamp allowance; clamp to receipt tick");
        disengage = config.getInt("disengageTicks", "combat", 60, 1, 400, "Exit hysteresis");
        radius = config.getFloat("engageRadius", "combat", 8, 1, 32, "Hostile target radius");
        lockAcquireRange = config.getFloat("lockAcquireRange", "lock", 16, 1, 64, "Visible target acquisition range");
        lockKeepRange = config.getFloat("lockKeepRange", "lock", 20, 1, 96, "Keep range; clamped above acquire range");
        lockKeepRange = Math.max(lockAcquireRange + 1, lockKeepRange);
        lockConeHalfAngle = config
            .getFloat("lockConeHalfAngle", "lock", 30, 1, 90, "Horizontal acquisition half angle");
        lockConeVerticalHalfAngle = config.getFloat(
            "lockConeVerticalHalfAngle",
            "lock",
            60,
            1,
            90,
            "Vertical acquisition half angle about player elevation, using target bbox center; empirical start");
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
            4.3f,
            1,
            10,
            "Initial rear orbit distance; hard bounds and soft anchors determine the settled pose");
        cameraHeightOffset = config
            .getFloat("cameraHeightOffset", "camera", .6f, 0, 3, "Camera pivot height above player eye in blocks");
        baselinePitchDown = config
            .getFloat("baselinePitchDown", "camera", 15, 0, 45, "Baseline downward camera pitch in degrees");
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
            .45f,
            .1f,
            .9f,
            "Desired target body center screen fraction measured from top");
        playerScreenY = config.getFloat(
            "playerScreenY",
            "camera",
            .68f,
            .1f,
            .9f,
            "Desired player body center screen fraction measured from top");
        cameraDistanceMin = finite(
            config.getFloat(
                "cameraDistanceMin",
                "camera",
                3.5f,
                1f,
                10f,
                "Hard orbit distance floor; collision alone may shorten it"),
            3.5);
        cameraDistanceMax = finite(
            config.getFloat("cameraDistanceMax", "camera", 5f, 1f, 10f, "Hard orbit distance ceiling"),
            5);
        cameraPitchMin = finite(
            config.getFloat("cameraPitchMin", "camera", 8f, 0f, 45f, "Minimum downward pitch in degrees"),
            8);
        cameraPitchMax = finite(
            config.getFloat("cameraPitchMax", "camera", 35f, 0f, 45f, "Maximum downward pitch for large targets"),
            35);
        cameraHeadClearance = finite(
            config.getFloat(
                "cameraHeadClearance",
                "camera",
                0.15f,
                0f,
                1f,
                "Aim ray clearance above player head in blocks"),
            0.15);
        smallTargetHeight = finite(
            config.getFloat(
                "smallTargetHeight",
                "camera",
                0.8f,
                0.1f,
                2f,
                "Target bbox height at or below which small-target limits apply"),
            0.8);
        largeTargetHeight = finite(
            config.getFloat(
                "largeTargetHeight",
                "camera",
                1.5f,
                0.2f,
                4f,
                "Target bbox height at or above which large-target limits apply"),
            1.5);
        smallTargetPitchMax = finite(
            config.getFloat(
                "smallTargetPitchMax",
                "camera",
                45f,
                0f,
                45f,
                "Hard small-target pitch ceiling; interpolated by bbox height"),
            45);
        smallTargetAimFraction = finite(
            config.getFloat(
                "smallTargetAimFraction",
                "camera",
                0.85f,
                0.5f,
                1f,
                "Small-target aim height fraction; large targets use torso center"),
            0.85);
        cameraFeedForwardMaxSpeed = finite(
            config.getFloat(
                "cameraFeedForwardMaxSpeed",
                "camera",
                180f,
                0f,
                720f,
                "Bound on filtered target bearing velocity prediction in degrees per second"),
            180);
        cameraFeedForwardTeleportDistance = finite(
            config.getFloat(
                "cameraFeedForwardTeleportDistance",
                "camera",
                3f,
                0.1f,
                10f,
                "Target displacement in blocks that discards velocity prediction"),
            3);
        cameraFeedForwardStallSeconds = finite(
            config.getFloat(
                "cameraFeedForwardStallSeconds",
                "camera",
                .1f,
                .05f,
                1f,
                "Real render interval above which bearing prediction is discarded, in seconds"),
            .1);
        cameraFeedForwardStallRecovery = finite(
            config.getFloat(
                "cameraFeedForwardStallRecovery",
                "camera",
                .3f,
                0f,
                2f,
                "Wall-clock suppression after a stalled frame; existing damping continues, in seconds"),
            .3);
        cameraCollisionMaxSpeed = finite(
            config.getFloat(
                "cameraCollisionMaxSpeed",
                "camera",
                1.2f,
                .1f,
                4,
                "Maximum collision fraction change per second; safety clamping retains priority"),
            1.2);
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
            "Hide only the local model when collision-shortened orbit distance falls below this threshold; 0 disables");
        cameraHidePlayerHysteresis = config.getFloat(
            "cameraHidePlayerHysteresis",
            "camera",
            .2f,
            0,
            1,
            "Additional distance before showing the local model again; prevents flicker");
        cameraHidePlayerDistance = finite(cameraHidePlayerDistance, 1);
        cameraHidePlayerHysteresis = finite(cameraHidePlayerHysteresis, .2);
        cameraDistance = finite(cameraDistance, 4.3);
        cameraHeightOffset = finite(cameraHeightOffset, .6);
        baselinePitchDown = finite(baselinePitchDown, 15);
        lateralOffset = finite(lateralOffset, 0);
        targetScreenY = finite(targetScreenY, .45);
        playerScreenY = finite(playerScreenY, .68);
        cameraDistanceMax = Math.max(cameraDistanceMin, cameraDistanceMax);
        cameraPitchMax = Math.max(cameraPitchMin, cameraPitchMax);
        smallTargetPitchMax = Math.max(cameraPitchMax, smallTargetPitchMax);
        largeTargetHeight = Math.max(smallTargetHeight + .01, largeTargetHeight);
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
            12,
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
        effectVelocityLimit = config
            .getFloat("effectVelocityLimit", "control", 1.2f, 0, 2, "Max effect/lift velocity, blocks per tick");
        controlCapacity = config.getInt("capacity", "control", 3, 1, 20, "Shared control points per target chain");
        launchCost = config
            .getInt("launchCost", "control", 1, 1, controlCapacity, "Points spent on each accepted launch");
        launchProtectionTicks = config.getInt(
            "protectionTicks",
            "control",
            40,
            1,
            400,
            "Anti-launch ticks after exhaustion; damage is unaffected");
        controlResetTicks = config.getInt(
            "groundQuietTicks",
            "control",
            20,
            1,
            200,
            "Grounded and uncontrolled duration required to replenish");
        controlExpiryTicks = config.getInt(
            "chainExpiryTicks",
            "control",
            120,
            controlResetTicks,
            1200,
            "Inactive chain retention; never replenishes in air");
        chainDecay = config.getFloat(
            "chainDecay",
            "control",
            0.75f,
            0.2f,
            1,
            "Each further accepted launch in one chain lifts this share of the previous one");
        // A system property overrides the file so a live run can take posture out of a measurement.
        postureMax = Integer.getInteger(
            "cw.p2.postureMax",
            config.getInt("max", "posture", 100, 10, 1000, "Stance stability of a full-steady entity"));
        postureRecoveryDelay = config
            .getInt("recoveryDelayTicks", "posture", 40, 0, 400, "Quiet ticks before it recovers");
        postureRecoveryPerTick = config.getInt("recoveryPerTick", "posture", 2, 0, 50, "Stability regained per tick");
        postureResetPercent = config
            .getInt("resetPercent", "posture", 50, 1, 100, "Stability a finished break restores");
        breakTicksMonster = config.getInt("breakTicksMonster", "posture", 16, 1, 200, "Broken duration of a monster");
        breakTicksPlayer = config.getInt("breakTicksPlayer", "posture", 10, 1, 200, "Broken duration of a player");
        breakDamageBonus = config
            .getFloat("breakDamageBonus", "posture", 1.3f, 1, 3, "Damage multiplier on a broken target");
        postureBlockedShare = config.getFloat(
            "blockedShare",
            "posture",
            0.67f,
            0,
            2,
            "Share of a blow's posture damage a plain guard still takes");
        posturePrecise = config.getFloat("preciseDamage", "posture", 2, 0, 50, "Posture damage a precise guard takes");
        postureScale = config.getFloat(
            "perDamage",
            "posture",
            4,
            0,
            40,
            "Posture damage per point of damage when the action declares none");
        dodgeCooldown = config
            .getInt("dodgeCooldownTicks", "combat", 24, 0, 400, "Ticks before the next step is allowed");
        finisherMultiplier = config.getFloat(
            "multiplier",
            "finisher",
            3f,
            1,
            10,
            "Damage multiplier of the finisher on a target that is still broken when it lands");
        finisherPlayerCap = config.getFloat(
            "playerCap",
            "finisher",
            10f,
            1,
            200,
            "Most damage a finisher does to a player, before the break bonus");
        finisherRange = config
            .getFloat("range", "finisher", 3.2f, 1, 8, "How far a broken target may stand for heavy to mean finisher");
        gaugeFinisher = config.getFloat("gauge", "finisher", 20f, 0, 100, "Gauge for a finisher that lands");
        gaugeMax = config.getInt("max", "gauge", 100, 10, 1000, "Gauge needed to start the mode");
        gaugeHit = config.getFloat("hit", "gauge", 6, 0, 100, "Gauge for a blow that lands (once per swing)");
        gaugeStreak = config.getFloat("streakStep", "gauge", 1, 0, 20, "Extra gauge per blow in a streak");
        gaugeStreakCap = config.getInt("streakCap", "gauge", 4, 0, 20, "Largest streak bonus, in steps");
        gaugeStreakTicks = config.getInt("streakTicks", "gauge", 30, 1, 400, "Ticks between blows that keep a streak");
        gaugeBlock = config.getFloat("block", "gauge", 4, 0, 100, "Gauge for a plain guarded blow");
        gaugeParry = config.getFloat("parry", "gauge", 8, 0, 100, "Gauge for a precise guard");
        gaugeHurt = config.getFloat("hurt", "gauge", 5, 0, 100, "Gauge for damage taken");
        gaugeBlockInterval = config
            .getInt("blockIntervalTicks", "gauge", 8, 0, 400, "Minimum ticks between guard credits");
        gaugeHurtInterval = config
            .getInt("hurtIntervalTicks", "gauge", 20, 0, 400, "Minimum ticks between damage credits");
        modeTicks = config.getInt("modeTicks", "gauge", 300, 20, 6000, "Length of the mode");
        slamGraceTicks = config.getInt(
            "slamGraceTicks",
            "control",
            3,
            0,
            20,
            "A slam still counts a target that left the air this many ticks ago, so network delay cannot cost the finisher");
        blockRecoil = config.getFloat(
            "blockRecoil",
            "combat",
            0.25f,
            0,
            1.5f,
            "Blocks per tick an attacker is pushed back from a held guard");
        parryRecoil = config.getFloat(
            "parryRecoil",
            "combat",
            0.7f,
            0,
            1.5f,
            "Blocks per tick an attacker is pushed back from a precise guard");
        slamRequiresAir = config
            .getBoolean("slamRequiresAir", "control", true, "A slam only drives a target that is off the ground");
        launchCreditTicks = config.getInt(
            "velocityCreditTicks",
            "control",
            60,
            1,
            400,
            "Finite launch movement credit lifetime across action changes");
        launchRequiresHit = config.getBoolean(
            "requiresConfirmedHit",
            "control",
            true,
            "Provisional launch route requires prior unblocked damage hit");
        launchRequiresGround = config
            .getBoolean("requiresGround", "control", true, "Provisional launch route is grounded only");
        launchExcludeSwimming = config
            .getBoolean("excludeSwimming", "control", true, "No launch for targets currently in water");
        launchExemptEntities = config
            .get(
                "control",
                "exemptEntities",
                new String[] { "EnderDragon", "WitherBoss", "Ghast", "Blaze", "Bat", "Squid" },
                "Bosses and native flying/water mobs; chickens remain launchable")
            .getStringList();
        legacyKatanaJson = config.getBoolean(
            "legacyKatanaJson",
            "content",
            false,
            "Emergency fallback for one release: run the katana style from the legacy combat/katana.json resource "
                + "instead of its content pack. Not the source of truth; it changes the negotiated rules hash");
        weaponItems = config
            .get(
                "weapons",
                "items",
                new String[] { "clashweave:katana=katana:katana" },
                "Server-authoritative table: <item registry name>=<weapon ID>. Clients are sent this table on join")
            .getStringList();
        helloTimeoutTicks = config.getInt(
            "helloTimeoutTicks",
            "network",
            400,
            40,
            6000,
            "Ticks a connected player may take to present matching content hashes before being kicked");
        config.save();
    }

    /** Server settings that change gameplay data; they are folded into the negotiated rules hash. */
    public EffectiveRules.Overrides overrides() {
        return new EffectiveRules.Overrides(
            launchRequiresHit,
            launchRequiresGround,
            effectVelocityLimit,
            legacyKatanaJson);
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
