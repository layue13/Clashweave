package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import java.io.StringReader;

import org.junit.Test;

/** The four review findings are reproduced before changing their implementation. */
public class P1AuditTest {

    private ActionCatalog catalog(String effects, String requirement) {
        return new ActionCatalog(
            new StringReader(
                "{\"bufferTicks\":4,\"actions\":[{\"id\":\"one\","
                    + "\"startup\":1,\"active\":1,\"recovery\":10,\"effects\":["
                    + effects
                    + "],\"edges\":[{\"input\":\"HEAVY\",\"target\":\"one\",\"from\":1,\"until\":3"
                    + requirement
                    + "}]}]}"));
    }

    @Test
    public void repeatedMoveReadyCannotRecreateTheSameInstanceBudget() {
        ActionCatalog.Definition definition = catalog("", "").get("one");
        Scheduler.Instance instance = new Scheduler.Instance(1, 0, 1, definition);
        assertTrue(instance.claimMovementReady());
        MovementBudget budget = new MovementBudget(0, 0, 0, 0);
        budget.creditVertical(2.4, 60);
        assertTrue(budget.accept(50_000_000L, 0, 2, 0, 0, 0));
        assertFalse(instance.claimMovementReady());
        assertFalse(budget.accept(100_000_000L, 0, 2, 0, 0, 0));
        instance.clearLedger();
        assertFalse(instance.claimMovementReady());
        assertTrue(new Scheduler.Instance(2, 0, 2, definition).claimMovementReady());
    }

    @Test
    public void expirationMustRevokeCreditAlreadyCopiedIntoBudget() {
        MovementBudget budget = new MovementBudget(0, 0, 0, 0);
        budget.creditVertical(2.4, 60);
        assertEquals(0, MovementBudget.remainingVertical(2.4, 64, 64, 60, 60), 0);
        assertFalse(budget.accept(3_000_000_000L, 0, 2, 0, 0, 0));
    }

    @Test
    public void repeatedLaunchIsRejectedInsteadOfAddingTwoCreditsForMaxVelocity() {
        try {
            catalog("{\"type\":\"LAUNCH\",\"vy\":.6},{\"type\":\"LAUNCH\",\"vy\":.6}", "");
            fail("duplicate LAUNCH accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("effects"));
            assertTrue(
                expected.getMessage()
                    .contains("one"));
        }
    }

    @Test
    public void enabledGroundPolicyMustAddTheMissingRequirement() {
        ActionCatalog data = catalog("{\"type\":\"LAUNCH\",\"vy\":.6}", "");
        data.launchPolicy(true, true);
        assertEquals("GROUND", data.get("one").edges.get(0).requires);
        data.launchPolicy(true, false);
        assertNull(data.get("one").edges.get(0).requires);
        data.launchPolicy(true, true);
        assertEquals("GROUND", data.get("one").edges.get(0).requires);
    }

    @Test
    public void expiredUnspentCreditDoesNotInvalidatePreviouslyAuthorizedRise() {
        MovementBudget budget = new MovementBudget(0, 0, 0, 0);
        budget.creditVertical(2.4, 60);
        assertTrue(budget.accept(50_000_000L, 1, 0, 2, 0, 0, 0));
        assertTrue(budget.accept(3_000_000_000L, 60, 0, 0, 0, 0, 0));
        assertFalse(budget.accept(3_050_000_000L, 61, 0, .01, 0, 0, 0));
    }

    @Test
    public void eachGrantKeepsItsOwnDeadlineAndChecksLogicalTicksDuringLowTps() {
        MovementBudget budget = new MovementBudget(0, 0, 0, 0);
        budget.creditVertical(1, 10);
        budget.creditVertical(1, 20);
        assertTrue(budget.accept(9_000_000_000L, 9, 0, 0, 0, 0, 0));
        assertFalse(budget.accept(10_000_000_000L, 10, 0, 2.3, 0, 0, 0));
        assertTrue(budget.accept(11_000_000_000L, 11, 0, 2, 0, 0, 0));
        assertTrue(budget.accept(20_000_000_000L, 20, 0, 0, 0, 0, 0));
        assertFalse(budget.accept(21_000_000_000L, 21, 0, .01, 0, 0, 0));
    }

    @Test
    public void policyOverridesAirOnlyForLaunchEntriesAndLeavesOtherEdgesAlone() {
        ActionCatalog data = catalog("{\"type\":\"LAUNCH\",\"vy\":.6}", ",\"requires\":\"AIR\"");
        data.launchPolicy(true, false);
        assertEquals("AIR", data.get("one").edges.get(0).requires);
        data.launchPolicy(true, true);
        assertEquals("GROUND", data.get("one").edges.get(0).requires);
        data = catalog("", ",\"requires\":\"AIR\"");
        data.launchPolicy(true, true);
        assertEquals("AIR", data.get("one").edges.get(0).requires);
    }
}
