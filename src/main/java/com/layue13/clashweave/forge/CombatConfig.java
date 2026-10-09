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

    public int maxResistance;
    public int defer;
    public int perfectWindow;
    public int guardCooldown;
    public int stampAge;
    public int disengage;
    public double radius;
    public double guardArc;
    public double blockDamage;
    public double movementMargin;
    public boolean friendly;
    public boolean blocks;
    public ActionCatalog actions;

    public void load(File directory) throws Exception {
        directory.mkdirs();
        Configuration config = new Configuration(new File(directory, "combat.cfg"));
        config.load();
        maxResistance = config.getInt("maxHurtResistantTime", "combat", 8, 1, 40, "Persistent engaged-target timer");
        defer = config.getInt("confirmationDelay", "combat", 3, 1, 8, "Tick and wall-clock minimum");
        perfectWindow = config.getInt("perfectWindow", "combat", 5, 1, 8, "Half-open press window");
        guardCooldown = config.getInt("guardCooldown", "combat", 6, 1, 40, "Repeat press restriction");
        stampAge = config.getInt("stampAgeLimit", "network", 8, 1, 20, "Maximum accepted input age");
        disengage = config.getInt("disengageTicks", "combat", 60, 1, 400, "Exit hysteresis");
        radius = config.getFloat("engageRadius", "combat", 8, 1, 32, "Hostile target radius");
        guardArc = config.getFloat("guardArc", "combat", 140, 1, 180, "Degrees");
        blockDamage = config.getFloat("ordinaryGuardDamage", "combat", 0.25f, 0, 1, "Damage multiplier");
        movementMargin = config.getFloat("movementMarginTicks", "movement", 2, 0, 4, "Batch allowance");
        friendly = config.getBoolean("protectFriendly", "combat", true, "Villagers, tamed pets, team players");
        blocks = config.getBoolean("peacefulBlockInteraction", "input", true, "Block right click outside engagement");
        config.save();
        File data = new File(directory, "katana.json");
        if (!data.exists()) {
            try (InputStream input = getClass().getResourceAsStream("/assets/clashweave/combat/katana.json");
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
