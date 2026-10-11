package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class P1EffectsTest {

    private ActionCatalog catalog(String fields, String edge) {
        return new ActionCatalog(
            new StringReader(
                "{\"bufferTicks\":4,\"actions\":[{\"id\":\"one\",\"startup\":1,\"active\":1,\"recovery\":10," + fields
                    + "\"edges\":["
                    + edge
                    + "]}]}"),
            .8);
    }

    private void invalid(String fields, String edge) {
        try {
            catalog(fields, edge);
            fail("invalid schema accepted");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void legacyJsonKeepsDefaults() {
        ActionCatalog old = new ActionCatalog(new InputStreamReader(getClass().getResourceAsStream("/p0-katana.json")));
        assertTrue(old.get("light_1").effects.isEmpty());
        assertEquals(0, old.get("light_1").lift, 0);
        assertNull(old.get("light_1").edges.get(0).requires);
    }

    @Test
    public void unknownNegativeAndExcessVelocityReject() {
        invalid("\"effects\":[{\"type\":\"BOGUS\",\"vy\":.6}],", "");
        invalid("\"effects\":[{\"type\":\"LAUNCH\",\"vy\":-.1}],", "");
        invalid("\"effects\":[{\"type\":\"LAUNCH\",\"vy\":.81}],", "");
        invalid("\"lift\":-.1,", "");
        invalid("\"lift\":.81,", "");
        invalid("\"sweepElevation\":91,", "");
    }

    @Test
    public void requirementsAndAmbiguousEdgesValidate() {
        String edge = "{\"input\":\"LIGHT\",\"target\":\"one\",\"from\":1,\"until\":3,\"requires\":\"AIR\"}";
        assertEquals("AIR", catalog("", edge).get("one").edges.get(0).requires);
        assertEquals("GROUND", catalog("", edge.replace("AIR", "GROUND")).get("one").edges.get(0).requires);
        invalid("", edge.replace("AIR", "UNKNOWN"));
        invalid("", edge + "," + edge.replace("AIR", "GROUND"));
    }

    @Test
    public void configuredMaximumAndTiltAreParsed() {
        ActionCatalog.Definition a = catalog(
            "\"lift\":.4,\"sweepElevation\":20,\"effects\":[{\"type\":\"LAUNCH\",\"vy\":.8}],",
            "").get("one");
        assertEquals(.4, a.lift, 0);
        assertEquals(20, a.sweepElevation, 0);
        assertEquals(.8, a.effects.get(0).vy, 0);
    }

    @Test
    public void blockedImmuneDeadAndExemptContactsHaveNoEffects() {
        assertTrue(HitEffect.allowed(true, 0, true, false));
        assertFalse(HitEffect.allowed(true, 1, true, false));
        assertFalse(HitEffect.allowed(false, 2, true, false));
        assertFalse(HitEffect.allowed(false, 0, true, false));
        assertFalse(HitEffect.allowed(true, 0, false, false));
        assertFalse(HitEffect.allowed(true, 0, true, true));
    }

    @Test
    public void launchSemanticFollowsHitAndHurtWithoutChangingOldOrdinals() {
        SemanticEvents bus = new SemanticEvents();
        List<SemanticEvents.Kind> order = new ArrayList<>();
        bus.subscribe(e -> order.add(e.kind));
        bus.contact(1, 2, 1, 6, 100, "launcher", true, 0);
        bus.emit(new SemanticEvents.Event(SemanticEvents.Kind.LAUNCH, 2, 1, 1, 6, 100, "launcher"));
        assertEquals(
            Arrays.asList(SemanticEvents.Kind.HIT, SemanticEvents.Kind.HURT, SemanticEvents.Kind.LAUNCH),
            order);
        assertEquals(6, SemanticEvents.Kind.HURT.ordinal());
        assertEquals(7, SemanticEvents.Kind.LAUNCH.ordinal());
    }

    @Test
    public void launchCreditIsFiniteAndCannotLoosenHorizontalBudget() {
        MovementBudget budget = new MovementBudget(0, 0, 0, 0, 1.25, .03);
        budget.creditVertical(.6 * 4);
        assertTrue(budget.accept(50_000_000L, 0, 2, 0, 0, 0));
        assertFalse(budget.accept(100_000_000L, 0, 2, 0, 0, 0));
        assertFalse(budget.accept(100_000_000L, 1, 0, 0, 0, 0));
        try {
            budget.creditVertical(Double.NaN);
            fail();
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void effectSubmissionPreservesDataOrderAndDoesNotRunOnRejectedDamage() {
        HitEffect first = new HitEffect(), second = new HitEffect();
        first.type = second.type = HitEffect.Type.LAUNCH;
        first.vy = .2;
        second.vy = .6;
        List<Double> order = new ArrayList<>();
        HitEffect.submit(Arrays.asList(first, second), true, 0, true, false, e -> order.add(e.vy));
        assertEquals(Arrays.asList(.2, .6), order);
        for (int guard : new int[] { 1, 2 })
            HitEffect.submit(Arrays.asList(first), true, guard, true, false, e -> fail("blocked effect"));
        HitEffect.submit(Arrays.asList(first), false, 0, true, false, e -> fail("rejected damage effect"));
    }

    @Test
    public void delayedLandingBeforeLaunchDeliveryCannotDiscardAuthorizedCredit() {
        double remaining = MovementBudget.remainingVertical(2.4, 64.57, 64.16, 1031, 1090);
        remaining = MovementBudget.remainingVertical(remaining, 64.16, 64, 1032, 1090);
        assertEquals(2.4, remaining, .000001);
        remaining = MovementBudget.remainingVertical(remaining, 64, 64.6, 1033, 1090);
        remaining = MovementBudget.remainingVertical(remaining, 64.6, 65.2, 1034, 1090);
        MovementBudget nextAction = new MovementBudget(0, 0, 0, 0);
        nextAction.creditVertical(remaining);
        assertTrue(nextAction.accept(50_000_000, 0, 1.76363, 0, 0, 0));
        assertFalse(nextAction.accept(100_000_000, 0, 1, 0, 0, 0));
    }

    @Test
    public void authorizedVerticalCreditExpiresAndDescendingCannotReplenishIt() {
        assertEquals(.04, MovementBudget.remainingVertical(.04, 66, 64, 59, 60), .000001);
        assertEquals(0, MovementBudget.remainingVertical(.04, 64, 64, 60, 60), 0);
        assertEquals(0, MovementBudget.remainingVertical(0, 66, 64, 61, 60), 0);
    }

    @Test
    public void newActionsInheritOnlyUnspentImpulseAndDoNotExtendExpiry() {
        double remaining = MovementBudget.remainingVertical(2.4, 64, 66, 10, 60);
        assertEquals(.4, remaining, .000001);
        MovementBudget next = new MovementBudget(0, 0, 0, 0);
        next.creditVertical(remaining);
        assertFalse(next.accept(50_000_000, 0, 2, 0, 0, 0));
        assertEquals(0, MovementBudget.remainingVertical(remaining, 66, 66, 60, 60), 0);
    }

    private Scheduler lightTwo(boolean confirm) {
        ActionCatalog actions = new ActionCatalog(
            new InputStreamReader(getClass().getResourceAsStream("/assets/clashweave/combat/katana.json")));
        Scheduler s = new Scheduler(actions);
        s.request(Intent.LIGHT, 1, 0);
        s.tick(0);
        s.tick(17);
        s.request(Intent.LIGHT, 2, 18);
        s.tick(18);
        s.current().confirmedHit = true;
        s.request(Intent.LIGHT, 3, 25);
        s.tick(25);
        s.current().confirmedHit = true;
        s.current().confirmedDamageHit = confirm;
        s.drainResults();
        return s;
    }

    @Test
    public void launcherRequiresRealPriorHitAndGroundButLightEdgeIsUnchanged() {
        Scheduler s = lightTwo(true);
        s.request(Intent.HEAVY, 4, 31);
        s.tick(31, true);
        assertEquals("launcher", s.current().definition.id);
        s = lightTwo(true);
        s.request(Intent.HEAVY, 4, 31);
        s.tick(31, false);
        assertEquals("light_2", s.current().definition.id);
        assertEquals(
            "CONDITION_CHANGED",
            s.drainResults()
                .get(0).status);
        s = lightTwo(false);
        s.request(Intent.HEAVY, 4, 31);
        s.tick(31, true);
        assertEquals("light_2", s.current().definition.id);
        s.tick(35);
        assertEquals("light_2", s.current().definition.id);
        s.tick(37);
        assertNull(s.current());
        s = lightTwo(false);
        s.request(Intent.LIGHT, 4, 31);
        s.tick(31);
        assertEquals("light_3", s.current().definition.id);
    }
}
