package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class AnimationLookupTest {

    @Test
    public void lookupResolvesWeaponThenTypeThenGlobal() {
        AnimationLookup lookup = new AnimationLookup();
        lookup.put("global", "*", "global");
        lookup.put("type:katana", "light_1", "type");
        lookup.put("weapon", "light_1", "weapon");
        assertEquals("weapon", lookup.resolve("weapon", "type:katana", "light_1"));
        assertEquals("type", lookup.resolve("unknown", "type:katana", "light_1"));
        assertEquals("global", lookup.resolve("unknown", "unknown", "heavy"));
        lookup.put("weapon", "*", "weaponWildcard");
        assertEquals("weaponWildcard", lookup.resolve("weapon", "type:katana", "heavy"));
        assertThrows(IllegalArgumentException.class, () -> new AnimationLookup().resolve("none", "none", "light_1"));
    }
}
