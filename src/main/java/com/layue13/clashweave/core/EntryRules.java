package com.layue13.clashweave.core;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Idle inputs and posture transitions. Omitted sheathed means a ready-only style. */
public final class EntryRules {

    public Map<Intent, String> sheathed;
    public Map<Intent, String> ready;
    /** Inputs that mean something else right after a precise guard; omitted when there is no counter. */
    public Map<Intent, String> counter;
    public Set<String> entersReady = new HashSet<>();
    public Set<String> entersSheathed = new HashSet<>();

    /** Compatibility for pre-content catalogs only; new styles must supply explicit rules. */
    static EntryRules legacyKatana(Iterable<ActionCatalog.Definition> actions) {
        Set<String> present = new HashSet<>();
        for (ActionCatalog.Definition action : actions) present.add(ActionCatalog.legacyNormalize(action.id));
        EntryRules rules = new EntryRules();
        rules.sheathed = new EnumMap<>(Intent.class);
        rules.ready = new EnumMap<>(Intent.class);
        put(rules.sheathed, Intent.LIGHT, "katana:iai", present);
        put(rules.ready, Intent.LIGHT, "katana:light_1", present);
        put(rules.ready, Intent.HEAVY, "katana:heavy", present);
        put(rules.ready, Intent.SHEATHE, "katana:sheathe", present);
        if (present.contains("katana:sheathe")) rules.entersSheathed.add("katana:sheathe");
        for (String id : present) if (!rules.entersSheathed.contains(id)) rules.entersReady.add(id);
        return rules;
    }

    private static void put(Map<Intent, String> map, Intent input, String id, Set<String> present) {
        if (present.contains(id)) map.put(input, id);
    }
}
