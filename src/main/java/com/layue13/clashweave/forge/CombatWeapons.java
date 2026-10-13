package com.layue13.clashweave.forge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.layue13.clashweave.content.EffectiveRules;
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.WeaponDefinition;

/**
 * Item to weapon table. The server owns it; a client holds a copy the server sent. A lookup is one identity-hash
 * probe: it never parses, concatenates or allocates, so it is safe on every tick and every frame.
 */
public final class CombatWeapons {

    /** Everything a weapon needs at run time, resolved once when the table is built. */
    public static final class Entry {

        public final String id;
        public final WeaponDefinition definition;
        public final ActionCatalog catalog;
        public final String wireJson;

        Entry(String id, WeaponDefinition definition, ActionCatalog catalog, String wireJson) {
            this.id = id;
            this.definition = definition;
            this.catalog = catalog;
            this.wireJson = wireJson;
        }
    }

    /** Immutable; replaced as a whole. */
    public static final class Table {

        static final Table EMPTY = new Table(new IdentityHashMap<>(), new LinkedHashMap<>(), null);

        private final Map<Item, Entry> byItem;
        private final Map<String, String> names;
        private final Entry fallback;

        Table(Map<Item, Entry> byItem, Map<String, String> names, Entry fallback) {
            this.byItem = byItem;
            this.names = Collections.unmodifiableMap(names);
            this.fallback = fallback;
        }

        /** Item registry name to weapon ID, in configuration order; what the server sends to clients. */
        public Map<String, String> names() {
            return names;
        }

        public Entry fallback() {
            return fallback;
        }

        public int size() {
            return byItem.size();
        }
    }

    private static volatile Table table = Table.EMPTY;

    private CombatWeapons() {}

    /**
     * Builds a table from "item=weapon" lines. Every problem is reported, naming the line, and nothing is installed
     * unless all lines are valid.
     */
    public static Table build(List<String> lines, EffectiveRules rules, double multiplierMinimum,
        double multiplierMaximum, Function<String, Item> items) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String line : lines) {
            int split = line.indexOf('=');
            if (split <= 0 || split == line.length() - 1)
                throw new IllegalArgumentException("weapons.items: expected <item>=<weapon>, got \"" + line + "\"");
            if (names.put(
                line.substring(0, split)
                    .trim(),
                line.substring(split + 1)
                    .trim())
                != null) throw new IllegalArgumentException("weapons.items: duplicate item in \"" + line + "\"");
        }
        return build(names, rules, multiplierMinimum, multiplierMaximum, items);
    }

    public static Table build(Map<String, String> names, EffectiveRules rules, double multiplierMinimum,
        double multiplierMaximum, Function<String, Item> items) {
        Map<Item, Entry> byItem = new IdentityHashMap<>();
        Map<String, Entry> byWeapon = new HashMap<>();
        List<String> problems = new ArrayList<>();
        Entry first = null;
        for (Map.Entry<String, String> mapping : names.entrySet()) {
            Item item = items.apply(mapping.getKey());
            WeaponDefinition definition = rules.weapon(mapping.getValue());
            if (item == null) {
                problems.add("weapons.items: unknown item \"" + mapping.getKey() + "\"");
                continue;
            }
            if (definition == null) {
                problems.add("weapons.items: unknown weapon \"" + mapping.getValue() + "\"");
                continue;
            }
            try {
                definition.validate(multiplierMinimum, multiplierMaximum);
            } catch (IllegalArgumentException e) {
                problems.add("weapons.items: weapon \"" + mapping.getValue() + "\": " + e.getMessage());
                continue;
            }
            Entry entry = byWeapon.get(mapping.getValue());
            if (entry == null) {
                entry = new Entry(
                    mapping.getValue(),
                    definition,
                    rules.catalog(definition.style),
                    rules.wireJson(definition.style));
                byWeapon.put(mapping.getValue(), entry);
            }
            if (byItem.put(item, entry) != null) problems.add("weapons.items: item mapped twice: " + mapping.getKey());
            if (first == null) first = entry;
        }
        if (!problems.isEmpty()) throw new IllegalArgumentException(String.join("; ", problems));
        if (first == null) throw new IllegalArgumentException("weapons.items: at least one weapon is required");
        return new Table(byItem, names, first);
    }

    public static void install(Table next) {
        table = next == null ? Table.EMPTY : next;
    }

    public static Table table() {
        return table;
    }

    /** O(1), allocation free; null when the stack is empty or not a weapon. */
    public static Entry entry(ItemStack stack) {
        return stack != null && stack.stackSize > 0 ? table.byItem.get(stack.getItem()) : null;
    }

    public static boolean armed(ItemStack stack) {
        return entry(stack) != null;
    }

    /** What a player with nothing weapon-like in hand is scheduled with. */
    public static Entry fallback() {
        return table.fallback;
    }
}
