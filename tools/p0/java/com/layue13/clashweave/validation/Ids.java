package com.layue13.clashweave.validation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.core.ActionCatalog;

/**
 * Fixture helpers that work against both the pre-WF-1c jar (short action IDs, config.actions) and the content-driven
 * jar (qualified IDs, per-style catalogs), so one validation jar can drive an A/B run of the two.
 */
final class Ids {

    private Ids() {}

    /** Action ID without its namespace: "katana:light_1" and "light_1" both give "light_1". */
    static String local(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    /** The server's katana catalog, with whatever overrides are in force. */
    static ActionCatalog katana() {
        try {
            try {
                Field field = Clashweave.config.getClass()
                    .getField("actions");
                return (ActionCatalog) field.get(Clashweave.config);
            } catch (NoSuchFieldException legacy) {
                Object rules = Clashweave.server.getClass()
                    .getMethod("rules")
                    .invoke(Clashweave.server);
                Method catalog = rules.getClass()
                    .getMethod("catalog", String.class);
                return (ActionCatalog) catalog.invoke(rules, "katana:default");
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
