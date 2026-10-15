package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import java.io.StringReader;

import org.junit.Test;

public class ModeBlinkTest {

    private static final String DATA = "{\"bufferTicks\":4,\"namespace\":\"t\",\"actions\":["
        + "{\"id\":\"t:light\",\"startup\":2,\"active\":1,\"recovery\":8,\"edges\":[{\"input\":\"DODGE\",\"target\":\"t:step\",\"from\":3,\"until\":8}]},"
        + "{\"id\":\"t:step\",\"startup\":1,\"active\":0,\"recovery\":9,\"edges\":[]},"
        + "{\"id\":\"t:blink\",\"startup\":1,\"active\":0,\"recovery\":11,\"edges\":[]}],"
        + "\"entry\":{\"ready\":{\"LIGHT\":\"t:light\",\"DODGE\":\"t:step\"},\"mode\":{\"DODGE\":\"t:blink\"},"
        + "\"entersReady\":[\"t:light\",\"t:step\",\"t:blink\"],\"entersSheathed\":[]}}";

    private static Scheduler scheduler() {
        return new Scheduler(WeaponDefinition.katana(), new ActionCatalog(new StringReader(DATA)));
    }

    private static String started(Scheduler s, long from, long to) {
        for (long tick = from; tick < to; tick++) {
            s.tick(tick);
            for (Scheduler.Result r : s.drainResults()) if (r.status.equals("START")) return r.action;
        }
        return null;
    }

    @Test
    public void aStepIsABlinkOnlyWhileTheServerSaysTheModeIsOn() {
        Scheduler plain = scheduler();
        plain.request(Intent.DODGE, 1, 10);
        assertEquals("t:step", started(plain, 11, 13));

        Scheduler on = scheduler();
        on.setMode(true);
        on.request(Intent.DODGE, 1, 10);
        assertEquals("t:blink", started(on, 11, 13));

        Scheduler off = scheduler();
        off.setMode(true);
        off.setMode(false);
        off.request(Intent.DODGE, 1, 10);
        assertEquals("t:step", started(off, 11, 13));
    }

    @Test
    public void otherInputsAreUntouchedByTheMode() {
        Scheduler s = scheduler();
        s.setMode(true);
        s.request(Intent.LIGHT, 1, 10);
        assertEquals("t:light", started(s, 11, 13));
    }

    @Test
    public void aDodgeEdgeOfARunningActionIsABlinkInTheModeToo() {
        Scheduler s = scheduler();
        s.request(Intent.LIGHT, 1, 10);
        assertEquals("t:light", started(s, 11, 13));
        s.setMode(true);
        s.request(Intent.DODGE, 2, 15);
        assertEquals("t:blink", started(s, 16, 24));
    }

    @Test
    public void theModeMapIsPartOfTheCatalogHashAndWireRules() {
        ActionCatalog with = new ActionCatalog(new StringReader(DATA));
        ActionCatalog without = new ActionCatalog(
            new StringReader(DATA.replace(",\"mode\":{\"DODGE\":\"t:blink\"}", "")));
        assertEquals("t:blink", with.modeTarget(Intent.DODGE));
        assertNull(without.modeTarget(Intent.DODGE));
        assertNotEquals(with.toWireJson(), without.toWireJson());
    }
}
