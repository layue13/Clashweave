package com.layue13.clashweave.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.HitEffect;

/** Frozen pack data. Mutable core definitions and schema DTOs are returned as detached snapshots. */
public final class ContentPack {

    public static final int SCHEMA_VERSION = 1;
    public final int schemaVersion = SCHEMA_VERSION;
    public final String namespace;
    private final Map<String, Map<String, JsonObject>> data;
    private static final Gson GSON = new Gson();

    public static final class Weapon {

        public String id, style, resource, animation;
        public double damageMultiplier = 1, poiseMultiplier = 1, costMultiplier = 1;
        public List<String> saSlots = new ArrayList<>(), effectSlots = new ArrayList<>();
    }

    /** One piece of a weapon's special ability: extra effects attached to named actions of the base style. */
    public static final class Sa {

        public String id;
        public List<Attach> attach = new ArrayList<>();
        /** Optional key-triggered form: strikes the target in front a few times in a row. */
        public Active active;
    }

    /**
     * A deliberate use of an ability: {@code strikes} cuts, {@code interval} ticks apart, at the nearest target ahead.
     */
    public static final class Active {

        public double range = 6;
        public int cooldown = 200, strikes = 3, interval = 4;
        public float damage = 4, posture = 12;
    }

    public static final class Attach {

        public String action;
        public List<HitEffect> effects = new ArrayList<>();
    }

    public static final class Style {

        public String id;
        public int bufferTicks = 4;
        public double effectVelocityLimit = 1.2;
        public List<String> actions;
        public com.layue13.clashweave.core.EntryRules entry;
    }

    public static final class Animation {

        public String id, preset;
        /** Resource location of the baked track file; required by the "track" preset, ignored by the others. */
        public String source;
        /** Local ID of the action whose first frame is the engaged idle pose; used by the "track" preset. */
        public String stance;
    }

    public static final class Resource {

        public String id, texture;
        public Models models;
        public Grip grip;
        public HandGrips handGrips;
        public List<Mount> mounts;
    }

    public static final class Models {

        public String held, stowed, stowed_empty;
    }

    public static final class Offset {

        public double x, y, z;
    }

    public static final class Grip {

        public Offset offset;
    }

    public static final class HandGrips {

        public Offset right, left;
    }

    public static final class Mount {

        public String slot;
        public double x, y, z, angle;
    }

    ContentPack(String namespace, Map<String, Map<String, JsonObject>> source) {
        this.namespace = namespace;
        Map<String, Map<String, JsonObject>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, JsonObject>> category : source.entrySet()) {
            Map<String, JsonObject> entries = new LinkedHashMap<>();
            for (Map.Entry<String, JsonObject> entry : category.getValue()
                .entrySet()) entries.put(entry.getKey(), JsonCompat.copy(entry.getValue()));
            frozen.put(category.getKey(), Collections.unmodifiableMap(entries));
        }
        data = Collections.unmodifiableMap(frozen);
    }

    private <T> T get(String category, String id, Class<T> type) {
        JsonObject json = data.get(category)
            .get(ContentId.qualify(namespace, id));
        return json == null ? null : GSON.fromJson(json, type);
    }

    private <T> Map<String, T> all(String category, Class<T> type) {
        Map<String, T> result = new LinkedHashMap<>();
        for (String id : data.get(category)
            .keySet()) result.put(id, get(category, id, type));
        return Collections.unmodifiableMap(result);
    }

    public Map<String, Weapon> weapons() {
        return all("weapons", Weapon.class);
    }

    public Map<String, Sa> sas() {
        return all("sas", Sa.class);
    }

    public Map<String, Style> styles() {
        return all("styles", Style.class);
    }

    public Map<String, ActionCatalog.Definition> actions() {
        return all("actions", ActionCatalog.Definition.class);
    }

    public Map<String, Animation> animations() {
        return all("animations", Animation.class);
    }

    public Map<String, Resource> resources() {
        return all("resources", Resource.class);
    }
}
