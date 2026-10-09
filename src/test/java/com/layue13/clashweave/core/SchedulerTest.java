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
}
