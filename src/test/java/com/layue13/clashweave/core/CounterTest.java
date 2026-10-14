package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import java.io.StringReader;

import org.junit.Test;

public class CounterTest {

    private static final String DATA = "{\"bufferTicks\":4,\"namespace\":\"t\",\"actions\":["
        + "{\"id\":\"t:light\",\"startup\":2,\"active\":1,\"recovery\":6,\"edges\":[]},"
        + "{\"id\":\"t:riposte\",\"startup\":2,\"active\":1,\"recovery\":6,\"edges\":[]}],"
        + "\"entry\":{\"ready\":{\"LIGHT\":\"t:light\"},\"counter\":{\"LIGHT\":\"t:riposte\"},"
        + "\"entersReady\":[\"t:light\",\"t:riposte\"],\"entersSheathed\":[]}}";

    private static Scheduler scheduler() {
        return new Scheduler(WeaponDefinition.katana(), new ActionCatalog(new StringReader(DATA)));
    }

    private static String started(Scheduler s, long tick) {
        s.tick(tick);
        for (Scheduler.Result r : s.drainResults()) if (r.status.equals("START")) return r.action;
        return null;
    }

    @Test
    public void lightInsideTheWindowIsTheRiposteOtherwiseTheUsualMove() {
        Scheduler s = scheduler();
        s.request(Intent.LIGHT, 1, 10);
        assertEquals("t:light", started(s, 11));

        Scheduler c = scheduler();
        c.openCounter(10, 8);
        assertTrue(c.counter(11));
        c.request(Intent.LIGHT, 1, 11);
        assertEquals("t:riposte", started(c, 12));
    }

    @Test
    public void afterTheWindowTheUsualMoveReturns() {
        Scheduler s = scheduler();
        s.openCounter(10, 8);
        assertFalse(s.counter(18));
        s.request(Intent.LIGHT, 1, 19);
        assertEquals("t:light", started(s, 20));
    }

    @Test
    public void theCounterRulesAreCoveredByTheCatalogHash() {
        ActionCatalog with = new ActionCatalog(new StringReader(DATA));
        ActionCatalog without = new ActionCatalog(
            new StringReader(DATA.replace("\"counter\":{\"LIGHT\":\"t:riposte\"},", "")));
        assertNotEquals(with.toWireJson(), without.toWireJson());
        assertNull(without.counterTarget(Intent.LIGHT));
        assertEquals("t:riposte", with.counterTarget(Intent.LIGHT));
    }
}
