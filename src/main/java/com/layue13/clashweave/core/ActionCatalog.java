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
        public List<Edge> edges;

        public int duration() {
            return startup + active + recovery;
        }
    }

    private static final class Data {

        int bufferTicks;
        List<Definition> actions;
        List<String> deferred;
    }

    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    public final int bufferTicks;

    public ActionCatalog(Reader reader) {
        Data data = new Gson().fromJson(reader, Data.class);
        if (data == null || data.actions == null || data.bufferTicks < 1) {
            throw new IllegalArgumentException("Missing action data");
        }
        bufferTicks = data.bufferTicks;
        for (Definition definition : data.actions) {
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
                if (edge.input == null || !definitions.containsKey(edge.target)
                    || edge.from < 0
                    || edge.until <= edge.from
                    || edge.until > definition.duration()) {
                    throw new IllegalArgumentException("Invalid edge in " + definition.id);
                }
                for (Edge other : definition.edges) {
                    if (other != edge && other.input == edge.input
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

    public Map<String, Definition> all() {
        return Collections.unmodifiableMap(definitions);
    }
}
