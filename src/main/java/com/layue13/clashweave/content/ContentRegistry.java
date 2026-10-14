package com.layue13.clashweave.content;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.HitEffect;
import com.layue13.clashweave.core.Intent;

/** Load on startup, then reuse indexed definitions and the cached manifest. Owns no IO lifecycle. */
public final class ContentRegistry {

    private static final Gson GSON = new Gson();
    private static final List<String> CATEGORIES = Arrays
        .asList("weapons", "styles", "actions", "animations", "resources", "sas");
    private static final Class<?>[] TYPES = { ContentPack.Weapon.class, ContentPack.Style.class,
        ActionCatalog.Definition.class, ContentPack.Animation.class, ContentPack.Resource.class, ContentPack.Sa.class };
    private final Map<String, Map<String, JsonObject>> entries;
    private final Map<String, ContentPack> packs;
    private final ContentManifest manifest;

    public static final class Source {

        public final String name;
        private final Reader reader;

        public Source(String name, Reader reader) {
            this.name = java.util.Objects.requireNonNull(name);
            this.reader = java.util.Objects.requireNonNull(reader);
        }

        public Source(String name, InputStream stream) {
            this(name, new InputStreamReader(java.util.Objects.requireNonNull(stream), StandardCharsets.UTF_8));
        }
    }

    public static ContentRegistry load(Source... sources) {
        return load(Arrays.asList(sources));
    }

    public static ContentRegistry load(List<Source> sources) {
        Loader loader = new Loader();
        for (Source source : sources) loader.read(source);
        loader.references();
        if (!loader.errors.isEmpty()) throw new ContentValidationException(loader.errors);
        return new ContentRegistry(loader);
    }

    private ContentRegistry(Loader loader) {
        Map<String, Map<String, JsonObject>> frozen = new LinkedHashMap<>();
        JsonObject gameplay = new JsonObject(), presentation = new JsonObject();
        gameplay.addProperty("schemaVersion", ContentPack.SCHEMA_VERSION);
        presentation.addProperty("schemaVersion", ContentPack.SCHEMA_VERSION);
        for (String category : CATEGORIES) {
            Map<String, JsonObject> index = new LinkedHashMap<>();
            JsonObject game = new JsonObject(), visual = new JsonObject();
            for (Map.Entry<String, JsonObject> entry : loader.entries.get(category)
                .entrySet()) {
                JsonObject value = entry.getValue();
                index.put(entry.getKey(), JsonCompat.copy(value));
                if (category.equals("weapons")) {
                    JsonObject rules = JsonCompat.copy(value);
                    JsonObject appearance = new JsonObject();
                    for (String field : Arrays.asList("resource", "animation")) {
                        appearance.add(field, rules.remove(field));
                    }
                    game.add(entry.getKey(), rules);
                    visual.add(entry.getKey(), appearance);
                } else if (category.equals("actions") || category.equals("styles") || category.equals("sas")) {
                    game.add(entry.getKey(), value);
                } else visual.add(entry.getKey(), value);
            }
            frozen.put(category, Collections.unmodifiableMap(index));
            if (JsonCompat.size(game) > 0) gameplay.add(category, game);
            if (JsonCompat.size(visual) > 0) presentation.add(category, visual);
        }
        entries = Collections.unmodifiableMap(frozen);
        Map<String, ContentPack> packIndex = new LinkedHashMap<>();
        for (String namespace : loader.namespaces) {
            Map<String, Map<String, JsonObject>> contents = emptyIndex();
            for (String category : CATEGORIES) for (Map.Entry<String, JsonObject> entry : loader.entries.get(category)
                .entrySet())
                if (entry.getKey()
                    .startsWith(namespace + ":"))
                    contents.get(category)
                        .put(entry.getKey(), entry.getValue());
            packIndex.put(namespace, new ContentPack(namespace, contents));
        }
        packs = Collections.unmodifiableMap(packIndex);
        manifest = new ContentManifest(gameplay, presentation);
    }

    public Map<String, ContentPack> packs() {
        return packs;
    }

    public ContentManifest manifest() {
        return manifest;
    }

    public ContentPack.Weapon weapon(String id) {
        return get("weapons", id, ContentPack.Weapon.class);
    }

    public ContentPack.Sa sa(String id) {
        return get("sas", id, ContentPack.Sa.class);
    }

    public ContentPack.Style style(String id) {
        return get("styles", id, ContentPack.Style.class);
    }

