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
    }

    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    public final int bufferTicks;

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
                || definitions.put(definition.id, definition) != null) {
                throw new IllegalArgumentException("Invalid action definition");
            }
        }
        for (Definition definition : definitions.values()) {
            for (Edge edge : definition.edges) {
                if (edge == null
                    || edge.requires != null && !edge.requires.equals("GROUND") && !edge.requires.equals("AIR")
                    || edge.input == null
                    || !definitions.containsKey(edge.target)
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
    }

    public Definition get(String id) {
        Definition definition = definitions.get(id);
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
        return Collections.unmodifiableMap(definitions);
    }
}
