package com.layue13.clashweave.content;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Deliberately broken packs, loaded the way the server loads a configuration pack. Every error has to name the file
 * and the JSON path, and nothing may escape as an unexplained exception. tools/wf1/bad_pack_start.py runs the same
 * mutations against the real dedicated server.
 */
public class BadPackTest {

    private static final String FILE = "config/clashweave/content/wf1test/glaive.json";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static String valid() throws IOException {
        try (InputStream stream = BadPackTest.class.getResourceAsStream("/wf1test/glaive.json")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) >= 0) out.write(buffer, 0, count);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private List<String> errorsOf(UnaryOperator<String> mutate) throws IOException {
        String text = valid();
        String changed = mutate.apply(text);
        assertNotEquals("the mutation must change the pack", text, changed);
        File root = temp.newFolder();
        File file = new File(root, "wf1test/glaive.json");
        file.getParentFile()
            .mkdirs();
        Files.write(file.toPath(), changed.getBytes(StandardCharsets.UTF_8));
        ContentValidationException error = assertThrows(
            ContentValidationException.class,
            () -> ContentLoader.load(getClass().getClassLoader(), root));
        for (String line : error.errors()) System.out.println("WF1C_BADPACK " + line);
        return error.errors();
    }

    private static void assertNames(List<String> errors, String path, String reason) {
        for (String line : errors)
            if (line.startsWith(FILE + ": ") && line.contains(path + ": ") && line.contains(reason)) return;
        fail("no error naming " + FILE + " and " + path + " with \"" + reason + "\" in " + errors);
    }

    @Test
    public void missingFieldNamesTheFileAndPath() throws IOException {
        List<String> errors = errorsOf(
            t -> t.replace("\"damage\": 4, \"reach\": 3.6, \"arc\": 40,", "\"reach\": 3.6, \"arc\": 40,"));
        assertNames(errors, "actions[0].damage", "missing field");
    }

    @Test
    public void unknownActionInAStyleNamesTheFileAndPath() throws IOException {
        List<String> errors = errorsOf(
            t -> t.replace(
                "\"actions\": [\"wf1test:thrust\", \"wf1test:thrust_b\", \"wf1test:sweep\"]",
                "\"actions\": [\"wf1test:thrust\", \"wf1test:thrust_b\", \"wf1test:sweep\", \"wf1test:ghost\"]"));
        assertNames(errors, "styles[0].actions[3]", "wf1test:ghost");
    }

    @Test
    public void misspelledEntryFieldNamesTheFileAndPath() throws IOException {
        List<String> errors = errorsOf(t -> t.replace("\"ready\": { \"LIGHT\"", "\"reedy\": { \"LIGHT\""));
        assertNames(errors, "styles[0].entry.reedy", "unknown field");
        assertNames(errors, "styles[0].entry.ready", "missing field");
    }

    @Test
    public void duplicateIdNamesBothDefinitions() throws IOException {
        List<String> errors = errorsOf(t -> t.replace("\"id\": \"wf1test:thrust_b\",", "\"id\": \"wf1test:thrust\","));
        assertNames(errors, "actions[1].id", "duplicate");
    }

    @Test
    public void invalidJsonIsReportedAtTheRoot() throws IOException {
        List<String> errors = errorsOf(t -> t.replace("\"actions\": [", "\"actions\": [,"));
        assertNames(errors, "$", "invalid JSON");
    }

    @Test
    public void unknownEdgeTargetNamesTheEdge() throws IOException {
        List<String> errors = errorsOf(
            t -> t
                .replace("\"target\": \"wf1test:sweep\", \"from\": 6", "\"target\": \"wf1test:nowhere\", \"from\": 6"));
        assertNames(errors, "actions[0].edges[1].target", "wf1test:nowhere");
    }

    @Test
    public void everyErrorFromOneFileIsReportedTogetherNotJustTheFirst() throws IOException {
        List<String> errors = errorsOf(
            t -> t.replace("\"damage\": 4, \"reach\": 3.6, \"arc\": 40,", "\"reach\": 3.6, \"arc\": 40,")
                .replace("\"ready\": { \"LIGHT\"", "\"reedy\": { \"LIGHT\"")
                .replace("\"id\": \"wf1test:thrust_b\",", "\"id\": \"wf1test:thrust\","));
        assertTrue(errors.toString(), errors.size() >= 4);
    }
}
