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
        /** Posture damage of this action's blow; negative derives it from the damage. */
        public int posture = -1;
        /**
         * A protected span inside the action, in ticks since it started: blows that arrive in it do not land. 0, 0 =
         * none.
         */
        public int protectFrom, protectUntil;

        /** The tick of the action at which an ability bound when it started lands its cut; 0 = none. */
        public int release;

        public boolean protects(long age) {
            return protectUntil > 0 && age >= protectFrom && age < protectUntil;
        }

        public List<HitEffect> effects = new java.util.ArrayList<>();
        public List<Edge> edges;

        public int duration() {
            return startup + active + recovery;
        }
    }

    private static final class Data {

        int bufferTicks;
        double effectVelocityLimit = 1.2;
        String namespace;
        List<Definition> actions;
        List<String> deferred;
        EntryRules entry;
    }

    public static final String LEGACY_NAMESPACE = "katana";
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    public final int bufferTicks;
    /** Namespace that completes short IDs in this catalog; legacy data without one belongs to katana. */
    public final String namespace;
    public final double effectVelocityLimit;
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
        namespace = data.namespace == null ? LEGACY_NAMESPACE : data.namespace;
        if (!namespace.matches("[a-z0-9_]+")) throw new IllegalArgumentException("namespace");
        double maximum = Double.isNaN(velocityLimit) ? data.effectVelocityLimit : velocityLimit;
        if (!Double.isFinite(maximum) || maximum < 0) throw new IllegalArgumentException("effectVelocityLimit");
        effectVelocityLimit = maximum;
        for (Definition definition : data.actions) {
            if (definition == null || definition.effects == null
                || !Double.isFinite(definition.lift)
                || definition.posture < -1
                || definition.protectFrom < 0
                || definition.protectUntil < 0
                || definition.protectUntil > 0 && (definition.protectFrom >= definition.protectUntil
                    || definition.protectUntil > definition.startup + definition.active + definition.recovery)
                || definition.protectUntil == 0 && definition.protectFrom != 0
                || definition.posture > 1000
                || definition.lift < 0
                || definition.lift > maximum
                || !Double.isFinite(definition.sweepElevation)
                || Math.abs(definition.sweepElevation) > 90)
                throw new IllegalArgumentException("actions.lift/sweepElevation/effects");
            boolean launch = false, slam = false;
            for (HitEffect effect : definition.effects) {
                if (effect == null) throw new IllegalArgumentException("actions.effects");
                effect.validate(maximum);
                if (effect.type == HitEffect.Type.LAUNCH) {
                    if (launch)
                        throw new IllegalArgumentException("actions[" + definition.id + "].effects: duplicate LAUNCH");
                    launch = true;
                } else if (effect.type == HitEffect.Type.SLAM) {
                    if (slam)
                        throw new IllegalArgumentException("actions[" + definition.id + "].effects: duplicate SLAM");
                    slam = true;
                }
                if (launch && slam) throw new IllegalArgumentException(
                    "actions[" + definition.id + "].effects: LAUNCH and SLAM in one action");
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
        if (entry.counter != null) entry.counter = normalized(entry.counter);
        if (entry.finisher != null) entry.finisher = normalized(entry.finisher);
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

    /** What the input starts in the window after a precise guard, or null when it means its usual thing. */
    public String counterTarget(Intent input) {
        return entry.counter == null ? null : entry.counter.get(input);
    }

    /** What the input starts while a broken target is in reach, or null when it means its usual thing. */
    public String finisherTarget(Intent input) {
        return entry.finisher == null ? null : entry.finisher.get(input);
    }

    public boolean entersReady(String id) {
        return entry.entersReady.contains(normalize(id));
    }

    public boolean entersSheathed(String id) {
        return entry.entersSheathed.contains(normalize(id));
    }

    /** Completes a short ID with this catalog's namespace; qualified IDs are returned as is, without allocation. */
    public String normalize(String id) {
        if (id == null) throw new IllegalArgumentException("Missing action ID");
        return id.indexOf(':') < 0 ? namespace + ":" + id : id;
    }

    /** Pre-namespace data only: short IDs belong to katana. */
    static String legacyNormalize(String id) {
        if (id == null) throw new IllegalArgumentException("Missing action ID");
        return id.indexOf(':') < 0 ? LEGACY_NAMESPACE + ":" + id : id;
    }

    /** Hot-path lookup for an already qualified ID; never concatenates and returns null when unknown. */
    public Definition byId(String qualified) {
        return definitions.get(qualified);
    }

    /** Hot-path lookup for an ID the scheduler already qualified; unknown IDs are a programming error. */
    public Definition require(String qualified) {
        Definition definition = definitions.get(qualified);
        if (definition == null) throw new IllegalArgumentException("Unknown action: " + qualified);
        return definition;
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

    /** Deterministic wire form for the client, including entry rules and the post-override action fields. */
    public String toWireJson() {
        com.google.gson.Gson gson = new Gson();
        com.google.gson.JsonObject data = new com.google.gson.JsonObject();
        data.addProperty("namespace", namespace);
        data.addProperty("bufferTicks", bufferTicks);
        data.addProperty("effectVelocityLimit", effectVelocityLimit);
        com.google.gson.JsonArray list = new com.google.gson.JsonArray();
        for (Definition definition : definitions.values()) list.add(gson.toJsonTree(definition));
        data.add("actions", list);
        com.google.gson.JsonObject rules = new com.google.gson.JsonObject();
        if (entry.sheathed != null) rules.add("sheathed", intents(entry.sheathed));
        rules.add("ready", intents(entry.ready));
        if (entry.counter != null && !entry.counter.isEmpty()) rules.add("counter", intents(entry.counter));
        if (entry.finisher != null && !entry.finisher.isEmpty()) rules.add("finisher", intents(entry.finisher));
        rules.add("entersReady", sorted(entry.entersReady));
        rules.add("entersSheathed", sorted(entry.entersSheathed));
        data.add("entry", rules);
        return data.toString();
    }

    private static com.google.gson.JsonObject intents(Map<Intent, String> source) {
        com.google.gson.JsonObject result = new com.google.gson.JsonObject();
        for (Intent intent : Intent.values())
            if (source.containsKey(intent)) result.addProperty(intent.name(), source.get(intent));
        return result;
    }

    private static com.google.gson.JsonArray sorted(java.util.Set<String> source) {
        com.google.gson.JsonArray result = new com.google.gson.JsonArray();
        for (String id : new java.util.TreeSet<>(source)) result.add(new com.google.gson.JsonPrimitive(id));
        return result;
    }

    public Map<String, Definition> all() {
        Map<String, Definition> result = new LinkedHashMap<>();
        for (Definition definition : definitions.values()) result.put(definition.id, definition);
        return Collections.unmodifiableMap(result);
    }
}