    public ActionCatalog.Definition action(String id) {
        return get("actions", id, ActionCatalog.Definition.class);
    }

    public ContentPack.Animation animation(String id) {
        return get("animations", id, ContentPack.Animation.class);
    }

    public ContentPack.Resource resource(String id) {
        return get("resources", id, ContentPack.Resource.class);
    }

    private <T> T get(String category, String id, Class<T> type) {
        JsonObject json = entries.get(category)
            .get(ContentId.legacy(id));
        return json == null ? null : GSON.fromJson(json, type);
    }

    /** Detached catalog for the existing engine; configuration may mutate it without changing this registry. */
    public ActionCatalog catalog(String styleId) {
        return catalog(styleId, Double.NaN);
    }

    /** A finite serverLimit can only tighten the style's own effect velocity limit, never raise it. */
    public ActionCatalog catalog(String styleId, double serverLimit) {
        ContentPack.Style style = style(styleId);
        if (style == null) throw new IllegalArgumentException("Unknown style: " + styleId);
        JsonObject data = new JsonObject();
        data.addProperty("namespace", style.id.substring(0, style.id.indexOf(':')));
        data.addProperty("bufferTicks", style.bufferTicks);
        data.addProperty("effectVelocityLimit", style.effectVelocityLimit);
        JsonArray actions = new JsonArray();
        for (String id : style.actions) actions.add(
            JsonCompat.copy(
                entries.get("actions")
                    .get(id)));
        data.add("actions", actions);
        JsonObject entry = entries.get("styles")
            .get(style.id)
            .getAsJsonObject("entry");
        data.add("entry", JsonCompat.copy(entry));
        double limit = Double.isNaN(serverLimit) ? Double.NaN : Math.min(serverLimit, style.effectVelocityLimit);
        return new ActionCatalog(new java.io.StringReader(data.toString()), limit);
    }

    private static Map<String, Map<String, JsonObject>> emptyIndex() {
        Map<String, Map<String, JsonObject>> result = new LinkedHashMap<>();
        for (String category : CATEGORIES) result.put(category, new LinkedHashMap<>());
        return result;
    }

    private static final class Record {

        final String source, path, category;
        final JsonObject value;

        Record(String source, String path, String category, JsonObject value) {
            this.source = source;
            this.path = path;
            this.category = category;
            this.value = value;
        }
    }

    private static final class Loader {

        final List<String> errors = new ArrayList<>(), namespaces = new ArrayList<>();
        final Map<String, Map<String, JsonObject>> entries = emptyIndex();
        final List<Record> records = new ArrayList<>();

        void error(String source, String path, String reason) {
            errors.add(source + ": " + path + ": " + reason);
        }

