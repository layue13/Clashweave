package com.layue13.clashweave.forge;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.Logger;

import com.layue13.clashweave.content.ContentLoader;
import com.layue13.clashweave.content.ContentValidationException;
import com.layue13.clashweave.content.EffectiveRules;

/** Startup owner of the loaded registry and the rules built from it. Never touched on a tick or a frame. */
public final class ContentRuntime {

    private static final String LEGACY_KATANA = "/assets/clashweave/combat/katana.json";

    public final ContentLoader.Loaded loaded;
    public final EffectiveRules rules;

    private ContentRuntime(ContentLoader.Loaded loaded, EffectiveRules rules) {
        this.loaded = loaded;
        this.rules = rules;
    }

    /**
     * Loads every pack file and applies the server overrides. A rejected pack stops start-up with one ERROR line per
     * problem ("file: JSON path: reason") and an explicit refusal; there is no partial content.
     */
    public static ContentRuntime load(File configDirectory, CombatConfig config, Logger log) {
        long begin = System.nanoTime();
        ContentLoader.Loaded loaded;
        try {
            loaded = ContentLoader.load(ContentRuntime.class.getClassLoader(), new File(configDirectory, "content"));
        } catch (ContentValidationException e) {
            for (String error : e.errors()) log.error("Clashweave content error: {}", error);
            throw new IllegalStateException(
                "Clashweave refuses to start: content validation failed with " + e.errors()
                    .size()
                    + " error(s); the first is \""
                    + e.errors()
                        .get(0)
                    + "\". All errors are logged above.",
                e);
        } catch (IOException | RuntimeException e) {
            log.error("Clashweave content could not be read: {}", String.valueOf(e));
            throw new IllegalStateException("Clashweave refuses to start: content could not be read: " + e, e);
        }
        EffectiveRules rules;
        try {
            rules = rulesFor(loaded, config.overrides());
        } catch (RuntimeException e) {
            log.error("Clashweave content rejected by server configuration: {}", String.valueOf(e));
            throw new IllegalStateException(
                "Clashweave refuses to start: content conflicts with server configuration: " + e.getMessage(),
                e);
        }
        long total = System.nanoTime() - begin;
        log.info(
            "Clashweave content: {} file(s), {} pack(s), {} weapon(s), {} style(s); registry load+validate {} ms, "
                + "with server overrides {} ms; packGameplay={} presentation={} effectiveGameplay={} overrides={}",
            loaded.files,
            loaded.registry.packs()
                .size(),
            rules.weapons()
                .size(),
            rules.catalogs()
                .size(),
            String.format(java.util.Locale.ROOT, "%.1f", loaded.nanos / 1e6),
            String.format(java.util.Locale.ROOT, "%.1f", total / 1e6),
            rules.packGameplayHash,
            rules.presentationHash,
            rules.effectiveHash,
            rules.overridesCanonical);
        if (config.legacyKatanaJson)
            log.warn("Clashweave: legacyKatanaJson is on; katana:default runs from combat/katana.json, not its pack");
        return new ContentRuntime(loaded, rules);
    }

    /** Builds the rules for a given set of overrides; a client uses this with the overrides its server announced. */
    public static EffectiveRules rulesFor(ContentLoader.Loaded loaded, EffectiveRules.Overrides overrides) {
        Reader legacy = null;
        if (overrides.legacyKatana) {
            InputStream stream = ContentRuntime.class.getResourceAsStream(LEGACY_KATANA);
            if (stream == null) throw new IllegalStateException("missing " + LEGACY_KATANA);
            legacy = new InputStreamReader(stream, StandardCharsets.UTF_8);
        }
        try {
            return EffectiveRules.build(loaded.registry, overrides, legacy);
        } finally {
            if (legacy != null) try {
                legacy.close();
            } catch (IOException ignored) {
                // Nothing to recover from a failed close of a classpath stream.
            }
        }
    }

    public EffectiveRules rulesFor(EffectiveRules.Overrides overrides) {
        return rulesFor(loaded, overrides);
    }
}
