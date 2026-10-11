package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class SchedulerTest {

    private Scheduler scheduler() {
        return new Scheduler(
            new ActionCatalog(
                new InputStreamReader(
                    getClass().getResourceAsStream("/assets/clashweave/combat/katana.json"),
                    StandardCharsets.UTF_8)));
    }

    private Scheduler drawn() {
        Scheduler scheduler = scheduler();
        scheduler.request(Intent.LIGHT, 1, 0);
        scheduler.tick(0);
        scheduler.tick(17);
        scheduler.drainResults();
        return scheduler;
    }

    private Scheduler lightTwo() {
        Scheduler scheduler = drawn();
        scheduler.request(Intent.LIGHT, 2, 894, 0);
        scheduler.tick(894);
        scheduler.current().confirmedHit = true;
        scheduler.request(Intent.LIGHT, 3, 902, scheduler.current().id);
        scheduler.tick(902);
        assertEquals("light_2", scheduler.current().definition.id);
        assertEquals(902, scheduler.current().start);
        scheduler.current().confirmedHit = true;
        scheduler.drainResults();
        return scheduler;
    }

    private String derivative(long tick, long age, int limit) {
        Scheduler scheduler = lightTwo();
        // Keep the origin alive to isolate the until boundary from action completion.
        scheduler.current().definition.recovery = 20;
        scheduler.request(Intent.LIGHT, 4, tick, scheduler.current().id, age, limit);
        scheduler.tick(tick);
        return scheduler.drainResults()
            .get(0).status;
    }

    @Test
    public void boundedReceiptAgeRecoversLateDerivativeWithoutBackdatingStart() {
        InputGate gate = new InputGate(1);
        assertEquals("OK", gate.validate(1, 4, 906, 912, 8, 2));
        long age = 912 - InputGate.effectiveStamp(906, 912);
        assertEquals("ILLEGAL_NODE", derivative(913, age, 0));
        Scheduler scheduler = lightTwo();
        scheduler.request(Intent.LIGHT, 4, 913, scheduler.current().id, age, 4);
        scheduler.tick(913);
        assertEquals(
            "START",
            scheduler.drainResults()
                .get(0).status);
        assertEquals("light_3", scheduler.current().definition.id);
        assertEquals(913, scheduler.current().start);
    }

    @Test
    public void compensationCapAndHalfOpenUntilAreDeterministic() {
        for (int run = 0; run < 20; run++) {
            assertEquals("START", derivative(915, 8, 4));
            assertEquals("ILLEGAL_NODE", derivative(916, 8, 4));
            assertEquals("ILLEGAL_NODE", derivative(913, 8, 0));
            assertEquals("ILLEGAL_NODE", derivative(912, 0, 4));
            assertEquals("ILLEGAL_NODE", derivative(912, -2, 4));
        }
    }

    @Test
    public void disabledCompensationMatchesLegacyRequestsAtEveryAge() {
        for (int age = 0; age <= 14; age++) {
            Scheduler legacy = lightTwo();
            Scheduler disabled = lightTwo();
            long tick = 902 + age;
            legacy.request(Intent.LIGHT, 4, tick, legacy.current().id);
            disabled.request(Intent.LIGHT, 4, tick, disabled.current().id, 8, 0);
            for (long consume = tick; consume <= tick + 4; consume++) {
                legacy.tick(consume);
                disabled.tick(consume);
                List<Scheduler.Result> expected = legacy.drainResults();
                List<Scheduler.Result> actual = disabled.drainResults();
                assertEquals(expected.size(), actual.size());
                for (int index = 0; index < expected.size(); index++) {
                    assertEquals(expected.get(index).sequence, actual.get(index).sequence);
                    assertEquals(expected.get(index).status, actual.get(index).status);
                    assertEquals(expected.get(index).action, actual.get(index).action);
                }
                if (legacy.current() == null) assertNull(disabled.current());
                else {
                    assertEquals(legacy.current().id, disabled.current().id);
                    assertEquals(legacy.current().start, disabled.current().start);
                    assertEquals(legacy.current().definition.id, disabled.current().definition.id);
                }
            }
        }
    }

    @Test
    public void toleratedFutureStampGivesNoEdgeCompensation() {
        InputGate gate = new InputGate(1);
        assertEquals("OK", gate.validate(1, 4, 914, 912, 8, 2));
        assertEquals("ILLEGAL_NODE", derivative(912, 912 - InputGate.effectiveStamp(914, 912), 4));
    }

    @Test
    public void compensationDoesNotMoveFromOrExtendBufferExpiry() {
        Scheduler scheduler = lightTwo();
        Scheduler.Instance origin = scheduler.current();
        scheduler.request(Intent.LIGHT, 4, 907, origin.id, 4, 4);
        scheduler.tick(907);
        assertSame(origin, scheduler.current());
        scheduler.tick(908);
        assertEquals("light_3", scheduler.current().definition.id);
        assertEquals(908, scheduler.current().start);
        scheduler = lightTwo();
        origin = scheduler.current();
        scheduler.request(Intent.LIGHT, 4, 903, origin.id, 4, 4);
        scheduler.tick(907);
        assertSame(origin, scheduler.current());
        assertEquals(
            "EXPIRED_OR_CHANGED",
            scheduler.drainResults()
                .get(0).status);
    }

    @Test
    public void compensationKeepsConfirmedHitAndDoesNotAccumulateWhileBuffered() {
        Scheduler scheduler = lightTwo();
        Scheduler.Instance origin = scheduler.current();
        origin.definition.recovery = 20;
        origin.confirmedHit = false;
        scheduler.request(Intent.LIGHT, 4, 913, origin.id, 6, 4);
        scheduler.tick(913);
        scheduler.tick(914);
        scheduler.tick(915);
        assertSame(origin, scheduler.current());
        assertTrue(
            scheduler.drainResults()
                .isEmpty());
        origin.confirmedHit = true;
        scheduler.tick(916);
        assertSame(origin, scheduler.current());
        assertEquals(
            "WINDOW_CLOSED",
            scheduler.drainResults()
                .get(0).status);
    }

    @Test
    public void compensationLeavesIdleCounterAndOriginChecksUnchanged() {
        Scheduler scheduler = drawn();
        scheduler.openCounter(900, 2);
        scheduler.request(Intent.LIGHT, 4, 901, 0, 8, 4);
        scheduler.tick(902);
        assertNull(scheduler.current());
        assertEquals(
            "EXPIRED_OR_CHANGED",
            scheduler.drainResults()
                .get(0).status);
        scheduler.request(Intent.LIGHT, 5, 903, 0, 8, 4);
        scheduler.tick(903);
        assertEquals("light_1", scheduler.current().definition.id);
        assertEquals(903, scheduler.current().start);
        scheduler = lightTwo();
        long origin = scheduler.current().id;
        scheduler.tick(914);
        scheduler.request(Intent.LIGHT, 4, 914, origin, 8, 4);
        assertEquals(
            "ORIGIN_CHANGED",
            scheduler.drainResults()
                .get(0).status);
        scheduler.request(Intent.LIGHT, 5, 915, 0, 8, 4);
        scheduler.tick(915);
        scheduler.request(Intent.LIGHT, 6, 916, origin, 8, 4);
        List<Scheduler.Result> results = scheduler.drainResults();
        assertEquals("START", results.get(0).status);
        assertEquals("ORIGIN_CHANGED", results.get(1).status);
    }

    @Test
    public void lateWireDerivativeAndStaleIdleCannotBecomeAnotherAttack() {
        Scheduler scheduler = drawn();
        scheduler.request(Intent.LIGHT, 2, 18, 0);
        scheduler.tick(18);
        long origin = scheduler.current().id;
        scheduler.tick(31);
        scheduler.request(Intent.LIGHT, 3, 32, origin);
        scheduler.tick(32);
        assertNull(scheduler.current());
        assertTrue(
            scheduler.drainResults()
                .stream()
                .anyMatch(result -> result.status.equals("ORIGIN_CHANGED")));
        scheduler.request(Intent.LIGHT, 4, 32, 0);
        scheduler.tick(32);
        Scheduler.Instance current = scheduler.current();
        scheduler.request(Intent.HEAVY, 5, 33, 0);
        scheduler.tick(33);
        assertSame(current, scheduler.current());
        scheduler.interrupt();
        scheduler.request(Intent.LIGHT, 6, 34, current.id);
        scheduler.tick(34);
        assertNull(scheduler.current());
    }

    @Test
    public void expiredDerivativeNeverBecomesIdleAttack() {
        Scheduler scheduler = drawn();
        scheduler.request(Intent.LIGHT, 2, 18);
        scheduler.tick(18);
        scheduler.request(Intent.LIGHT, 3, 19);
        scheduler.current().confirmedHit = true;
        scheduler.tick(25);
        assertEquals("light_1", scheduler.current().definition.id);
        assertTrue(
            scheduler.drainResults()
                .stream()
                .anyMatch(result -> result.status.equals("EXPIRED_OR_CHANGED")));
        scheduler.tick(31);
        scheduler.tick(32);
        assertNull(scheduler.current());
    }

    @Test
    public void hitLedgerIsPerTargetAndSegmentAndClearsAtEnd() {
        Scheduler scheduler = scheduler();
        scheduler.request(Intent.LIGHT, 1, 0);
        scheduler.tick(0);
        Scheduler.Instance instance = scheduler.current();
        assertTrue(instance.claim("target", 0));
        assertFalse(instance.claim("target", 0));
        assertTrue(instance.claim("target", 1));
        assertTrue(instance.claim("other", 0));
        scheduler.tick(17);
        assertEquals(0, instance.ledgerSize());
    }

    @Test
    public void latestValidRequestReplacesButInvalidDoesNotErase() {
        Scheduler scheduler = drawn();
        scheduler.request(Intent.LIGHT, 2, 18);
        scheduler.tick(18);
        scheduler.current().confirmedHit = true;
        scheduler.request(Intent.LIGHT, 3, 22);
        scheduler.request(Intent.LIGHT, 4, 24);
        scheduler.request(Intent.SPECIAL, 5, 24);
        scheduler.tick(25);
        assertEquals(4, scheduler.current().sequence);
        assertEquals("light_2", scheduler.current().definition.id);
    }

    @Test
    public void illegalNodesAndUnavailableLauncherReject() {
        Scheduler scheduler = scheduler();
        scheduler.request(Intent.HEAVY, 1, 0);
        scheduler.tick(0);
        assertNull(scheduler.current());
        assertEquals(
            "ILLEGAL_NODE",
            scheduler.drainResults()
                .get(0).status);
        scheduler = drawn();
        scheduler.request(Intent.LIGHT, 2, 18);
        scheduler.tick(18);
        scheduler.current().confirmedHit = true;
        scheduler.request(Intent.LIGHT, 3, 25);
        scheduler.tick(25);
        scheduler.request(Intent.HEAVY, 4, 31);
        scheduler.tick(31);
        assertEquals("light_2", scheduler.current().definition.id);
        assertTrue(
            scheduler.drainResults()
                .stream()
                .anyMatch(result -> result.status.equals("ILLEGAL_NODE")));
    }

    @Test
    public void interruptionRejectsBoundEdge() {
        Scheduler scheduler = drawn();
        scheduler.request(Intent.LIGHT, 2, 18);
        scheduler.tick(18);
        scheduler.request(Intent.HEAVY, 3, 22);
        scheduler.interrupt();
        scheduler.tick(25);
        assertNull(scheduler.current());
    }

    @Test
    public void frozenTradeSurvivesEveryTraversalPermutation() {
        List<HitBatch.Hit> hits = Arrays.asList(new HitBatch.Hit("A", "B", 1, 0), new HitBatch.Hit("B", "A", 1, 0));
        List<String> expected = Arrays.asList("A", "B");
        for (int seed = 0; seed < 20; seed++) {
            List<HitBatch.Hit> shuffled = new ArrayList<>(hits);
            Collections.shuffle(shuffled, new java.util.Random(seed));
            List<String> actual = new ArrayList<>();
            for (HitBatch.Hit hit : HitBatch.ordered(shuffled)) actual.add(hit.target);
            assertEquals(expected, actual);
            // Both prevalidated attacks commit even when the first kills its target.
            java.util.Map<String, Integer> health = new java.util.HashMap<>();
            health.put("A", 1);
            health.put("B", 1);
            for (HitBatch.Hit hit : HitBatch.ordered(shuffled)) health.put(hit.target, health.get(hit.target) - 1);
            assertEquals(Integer.valueOf(0), health.get("A"));
            assertEquals(Integer.valueOf(0), health.get("B"));
        }
    }

    @Test
    public void counterRequestExpiresWithoutFallingBackToIdleAttack() {
        Scheduler scheduler = drawn();
        scheduler.openCounter(20, 2);
        scheduler.request(Intent.LIGHT, 3, 21);
        scheduler.tick(22);
        assertNull(scheduler.current());
        assertEquals(
            "EXPIRED_OR_CHANGED",
            scheduler.drainResults()
                .get(0).status);
    }

    @Test
    public void guiClearsBufferedDerivativeButKeepsCurrentRecoveryAndLedger() {
        Scheduler scheduler = drawn();
        scheduler.request(Intent.LIGHT, 2, 18);
        scheduler.tick(18);
        Scheduler.Instance action = scheduler.current();
        action.claim("target", 0);
        scheduler.request(Intent.HEAVY, 3, 19);
        scheduler.clearBuffer();
        scheduler.tick(20);
        assertSame(action, scheduler.current());
        assertEquals(1, action.ledgerSize());
        scheduler.tick(31);
        assertNull(scheduler.current());
    }
}