        void read(Source source) {
            JsonElement parsed;
            try {
                parsed = JsonCompat.parse(source.reader);
            } catch (RuntimeException e) {
                error(source.name, "$", "invalid JSON: " + e.getMessage());
                return;
            }
            if (!parsed.isJsonObject()) {
                error(source.name, "$", "expected pack object");
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            for (String field : JsonCompat.keys(root)) if (!CATEGORIES.contains(field) && !field.equals("namespace")
                && !field.equals("schemaVersion")
                && !field.startsWith("_")) error(source.name, field, "unknown field");
            JsonElement version = root.get("schemaVersion");
            if (version == null || !version.isJsonPrimitive()
                || !version.getAsJsonPrimitive()
                    .isNumber()
                || !isVersionOne(version)) error(source.name, "schemaVersion", "expected supported integer version 1");
            String namespace;
            try {
                JsonElement ns = root.get("namespace");
                if (ns == null || !ns.isJsonPrimitive()
                    || !ns.getAsJsonPrimitive()
                        .isString())
                    throw new IllegalArgumentException("expected namespace string");
                namespace = ContentId.namespace(ns.getAsString());
            } catch (RuntimeException e) {
                error(source.name, "namespace", e.getMessage());
                return;
            }
            if (!namespaces.contains(namespace)) namespaces.add(namespace);
            for (int c = 0; c < CATEGORIES.size(); c++) {
                String category = CATEGORIES.get(c);
                JsonElement array = root.get(category);
                if (array == null) continue;
                if (!array.isJsonArray()) {
                    error(source.name, category, "expected array");
                    continue;
                }
                for (int i = 0; i < array.getAsJsonArray()
                    .size(); i++) {
                    String path = category + "[" + i + "]";
                    JsonElement raw = array.getAsJsonArray()
                        .get(i);
                    if (!raw.isJsonObject()) {
                        error(source.name, path, "expected object");
                        continue;
                    }
                    JsonObject value = JsonCompat.copy(raw.getAsJsonObject());
                    required(source.name, path, value, requiredFields(category));
                    int before = errors.size();
                    shape(source.name, path, value, TYPES[c]);
                    if (errors.size() != before) {
                        value = sanitize(value, TYPES[c]).getAsJsonObject();
                    }
                    nestedRequired(source.name, path, raw.getAsJsonObject(), category);
                    qualify(source.name, path + ".id", value, "id", namespace);
                    if (!value.has("id") || value.get("id")
                        .isJsonNull()) continue;
                    String id = value.get("id")
                        .getAsString();
                    if (!id.startsWith(namespace + ":"))
                        error(source.name, path + ".id", "ID must belong to pack namespace");
                    if (category.equals("weapons")) {
                        for (String field : Arrays.asList("style", "resource", "animation"))
                            qualify(source.name, path + "." + field, value, field, namespace);
                        for (String field : Arrays.asList("saSlots", "effectSlots"))
                            qualifyArray(source.name, path, value, field, namespace);
                    } else if (category.equals("sas")) {
                        if (value.has("attach") && value.get("attach")
                            .isJsonArray()) {
                            int attachIndex = 0;
                            for (JsonElement attach : value.getAsJsonArray("attach")) {
                                if (attach.isJsonObject()) qualify(
                                    source.name,
                                    path + ".attach[" + attachIndex + "].action",
                                    attach.getAsJsonObject(),
                                    "action",
                                    namespace);
                                attachIndex++;
                            }
                        }
                    } else if (category.equals("styles")) {
                        qualifyArray(source.name, path, value, "actions", namespace);
                        qualifyEntry(source.name, path, value, namespace);
                    } else if (category.equals("actions") && value.has("edges")
                        && value.get("edges")
                            .isJsonArray()) {
                                int edgeIndex = 0;
                                for (JsonElement edge : value.getAsJsonArray("edges")) {
                                    required(
                                        source.name,
                                        path + ".edges[" + edgeIndex + "]",
                                        edge.getAsJsonObject(),
                                        "input",
                                        "target",
                                        "from",
                                        "until");
                                    qualify(
                                        source.name,
                                        path + ".edges[" + edgeIndex++ + "].target",
                                        edge.getAsJsonObject(),
                                        "target",
                                        namespace);
                                }
                            }
                    Object dto = GSON.fromJson(value, TYPES[c]);
                    value = GSON.toJsonTree(dto)
                        .getAsJsonObject();
                    Record record = new Record(source.name, path, category, value);
                    records.add(record);
                    validate(record, dto);
                    if (entries.get(category)
                        .containsKey(id)) error(source.name, path + ".id", "duplicate ID \"" + id + "\"");
                    else entries.get(category)
                        .put(id, value);
                }
            }
        }

        boolean isVersionOne(JsonElement value) {
            try {
                return new java.math.BigDecimal(value.getAsString()).intValueExact() == 1;
            } catch (RuntimeException e) {
                return false;
            }
        }

        // Keep independent semantic checks running after type errors, without coercing invalid input.
        JsonElement sanitize(JsonElement value, Type type) {
            if (type instanceof ParameterizedType && ((ParameterizedType) type).getRawType() == Map.class) {
                JsonObject result = new JsonObject();
                if (value != null && value.isJsonObject()) {
                    Type[] args = ((ParameterizedType) type).getActualTypeArguments();
                    for (Map.Entry<String, JsonElement> item : value.getAsJsonObject()
                        .entrySet()) {
                        if (primitiveReason(new JsonPrimitive(item.getKey()), (Class<?>) args[0]) != null) continue;
                        JsonElement clean = sanitize(item.getValue(), args[1]);
                        if (!clean.isJsonNull()) result.add(item.getKey(), clean);
                    }
                }
                return result;
            }
            if (type instanceof ParameterizedType) {
                JsonArray result = new JsonArray();
                if (value != null && value.isJsonArray()) {
                    Type item = ((ParameterizedType) type).getActualTypeArguments()[0];
                    for (JsonElement entry : value.getAsJsonArray()) result.add(sanitize(entry, item));
                }
                return result;
            }
            Class<?> cls = (Class<?>) type;
            if (cls == String.class || cls.isPrimitive() || cls.isEnum()) {
                if (value != null && value.isJsonPrimitive()
                    && primitiveReason(value.getAsJsonPrimitive(), cls) == null) return value;
                if (cls == boolean.class) return new JsonPrimitive(false);
                if (cls.isPrimitive()) return new JsonPrimitive(0);
                return com.google.gson.JsonNull.INSTANCE;
            }
            JsonObject result = new JsonObject();
            if (value != null && value.isJsonObject()) {
                for (Field field : cls.getFields()) {
                    JsonElement child = value.getAsJsonObject()
                        .get(field.getName());
                    if (child == null) continue;
                    JsonElement clean = sanitize(child, field.getGenericType());
                    if (!clean.isJsonNull()) result.add(field.getName(), clean);
                }
            }
            return result;
        }

        void nestedRequired(String source, String path, JsonObject raw, String category) {
            if (category.equals("actions") && raw.has("effects")
                && raw.get("effects")
                    .isJsonArray()) {
                int i = 0;
                for (JsonElement effect : raw.getAsJsonArray("effects")) {
                    if (effect.isJsonObject())
                        required(source, path + ".effects[" + i + "]", effect.getAsJsonObject(), "type", "vy");
                    i++;
                }
            } else if (category.equals("styles")) {
                if (raw.has("entry") && raw.get("entry")
                    .isJsonObject()) required(source, path + ".entry", raw.getAsJsonObject("entry"), "ready");
            } else if (category.equals("resources")) {
                if (raw.has("mounts") && raw.get("mounts")
                    .isJsonArray()) {
                    int i = 0;
                    for (JsonElement mount : raw.getAsJsonArray("mounts")) {
                        if (mount.isJsonObject()) required(
                            source,
                            path + ".mounts[" + i + "]",
                            mount.getAsJsonObject(),
                            "slot",
                            "x",
                            "y",
                            "z",
                            "angle");
                        i++;
                    }
                }
                if (raw.has("grip") && raw.get("grip")
                    .isJsonObject())
                    requiredOffset(
                        source,
                        path + ".grip.offset",
                        raw.getAsJsonObject("grip")
                            .get("offset"));
                if (raw.has("handGrips") && raw.get("handGrips")
                    .isJsonObject()) {
                    requiredOffset(
                        source,
                        path + ".handGrips.right",
                        raw.getAsJsonObject("handGrips")
                            .get("right"));
                    requiredOffset(
                        source,
                        path + ".handGrips.left",
                        raw.getAsJsonObject("handGrips")
                            .get("left"));
                }
            }
        }

        void requiredOffset(String source, String path, JsonElement value) {
            if (value != null && value.isJsonObject()) required(source, path, value.getAsJsonObject(), "x", "y", "z");
        }

        String[] requiredFields(String category) {
            if (category.equals("actions")) return new String[] { "id", "startup", "active", "recovery", "damage",
                "reach", "arc", "distance", "speed", "edges" };
            if (category.equals("weapons")) return new String[] { "id", "style", "resource", "animation" };
            if (category.equals("styles")) return new String[] { "id", "actions", "entry" };
            if (category.equals("animations")) return new String[] { "id", "preset" };
            if (category.equals("sas")) return new String[] { "id", "attach" };
            return new String[] { "id", "models", "texture", "grip", "handGrips", "mounts" };
        }

        void required(String source, String path, JsonObject value, String... fields) {
            for (String field : fields) if (!value.has(field) || value.get(field)
                .isJsonNull()) error(source, path + "." + field, "missing field");
        }

        void qualify(String source, String path, JsonObject value, String field, String namespace) {
            try {
                String id = value.has(field) ? value.get(field)
                    .getAsString() : null;
                value.addProperty(field, ContentId.qualify(namespace, id));
            } catch (RuntimeException e) {
                error(source, path, e.getMessage());
            }
        }

        void qualifyEntry(String source, String path, JsonObject style, String namespace) {
            if (!style.has("entry") || !style.get("entry")
                .isJsonObject()) return;
            JsonObject entry = style.getAsJsonObject("entry");
            for (String state : Arrays.asList("sheathed", "ready", "counter", "finisher")) {
                if (!entry.has(state) || !entry.get(state)
                    .isJsonObject()) continue;
                JsonObject map = entry.getAsJsonObject(state);
                for (String intent : JsonCompat.keys(map)) if (map.get(intent)
                    .isJsonPrimitive())
                    qualify(source, path + ".entry." + state + "." + intent, map, intent, namespace);
            }
            for (String field : Arrays.asList("entersReady", "entersSheathed"))
                qualifyArray(source, path + ".entry", entry, field, namespace);
        }

        void qualifyArray(String source, String path, JsonObject value, String field, String namespace) {
            if (!value.has(field) || !value.get(field)
                .isJsonArray()) return;
            JsonArray array = value.getAsJsonArray(field), result = new JsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement item = array.get(i);
                try {
                    result.add(new JsonPrimitive(ContentId.qualify(namespace, item.getAsString())));
                } catch (RuntimeException e) {
                    result.add(item);
                    error(source, path + "." + field + "[" + i + "]", e.getMessage());
                }
            }
            value.add(field, result);
        }

