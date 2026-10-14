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
    private final Map<String, List<Binding>> byEvent = new HashMap<>();

    /** What an event kind plays, where, and for which kind of entity ({@code null} entity = any). */
    public static final class Binding {

        public String kind, effect, anchor = "actor", dir = "up", entity, follow;
        /** Share of the entity's height the effect plays at: 0 feet, 1 top. */
        public float height = .5f;
        public int followInterval = 4, followDuration = 0;
        transient int effectHandle = -1, followHandle = -1;

        public int effect() {
            return effectHandle;
        }

        public int followEffect() {
            return followHandle;
        }
    }

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

    /** Reads {@code {"events":[{"kind":"BLOCK","effect":"id", ...}]}}; see {@link Binding} for the other fields. */
    public void readBindings(Reader reader) {
        JsonObject root = new JsonParser().parse(reader)
            .getAsJsonObject();
        if (!root.has("events")) return;
        Gson gson = new Gson();
        for (JsonElement element : root.getAsJsonArray("events")) {
            try {
                Binding binding = gson.fromJson(element, Binding.class);
                if (binding.kind == null || binding.effect == null)
                    throw new IllegalArgumentException("binding needs kind and effect");
                binding.effectHandle = handle(binding.effect);
                if (binding.effectHandle < 0)
                    throw new IllegalArgumentException(binding.kind + " -> unknown effect " + binding.effect);
                if (binding.follow != null) {
                    binding.followHandle = handle(binding.follow);
                    if (binding.followHandle < 0 || binding.followDuration < 1 || binding.followInterval < 1)
                        throw new IllegalArgumentException(binding.kind + " -> bad follow " + binding.follow);
                }
                if (!(binding.height >= 0 && binding.height <= 1.5f)
                    || !("actor".equals(binding.anchor) || "target".equals(binding.anchor))
                    || !("up".equals(binding.dir) || "side".equals(binding.dir)))
                    throw new IllegalArgumentException(
                        binding.kind + ": anchor actor|target, dir up|side, height 0-1.5");
                byEvent.computeIfAbsent(binding.kind, k -> new ArrayList<>())
                    .add(binding);
            } catch (RuntimeException e) {
                errors.add(String.valueOf(e.getMessage() == null ? e : e.getMessage()));
            }
        }
    }

    /**
     * The binding for an event kind and an entity class (simple name): a class-specific one first, else the generic.
     */
    public Binding resolve(String kind, String entityClass) {
        List<Binding> list = byEvent.get(kind);
        if (list == null) return null;
        Binding generic = null;
        for (int i = 0; i < list.size(); i++) {
            Binding binding = list.get(i);
            if (binding.entity == null) {
                if (generic == null) generic = binding;
            } else if (binding.entity.equals(entityClass)) return binding;
        }
        return generic;
    }

    /** The effect bound to a semantic event kind name, or -1. */
    public int forEvent(String kind) {
        Binding binding = resolve(kind, null);
        return binding == null ? -1 : binding.effectHandle;
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
