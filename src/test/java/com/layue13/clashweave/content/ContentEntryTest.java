package com.layue13.clashweave.content;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.Intent;
import com.layue13.clashweave.core.Scheduler;

/** Data-driven idle entry rules: katana equivalence, a ready-only style and validation. */
public class ContentEntryTest {

    private static final String ROOT = "/assets/clashweave/content/katana/";
    private static final String[] FILES = { "weapon.json", "style.json", "actions.json", "resources.json",
        "animations.json" };

    private static JsonObject read(String path) throws Exception {
        try (InputStream stream = ContentEntryTest.class.getResourceAsStream(path)) {
            assertNotNull(path, stream);
            return new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
        }
    }

    private static ContentRegistry katana(JsonObject style) throws Exception {
        List<ContentRegistry.Source> sources = new ArrayList<>();
        for (String file : FILES) {
            JsonObject data = file.equals("style.json") && style != null ? style : read(ROOT + file);
            sources.add(new ContentRegistry.Source(file, new StringReader(data.toString())));
        }
        return ContentRegistry.load(sources);
    }

    private static ContentRegistry spear() throws Exception {
        return ContentRegistry
            .load(new ContentRegistry.Source("spear.json", new StringReader(read("/content/spear.json").toString())));
    }

    private static ActionCatalog legacy() throws Exception {
        try (InputStream stream = ContentEntryTest.class.getResourceAsStream("/assets/clashweave/combat/katana.json")) {
            return new ActionCatalog(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static String bare(String id) {
        return id.startsWith("katana:") ? id.substring("katana:".length()) : id;
    }

    private static String describe(Scheduler s) {
        return (s.current() == null ? "-" : bare(s.current().definition.id)) + "/" + s.sheathed();
    }

    private static List<String> results(Scheduler s) {
        List<String> out = new ArrayList<>();
        for (Scheduler.Result r : s.drainResults()) out.add(r.sequence + ":" + r.status + ":" + bare(r.action));
        return out;
    }

    @Test
    public void katanaEntryRulesMatchTheLegacyHardcodedBehaviour() throws Exception {
        Intent[] inputs = { Intent.LIGHT, Intent.HEAVY, Intent.SHEATHE };
        ContentRegistry registry = katana(null);
        java.util.Set<String> started = new java.util.HashSet<>();
        for (long seed = 1; seed <= 40; seed++) {
            Scheduler old = new Scheduler(legacy());
            Scheduler data = new Scheduler(registry.catalog("katana:default"));
            Random random = new Random(seed);
            int sequence = 0;
            for (long tick = 0; tick < 400; tick++) {
                if (random.nextInt(4) == 0) {
                    Intent input = inputs[random.nextInt(inputs.length)];
                    long origin = old.current() == null ? 0 : old.current().id;
                    old.request(input, ++sequence, tick, origin);
                    data.request(input, sequence, tick, origin);
                }
                if (old.current() != null && random.nextInt(5) == 0) {
                    old.current().confirmedHit = data.current().confirmedHit = true;
                    old.current().confirmedDamageHit = data.current().confirmedDamageHit = random.nextBoolean();
                }
                old.tick(tick, true);
                data.tick(tick, true);
                assertEquals("seed " + seed + " tick " + tick, describe(old), describe(data));
                List<String> expected = results(old);
                assertEquals("seed " + seed + " tick " + tick, expected, results(data));
                for (String result : expected) if (result.contains(":START:")) started.add(result.split(":")[2]);
            }
        }
        // The random walk must actually reach every posture transition, or the equivalence proves nothing.
        assertTrue(
            started.toString(),
            started.containsAll(Arrays.asList("iai", "light_1", "light_2", "heavy", "sheathe")));
    }

    @Test
    public void everyKatanaStateAndIntentResolvesLikeTheOldTable() throws Exception {
        ActionCatalog catalog = katana(null).catalog("katana:default");
        for (Intent input : Intent.values()) {
            String sheathed = catalog.entryTarget(true, input);
            String ready = catalog.entryTarget(false, input);
            assertEquals(input == Intent.LIGHT ? "katana:iai" : null, sheathed);
            assertEquals(
                input == Intent.LIGHT ? "katana:light_1"
                    : input == Intent.HEAVY ? "katana:heavy" : input == Intent.SHEATHE ? "katana:sheathe" : null,
                ready);
        }
        assertTrue(catalog.hasSheathedState());
        assertTrue(catalog.entersSheathed("sheathe"));
        assertTrue(catalog.entersReady("katana:iai"));
        assertFalse(catalog.entersReady("katana:sheathe"));
    }

    @Test
    public void readyOnlyStyleStartsFromDataAndNeverSheathes() throws Exception {
        ActionCatalog catalog = spear().catalog("test:thrust");
        assertFalse(catalog.hasSheathedState());
        Scheduler scheduler = new Scheduler(catalog);
        assertFalse(scheduler.sheathed());
        scheduler.request(Intent.LIGHT, 1, 0);
        scheduler.tick(0, true);
        assertEquals("test:thrust", scheduler.current().definition.id);
        assertEquals("1:START:test:thrust", results(scheduler).get(0));
        scheduler.request(Intent.SHEATHE, 2, 1);
        assertEquals(Arrays.asList("2:ILLEGAL_NODE:"), results(scheduler));
        assertFalse(scheduler.sheathed());
    }

    private static JsonObject style(String entryJson) {
        JsonObject style = read1("/assets/clashweave/content/katana/style.json");
        style.getAsJsonArray("styles")
            .get(0)
            .getAsJsonObject()
            .add("entry", new JsonParser().parse(entryJson));
        return style;
    }

    private static JsonObject read1(String path) {
        try {
            return read(path);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> errors(JsonObject style) throws Exception {
        try {
            katana(style);
            fail("expected validation failure");
            return null;
        } catch (ContentValidationException e) {
            return e.errors();
        }
    }

    private static boolean has(List<String> errors, String... parts) {
        for (String error : errors) {
            boolean all = true;
            for (String part : parts) all &= error.contains(part);
            if (all) return true;
        }
        return false;
    }

    @Test
    public void invalidEntryReportsFileFieldAndReason() throws Exception {
        List<String> unknownAction = errors(
            style(
                "{\"sheathed\":{\"LIGHT\":\"katana:ghost\"},\"ready\":{\"LIGHT\":\"light_1\"},"
                    + "\"entersReady\":[\"light_1\"],\"entersSheathed\":[\"sheathe\"]}"));
        assertTrue(unknownAction.toString(), has(unknownAction, "style.json", "styles[0].entry.sheathed.LIGHT"));

        List<String> unknownIntent = errors(
            style("{\"ready\":{\"JUMP\":\"light_1\"},\"entersReady\":[],\"entersSheathed\":[]}"));
        assertTrue(unknownIntent.toString(), has(unknownIntent, "style.json", "styles[0].entry.ready.JUMP"));

        List<String> missingReady = errors(style("{\"entersReady\":[],\"entersSheathed\":[]}"));
        assertTrue(missingReady.toString(), has(missingReady, "style.json", "styles[0].entry.ready", "missing"));

        List<String> noWayBack = errors(
            style(
                "{\"sheathed\":{\"LIGHT\":\"iai\"},\"ready\":{\"LIGHT\":\"light_1\"},"
                    + "\"entersReady\":[\"iai\"],\"entersSheathed\":[]}"));
        assertTrue(noWayBack.toString(), has(noWayBack, "styles[0].entry.entersSheathed"));

        List<String> typo = errors(style("{\"reedy\":{},\"ready\":{\"LIGHT\":\"light_1\"}}"));
        assertTrue(typo.toString(), has(typo, "styles[0].entry.reedy", "unknown field"));
    }

    @Test
    public void styleWithoutEntryIsRejected() throws Exception {
        JsonObject style = read(ROOT + "style.json");
        style.getAsJsonArray("styles")
            .get(0)
            .getAsJsonObject()
            .remove("entry");
        List<String> errors = errors(style);
        assertTrue(errors.toString(), has(errors, "style.json", "styles[0].entry", "missing"));
    }

    @Test
    public void entryRulesAreGameplayAndChangeTheGameplayHash() throws Exception {
        ContentManifest base = katana(null).manifest();
        JsonObject changed = read(ROOT + "style.json");
        JsonObject entry = changed.getAsJsonArray("styles")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("entry");
        entry.getAsJsonObject("ready")
            .addProperty("HEAVY", "katana:light_1");
        ContentManifest other = katana(changed).manifest();
        assertEquals(ContentManifest.Comparison.GAMEPLAY_MISMATCH, base.compare(other));
        assertEquals(ContentManifest.Comparison.MATCH, base.compare(katana(null).manifest()));
        assertTrue(
            Arrays.asList(Intent.values())
                .contains(Intent.HEAVY));
    }
}
