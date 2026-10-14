package com.layue13.clashweave.client.vfx;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.Random;

import org.junit.Test;

public class VfxTest {

    private static EffectLibrary library(String json) {
        EffectLibrary library = new EffectLibrary();
        library.readEffects(new StringReader(json));
        return library;
    }

    private static final String SPARKS = "{\"effects\":[{\"id\":\"t:s\",\"emitters\":[{\"kind\":\"STREAK\",\"count\":5,\"life\":[4,6]}]}]}";

    @Test
    public void curveIsConstantForOneValueAndInterpolatesEvenlyOtherwise() {
        assertEquals(.5f, new Curve(.5f).at(.7f), 1e-6);
        Curve c = new Curve(1, 0);
        assertEquals(1f, c.at(0), 1e-6);
        assertEquals(.5f, c.at(.5f), 1e-4);
        assertEquals(0f, c.at(1), 1e-6);
        assertEquals(0f, c.at(2), 1e-6);
        Curve peak = new Curve(0, 1, 0);
        assertEquals(1f, peak.at(.5f), 1e-4);
    }

    @Test
    public void aGoodEffectLoadsAndABadOneIsSkippedAndReported() {
        EffectLibrary library = library(
            "{\"effects\":[{\"id\":\"t:good\",\"emitters\":[{\"kind\":\"STREAK\"}]},"
                + "{\"id\":\"t:bad\",\"emitters\":[{\"kind\":\"STREAK\",\"count\":500}]},"
                + "{\"id\":\"t:empty\",\"emitters\":[]}]}");
        assertEquals(1, library.size());
        assertEquals(0, library.handle("t:good"));
        assertEquals(-1, library.handle("t:bad"));
        assertEquals(
            2,
            library.errors()
                .size());
        assertTrue(
            library.errors()
                .get(0)
                .contains("t:bad"));
    }

    @Test
    public void bindingsPointAtLoadedEffectsOnly() {
        EffectLibrary library = library(SPARKS);
        library.readBindings(
            new StringReader(
                "{\"events\":[{\"kind\":\"BLOCK\",\"effect\":\"t:s\"},{\"kind\":\"PARRY\",\"effect\":\"t:nope\"}]}"));
        assertEquals(0, library.forEvent("BLOCK"));
        assertEquals(-1, library.forEvent("PARRY"));
        assertEquals(
            1,
            library.errors()
                .size());
    }

    @Test
    public void theShippedDataLoadsWithoutErrors() {
        EffectLibrary library = EffectLibrary.load();
        assertTrue(
            library.errors()
                .toString(),
            library.errors()
                .isEmpty());
        assertTrue(library.forEvent("BLOCK") >= 0);
        assertTrue(library.forEvent("PARRY") >= 0);
        assertNotNull(library.trail());
    }

    @Test
    public void particlesLeaveWithinTheConeAgeOutAndTheRingRecyclesWhenFull() {
        EffectLibrary library = library(SPARKS);
        ParticlePool pool = new ParticlePool();
        Random random = new Random(1);
        pool.emit(library.get(0), 0, 0, 0, 1, 0, 0, random);
        assertEquals(5, pool.count);
        for (int i = 0; i < 5; i++) assertTrue("leaves toward +x", pool.vx[i] > 0);
        for (int i = 0; i < 7; i++) pool.tick();
        assertEquals("all expired", 0, pool.count);
        for (int n = 0; n < 200; n++) pool.emit(library.get(0), 0, 0, 0, 0, 1, 0, random);
        assertEquals(ParticlePool.CAPACITY, pool.count);
    }
}