        void shape(String source, String path, JsonElement value, Type type) {
            if (value == null || value.isJsonNull()) {
                error(source, path, "null is not allowed; omit optional fields instead");
                return;
            }
            if (type instanceof ParameterizedType && ((ParameterizedType) type).getRawType() == Map.class) {
                if (!value.isJsonObject()) {
                    error(source, path, "expected object");
                    return;
                }
                Type[] args = ((ParameterizedType) type).getActualTypeArguments();
                for (Map.Entry<String, JsonElement> item : value.getAsJsonObject()
                    .entrySet()) {
                    String reason = primitiveReason(new JsonPrimitive(item.getKey()), (Class<?>) args[0]);
                    if (reason != null) error(source, path + "." + item.getKey(), reason);
                    else shape(source, path + "." + item.getKey(), item.getValue(), args[1]);
                }
                return;
            }
            if (type instanceof ParameterizedType) {
                if (!value.isJsonArray()) {
                    error(source, path, "expected array");
                    return;
                }
                Type itemType = ((ParameterizedType) type).getActualTypeArguments()[0];
                int i = 0;
                for (JsonElement item : value.getAsJsonArray()) shape(source, path + "[" + i++ + "]", item, itemType);
                return;
            }
            Class<?> cls = (Class<?>) type;
            if (cls == String.class || cls.isPrimitive() || cls.isEnum()) {
                if (!value.isJsonPrimitive()) {
                    error(source, path, "expected " + cls.getSimpleName());
                    return;
                }
                String reason = primitiveReason(value.getAsJsonPrimitive(), cls);
                if (reason != null) error(source, path, reason);
                return;
            }
            if (!value.isJsonObject()) {
                error(source, path, "expected object");
                return;
            }
            Map<String, Field> fields = new LinkedHashMap<>();
            for (Field field : cls.getFields()) fields.put(field.getName(), field);
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject()
                .entrySet()) {
                Field field = fields.get(entry.getKey());
                if (field != null) shape(source, path + "." + entry.getKey(), entry.getValue(), field.getGenericType());
                else if (!entry.getKey()
                    .startsWith("_")) error(source, path + "." + entry.getKey(), "unknown field");
            }
        }

