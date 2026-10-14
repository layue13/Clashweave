package com.layue13.clashweave.client.vfx;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Effects and their bindings, read once. A bad effect is skipped and reported, the rest still load, and a missing file
 * leaves the library empty so the game runs with vanilla particles. Effects are referred to by index afterwards.
 */
public final class EffectLibrary {

    public static final String EFFECTS = "/assets/clashweave/vfx/effects.json";
    public static final String BINDINGS = "/assets/clashweave/vfx/bindings.json";

    private final List<EffectDef> effects = new ArrayList<>();
    private final Map<String, Integer> handles = new HashMap<>();
    private final Map<String, Integer> byEvent = new HashMap<>();
    private final List<String> errors = new ArrayList<>();
    private EffectDef.Trail trail;

    public static EffectLibrary load() {
        EffectLibrary library = new EffectLibrary();
        try (InputStream effects = EffectLibrary.class.getResourceAsStream(EFFECTS)) {
            if (effects == null) {
                library.errors.add("missing " + EFFECTS);
                return library;
            }
            library.readEffects(new InputStreamReader(effects, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            library.errors.add(EFFECTS + ": " + e);
            return library;
        }
        try (InputStream bindings = EffectLibrary.class.getResourceAsStream(BINDINGS)) {
            if (bindings != null) library.readBindings(new InputStreamReader(bindings, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            library.errors.add(BINDINGS + ": " + e);
        }
        return library;
    }

    /** Reads an {@code {"effects":[...]}} document. */
    public void readEffects(Reader reader) {
        JsonObject root = new JsonParser().parse(reader)
            .getAsJsonObject();
        Gson gson = new Gson();
        JsonArray array = root.has("effects") ? root.getAsJsonArray("effects") : new JsonArray();
        for (JsonElement element : array) {
            try {
                EffectDef def = gson.fromJson(element, EffectDef.class);
                def.validate();
                if (handles.containsKey(def.id)) throw new IllegalArgumentException(def.id + ": duplicate id");
                if (def.trail != null) {
                    if (trail == null) trail = def.trail;
                    handles.put(def.id, -2);
                    continue;
                }
                handles.put(def.id, effects.size());
                effects.add(def);
            } catch (RuntimeException e) {
                errors.add(String.valueOf(e.getMessage() == null ? e : e.getMessage()));
            }
        }
    }

    /** Reads {@code {"events":[{"kind":"BLOCK","effect":"id"}]}}. */
    public void readBindings(Reader reader) {
        JsonObject root = new JsonParser().parse(reader)
            .getAsJsonObject();
        if (!root.has("events")) return;
        for (JsonElement element : root.getAsJsonArray("events")) {
            JsonObject o = element.getAsJsonObject();
            String kind = o.get("kind")
                .getAsString();
            String effect = o.get("effect")
                .getAsString();
            Integer handle = handles.get(effect);
            if (handle == null || handle < 0) errors.add("binding " + kind + " -> unknown effect " + effect);
            else byEvent.put(kind, handle);
        }
    }

    /** The effect bound to a semantic event kind name, or -1. */
    public int forEvent(String kind) {
        Integer handle = byEvent.get(kind);
        return handle == null ? -1 : handle;
    }

    public int handle(String id) {
        Integer handle = handles.get(id);
        return handle == null || handle < 0 ? -1 : handle;
    }

    public EffectDef get(int handle) {
        return effects.get(handle);
    }

    public int size() {
        return effects.size();
    }

    /** The blade-trail description, or null when the data defines none (no trail is drawn then). */
    public EffectDef.Trail trail() {
        return trail;
    }

    public List<String> errors() {
        return errors;
    }
}
