package com.layue13.clashweave.content;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Startup/handshake IO: finds every pack file under assets/clashweave/content/ on the class path (jar or directory)
 * and under an optional configuration directory, reading each fully so no stream outlives the call.
 */
public final class ContentLoader {

    public static final String RESOURCE_ROOT = "assets/clashweave/content";

    public static final class Loaded {

        public final ContentRegistry registry;
        public final int files;
        public final long nanos;
        public final List<String> sources;

        Loaded(ContentRegistry registry, List<String> sources, long nanos) {
            this.registry = registry;
            this.sources = Collections.unmodifiableList(sources);
            this.files = sources.size();
            this.nanos = nanos;
        }
    }

    private ContentLoader() {}

    /**
     * Throws ContentValidationException (one "file: JSON path: reason" per error) or IOException for unreadable files.
     */
    public static Loaded load(ClassLoader loader, File configContent) throws IOException {
        long begin = System.nanoTime();
        TreeMap<String, String> texts = new TreeMap<>();
        Enumeration<URL> roots = loader.getResources(RESOURCE_ROOT);
        while (roots.hasMoreElements()) scan(roots.nextElement(), texts);
        scanOwnJar(texts);
        if (configContent != null && configContent.isDirectory())
            scanDirectory(configContent, "config/clashweave/content", texts);
        List<ContentRegistry.Source> sources = new ArrayList<>();
        for (java.util.Map.Entry<String, String> entry : texts.entrySet())
            sources.add(new ContentRegistry.Source(entry.getKey(), new StringReader(entry.getValue())));
        ContentRegistry registry = ContentRegistry.load(sources);
        return new Loaded(registry, new ArrayList<>(texts.keySet()), System.nanoTime() - begin);
    }

    /** A jar built without directory entries is invisible to getResources; read the mod's own jar directly too. */
    private static void scanOwnJar(TreeMap<String, String> texts) throws IOException {
        java.security.CodeSource source = ContentLoader.class.getProtectionDomain()
            .getCodeSource();
        if (source == null || source.getLocation() == null) return;
        File file;
        try {
            file = new File(
                source.getLocation()
                    .toURI());
        } catch (java.net.URISyntaxException | IllegalArgumentException e) {
            return;
        }
        if (!file.isFile()) return;
        try (JarFile jar = new JarFile(file)) {
            scanJar(jar, texts);
        }
    }

    private static void scanJar(JarFile jar, TreeMap<String, String> texts) throws IOException {
        for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!entry.isDirectory() && name.startsWith(RESOURCE_ROOT + "/") && name.endsWith(".json")) {
                try (InputStream stream = jar.getInputStream(entry)) {
                    put(texts, name, read(stream));
                }
            }
        }
    }

    private static void scan(URL root, TreeMap<String, String> texts) throws IOException {
        if (root.getProtocol()
            .equals("jar")) {
            JarURLConnection connection = (JarURLConnection) root.openConnection();
            connection.setUseCaches(false);
            try (JarFile jar = connection.getJarFile()) {
                scanJar(jar, texts);
            }
        } else if (root.getProtocol()
            .equals("file")) {
                try {
                    scanDirectory(new File(root.toURI()), RESOURCE_ROOT, texts);
                } catch (java.net.URISyntaxException e) {
                    throw new IOException(e);
                }
            }
    }

    private static void scanDirectory(File directory, String prefix, TreeMap<String, String> texts) throws IOException {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = prefix + "/" + child.getName();
            if (child.isDirectory()) scanDirectory(child, name, texts);
            else if (child.getName()
                .endsWith(".json"))
                put(texts, name, new String(Files.readAllBytes(child.toPath()), StandardCharsets.UTF_8));
        }
    }

    private static void put(TreeMap<String, String> texts, String name, String text) {
        // The same class-path root can be reported twice; identical text under one name is the same file.
        String previous = texts.put(name, text);
        if (previous != null && !previous.equals(text))
            throw new IllegalStateException("conflicting pack file " + name);
    }

    private static String read(InputStream stream) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = stream.read(chunk)) >= 0) buffer.write(chunk, 0, count);
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