        String primitiveReason(JsonPrimitive value, Class<?> type) {
            if (type == String.class) return value.isString() ? null : "expected string";
            if (type == boolean.class) return value.isBoolean() ? null : "expected boolean";
            if (type.isEnum()) {
                for (Object constant : type.getEnumConstants()) if (value.isString() && constant.toString()
                    .equals(value.getAsString())) return null;
                return "unknown " + type.getSimpleName() + " \"" + value.getAsString() + "\"";
            }
            try {
                if (!value.isNumber() || !Double.isFinite(value.getAsDouble())) throw new NumberFormatException();
                if (type == int.class) new java.math.BigDecimal(value.getAsString()).intValueExact();
                if (type == float.class && !Float.isFinite(value.getAsFloat())) throw new NumberFormatException();
                return null;
            } catch (RuntimeException e) {
                return "expected finite " + type.getSimpleName() + " number";
            }
        }

        void range(Record r, String field, double value, double minimum, double maximum) {
            if (!Double.isFinite(value) || value < minimum || value > maximum)
                error(r.source, r.path + "." + field, "out of range [" + minimum + ", " + maximum + "]");
        }

        void validate(Record r, Object dto) {
            if (dto instanceof ActionCatalog.Definition) {
                ActionCatalog.Definition a = (ActionCatalog.Definition) dto;
                range(r, "startup", a.startup, 0, Integer.MAX_VALUE);
                range(r, "active", a.active, 0, Integer.MAX_VALUE);
                range(r, "recovery", a.recovery, 1, Integer.MAX_VALUE);
                if ((long) a.startup + a.active + a.recovery > Integer.MAX_VALUE)
                    error(r.source, r.path + ".recovery", "duration overflows integer ticks");
                range(r, "damage", a.damage, 0, Float.MAX_VALUE);
                range(r, "reach", a.reach, 0, Double.MAX_VALUE);
                range(r, "arc", a.arc, 0, 360);
                range(r, "distance", a.distance, 0, Double.MAX_VALUE);
                range(r, "speed", a.speed, 0, Double.MAX_VALUE);
                range(r, "lift", a.lift, 0, Double.MAX_VALUE);
                range(r, "sweepElevation", a.sweepElevation, -90, 90);
                range(r, "posture", a.posture, -1, 1000);
                range(r, "release", a.release, 0, Integer.MAX_VALUE);
                if (a.release > a.duration()) error(r.source, r.path + ".release", "past the end of the action");
                range(r, "protectFrom", a.protectFrom, 0, Integer.MAX_VALUE);
                range(r, "protectUntil", a.protectUntil, 0, Integer.MAX_VALUE);
                if (a.protectUntil > 0 && a.protectFrom >= a.protectUntil)
                    error(r.source, r.path + ".protectUntil", "must be after protectFrom");
                if (a.protectUntil > a.duration())
                    error(r.source, r.path + ".protectUntil", "past the end of the action");
                boolean launch = false, slam = false;
                for (int i = 0; i < a.effects.size(); i++) {
                    HitEffect effect = a.effects.get(i);
                    if (effect.type == null)
                        error(r.source, r.path + ".effects[" + i + "].type", "missing effect type");
                    if (effect.type == HitEffect.Type.LAUNCH && launch)
                        error(r.source, r.path + ".effects[" + i + "].type", "duplicate LAUNCH");
                    if (effect.type == HitEffect.Type.SLAM && slam)
                        error(r.source, r.path + ".effects[" + i + "].type", "duplicate SLAM");
                    launch |= effect.type == HitEffect.Type.LAUNCH;
                    slam |= effect.type == HitEffect.Type.SLAM;
                    if (launch && slam)
                        error(r.source, r.path + ".effects[" + i + "].type", "LAUNCH and SLAM in one action");
                    range(r, "effects[" + i + "].vy", effect.vy, 0, Double.MAX_VALUE);
                }
                if (a.edges == null) return;
                for (int i = 0; i < a.edges.size(); i++) {
                    ActionCatalog.Edge edge = a.edges.get(i);
                    String path = "edges[" + i + "]";
                    if (edge.requires != null && !edge.requires.equals("GROUND") && !edge.requires.equals("AIR"))
                        error(r.source, r.path + "." + path + ".requires", "expected GROUND or AIR");
                    range(r, path + ".from", edge.from, 0, a.duration());
                    range(r, path + ".until", edge.until, (double) edge.from + 1, a.duration());
                    for (int j = 0; j < i; j++) {
                        ActionCatalog.Edge other = a.edges.get(j);
                        if (edge.input == other.input && edge.from < other.until && other.from < edge.until)
                            error(r.source, r.path + "." + path + ".input", "ambiguous overlapping input window");
                    }
                }
            } else if (dto instanceof ContentPack.Sa) {
                ContentPack.Sa sa = (ContentPack.Sa) dto;
                if (sa.attach.isEmpty() && sa.active == null)
                    error(r.source, r.path + ".attach", "an ability attaches to at least one action or is active");
                if (sa.active != null) {
                    ContentPack.Active a = sa.active;
                    if (!(a.range >= 1 && a.range <= 16)) error(r.source, r.path + ".active.range", "1-16 blocks");
                    if (a.cooldown < 20 || a.cooldown > 6000)
                        error(r.source, r.path + ".active.cooldown", "20-6000 ticks");
                    if (!(a.damage >= 0 && a.damage <= 200)) error(r.source, r.path + ".active.damage", "0-200");
                    if (!(a.posture >= 0 && a.posture <= 1000)) error(r.source, r.path + ".active.posture", "0-1000");
                }
                for (int i = 0; i < sa.attach.size(); i++) {
                    ContentPack.Attach attach = sa.attach.get(i);
                    if (attach.effects.isEmpty())
                        error(r.source, r.path + ".attach[" + i + "].effects", "at least one effect");
                    for (int j = 0; j < attach.effects.size(); j++) {
                        try {
                            attach.effects.get(j)
                                .validate(Double.MAX_VALUE);
                        } catch (IllegalArgumentException e) {
                            error(r.source, r.path + ".attach[" + i + "].effects[" + j + "]", e.getMessage());
                        }
                    }
                }
            } else if (dto instanceof ContentPack.Weapon) {
                ContentPack.Weapon w = (ContentPack.Weapon) dto;
                range(r, "damageMultiplier", w.damageMultiplier, Double.MIN_VALUE, Double.MAX_VALUE);
                range(r, "poiseMultiplier", w.poiseMultiplier, Double.MIN_VALUE, Double.MAX_VALUE);
                range(r, "costMultiplier", w.costMultiplier, Double.MIN_VALUE, Double.MAX_VALUE);
                if (w.saSlots.size() > 4) error(r.source, r.path + ".saSlots", "maximum 4 slots");
                if (w.effectSlots.size() > 4) error(r.source, r.path + ".effectSlots", "maximum 4 slots");
            } else if (dto instanceof ContentPack.Style) {
                ContentPack.Style s = (ContentPack.Style) dto;
                range(r, "bufferTicks", s.bufferTicks, 1, Integer.MAX_VALUE);
                range(r, "effectVelocityLimit", s.effectVelocityLimit, 0, Double.MAX_VALUE);
                if (s.actions != null && s.actions.isEmpty())
                    error(r.source, r.path + ".actions", "empty action graph");
            } else if (dto instanceof ContentPack.Animation) {
                if (((ContentPack.Animation) dto).preset == null || ((ContentPack.Animation) dto).preset.isEmpty())
                    error(r.source, r.path + ".preset", "missing animation preset");
                else if (((ContentPack.Animation) dto).preset.equals("track")) {
                    String source = ((ContentPack.Animation) dto).source;
                    int colon = source == null ? -1 : source.indexOf(':');
                    if (colon <= 0 || colon == source.length() - 1)
                        error(r.source, r.path + ".source", "preset \"track\" needs source as domain:path");
                }
            } else if (dto instanceof ContentPack.Resource) {
                validateResource(r, (ContentPack.Resource) dto);
            }
        }

