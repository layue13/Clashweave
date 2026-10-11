package com.layue13.clashweave.core;

import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

/** Internal content schema. Windows are half open; unsupported nodes remain explicit. */
public final class ActionCatalog {

    public static final class Edge {

        public Intent input;
        public String target;
        public int from;
        public int until;
        public boolean hit;
        public boolean damageHit;
        public String requires;
    }

    public static final class Definition {

        public String id;
        public int startup;
        public int active;
        public int recovery;
        public float damage;
        public double reach;
        public double arc;
        public double distance;
        public double speed;
        public double lift;
        public double sweepElevation;
        public List<HitEffect> effects = new java.util.ArrayList<>();
        public List<Edge> edges;

        public int duration() {
            return startup + active + recovery;
        }
    }

    private static final class Data {

        int bufferTicks;
        double effectVelocityLimit = 1.2;
        List<Definition> actions;
        List<String> deferred;
        EntryRules entry;
    }

    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    public final int bufferTicks;
    private final EntryRules entry;

    public ActionCatalog(Reader reader) {
        this(reader, Double.NaN);
    }

    public ActionCatalog(Reader reader, double velocityLimit) {
        Data data = new Gson().fromJson(reader, Data.class);
        if (data == null || data.actions == null || data.bufferTicks < 1) {
            throw new IllegalArgumentException("Missing action data");
        }
        bufferTicks = data.bufferTicks;
        double maximum = Double.isNaN(velocityLimit) ? data.effectVelocityLimit : velocityLimit;
        if (!Double.isFinite(maximum) || maximum < 0) throw new IllegalArgumentException("effectVelocityLimit");
        for (Definition definition : data.actions) {
            if (definition == null || definition.effects == null
                || !Double.isFinite(definition.lift)
                || definition.lift < 0
                || definition.lift > maximum
                || !Double.isFinite(definition.sweepElevation)
                || Math.abs(definition.sweepElevation) > 90)
                throw new IllegalArgumentException("actions.lift/sweepElevation/effects");
            boolean launch = false;
            for (HitEffect effect : definition.effects) {
                if (effect == null) throw new IllegalArgumentException("actions.effects");
                effect.validate(maximum);
                if (effect.type == HitEffect.Type.LAUNCH) {
                    if (launch)
                        throw new IllegalArgumentException("actions[" + definition.id + "].effects: duplicate LAUNCH");
                    launch = true;
                }
            }
            if (definition.id == null || definition.startup < 0
                || definition.active < 0
                || definition.recovery < 1
                || definition.damage < 0
                || definition.reach < 0
                || definition.arc < 0
                || definition.arc > 360
                || definition.distance < 0
                || definition.speed < 0
                || definition.edges == null
                || definitions.put(normalize(definition.id), definition) != null) {
                throw new IllegalArgumentException("Invalid action definition");
            }
        }
        for (Definition definition : definitions.values()) {
            for (Edge edge : definition.edges) {
                if (edge == null
                    || edge.requires != null && !edge.requires.equals("GROUND") && !edge.requires.equals("AIR")
                    || edge.input == null
                    || !definitions.containsKey(normalize(edge.target))
                    || edge.from < 0
                    || edge.until <= edge.from
                    || edge.until > definition.duration()) {
                    throw new IllegalArgumentException("Invalid edge in " + definition.id);
                }
                for (Edge other : definition.edges) {
                    if (other != null && other != edge
                        && other.input == edge.input
                        && other.from < edge.until
                        && edge.from < other.until) {
                        throw new IllegalArgumentException("Ambiguous edge in " + definition.id);
                    }
                }
            }
        }
        entry = data.entry == null ? EntryRules.legacyKatana(definitions.values()) : data.entry;
        if (entry.ready == null || entry.entersReady == null || entry.entersSheathed == null)
            throw new IllegalArgumentException("entry");
        entry.ready = normalized(entry.ready);
        if (entry.sheathed != null) entry.sheathed = normalized(entry.sheathed);
        entry.entersReady = normalized(entry.entersReady);
        entry.entersSheathed = normalized(entry.entersSheathed);
        if (data.entry != null && entry.sheathed != null
            && (entry.entersReady.isEmpty() || entry.entersSheathed.isEmpty()))
            throw new IllegalArgumentException("entry: a sheathed state needs entersReady and entersSheathed");
    }

    private java.util.Map<Intent, String> normalized(java.util.Map<Intent, String> source) {
        java.util.Map<Intent, String> result = new java.util.EnumMap<>(Intent.class);
        for (java.util.Map.Entry<Intent, String> e : source.entrySet()) result.put(e.getKey(), member(e.getValue()));
        return result;
    }

    private java.util.Set<String> normalized(java.util.Set<String> source) {
        java.util.Set<String> result = new java.util.HashSet<>();
        for (String id : source) result.add(member(id));
        return result;
    }

    private String member(String id) {
        String full = normalize(id);
        if (!definitions.containsKey(full)) throw new IllegalArgumentException("entry: unknown action " + id);
        return full;
    }

    public boolean hasSheathedState() {
        return entry.sheathed != null;
    }

    public String entryTarget(boolean sheathed, Intent input) {
        Map<Intent, String> state = sheathed ? entry.sheathed : entry.ready;
        return state == null ? null : state.get(input);
    }

    public boolean entersReady(String id) {
        return entry.entersReady.contains(normalize(id));
    }

    public boolean entersSheathed(String id) {
        return entry.entersSheathed.contains(normalize(id));
    }

    /** Short IDs retain the legacy katana namespace, including on namespaced catalogs. */
    public static String normalize(String id) {
        if (id == null) throw new IllegalArgumentException("Missing action ID");
        return id.indexOf(':') < 0 ? "katana:" + id : id;
    }

    public Definition get(String id) {
        Definition definition = definitions.get(normalize(id));
        if (definition == null) throw new IllegalArgumentException("Unknown action: " + id);
        return definition;
    }

    /** Server configuration overrides for edges that enter launch-bearing actions. */
    public void launchPolicy(boolean requiresHit, boolean requiresGround) {
        for (Definition action : definitions.values()) for (Edge edge : action.edges) {
            boolean launch = get(edge.target).effects.stream()
                .anyMatch(effect -> effect.type == HitEffect.Type.LAUNCH);
            if (launch) {
                edge.hit = requiresHit;
                edge.damageHit = requiresHit;
                if (requiresGround) edge.requires = "GROUND";
                else if ("GROUND".equals(edge.requires)) edge.requires = null;
            }
        }
    }

    public Map<String, Definition> all() {
        Map<String, Definition> result = new LinkedHashMap<>();
        for (Definition definition : definitions.values()) result.put(definition.id, definition);
        return Collections.unmodifiableMap(result);
    }
}
