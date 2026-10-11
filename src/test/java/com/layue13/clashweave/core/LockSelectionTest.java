package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class LockSelectionTest {

    @org.junit.Rule
    public org.junit.rules.TemporaryFolder temporary = new org.junit.rules.TemporaryFolder();
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

    @Test
    public void balancesAngleAndDistanceAndStableTies() {
        assertTrue(LockSelection.score(4, 0, 16, 30) < LockSelection.score(8, 0, 16, 30));
        assertTrue(LockSelection.score(8, 0, 16, 30) < LockSelection.score(8, 10, 16, 30));
        assertEquals(LockSelection.score(8, 0, 16, 30), LockSelection.score(4, 7.5, 16, 30), 1e-12);
        assertTrue(LockSelection.better(.5, 1, .5, 2));
        assertFalse(LockSelection.better(.5, 2, .5, 1));
        assertFalse(LockSelection.better(Double.POSITIVE_INFINITY, 1, Double.POSITIVE_INFINITY, 2));
    }

    @Test
    public void coneAndRangeIncludeBoundaryOnly() {
        assertTrue(Double.isFinite(LockSelection.score(16, 30, 16, 30)));
        assertFalse(Double.isFinite(LockSelection.score(16, 30.0001, 16, 30)));
        assertFalse(Double.isFinite(LockSelection.score(16.0001, 0, 16, 30)));
        assertFalse(Double.isFinite(LockSelection.score(Double.NaN, 0, 16, 30)));
    }

    @Test
    public void acquireKeepHysteresis() {
        assertFalse(Double.isFinite(LockSelection.score(20, 0, 16, 30)));
        assertTrue(LockSelection.keep(20, 20, true));
        assertFalse(LockSelection.keep(22, 20, true));
        assertFalse(LockSelection.keep(4, 20, false));
    }

    @Test
    public void lowTargetUsesSeparateAnglesAndCenterHeight() {
        double[] angles = LockSelection.angles(0, -1.27, 1.5, 0, 0);
        assertEquals(0, angles[0], 0);
        assertEquals(40.253477, angles[1], 1e-5);
        assertFalse(Double.isFinite(LockSelection.score(1.5, 34.3461, 16, 30)));
        assertTrue(Double.isFinite(LockSelection.acquisitionScore(1.5, angles[0], angles[1], -1.27, 16, 30, 60)));
        assertEquals(0, LockSelection.angles(0, -1, 1, 0, 45)[1], 1e-9);
    }

    @Test
    public void splitConeIncludesBoundariesButNotOutsideOrNonfinite() {
        assertTrue(Double.isFinite(LockSelection.acquisitionScore(16, 30, 60, 8, 16, 30, 60)));
        assertFalse(Double.isFinite(LockSelection.acquisitionScore(4, 30.0001, 0, 0, 16, 30, 60)));
        assertFalse(Double.isFinite(LockSelection.acquisitionScore(4, 0, 60.0001, 0, 16, 30, 60)));
        assertFalse(Double.isFinite(LockSelection.acquisitionScore(16.0001, 0, 0, 0, 16, 30, 60)));
        assertFalse(Double.isFinite(LockSelection.acquisitionScore(4, 0, Double.NaN, 0, 16, 30, 60)));
        assertFalse(Double.isFinite(LockSelection.acquisitionScore(4, 0, 0, Double.POSITIVE_INFINITY, 16, 30, 60)));
        assertFalse(Double.isFinite(LockSelection.acquisitionScore(4, 0, 0, 0, 16, 30, Double.NaN)));
    }

    @Test
    public void heightSafetyCapAppliesEvenWhenLookingStraightUpOrDown() {
        assertTrue(Double.isFinite(LockSelection.acquisitionScore(8, 0, 0, 8, 16, 30, 60)));
        assertFalse(Double.isFinite(LockSelection.acquisitionScore(9, 0, 0, 8.000001, 16, 30, 60)));
        assertFalse(Double.isFinite(LockSelection.acquisitionScore(9, 0, 0, -8.000001, 16, 30, 60)));
        assertEquals(0, LockSelection.angles(0, 9, 0, 45, -90)[1], 0);
    }

    @Test
    public void verticalDoesNotOutweighHorizontalAndYawWraps() {
        double centeredLarge = LockSelection.acquisitionScore(8, 0, 20, -.72, 16, 30, 60);
        double nearbyLow = LockSelection.acquisitionScore(8, 2, 0, 0, 16, 30, 60);
        assertTrue(centeredLarge < nearbyLow);
        assertEquals(
            2,
            LockSelection.angles(Math.sin(Math.toRadians(179)), 0, Math.cos(Math.toRadians(179)), 179, 0)[0],
            1e-5);
        assertTrue(LockSelection.better(centeredLarge, 1, centeredLarge, 2));
    }

    @Test
    public void verticalConfigurationDefaultAndOverride() throws Exception {
        java.io.File directory = temporary.newFolder();
        com.layue13.clashweave.forge.CombatConfig config = new com.layue13.clashweave.forge.CombatConfig();
        config.load(directory);
        assertEquals(60, config.lockConeVerticalHalfAngle, 0);
        assertEquals(30, config.lockConeHalfAngle, 0);
        assertEquals(16, config.lockAcquireRange, 0);
        assertEquals(20, config.lockKeepRange, 0);
        net.minecraftforge.common.config.Configuration c = new net.minecraftforge.common.config.Configuration(
            new java.io.File(directory, "combat.cfg"));
        c.load();
        c.get("lock", "lockConeVerticalHalfAngle", 60)
            .set(55);
        c.save();
        config.load(directory);
        assertEquals(55, config.lockConeVerticalHalfAngle, 0);
    }
}