        void validateResource(Record r, ContentPack.Resource resource) {
            JsonObject value = r.value;
            if (resource.models != null) {
                required(
                    r.source,
                    r.path + ".models",
                    value.getAsJsonObject("models"),
                    "held",
                    "stowed",
                    "stowed_empty");
                for (String field : Arrays.asList("held", "stowed", "stowed_empty")) asset(
                    r,
                    "models." + field,
                    value.getAsJsonObject("models")
                        .get(field));
            }
            asset(r, "texture", value.get("texture"));
            if (resource.grip != null) {
                required(r.source, r.path + ".grip", value.getAsJsonObject("grip"), "offset");
                offset(r, "grip.offset", resource.grip.offset);
            }
            if (resource.handGrips != null) {
                required(r.source, r.path + ".handGrips", value.getAsJsonObject("handGrips"), "right", "left");
                offset(r, "handGrips.right", resource.handGrips.right);
                offset(r, "handGrips.left", resource.handGrips.left);
            }
            if (resource.mounts != null) for (int i = 0; i < resource.mounts.size(); i++) {
                ContentPack.Mount mount = resource.mounts.get(i);
                if (mount.slot == null || !mount.slot.matches("[a-z0-9_]+"))
                    error(r.source, r.path + ".mounts[" + i + "].slot", "expected lowercase mount slot");
                range(r, "mounts[" + i + "].x", mount.x, -Double.MAX_VALUE, Double.MAX_VALUE);
                range(r, "mounts[" + i + "].y", mount.y, -Double.MAX_VALUE, Double.MAX_VALUE);
                range(r, "mounts[" + i + "].z", mount.z, -Double.MAX_VALUE, Double.MAX_VALUE);
                range(r, "mounts[" + i + "].angle", mount.angle, -Double.MAX_VALUE, Double.MAX_VALUE);
            }
        }

