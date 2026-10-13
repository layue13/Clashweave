package com.layue13.clashweave.content;

import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.AppearanceState;
import com.layue13.clashweave.core.WeaponDefinition;

/**
 * Registry content with server configuration applied, built once at startup (and once more by a client that is told
 * the server's overrides). Everything a tick or a frame needs is a prebuilt map; nothing here parses or concatenates
 * after construction. The effective hash covers the post-override rules, not the raw pack.
 */
public final class EffectiveRules {

    public static final String LEGACY_STYLE = "katana:default";
    /** Wire budget of one definitions payload (StateMessage rejects 24000 characters). */
    public static final int MAX_WIRE_CHARS = 20000;
    /** StateMessage and SemanticMessage reject longer action IDs. */
    public static final int MAX_ACTION_ID = 32;
    public static final String DEFAULT_PRESENTATION_EFFECTS = "clashweave:default";

    /** The server settings that change gameplay data. Canonical text is what both sides hash and exchange. */
    public static final class Overrides {

        public final boolean launchRequiresHit, launchRequiresGround, legacyKatana;
        public final double effectVelocityLimit;

        public Overrides(boolean launchRequiresHit, boolean launchRequiresGround, double effectVelocityLimit,
            boolean legacyKatana) {
            if (!Double.isFinite(effectVelocityLimit) || effectVelocityLimit < 0)
                throw new IllegalArgumentException("effectVelocityLimit");
            this.launchRequiresHit = launchRequiresHit;
            this.launchRequiresGround = launchRequiresGround;
            this.effectVelocityLimit = effectVelocityLimit;
            this.legacyKatana = legacyKatana;
        }

        public String canonical() {
            return "{\"effectVelocityLimit\":" + number(effectVelocityLimit)
                + ",\"launchRequiresGround\":"
                + launchRequiresGround
                + ",\"launchRequiresHit\":"
                + launchRequiresHit
                + ",\"legacyKatana\":"
                + legacyKatana
                + "}";
        }

        public static Overrides parse(String canonical) {
            JsonObject json = new JsonParser().parse(canonical)
                .getAsJsonObject();
            return new Overrides(
                json.get("launchRequiresHit")
                    .getAsBoolean(),
                json.get("launchRequiresGround")
                    .getAsBoolean(),
                json.get("effectVelocityLimit")
                    .getAsDouble(),
                json.get("legacyKatana")
                    .getAsBoolean());
        }

        private static String number(double value) {
            return new BigDecimal(Double.toString(value)).stripTrailingZeros()
                .toPlainString();
        }
    }

    public final String packGameplayHash, presentationHash, effectiveHash, overridesCanonical;
    private final Map<String, ActionCatalog> catalogs;
    private final Map<String, String> wire;
    private final Map<String, WeaponDefinition> weapons;
    private final Map<String, ContentPack.Resource> resources;
    private final Map<String, ContentPack.Animation> animations;

