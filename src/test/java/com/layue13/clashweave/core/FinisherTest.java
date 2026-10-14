package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import java.io.StringReader;

import org.junit.Test;

public class FinisherTest {

    private static final String DATA = "{\"bufferTicks\":4,\"namespace\":\"t\",\"actions\":["
        + "{\"id\":\"t:light\",\"startup\":2,\"active\":1,\"recovery\":8,\"edges\":[{\"input\":\"HEAVY\",\"target\":\"t:heavy\",\"from\":3,\"until\":8}]},"
        + "{\"id\":\"t:heavy\",\"startup\":2,\"active\":1,\"recovery\":6,\"edges\":[]},"
        + "{\"id\":\"t:finish\",\"startup\":2,\"active\":1,\"recovery\":6,\"edges\":[]}],"
        + "\"entry\":{\"ready\":{\"HEAVY\":\"t:heavy\",\"LIGHT\":\"t:light\"},\"finisher\":{\"HEAVY\":\"t:finish\"},"
        + "\"entersReady\":[\"t:light\",\"t:heavy\",\"t:finish\"],\"entersSheathed\":[]}}";

    private static Scheduler scheduler() {
        return new Scheduler(WeaponDefinition.katana(), new ActionCatalog(new StringReader(DATA)));
    }

    private static String started(Scheduler s, long tick) {
        s.tick(tick);
        for (Scheduler.Result r : s.drainResults()) if (r.status.equals("START")) return r.action;
        return null;
    }

    @Test
    public void heavyMeansTheFinisherOnlyInsideTheWindowTheServerOpens() {
        Scheduler plain = scheduler();
        plain.request(Intent.HEAVY, 1, 10);
        assertEquals("t:heavy", started(plain, 11));

        Scheduler open = scheduler();
        open.openFinisher(10, 2);
        assertTrue(open.finisher(11));
        open.request(Intent.HEAVY, 1, 10);
        assertEquals("t:finish", started(open, 11));
    }

    @Test
    public void heavyFromARunningComboIsTheFinisherToo() {
        Scheduler s = scheduler();
        s.request(Intent.LIGHT, 1, 10);
        assertEquals("t:light", started(s, 11));
        s.openFinisher(14, 2);
        s.request(Intent.HEAVY, 2, 14);
        String second = null;
        for (long tick = 15; tick < 22 && second == null; tick++) second = started(s, tick);
        assertEquals("t:finish", second);

        Scheduler p = scheduler();
        p.request(Intent.LIGHT, 1, 10);
        started(p, 11);
        p.request(Intent.HEAVY, 2, 14);
        String other = null;
        for (long tick = 15; tick < 22 && other == null; tick++) other = started(p, tick);
        assertEquals("t:heavy", other);
    }

    @Test
    public void theWindowClosesAndAFinisherStartDoesNotLeaveItOpen() {
        Scheduler s = scheduler();
        s.openFinisher(10, 2);
        assertFalse(s.finisher(12));
        s.request(Intent.HEAVY, 1, 13);
        assertEquals("t:heavy", started(s, 14));

        Scheduler t = scheduler();
        t.openFinisher(10, 2);
        t.request(Intent.HEAVY, 1, 10);
        assertEquals("t:finish", started(t, 11));
        assertFalse("the window is spent by the start", t.finisher(11));
    }

    @Test
    public void theFinisherRulesAreCoveredByTheCatalogHash() {
        ActionCatalog with = new ActionCatalog(new StringReader(DATA));
        ActionCatalog without = new ActionCatalog(
            new StringReader(DATA.replace("\"finisher\":{\"HEAVY\":\"t:finish\"},", "")));
        assertNotEquals(with.toWireJson(), without.toWireJson());
        assertNull(without.finisherTarget(Intent.HEAVY));
        assertEquals("t:finish", with.finisherTarget(Intent.HEAVY));
    }

    @Test
    public void endingABreakRestoresTheSharePromptlyAndOnlyWhileBroken() {
        Posture posture = new Posture(100, 40, 2, 50);
        assertTrue(posture.damage(100, 10, 16));
        assertTrue(posture.broken(12));
        posture.endBreak(12);
        assertFalse(posture.broken(12));
        posture.advance(13);
        assertEquals(50f, posture.value(), 1e-4);
        posture.endBreak(14); // nothing to end
        assertEquals(50f, posture.value(), 1e-4);
    }
}
