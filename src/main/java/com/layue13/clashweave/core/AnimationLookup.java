package com.layue13.clashweave.core;

import java.util.HashMap;
import java.util.Map;

/** Pure lookup: weapon set, type default, global default. No gameplay data is selected here. */
public final class AnimationLookup {

    private final Map<String, String> entries = new HashMap<>();

    public void put(String set, String action, String animation) {
        entries.put(set + "/" + action, animation);
    }

    private String find(String set, String action) {
        String exact = entries.get(set + "/" + action);
        return exact == null ? entries.get(set + "/*") : exact;
    }

    public String resolve(String set, String typeDefault, String action) {
        for (String candidate : new String[] { set, typeDefault, "global" }) {
            String value = find(candidate, action);
            if (value != null) return value;
        }
        throw new IllegalArgumentException("Missing global animation fallback");
    }
}
