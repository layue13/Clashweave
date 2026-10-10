package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.layue13.clashweave.forge.CombatConfig;

public class SoulsCompositionTest {

    private java.lang.reflect.Field home;
    private Object oldHome;

    @org.junit.Before
    public void forgeDirectory() throws Exception {
        home = cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        oldHome = home.get(null);
        home.set(null, temporary.getRoot());
    }

    @org.junit.After
    public void restoreDirectory() throws Exception {
        home.set(null, oldHome);
    }

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void configDefaultsAreEmpiricalStarts() throws Exception {
        CombatConfig c = new CombatConfig();
        c.load(temporary.newFolder());
        assertEquals(ThirdPersonCamera.Preset.SOULS, c.lockCompositionPreset);
        assertEquals(4.2, c.cameraDistance, .00001);
        assertEquals(.6, c.cameraHeightOffset, .00001);
        assertEquals(18, c.baselinePitchDown, 0);
        assertEquals(42, c.cameraPitchAdjustment, 0);
        assertEquals(2, c.cameraDistanceAdjustment, 0);
        assertEquals(0, c.lateralOffset, 0);
        assertEquals(.38, c.targetScreenY, .00001);
        assertEquals(.65, c.playerScreenY, .00001);
    }

    @Test
    public void configReadsAllCameraValuesAndOnlyOffSouls() throws Exception {
        File d = temporary.newFolder();
        Configuration f = new Configuration(new File(d, "combat.cfg"));
        f.get("camera", "lockCompositionPreset", "OFF")
            .set("OFF");
        String[] keys = { "cameraDistance", "cameraHeightOffset", "baselinePitchDown", "lateralOffset", "targetScreenY",
            "playerScreenY" };
        double[] values = { 5.8, 1.0, 18, 0, .4, .7 };
        for (int i = 0; i < keys.length; i++) f.get("camera", keys[i], values[i])
            .set(values[i]);
        f.save();
        CombatConfig c = new CombatConfig();
        c.load(d);
        assertEquals(ThirdPersonCamera.Preset.OFF, c.lockCompositionPreset);
        assertEquals(5.8, c.cameraDistance, .00001);
        assertEquals(1, c.cameraHeightOffset, 0);
        assertEquals(18, c.baselinePitchDown, 0);
        assertEquals(.4, c.targetScreenY, .00001);
        assertEquals(.7, c.playerScreenY, .00001);
    }

    @Test
    public void oldPresetMigratesInsteadOfThrowingOnStartup() throws Exception {
        File d = temporary.newFolder();
        Configuration f = new Configuration(new File(d, "combat.cfg"));
        f.get("camera", "lockCompositionPreset", "GOLDEN")
            .set("GOLDEN");
        f.save();
        CombatConfig c = new CombatConfig();
        c.load(d);
        assertEquals(ThirdPersonCamera.Preset.SOULS, c.lockCompositionPreset);
    }

    @Test
    public void collisionSmoothingCannotCrossNewSafetyLimit() {
        ThirdPersonCamera.Spring s = new ThirdPersonCamera.Spring();
        double fraction = 1;
        for (int i = 0; i < 100; i++) {
            fraction = SoulsComposition.safeFraction(fraction, .3, .016, .15, s);
            assertTrue(fraction <= .3);
        }
        double previous = fraction;
        fraction = SoulsComposition.safeFraction(fraction, 1, .016, .15, s);
        assertTrue(fraction >= previous && fraction < 1);
    }

    @Test
    public void configNonfiniteValuesFallBackAndDistanceIsBounded() throws Exception {
        File d = temporary.newFolder();
        Configuration f = new Configuration(new File(d, "combat.cfg"));
        f.get("camera", "cameraDistance", 100.0)
            .set(100.0);
        f.get("camera", "targetScreenY", "NaN")
            .set("NaN");
        f.save();
        CombatConfig c = new CombatConfig();
        c.load(d);
        assertEquals(10, c.cameraDistance, 0);
        assertEquals(.38, c.targetScreenY, .00001);
    }

    @Test
    public void closeCameraModelHidingIsLocalScopedAndHasHysteresis() {
        assertTrue(SoulsComposition.hideModel(true, false, .9, 1, .2));
        assertTrue(SoulsComposition.hideModel(true, true, 1.1, 1, .2));
        assertFalse(SoulsComposition.hideModel(true, true, 1.21, 1, .2));
        assertFalse(SoulsComposition.hideModel(false, true, .1, 1, .2));
        assertFalse(SoulsComposition.hideModel(true, true, .1, 0, .2));
        assertFalse(SoulsComposition.hideModel(true, true, Double.NaN, 1, .2));
    }
}
