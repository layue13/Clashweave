package com.layue13.clashweave.content;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.Intent;
import com.layue13.clashweave.core.Scheduler;

/** Loader, effective rules, handshake verdicts and the second weapon, against the real packs on the class path. */
public class WiringTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static final EffectiveRules.Overrides DEFAULTS = new EffectiveRules.Overrides(true, true, 1.2, false);

    private static String text(String resource) throws IOException {
        try (InputStream stream = WiringTest.class.getResourceAsStream(resource)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) >= 0) out.write(buffer, 0, count);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private File config(String... files) throws IOException {
        File root = temp.newFolder();
        for (int i = 0; i < files.length; i += 2) {
            File target = new File(root, files[i]);
            target.getParentFile()
                .mkdirs();
            Files.write(target.toPath(), files[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        return root;
    }

    private ContentLoader.Loaded loadWithGlaive() throws IOException {
        return ContentLoader
            .load(getClass().getClassLoader(), config("wf1test/glaive.json", text("/wf1test/glaive.json")));
    }

    @Test
    public void loaderFindsEveryKatanaFileOnTheClassPathAndAddsConfigPacks() throws IOException {
        ContentLoader.Loaded base = ContentLoader.load(getClass().getClassLoader(), null);
        assertEquals(5, base.files);
        assertTrue(base.sources.contains("assets/clashweave/content/katana/actions.json"));
        assertNotNull(base.registry.weapon("katana:katana"));
        ContentLoader.Loaded both = loadWithGlaive();
        assertEquals(6, both.files);
        assertTrue(both.sources.contains("config/clashweave/content/wf1test/glaive.json"));
        assertNotNull(both.registry.weapon("wf1test:glaive"));
        // Same katana pack, so its own hash parts cannot change; the registry-wide hash does.
        assertNotEquals(base.registry.manifest().gameplayHash, both.registry.manifest().gameplayHash);
        System.out.println(
            "WF1C_LOAD files=" + both.files
                + " nanos="
                + both.nanos
                + " gameplay="
                + both.registry.manifest().gameplayHash);
    }

    @Test
    public void loaderReadsJarPacksThroughTheJarProtocol() throws Exception {
        File jar = temp.newFile("pack.jar");
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
            for (String directory : new String[] { "assets/", "assets/clashweave/", "assets/clashweave/content/",
                "assets/clashweave/content/jarpack/" }) {
                out.putNextEntry(new JarEntry(directory));
                out.closeEntry();
            }
            out.putNextEntry(new JarEntry("assets/clashweave/content/jarpack/pack.json"));
            out.write(
                ("{\"schemaVersion\":1,\"namespace\":\"jarpack\",\"animations\":[{\"id\":\"jarpack:a\",\"preset\":\"rigid_arc\"}]}")
                    .getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (URLClassLoader loader = new URLClassLoader(
            new URL[] { jar.toURI()
                .toURL() },
            null)) {
            ContentLoader.Loaded loaded = ContentLoader.load(loader, null);
            assertEquals(1, loaded.files);
            assertEquals("assets/clashweave/content/jarpack/pack.json", loaded.sources.get(0));
            assertNotNull(loaded.registry.animation("jarpack:a"));
        }
    }

    @Test
    public void handshakeRefusesGameplayDifferencesAndOnlyNotesPresentationOnes() {
        assertEquals(ContentManifest.Comparison.MATCH, Handshake.check("g", "p", "g", "p"));
        assertEquals(ContentManifest.Comparison.PRESENTATION_ONLY_DIFF, Handshake.check("g", "p", "g", "other"));
        assertEquals(ContentManifest.Comparison.PRESENTATION_ONLY_DIFF, Handshake.check("g", "p", "g", null));
        assertEquals(ContentManifest.Comparison.GAMEPLAY_MISMATCH, Handshake.check("g", "p", "x", "p"));
        assertEquals(ContentManifest.Comparison.GAMEPLAY_MISMATCH, Handshake.check("g", "p", "x", "other"));
        assertEquals(ContentManifest.Comparison.GAMEPLAY_MISMATCH, Handshake.check("g", "p", null, "p"));
    }

    @Test
    public void catalogAcceptsShortStyleIdsLikeEveryOtherLookup() throws IOException {
        ContentRegistry registry = loadWithGlaive().registry;
        // Before WF-1c this threw NullPointerException: the index was probed with the unqualified ID.
        assertEquals(
            registry.catalog("katana:default")
                .toWireJson(),
            registry.catalog("default")
                .toWireJson());
        assertEquals("wf1test", registry.catalog("wf1test:reach").namespace);
    }

    @Test
    public void presentationOnlyEditsKeepGameplayHashAndAreAllowed() throws IOException {
        ContentLoader.Loaded server = loadWithGlaive();
        String edited = text("/wf1test/glaive.json").replace("\"y\": 0.5", "\"y\": 0.6");
        ContentLoader.Loaded client = ContentLoader
            .load(getClass().getClassLoader(), config("wf1test/glaive.json", edited));
        assertEquals(server.registry.manifest().gameplayHash, client.registry.manifest().gameplayHash);
        assertNotEquals(server.registry.manifest().presentationHash, client.registry.manifest().presentationHash);
        assertEquals(
            ContentManifest.Comparison.PRESENTATION_ONLY_DIFF,
            Handshake.check(
                server.registry.manifest().gameplayHash,
                server.registry.manifest().presentationHash,
                client.registry.manifest().gameplayHash,
                client.registry.manifest().presentationHash));
        String changed = text("/wf1test/glaive.json").replace("\"damage\": 7", "\"damage\": 8");
        ContentLoader.Loaded cheat = ContentLoader
            .load(getClass().getClassLoader(), config("wf1test/glaive.json", changed));
        assertEquals(
            ContentManifest.Comparison.GAMEPLAY_MISMATCH,
            Handshake.check(
                server.registry.manifest().gameplayHash,
                server.registry.manifest().presentationHash,
                cheat.registry.manifest().gameplayHash,
                cheat.registry.manifest().presentationHash));
    }

    @Test
    public void effectiveHashIncludesServerOverridesNotJustThePack() throws IOException {
        ContentRegistry registry = loadWithGlaive().registry;
        EffectiveRules base = EffectiveRules.build(registry, DEFAULTS, null);
        EffectiveRules relaxed = EffectiveRules
            .build(registry, new EffectiveRules.Overrides(false, false, 1.2, false), null);
        assertEquals(base.packGameplayHash, relaxed.packGameplayHash);
        assertNotEquals(
            "an override that edits launch edges must change the negotiated hash",
            base.effectiveHash,
            relaxed.effectiveHash);
        assertEquals(64, base.effectiveHash.length());
        assertNotEquals(base.packGameplayHash, base.effectiveHash);
        // The overridden fields really are in the catalog the hash covers.
        ActionCatalog.Edge launch = null;
        for (ActionCatalog.Edge edge : relaxed.catalog("katana:default")
            .byId("katana:light_2").edges) if (edge.target.equals("katana:launcher")) launch = edge;
        assertNotNull(launch);
        assertFalse(launch.hit);
        assertNull(launch.requires);
        launch = null;
        for (ActionCatalog.Edge edge : base.catalog("katana:default")
            .byId("katana:light_2").edges) if (edge.target.equals("katana:launcher")) launch = edge;
        assertTrue(launch.hit);
        assertEquals("GROUND", launch.requires);
        // A client that is told the overrides derives the same hash from its own copy of the pack.
        EffectiveRules derived = EffectiveRules
            .build(loadWithGlaive().registry, EffectiveRules.Overrides.parse(base.overridesCanonical), null);
        assertEquals(base.effectiveHash, derived.effectiveHash);
        assertEquals(
            base.overridesCanonical,
            EffectiveRules.Overrides.parse(base.overridesCanonical)
                .canonical());
    }

    @Test
    public void overridesAreAppliedToCachedCatalogsAndNeverToTheRegistry() throws IOException {
        ContentRegistry registry = loadWithGlaive().registry;
        String before = registry.manifest().gameplayHash;
        EffectiveRules relaxed = EffectiveRules
            .build(registry, new EffectiveRules.Overrides(false, false, 1.2, false), null);
        assertEquals(before, registry.manifest().gameplayHash);
        assertSame(relaxed.catalog("katana:default"), relaxed.catalog("katana:default"));
        for (ActionCatalog.Edge edge : registry.catalog("katana:default")
            .byId("katana:light_2").edges)
            if (edge.target.equals("katana:launcher")) assertEquals("GROUND", edge.requires);
    }

    @Test
    public void tighterServerVelocityLimitRejectsAStyleThatNeedsMore() throws IOException {
        ContentRegistry registry = loadWithGlaive().registry;
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> EffectiveRules.build(registry, new EffectiveRules.Overrides(true, true, 0.3, false), null));
        assertTrue(
            error.getMessage(),
            error.getMessage()
                .contains("katana:default"));
        assertTrue(
            error.getMessage(),
            error.getMessage()
                .contains("effectVelocityLimit=0.3"));
    }

    @Test
    public void namespacedCatalogsCompleteShortIdsWithTheirOwnNamespace() throws IOException {
        EffectiveRules rules = EffectiveRules.build(loadWithGlaive().registry, DEFAULTS, null);
        ActionCatalog katana = rules.catalog("katana:default"), glaive = rules.catalog("wf1test:reach");
        assertEquals("katana", katana.namespace);
        assertEquals("wf1test", glaive.namespace);
        assertEquals("katana:light_1", katana.normalize("light_1"));
        assertEquals("wf1test:thrust", glaive.normalize("thrust"));
        assertSame(glaive.byId("wf1test:thrust"), glaive.get("thrust"));
        assertNull(glaive.byId("thrust"));
        assertEquals("wf1test:thrust", glaive.entryTarget(false, Intent.LIGHT));
        assertEquals("wf1test:sweep", glaive.entryTarget(false, Intent.HEAVY));
        assertNull(glaive.entryTarget(false, Intent.SHEATHE));
        assertEquals("katana:iai", katana.entryTarget(true, Intent.LIGHT));
        assertNull(katana.entryTarget(true, Intent.HEAVY));
        assertFalse(glaive.hasSheathedState());
        assertTrue(katana.hasSheathedState());
    }

    @Test
    public void wireJsonRoundTripsToAnEquivalentCatalog() throws IOException {
        EffectiveRules rules = EffectiveRules.build(loadWithGlaive().registry, DEFAULTS, null);
        for (String style : new String[] { "katana:default", "wf1test:reach" }) {
            ActionCatalog server = rules.catalog(style);
            String json = rules.wireJson(style);
            assertTrue(json.length() < EffectiveRules.MAX_WIRE_CHARS);
            ActionCatalog client = new ActionCatalog(new StringReader(json));
            assertEquals(server.namespace, client.namespace);
            assertEquals(server.bufferTicks, client.bufferTicks);
            assertEquals(server.hasSheathedState(), client.hasSheathedState());
            assertEquals(server.toWireJson(), client.toWireJson());
            for (Intent intent : Intent.values()) {
                assertEquals(server.entryTarget(true, intent), client.entryTarget(true, intent));
                assertEquals(server.entryTarget(false, intent), client.entryTarget(false, intent));
            }
            for (String id : server.all()
                .keySet()) {
                assertEquals(server.entersReady(id), client.entersReady(id));
                assertEquals(server.entersSheathed(id), client.entersSheathed(id));
                assertEquals(
                    server.byId(id)
                        .duration(),
                    client.byId(id)
                        .duration());
            }
        }
        // Deterministic: the same inputs give the same text, on every JVM.
        assertEquals(
            rules.wireJson("katana:default"),
            rules.catalog("katana:default")
                .toWireJson());
    }

    @Test
    public void legacyFallbackRunsFromTheOldJsonAndIsVisibleInTheHash() throws IOException {
        ContentRegistry registry = loadWithGlaive().registry;
        EffectiveRules content = EffectiveRules.build(registry, DEFAULTS, null);
        try (java.io.Reader legacy = new java.io.InputStreamReader(
            getClass().getResourceAsStream("/assets/clashweave/combat/katana.json"),
            StandardCharsets.UTF_8)) {
            EffectiveRules old = EffectiveRules
                .build(registry, new EffectiveRules.Overrides(true, true, 1.2, true), legacy);
            assertNotEquals(content.effectiveHash, old.effectiveHash);
            ActionCatalog catalog = old.catalog("katana:default");
            assertNotNull("short legacy IDs still resolve in the katana namespace", catalog.get("light_1"));
            assertEquals("katana:iai", catalog.entryTarget(true, Intent.LIGHT));
            assertNull(
                old.catalog("wf1test:reach")
                    .byId("light_1"));
        }
        assertThrows(
            IllegalStateException.class,
            () -> EffectiveRules.build(registry, new EffectiveRules.Overrides(true, true, 1.2, true), null));
    }

    @Test
    public void legacyProductionCatalogAndWireUseFullyQualifiedActionIds() throws IOException {
        try (java.io.Reader legacy = new java.io.InputStreamReader(
            getClass().getResourceAsStream("/assets/clashweave/combat/katana.json"),
            StandardCharsets.UTF_8)) {
            EffectiveRules rules = EffectiveRules
                .build(loadWithGlaive().registry, new EffectiveRules.Overrides(true, true, 1.2, true), legacy);
            ActionCatalog catalog = rules.catalog("katana:default");
            assertEquals("katana:light_1", catalog.byId("katana:light_1").id);
            for (ActionCatalog.Definition action : catalog.all()
                .values()) {
                assertTrue(action.id.startsWith("katana:"));
                for (ActionCatalog.Edge edge : action.edges) assertTrue(edge.target.startsWith("katana:"));
            }
            ActionCatalog wire = new ActionCatalog(new StringReader(rules.wireJson("katana:default")));
            assertNotNull(wire.byId(wire.entryTarget(true, Intent.LIGHT)));
            assertEquals(catalog.get("light_1").id, wire.get("light_1").id);
        }
    }

    @Test
    public void overlongActionIdsAreRefusedAtLoad() throws IOException {
        String longId = "x23456789012345678901234567890123";
        String pack = "{\"schemaVersion\":1,\"namespace\":\"longns\",\"styles\":[{\"id\":\"longns:s\",\"actions\":[\"longns:"
            + longId
            + "\"],\"entry\":{\"ready\":{\"LIGHT\":\"longns:"
            + longId
            + "\"},\"entersReady\":[\"longns:"
            + longId
            + "\"],\"entersSheathed\":[]}}],\"actions\":[{\"id\":\"longns:"
            + longId
            + "\",\"startup\":1,\"active\":1,\"recovery\":1,\"damage\":1,\"reach\":1,\"arc\":1,\"distance\":0,\"speed\":0,\"edges\":[]}]}";
        ContentRegistry registry = ContentLoader
            .load(getClass().getClassLoader(), config("longns/pack.json", pack)).registry;
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> EffectiveRules.build(registry, DEFAULTS, null));
        assertTrue(
            error.getMessage(),
            error.getMessage()
                .contains("32 character"));
    }

    @Test
    public void secondWeaponIsReadyOnlyAndRunsThroughTheUnchangedScheduler() throws IOException {
        EffectiveRules rules = EffectiveRules.build(loadWithGlaive().registry, DEFAULTS, null);
        ActionCatalog catalog = rules.catalog("wf1test:reach");
        com.layue13.clashweave.core.WeaponDefinition weapon = rules.weapon("wf1test:glaive");
        assertEquals("wf1test:reach", weapon.style);
        assertEquals("wf1test:glaive", weapon.appearance.skin);
        assertEquals("wf1test:arc", weapon.appearance.animations);
        assertEquals(1.25, weapon.damageMultiplier, 0);
        Scheduler scheduler = new Scheduler(weapon, catalog);
        assertFalse("a style without a sheathed state starts ready", scheduler.sheathed());
        scheduler.request(Intent.SHEATHE, 1, 0);
        scheduler.tick(0);
        assertEquals(
            "ILLEGAL_NODE",
            scheduler.drainResults()
                .get(0).status);
        scheduler.request(Intent.LIGHT, 2, 1);
        scheduler.tick(1);
        assertEquals("wf1test:thrust", scheduler.current().definition.id);
        scheduler.request(Intent.LIGHT, 3, 8);
        scheduler.current().confirmedHit = true;
        scheduler.tick(8);
        assertEquals("wf1test:thrust_b", scheduler.current().definition.id);
        scheduler.request(Intent.HEAVY, 4, 14);
        scheduler.current().confirmedHit = true;
        scheduler.tick(14);
        assertEquals("wf1test:sweep", scheduler.current().definition.id);
        scheduler.tick(14 + scheduler.current().definition.duration());
        assertNull(scheduler.current());
        assertFalse(scheduler.sheathed());
    }

    @Test
    public void switchingBetweenWeaponsFollowsEachStylesPosture() throws IOException {
        EffectiveRules rules = EffectiveRules.build(loadWithGlaive().registry, DEFAULTS, null);
        Scheduler scheduler = new Scheduler(rules.weapon("katana:katana"), rules.catalog("katana:default"));
        assertTrue(scheduler.sheathed());
        scheduler.useWeapon(rules.weapon("wf1test:glaive"), rules.catalog("wf1test:reach"));
        assertFalse(scheduler.sheathed());
        scheduler.useWeapon(rules.weapon("katana:katana"), rules.catalog("katana:default"));
        assertTrue(scheduler.sheathed());
    }
}