    private EffectiveRules(ContentRegistry registry, Overrides overrides, Reader legacyKatana) {
        packGameplayHash = registry.manifest().gameplayHash;
        presentationHash = registry.manifest().presentationHash;
        overridesCanonical = overrides.canonical();
        Map<String, ActionCatalog> catalogIndex = new HashMap<>();
        Map<String, String> wireIndex = new HashMap<>();
        Map<String, WeaponDefinition> weaponIndex = new HashMap<>();
        Map<String, ContentPack.Resource> resourceIndex = new HashMap<>();
        Map<String, ContentPack.Animation> animationIndex = new HashMap<>();
        for (ContentPack pack : registry.packs()
            .values()) {
            for (String style : pack.styles()
                .keySet()) {
                ActionCatalog catalog = catalog(registry, overrides, legacyKatana, style);
                catalog.launchPolicy(overrides.launchRequiresHit, overrides.launchRequiresGround);
                for (String action : catalog.all()
                    .keySet())
                    if (action.length() > MAX_ACTION_ID) throw new IllegalStateException(
                        "action ID " + action
                            + " in style "
                            + style
                            + " exceeds the "
                            + MAX_ACTION_ID
                            + " character wire limit");
                String json = catalog.toWireJson();
                if (json.length() > MAX_WIRE_CHARS) throw new IllegalStateException(
                    "style " + style
                        + " definitions need "
                        + json.length()
                        + " characters, wire limit is "
                        + MAX_WIRE_CHARS);
                catalogIndex.put(style, catalog);
                wireIndex.put(style, json);
            }
            resourceIndex.putAll(pack.resources());
            animationIndex.putAll(pack.animations());
            for (Map.Entry<String, ContentPack.Weapon> entry : pack.weapons()
                .entrySet()) {
                ContentPack.Weapon w = entry.getValue();
                weaponIndex.put(
                    entry.getKey(),
                    new WeaponDefinition(
                        w.style,
                        w.style,
                        w.damageMultiplier,
                        w.poiseMultiplier,
                        w.costMultiplier,
                        w.saSlots,
                        w.effectSlots,
                        new AppearanceState(w.resource, DEFAULT_PRESENTATION_EFFECTS, w.animation)));
            }
        }
        catalogs = Collections.unmodifiableMap(catalogIndex);
        wire = Collections.unmodifiableMap(wireIndex);
        weapons = Collections.unmodifiableMap(weaponIndex);
        resources = Collections.unmodifiableMap(resourceIndex);
        animations = Collections.unmodifiableMap(animationIndex);
        effectiveHash = hash();
    }

    private static ActionCatalog catalog(ContentRegistry registry, Overrides overrides, Reader legacyKatana,
        String style) {
        if (overrides.legacyKatana && style.equals(LEGACY_STYLE)) {
            if (legacyKatana == null) throw new IllegalStateException("legacy katana data unavailable");
            ActionCatalog legacy = new ActionCatalog(legacyKatana, overrides.effectVelocityLimit);
            JsonObject data = new JsonParser().parse(legacy.toWireJson())
                .getAsJsonObject();
            for (com.google.gson.JsonElement value : data.getAsJsonArray("actions")) {
                JsonObject action = value.getAsJsonObject();
                action.addProperty(
                    "id",
                    legacy.normalize(
                        action.get("id")
                            .getAsString()));
                for (com.google.gson.JsonElement reference : action.getAsJsonArray("edges")) {
                    JsonObject edge = reference.getAsJsonObject();
                    edge.addProperty(
                        "target",
                        legacy.normalize(
                            edge.get("target")
                                .getAsString()));
                }
            }
            return new ActionCatalog(new java.io.StringReader(data.toString()), overrides.effectVelocityLimit);
        }
        try {
            return registry.catalog(style, overrides.effectVelocityLimit);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "style " + style
                    + " is incompatible with server effectVelocityLimit="
                    + overrides.effectVelocityLimit
                    + ": "
                    + e.getMessage(),
                e);
        }
    }

    /** legacyKatana supplies the old combat/katana.json only when overrides.legacyKatana is set. */
    public static EffectiveRules build(ContentRegistry registry, Overrides overrides, Reader legacyKatana) {
        return new EffectiveRules(registry, overrides, legacyKatana);
    }

    private String hash() {
        StringBuilder text = new StringBuilder("clashweave-rules-1\n").append(packGameplayHash)
            .append('\n')
            .append(overridesCanonical)
            .append('\n');
        for (Map.Entry<String, String> entry : new TreeMap<>(wire).entrySet()) text.append(entry.getKey())
            .append('\n')
            .append(entry.getValue())
            .append('\n');
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(
                    text.toString()
                        .getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** O(1); null for an unknown style. */
    public ActionCatalog catalog(String styleId) {
        return catalogs.get(styleId);
    }

    /** The deterministic client payload for a style, built once. */
    public String wireJson(String styleId) {
        return wire.get(styleId);
    }

    public WeaponDefinition weapon(String weaponId) {
        return weapons.get(weaponId);
    }

    public Map<String, WeaponDefinition> weapons() {
        return weapons;
    }

    public Map<String, ActionCatalog> catalogs() {
        return catalogs;
    }

    public ContentPack.Resource resource(String id) {
        return resources.get(id);
    }

    public Map<String, ContentPack.Resource> resources() {
        return resources;
    }

    public Map<String, ContentPack.Animation> animations() {
        return animations;
    }
}
