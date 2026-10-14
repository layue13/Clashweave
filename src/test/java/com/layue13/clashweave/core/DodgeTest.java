package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import java.io.StringReader;

import org.junit.Test;

public class DodgeTest {

    private static ActionCatalog catalog(String protect) {
        return new ActionCatalog(
            new StringReader(
                "{\"bufferTicks\":4,\"actions\":[{\"id\":\"step\",\"startup\":1,\"active\":0,\"recovery\":9," + protect
                    + "\"edges\":[]}]}"));
    }

    @Test
    public void theProtectedSpanIsHalfOpenAndOnlyWhereDeclared() {
        ActionCatalog.Definition step = catalog("\"protectFrom\":1,\"protectUntil\":6,").get("step");
        assertFalse(step.protects(0));
        assertTrue(step.protects(1));
        assertTrue(step.protects(5));
        assertFalse(step.protects(6));
        assertFalse(
            "an action without a span protects nothing",
            catalog("").get("step")
                .protects(3));
    }

    @Test
    public void aSpanThatIsEmptyBackwardsOrPastTheEndIsRejected() {
        for (String bad : new String[] { "\"protectFrom\":4,\"protectUntil\":4,",
            "\"protectFrom\":5,\"protectUntil\":3,", "\"protectFrom\":1,\"protectUntil\":11,", "\"protectFrom\":2," }) {
            try {
                catalog(bad);
                fail("accepted: " + bad);
            } catch (IllegalArgumentException expected) {
                assertNotNull(expected.getMessage());
            }
        }
    }

    @Test
    public void theShippedStepProtectsPartOfItsDurationOnly() throws Exception {
        ActionCatalog katana = new ActionCatalog(
            new java.io.InputStreamReader(
                getClass().getResourceAsStream("/assets/clashweave/combat/katana.json"),
                "UTF-8"));
        ActionCatalog.Definition step = katana.get("step");
        assertTrue(step.protectUntil > 0 && step.protectUntil < step.duration());
        assertEquals(0, step.damage, 0);
        assertTrue(step.distance > 0 && step.speed > 0);
    }
}
