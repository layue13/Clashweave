package com.layue13.clashweave.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.layue13.clashweave.core.ActionCatalog;

public class ContentRegistryTest {

    private static final String ROOT = "/assets/clashweave/content/katana/";
    private static final String[] FILES = {
        "weapon.json", "style.json", "actions.json", "resources.json", "animations.json" };
    private static final Gson GSON = new Gson();

    private static JsonObject read(String path) throws Exception {
        try (InputStream stream = ContentRegistryTest.class.getResourceAsStream(path)) {
            assertNotNull(path, stream);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static List<ContentRegistry.Source> katanaSources() throws Exception {
        List<ContentRegistry.Source> sources = new ArrayList<>();
        for (String file : FILES) sources.add(source(file, read(ROOT + file)));
        return sources;
    }

    private static ContentRegistry.Source source(String name, JsonObject data) {
        return new ContentRegistry.Source(name, new StringReader(data.toString()));
    }

    private static ContentRegistry katana() throws Exception {
        return ContentRegistry.load(katanaSources());
    }

    private static ContentRegistry spear(JsonObject data) {
        return ContentRegistry.load(source("spear.json", data));
    }

    private static ContentValidationException invalid(ContentRegistry.Source... sources) {
        try {
            ContentRegistry.load(sources);
            fail("expected aggregated validation failure");
            return null;
        } catch (ContentValidationException e) {
            for (String error : e.errors()) assertTrue(error, error.matches(".+: .+: .+"));
            return e;
        }
    }

    @Test
    public void migratedActionsEqualEveryCoreField() throws Exception {
        ActionCatalog old;
        try (InputStream stream = getClass().getResourceAsStream("/assets/clashweave/combat/katana.json");
            InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            old = new ActionCatalog(reader);
        }
        ContentRegistry registry = katana();
        Map<String, ActionCatalog.Definition> actions = registry.packs().get("katana").actions();
        assertEquals(old.all().size(), actions.size());
        assertEquals(old.bufferTicks, registry.style("katana:default").bufferTicks);
        for (ActionCatalog.Definition expected : old.all().values()) {
            JsonObject normalized = GSON.toJsonTree(expected).getAsJsonObject();
            normalized.addProperty("id", ContentId.legacy(expected.id));
            for (JsonElement edge : normalized.getAsJsonArray("edges")) {
                JsonObject e = edge.getAsJsonObject();
                e.addProperty("target", ContentId.legacy(e.get("target").getAsString()));
            }
            assertEquals(expected.id, normalized, GSON.toJsonTree(registry.action(expected.id)));
        }
        assertEquals(actions.keySet(), registry.catalog("katana:default").all().keySet());
    }

    @Test
    public void idsAndLegacyAliases() throws Exception {
        assertEquals("katana:light_1", ContentId.legacy("light_1"));
        assertEquals("test:path/one_2", ContentId.qualify("test", "path/one_2"));
        for (String id : new String[] { "Test:a", "test:A", "a:b:c", "a:/x", "a:x/", "a:x//y", "a:a-b", "a:a.b", "" }) {
            try {
                ContentId.legacy(id);
                fail(id);
            } catch (IllegalArgumentException expected) {}
        }
        assertEquals(katana().action("light_1").id, katana().action("katana:light_1").id);
        assertNull(katana().weapon("katana:absent"));
    }

    @Test
    public void registersSecondPureDataWeaponAndCrossPackReferences() throws Exception {
        List<ContentRegistry.Source> sources = katanaSources();
        JsonObject data = read("/content/spear.json");
        ContentRegistry second = spear(data);
        assertNotEquals(katana().manifest().gameplayHash, second.manifest().gameplayHash);
        sources.add(source("spear.json", data));
        ContentRegistry both = ContentRegistry.load(sources);
        assertEquals(2, both.packs().size());
        assertEquals("test:thrust", both.weapon("test:spear").style);
        assertEquals(4, both.action("test:thrust").reach, 0);
        assertNotEquals(both.resource("katana:default").models.held, both.resource("test:spear").models.held);
        data.getAsJsonArray("weapons").get(0).getAsJsonObject().addProperty("style", "katana:default");
        sources = katanaSources();
        sources.add(source("spear.json", data));
        assertEquals("katana:default", ContentRegistry.load(sources).weapon("test:spear").style);
    }

    @Test
    public void inputStreamSourceSupportsUtf8AndDoesNotCloseCallerStream() throws Exception {
        final boolean[] closed = { false };
        InputStream input = new java.io.ByteArrayInputStream(read("/content/spear.json").toString()
            .getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        assertNotNull(ContentRegistry.load(new ContentRegistry.Source("测试枪.json", input)).weapon("test:spear"));
        assertFalse(closed[0]);
        input.close();
    }

    @Test
    public void aggregatesAllKindsOfReferencesAndDuplicateIds() throws Exception {
        JsonObject data = read("/content/spear.json");
        JsonObject weapon = data.getAsJsonArray("weapons").get(0).getAsJsonObject();
        weapon.addProperty("style", "test:missing_style");
        weapon.addProperty("resource", "test:missing_resource");
        weapon.addProperty("animation", "test:missing_animation");
        data.getAsJsonArray("styles").get(0).getAsJsonObject().getAsJsonArray("actions").add("test:missing_action");
        JsonObject edge = JsonParser.parseString("{\"input\":\"LIGHT\",\"target\":\"test:foo\",\"from\":1,\"until\":2}")
            .getAsJsonObject();
        data.getAsJsonArray("actions").get(0).getAsJsonObject().getAsJsonArray("edges").add(edge);
        data.getAsJsonArray("weapons").add(weapon.deepCopy());
        ContentValidationException error = invalid(source("spear.json", data));
        assertTrue(error.getMessage(), error.errors().size() >= 6);
        assertTrue(error.getMessage().contains("spear.json: actions[0].edges[0].target: unknown action \"test:foo\""));
        assertTrue(error.getMessage().contains("weapons[1].id: duplicate ID"));
        assertTrue(error.getMessage().contains("styles[0].actions[1]: unknown action"));
    }

    @Test
    public void detectsConflictAcrossFilesAndUnsupportedVersion() throws Exception {
        JsonObject data = read("/content/spear.json");
        ContentValidationException error = invalid(source("a.json", data), source("b.json", data));
        assertEquals(5, error.errors().size());
        data.addProperty("schemaVersion", 2);
        assertTrue(invalid(source("v2.json", data)).getMessage().contains("v2.json: schemaVersion:"));
    }

    @Test
    public void validatesEffectsTypesAndShapesWithPrecisePaths() throws Exception {
        JsonObject data = read("/content/spear.json");
        JsonObject action = data.getAsJsonArray("actions").get(0).getAsJsonObject();
        action.add("effects", JsonParser.parseString(
            "[{\"type\":\"BOGUS\",\"vy\":0.2},{\"type\":\"OTHER\",\"vy\":-1}]"));
        String message = invalid(source("bad.json", data)).getMessage();
        assertTrue(message.contains("bad.json: actions[0].effects[0].type:"));
        assertTrue(message.contains("bad.json: actions[0].effects[1].type:"));
        action.add("effects", JsonParser.parseString(
            "[{\"type\":\"LAUNCH\",\"vy\":2},{\"type\":\"LAUNCH\",\"vy\":-1}]"));
        action.addProperty("lift", 2);
        message = invalid(source("bad.json", data)).getMessage();
        assertTrue(message.contains("effects[1].type: duplicate LAUNCH"));
        assertTrue(message.contains("effects[1].vy: out of range"));
        assertTrue(message.contains("lift exceeds effectVelocityLimit"));
        assertTrue(message.contains("effects.vy exceeds effectVelocityLimit"));
    }

    @Test
    public void validatesNumbersRequiredFieldsAndUnknownFields() throws Exception {
        JsonObject data = read("/content/spear.json");
        JsonObject action = data.getAsJsonArray("actions").get(0).getAsJsonObject();
        action.addProperty("reach", "NaN");
        action.addProperty("active", 1.5);
        action.addProperty("typo", 0);
        String message = invalid(source("bad.json", data)).getMessage();
        assertTrue(message.contains("actions[0].reach: expected finite"));
        assertTrue(message.contains("actions[0].active: expected finite int"));
        assertTrue(message.contains("actions[0].typo: unknown field"));
        action.remove("typo");
        action.remove("reach");
        action.addProperty("active", 1);
        assertTrue(invalid(source("bad.json", data)).getMessage().contains("actions[0].reach: missing field"));
    }

    @Test
    public void validatesEdgeWindowsConditionsAndStyleClosure() throws Exception {
        JsonObject data = read("/content/spear.json");
        JsonObject action = data.getAsJsonArray("actions").get(0).getAsJsonObject();
        action.add("edges", JsonParser.parseString("[{\"input\":\"LIGHT\",\"target\":\"test:thrust\",\"from\":1,"
            + "\"until\":20,\"requires\":\"FLY\"},"
            + "{\"input\":\"LIGHT\",\"target\":\"test:thrust\",\"from\":2,\"until\":3}]"));
        String message = invalid(source("edges.json", data)).getMessage();
        assertTrue(message.contains("edges[0].requires: expected GROUND or AIR"));
        assertTrue(message.contains("edges[0].until: out of range"));
        assertTrue(message.contains("edges[1].input: ambiguous overlapping input window"));
        JsonObject extra = action.deepCopy();
        extra.addProperty("id", "test:other");
        extra.add("edges", new JsonArray());
        action.add("edges", JsonParser.parseString(
            "[{\"input\":\"LIGHT\",\"target\":\"test:other\",\"from\":1,\"until\":2}]"));
        data.getAsJsonArray("actions").add(extra);
        assertTrue(invalid(source("edges.json", data)).getMessage().contains("edge target outside style graph"));
    }

    @Test
    public void frozenRegistryAndPackReturnDetachedSnapshots() throws Exception {
        ContentRegistry registry = katana();
        String hash = registry.manifest().gameplayHash;
        registry.action("light_1").edges.get(0).target = "broken";
        registry.action("launcher").effects.get(0).vy = 9;
        registry.weapon("katana:katana").damageMultiplier = 9;
        registry.resource("katana:default").mounts.clear();
        registry.packs().get("katana").actions().get("katana:light_1").damage = 9;
        registry.catalog("katana:default").launchPolicy(false, false);
        assertEquals("katana:light_2", registry.action("light_1").edges.get(0).target);
        assertEquals(0.6, registry.action("launcher").effects.get(0).vy, 0);
        assertEquals(1, registry.weapon("katana:katana").damageMultiplier, 0);
        assertEquals(1, registry.resource("katana:default").mounts.size());
        assertEquals(hash, registry.manifest().gameplayHash);
        try {
            registry.packs().clear();
            fail();
        } catch (UnsupportedOperationException expected) {}
    }

    @Test
    public void comparesManifestAndSeparatesPresentation() throws Exception {
        JsonObject data = read("/content/spear.json");
        ContentManifest original = spear(data).manifest();
        assertEquals(ContentManifest.Comparison.MATCH, original.compare(spear(data).manifest()));
        data.getAsJsonArray("resources").get(0).getAsJsonObject().addProperty("texture", "test:textures/changed.png");
        data.getAsJsonArray("animations").get(0).getAsJsonObject().addProperty("preset", "other_visual");
        assertEquals(ContentManifest.Comparison.PRESENTATION_ONLY_DIFF, original.compare(spear(data).manifest()));
        data.getAsJsonArray("weapons").get(0).getAsJsonObject().addProperty("damageMultiplier", 2);
        assertEquals(ContentManifest.Comparison.GAMEPLAY_MISMATCH, original.compare(spear(data).manifest()));
        assertEquals(64, original.gameplayHash.length());
    }

    @Test
    public void hashesEveryActionAndRuleField() throws Exception {
        JsonObject original = read("/content/spear.json");
        String hash = spear(original).manifest().gameplayHash;
        for (String field : new String[] { "startup", "active", "recovery", "damage", "reach", "arc", "distance",
            "speed",
            "lift", "sweepElevation" }) {
            JsonObject changed = original.deepCopy();
            JsonObject action = changed.getAsJsonArray("actions").get(0).getAsJsonObject();
            action.addProperty(field, action.has(field) ? action.get(field).getAsDouble() + 0.1 : 0.1);
            if (field.equals("startup") || field.equals("active") || field.equals("recovery"))
                action.addProperty(field,
                    original.getAsJsonArray("actions").get(0).getAsJsonObject().get(field).getAsInt() + 1);
            assertNotEquals(field, hash, spear(changed).manifest().gameplayHash);
        }
        JsonObject changed = original.deepCopy();
        changed.getAsJsonArray("actions").get(0).getAsJsonObject().add("effects",
            JsonParser.parseString("[{\"type\":\"LAUNCH\",\"vy\":0.5}]"));
        String launchHash = spear(changed).manifest().gameplayHash;
        assertNotEquals(hash, launchHash);
        changed.getAsJsonArray("actions").get(0).getAsJsonObject().getAsJsonArray("effects").get(0)
            .getAsJsonObject().addProperty("vy", 0.6);
        assertNotEquals(launchHash, spear(changed).manifest().gameplayHash);
        for (String field : new String[] { "bufferTicks", "effectVelocityLimit" }) {
            changed = original.deepCopy();
            changed.getAsJsonArray("styles").get(0).getAsJsonObject().addProperty(field, 5);
            assertNotEquals(field, hash, spear(changed).manifest().gameplayHash);
        }
    }

    @Test
    public void hashIgnoresFormattingFileOrderAndEquivalentNumbers() throws Exception {
        List<ContentRegistry.Source> sources = katanaSources();
        ContentManifest original = ContentRegistry.load(sources).manifest();
        sources = katanaSources();
        Collections.reverse(sources);
        assertEquals(ContentManifest.Comparison.MATCH, original.compare(ContentRegistry.load(sources).manifest()));
        JsonObject data = read("/content/spear.json");
        String hash = spear(data).manifest().gameplayHash;
        data.getAsJsonArray("actions").get(0).getAsJsonObject().add("damage", JsonParser.parseString("2.000e0"));
        data.getAsJsonArray("actions").get(0).getAsJsonObject().addProperty("_comment", "ignored editorial comment");
        assertEquals(hash, spear(data).manifest().gameplayHash);
        assertEquals("{\"a\":0,\"b\":2}", ContentManifest.canonical(JsonParser.parseString("{\"b\":2.00,\"a\":-0.0}")));
    }

    @Test
    public void mountAndGripMatchMigrationContract() throws Exception {
        ContentPack.Resource resource = katana().resource("katana:default");
        ContentPack.Mount mount = resource.mounts.get(0);
        assertEquals(0.58, mount.x, 0);
        assertEquals(0.55, mount.y, 0);
        assertEquals(0.18, mount.z, 0);
        assertEquals(45, mount.angle, 0);
        assertEquals(-0.0625, resource.handGrips.right.x, 0);
        assertEquals(0.55, resource.handGrips.right.y, 0);
        assertEquals(0, resource.grip.offset.y, 0);
    }

    @Test
    public void aggregatesShapeAndSemanticErrorsWithinOneAction() throws Exception {
        JsonObject data = read("/content/spear.json");
        JsonObject action = data.getAsJsonArray("actions").get(0).getAsJsonObject();
        action.addProperty("reach", -1);
        action.addProperty("recovery", 0);
        action.add("effects", JsonParser.parseString("[{\"type\":\"UNKNOWN\",\"vy\":-1}]"));
        action.addProperty("active", "broken");
        String message = invalid(source("mixed.json", data)).getMessage();
        assertTrue(message.contains("actions[0].active: expected finite int"));
        assertTrue(message.contains("actions[0].effects[0].type: unknown Type"));
        assertTrue(message.contains("actions[0].effects[0].vy: out of range"));
        assertTrue(message.contains("actions[0].reach: out of range"));
        assertTrue(message.contains("actions[0].recovery: out of range"));
    }

    @Test
    public void validatesResourceFieldsAndMissingEffectVelocity() throws Exception {
        JsonObject data = read("/content/spear.json");
        JsonObject resource = data.getAsJsonArray("resources").get(0).getAsJsonObject();
        resource.getAsJsonObject("models").addProperty("held", "bad asset");
        resource.getAsJsonObject("models").remove("stowed_empty");
        resource.getAsJsonObject("handGrips").getAsJsonObject("left").remove("z");
        resource.getAsJsonArray("mounts").get(0).getAsJsonObject().remove("angle");
        data.getAsJsonArray("actions").get(0).getAsJsonObject().add("effects",
            JsonParser.parseString("[{\"type\":\"LAUNCH\"}]"));
        String message = invalid(source("resource.json", data)).getMessage();
        assertTrue(message.contains("resources[0].models.held: expected namespaced asset location"));
        assertTrue(message.contains("resources[0].models.stowed_empty: missing field"));
        assertTrue(message.contains("resources[0].handGrips.left.z: missing field"));
        assertTrue(message.contains("resources[0].mounts[0].angle: missing field"));
        assertTrue(message.contains("actions[0].effects[0].vy: missing field"));
    }

    @Test
    public void malformedJsonDoesNotPreventOtherSourceValidation() throws Exception {
        JsonObject data = read("/content/spear.json");
        data.getAsJsonArray("weapons").get(0).getAsJsonObject().addProperty("costMultiplier", -1);
        String message = invalid(new ContentRegistry.Source("syntax.json", new StringReader("{broken")),
            source("rules.json", data)).getMessage();
        assertTrue(message.contains("syntax.json: $: invalid JSON"));
        assertTrue(message.contains("rules.json: weapons[0].costMultiplier: out of range"));
    }

    @Test
    public void hashesEveryEdgeFieldAndAllWeaponRules() throws Exception {
        JsonObject data = read("/content/spear.json");
        JsonObject edge = JsonParser.parseString("{\"input\":\"LIGHT\",\"target\":\"test:thrust\","
            + "\"from\":1,\"until\":3}").getAsJsonObject();
        data.getAsJsonArray("actions").get(0).getAsJsonObject().getAsJsonArray("edges").add(edge);
        String hash = spear(data).manifest().gameplayHash;
        for (String field : new String[] { "input", "from", "until", "hit", "damageHit", "requires" }) {
            JsonObject changed = data.deepCopy();
            JsonObject e = changed.getAsJsonArray("actions").get(0).getAsJsonObject().getAsJsonArray("edges")
                .get(0).getAsJsonObject();
            if (field.equals("input")) e.addProperty(field, "HEAVY");
            else if (field.equals("requires")) e.addProperty(field, "GROUND");
            else if (field.equals("hit") || field.equals("damageHit")) e.addProperty(field, true);
            else e.addProperty(field, e.get(field).getAsInt() + 1);
            assertNotEquals(field, hash, spear(changed).manifest().gameplayHash);
        }
        JsonObject targetChange = data.deepCopy();
        JsonObject other = targetChange.getAsJsonArray("actions").get(0).getAsJsonObject().deepCopy();
        other.addProperty("id", "test:other");
        other.add("edges", new JsonArray());
        targetChange.getAsJsonArray("actions").add(other);
        targetChange.getAsJsonArray("styles").get(0).getAsJsonObject().getAsJsonArray("actions").add("test:other");
        String targetHash = spear(targetChange).manifest().gameplayHash;
        targetChange.getAsJsonArray("actions").get(0).getAsJsonObject().getAsJsonArray("edges").get(0)
            .getAsJsonObject().addProperty("target", "test:other");
        assertNotEquals(targetHash, spear(targetChange).manifest().gameplayHash);
        for (String field : new String[] { "poiseMultiplier", "costMultiplier", "saSlots", "effectSlots" }) {
            JsonObject changed = data.deepCopy();
            JsonObject weapon = changed.getAsJsonArray("weapons").get(0).getAsJsonObject();
            if (field.endsWith("Slots")) weapon.add(field, JsonParser.parseString("[\"test:reserved\"]"));
            else weapon.addProperty(field, 2);
            assertNotEquals(field, hash, spear(changed).manifest().gameplayHash);
        }
    }

    @Test
    public void presentationBindingsAndExplicitDefaultsKeepGameplayIdentity() throws Exception {
        JsonObject data = read("/content/spear.json");
        ContentManifest original = spear(data).manifest();
        JsonObject weapon = data.getAsJsonArray("weapons").get(0).getAsJsonObject();
        weapon.addProperty("poiseMultiplier", 1);
        weapon.addProperty("costMultiplier", 1);
        data.getAsJsonArray("styles").get(0).getAsJsonObject().addProperty("bufferTicks", 4);
        assertEquals(ContentManifest.Comparison.MATCH, original.compare(spear(data).manifest()));
        data.getAsJsonArray("resources").get(0).getAsJsonObject().addProperty("id", "test:new_skin");
        data.getAsJsonArray("animations").get(0).getAsJsonObject().addProperty("id", "test:new_animation");
        weapon.addProperty("resource", "test:new_skin");
        weapon.addProperty("animation", "test:new_animation");
        assertEquals(ContentManifest.Comparison.PRESENTATION_ONLY_DIFF, original.compare(spear(data).manifest()));
    }

    @Test
    public void printsKatanaLoadValidationHashTimeWithoutThreshold() throws Exception {
        List<ContentRegistry.Source> sources = katanaSources();
        long start = System.nanoTime();
        ContentRegistry registry = ContentRegistry.load(sources);
        long elapsed = System.nanoTime() - start;
        assertSame(registry.manifest(), registry.manifest());
        System.out.printf(java.util.Locale.ROOT, "katana load+validate+SHA-256: %.3f ms; actions=%d; gameplay=%s%n",
            elapsed / 1000000.0, registry.packs().get("katana").actions().size(), registry.manifest().gameplayHash);
    }
}
