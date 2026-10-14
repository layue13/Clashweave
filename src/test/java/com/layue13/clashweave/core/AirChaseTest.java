package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import java.io.StringReader;

import org.junit.Test;

/** P1-b rules that do not need a world: the budget, the decay, the schema and the shipped chain. */
public class AirChaseTest {

    @Test
    public void aSlamEndsTheChainAndStartsTheProtection() {
        ControlBudget budget = new ControlBudget(3, 40, 20, 120);
        assertTrue(budget.consume(10, 1, false));
        assertTrue(budget.exhaust(15, false));
        assertEquals(0, budget.remaining());
        assertEquals(55, budget.protectedUntil());
        assertFalse("nothing launches the target again during the protection", budget.consume(30, 1, false));
    }

    @Test
    public void aSlamStillBringsADrainedTargetDownWithoutMovingTheProtection() {
        ControlBudget budget = new ControlBudget(2, 40, 20, 120);
        assertTrue(budget.consume(1, 1, false));
        assertTrue(budget.consume(2, 1, false));
        long until = budget.protectedUntil();
        assertEquals(42, until);
        assertTrue(budget.exhaust(3, false));
        assertEquals(until, budget.protectedUntil());
        assertEquals(0, budget.remaining());
        assertFalse("the protection still stops any further lift", budget.consume(4, 1, false));
    }

    @Test
    public void protectionEndsAndAGroundedQuietTargetIsReplenished() {
        ControlBudget budget = new ControlBudget(3, 40, 20, 120);
        assertTrue(budget.exhaust(0, false));
        budget.advance(41, true);
        budget.advance(62, true);
        assertEquals(3, budget.remaining());
    }

    @Test
    public void eachLaunchInAChainLiftsLessAndTheFirstIsFull() {
        assertEquals(.45, HitEffect.decayed(.45, 0, .75), 1e-9);
        assertEquals(.45 * .75, HitEffect.decayed(.45, 1, .75), 1e-9);
        assertEquals(.45 * .75 * .75, HitEffect.decayed(.45, 2, .75), 1e-9);
        assertEquals(.45, HitEffect.decayed(.45, 5, 1), 1e-9);
    }

    private static ActionCatalog catalog(String effects) {
        return new ActionCatalog(
            new StringReader(
                "{\"bufferTicks\":4,\"actions\":[{\"id\":\"one\",\"startup\":1,\"active\":1,\"recovery\":10,"
                    + "\"effects\":["
                    + effects
                    + "],\"edges\":[]}]}"),
            1.2);
    }

    @Test
    public void slamIsADeclaredEffectButNotTogetherWithALaunch() {
        assertEquals(HitEffect.Type.SLAM, catalog("{\"type\":\"SLAM\",\"vy\":1.0}").get("one").effects.get(0).type);
        for (String bad : new String[] { "{\"type\":\"SLAM\",\"vy\":1},{\"type\":\"SLAM\",\"vy\":1}",
            "{\"type\":\"LAUNCH\",\"vy\":.4},{\"type\":\"SLAM\",\"vy\":1}", "{\"type\":\"SLAM\",\"vy\":9}" }) {
            try {
                catalog(bad);
                fail("accepted: " + bad);
            } catch (IllegalArgumentException expected) {
                assertNotNull(expected.getMessage());
            }
        }
    }

    @Test
    public void theShippedKatanaChainsFromTheLauncherToTheFinisherAndNowhereElse() throws Exception {
        ActionCatalog katana = new ActionCatalog(
            new java.io.InputStreamReader(
                getClass().getResourceAsStream("/assets/clashweave/combat/katana.json"),
                "UTF-8"));
        ActionCatalog.Definition launcher = katana.get("launcher");
        boolean light = false, heavy = false;
        for (ActionCatalog.Edge edge : launcher.edges) {
            if (edge.target.endsWith("air_light") && edge.input == Intent.LIGHT) light = true;
            if (edge.target.endsWith("air_slam") && edge.input == Intent.HEAVY) heavy = true;
            assertTrue("a follow-up needs a real prior damage hit", edge.hit && edge.damageHit);
            assertTrue(edge.until <= launcher.duration());
        }
        assertTrue(light && heavy);
        ActionCatalog.Definition slam = katana.get("air_slam");
        assertTrue("the finisher chains into nothing", slam.edges.isEmpty());
        assertEquals(HitEffect.Type.SLAM, slam.effects.get(0).type);
        assertEquals(HitEffect.Type.LAUNCH, katana.get("air_light").effects.get(0).type);
    }
}
