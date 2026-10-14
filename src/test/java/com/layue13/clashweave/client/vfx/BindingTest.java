package com.layue13.clashweave.client.vfx;

import static org.junit.Assert.*;

import java.io.StringReader;

import org.junit.Test;

public class BindingTest {

    private static EffectLibrary library() {
        EffectLibrary library = new EffectLibrary();
        library.readEffects(
            new StringReader(
                "{\"effects\":[{\"id\":\"t:a\",\"emitters\":[{\"kind\":\"STREAK\"}]},"
                    + "{\"id\":\"t:b\",\"emitters\":[{\"kind\":\"DISC\",\"facing\":\"GROUND\"}]},"
                    + "{\"id\":\"t:bad\",\"emitters\":[{\"kind\":\"STREAK\",\"dirSign\":2}]}]}"));
        return library;
    }

    @Test
    public void anEntitySpecificBindingBeatsTheGenericOneAndOthersFallBack() {
        EffectLibrary library = library();
        library.readBindings(
            new StringReader(
                "{\"events\":[{\"kind\":\"BREAK\",\"effect\":\"t:a\"},"
                    + "{\"kind\":\"BREAK\",\"entity\":\"EntityZombie\",\"effect\":\"t:b\",\"height\":0.8}]}"));
        assertEquals(
            "only the deliberately bad effect",
            1,
            library.errors()
                .size());
        assertEquals(
            library.handle("t:b"),
            library.resolve("BREAK", "EntityZombie")
                .effect());
        assertEquals(
            library.handle("t:a"),
            library.resolve("BREAK", "EntitySkeleton")
                .effect());
        assertEquals(
            library.handle("t:a"),
            library.resolve("BREAK", null)
                .effect());
        assertNull(library.resolve("SLAM", "EntityZombie"));
        assertEquals(.8f, library.resolve("BREAK", "EntityZombie").height, 1e-6);
    }

    @Test
    public void badBindingsAreReportedAndSkipped() {
        EffectLibrary library = library();
        library.readBindings(
            new StringReader(
                "{\"events\":[{\"kind\":\"BREAK\",\"effect\":\"t:nope\"},"
                    + "{\"kind\":\"BREAK\",\"effect\":\"t:a\",\"dir\":\"diagonal\"},"
                    + "{\"kind\":\"BREAK\",\"effect\":\"t:a\",\"follow\":\"t:a\"},"
                    + "{\"kind\":\"SLAM\",\"effect\":\"t:a\",\"follow\":\"t:a\",\"followDuration\":10}]}"));
        assertEquals(
            "the bad effect plus three bad bindings",
            4,
            library.errors()
                .size());
        assertNull(library.resolve("BREAK", null));
        assertNotNull(library.resolve("SLAM", null));
        assertEquals(
            library.handle("t:a"),
            library.resolve("SLAM", null)
                .followEffect());
    }

    @Test
    public void aBadDirectionSignIsRejectedAtLoad() {
        assertEquals(-1, library().handle("t:bad"));
        assertEquals(
            1,
            library().errors()
                .size());
    }

    @Test
    public void groundFacingLoads() {
        EffectLibrary library = library();
        assertEquals(EffectDef.Facing.GROUND, library.get(library.handle("t:b")).emitters.get(0).facing);
    }
}