        void offset(Record r, String path, ContentPack.Offset offset) {
            if (offset == null) return;
            range(r, path + ".x", offset.x, -Double.MAX_VALUE, Double.MAX_VALUE);
            range(r, path + ".y", offset.y, -Double.MAX_VALUE, Double.MAX_VALUE);
            range(r, path + ".z", offset.z, -Double.MAX_VALUE, Double.MAX_VALUE);
        }

        void asset(Record r, String path, JsonElement value) {
            if (value == null || !value.getAsString()
                .matches("[a-z0-9_]+:[a-z0-9_./-]+"))
                error(r.source, r.path + "." + path, "expected namespaced asset location");
        }

        void reference(Record r, String path, String category, String id) {
            if (id == null || !entries.get(category)
                .containsKey(id))
                error(
                    r.source,
                    r.path + "." + path,
                    "unknown " + category.substring(0, category.length() - 1) + " \"" + id + "\"");
        }

        void entry(Record r, ContentPack.Style s, java.util.Set<String> members) {
            if (s.entry == null) return;
            if (s.entry.ready == null) return; // already reported as a missing field
            for (String state : Arrays.asList("sheathed", "ready", "counter", "finisher")) {
                Map<Intent, String> map = state.equals("ready") ? s.entry.ready
                    : state.equals("counter") ? s.entry.counter
                        : state.equals("finisher") ? s.entry.finisher : s.entry.sheathed;
                if (map != null)
                    for (Map.Entry<Intent, String> e : map.entrySet()) if (!members.contains(e.getValue())) error(
                        r.source,
                        r.path + ".entry." + state + "." + e.getKey(),
                        "action \"" + e.getValue() + "\" is not in the style graph");
            }
            for (String field : Arrays.asList("entersReady", "entersSheathed")) {
                java.util.Set<String> ids = field.equals("entersReady") ? s.entry.entersReady : s.entry.entersSheathed;
                for (String id : ids) if (!members.contains(id))
                    error(r.source, r.path + ".entry." + field, "action \"" + id + "\" is not in the style graph");
            }
            if (s.entry.sheathed != null && s.entry.entersReady.isEmpty())
                error(r.source, r.path + ".entry.entersReady", "a sheathed state needs an action that enters ready");
            if (s.entry.sheathed != null && s.entry.entersSheathed.isEmpty()) error(
                r.source,
                r.path + ".entry.entersSheathed",
                "a sheathed state needs an action that returns to it");
            if (s.entry.ready.isEmpty()) error(r.source, r.path + ".entry.ready", "at least one ready-state input");
        }

        void references() {
            for (Record r : records) {
                if (r.category.equals("weapons")) {
                    ContentPack.Weapon w = GSON.fromJson(r.value, ContentPack.Weapon.class);
                    reference(r, "style", "styles", w.style);
                    reference(r, "resource", "resources", w.resource);
                    reference(r, "animation", "animations", w.animation);
                    for (int i = 0; i < w.saSlots.size(); i++)
                        reference(r, "saSlots[" + i + "]", "sas", w.saSlots.get(i));
                } else if (r.category.equals("sas")) {
                    ContentPack.Sa sa = GSON.fromJson(r.value, ContentPack.Sa.class);
                    for (int i = 0; i < sa.attach.size(); i++)
                        reference(r, "attach[" + i + "].action", "actions", sa.attach.get(i).action);
                } else if (r.category.equals("actions")) {
                    ActionCatalog.Definition a = GSON.fromJson(r.value, ActionCatalog.Definition.class);
                    if (a.edges != null) for (int i = 0; i < a.edges.size(); i++)
                        reference(r, "edges[" + i + "].target", "actions", a.edges.get(i).target);
                } else if (r.category.equals("styles")) {
                    ContentPack.Style s = GSON.fromJson(r.value, ContentPack.Style.class);
                    if (s.actions == null) continue;
                    java.util.Set<String> members = new java.util.HashSet<>(s.actions);
                    for (int i = 0; i < s.actions.size(); i++) {
                        String id = s.actions.get(i);
                        reference(r, "actions[" + i + "]", "actions", id);
                        if (s.actions.indexOf(id) != i)
                            error(r.source, r.path + ".actions[" + i + "]", "duplicate action member");
                        JsonObject value = entries.get("actions")
                            .get(id);
                        if (value == null) continue;
                        ActionCatalog.Definition a = GSON.fromJson(value, ActionCatalog.Definition.class);
                        if (a.lift > s.effectVelocityLimit)
                            error(r.source, r.path + ".actions[" + i + "]", id + ": lift exceeds effectVelocityLimit");
                        for (HitEffect effect : a.effects) if (effect.vy > s.effectVelocityLimit) error(
                            r.source,
                            r.path + ".actions[" + i + "]",
                            id + ": effects.vy exceeds effectVelocityLimit");
                        if (a.edges != null)
                            for (ActionCatalog.Edge edge : a.edges) if (!members.contains(edge.target)) error(
                                r.source,
                                r.path + ".actions[" + i + "]",
                                id + ": edge target outside style graph \"" + edge.target + "\"");
                    }
                    entry(r, s, members);
                }
            }
        }
    }
}
