package com.layue13.clashweave.client.pose;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public class TrackDataTest {

    static JsonObject realTree() throws Exception {
        try (Reader reader = new InputStreamReader(
            TrackDataTest.class.getResourceAsStream("/assets/clashweave/tracks/katana.tracks.json"),
            StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader)
                .getAsJsonObject();
        }
    }

    static TrackData realData() throws Exception {
        try (Reader reader = new InputStreamReader(
            TrackDataTest.class.getResourceAsStream("/assets/clashweave/tracks/katana.tracks.json"),
            StandardCharsets.UTF_8)) {
            return TrackData.parse(reader);
        }
    }

    static JsonObject fixture() {
        String sample = "{\"t\":0,\"sword\":{\"p\":[0,0,0],\"q\":[0,0,0,1]},"
            + "\"handR\":[0,0,0],\"handL\":{\"mode\":\"grip\",\"p\":[0,0,0]},"
            + "\"baked\":{\"armR\":[0,0,-1],\"armL\":[0,0,-1],\"legR\":[0,0,-1],\"legL\":[0,0,-1]},"
            + "\"pelvis\":[0,0,0],\"footR\":[0,0],\"footL\":[0,0],\"torsoYaw\":0,\"bladeDrawn\":false}";
        return new JsonParser()
            .parse(
                "{\"version\":2,\"actions\":{\"test\":{\"samples\":[" + sample
                    + ","
                    + sample.replace("\"t\":0", "\"t\":2")
                    + "]}}}")
            .getAsJsonObject();
    }

    static JsonArray samples(JsonObject tree) {
        return tree.getAsJsonObject("actions")
            .getAsJsonObject("test")
            .getAsJsonArray("samples");
    }

    static Track track(JsonObject tree) {
        return TrackData.parse(new StringReader(tree.toString()))
            .track("test");
    }

    static void reject(JsonObject tree, String path) {
        try {
            TrackData.parse(new StringReader(tree.toString()));
            fail("accepted " + path);
        } catch (IllegalArgumentException e) {
            assertTrue(
                e.getMessage(),
                e.getMessage()
                    .contains(path));
        }
    }

    static JsonArray vector(double... values) {
        JsonArray result = new JsonArray();
        for (double value : values) result.add(new JsonPrimitive(value));
        return result;
    }

    @Test
    public void realActionsDurationsAndSampleCounts() throws Exception {
        TrackData data = realData();
        assertEquals(
            6,
            data.actionIds()
                .size());
        for (String id : new String[] { "light_1", "light_2", "light_3", "heavy", "iai", "sheathe" })
            assertTrue(data.has(id));
        for (Map.Entry<String, JsonElement> entry : realTree().getAsJsonObject("actions")
            .entrySet()) {
            JsonObject action = entry.getValue()
                .getAsJsonObject();
            JsonArray samples = action.getAsJsonArray("samples");
            Track track = data.track(entry.getKey());
            assertEquals(
                action.get("ticks")
                    .getAsFloat(),
                track.duration(),
                0);
            assertEquals(
                samples.get(samples.size() - 1)
                    .getAsJsonObject()
                    .get("t")
                    .getAsFloat(),
                track.duration(),
                0);
            assertEquals(samples.size(), (int) (track.duration() / .5f) + 1);
        }
        assertNull(data.track("unknown"));
        assertFalse(data.has("unknown"));
        try {
            data.actionIds()
                .clear();
            fail("mutable action IDs");
        } catch (UnsupportedOperationException expected) {}
    }

    @Test
    public void missingFieldsAreRejectedIndividually() {
        for (String name : new String[] { "t", "sword", "handR", "handL", "baked", "pelvis", "footR", "footL",
            "torsoYaw", "bladeDrawn" }) {
            JsonObject tree = fixture();
            samples(tree).get(0)
                .getAsJsonObject()
                .remove(name);
            reject(tree, "actions.test.samples[0]." + name);
        }
        for (String group : new String[] { "sword", "handL", "baked" }) {
            String[] names = group.equals("sword") ? new String[] { "p", "q" }
                : group.equals("handL") ? new String[] { "p", "mode" }
                    : new String[] { "armR", "armL", "legR", "legL" };
            for (String name : names) {
                JsonObject tree = fixture();
                samples(tree).get(0)
                    .getAsJsonObject()
                    .getAsJsonObject(group)
                    .remove(name);
                reject(tree, "actions.test.samples[0]." + group + "." + name);
            }
        }
        for (String name : new String[] { "version", "actions" }) {
            JsonObject tree = fixture();
            tree.remove(name);
            reject(tree, name);
        }
    }

    @Test
    public void invalidTimesVersionModeAndNormsAreRejected() {
        JsonObject tree = fixture();
        tree.addProperty("version", 3);
        reject(tree, "version");
        tree = fixture();
        tree.addProperty("version", 2.00000001);
        reject(tree, "version");
        for (double t : new double[] { -1, 0 }) {
            tree = fixture();
            samples(tree).get(1)
                .getAsJsonObject()
                .addProperty("t", t);
            reject(tree, "actions.test.samples[1].t");
        }
        tree = fixture();
        samples(tree).get(0)
            .getAsJsonObject()
            .addProperty("t", .1);
        reject(tree, "actions.test.samples[0].t");
        tree = fixture();
        samples(tree).get(0)
            .getAsJsonObject()
            .getAsJsonObject("handL")
            .addProperty("mode", "foo");
        reject(tree, "actions.test.samples[0].handL.mode");
        for (double norm : new double[] { 0, .998, 1.002 }) {
            tree = fixture();
            samples(tree).get(0)
                .getAsJsonObject()
                .getAsJsonObject("sword")
                .add("q", vector(0, 0, 0, norm));
            reject(tree, "actions.test.samples[0].sword.q");
        }
        tree = fixture();
        samples(tree).get(0)
            .getAsJsonObject()
            .getAsJsonObject("baked")
            .add("armR", vector(0, 0, -.98));
        reject(tree, "actions.test.samples[0].baked.armR");
    }

    @Test
    public void nonfiniteNumbersAreRejectedWithPaths() {
        for (double bad : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e100 }) {
            JsonObject tree = fixture();
            samples(tree).get(0)
                .getAsJsonObject()
                .getAsJsonObject("sword")
                .add("q", vector(bad, 0, 0, 1));
            reject(tree, "actions.test.samples[0].sword.q");
            tree = fixture();
            samples(tree).get(1)
                .getAsJsonObject()
                .addProperty("t", bad);
            reject(tree, "actions.test.samples[1].t");
            // Fields the parser does not read (sampleStepTicks, checks, tip) are the generator's notes and are not
            // judged.
        }
    }

    @Test
    public void wrongTypesSizesEmptyActionsAndShortTracksAreRejected() {
        JsonObject tree = fixture();
        tree.add("actions", new JsonObject());
        reject(tree, "actions");
        tree = fixture();
        JsonArray shortTrack = new JsonArray();
        shortTrack.add(samples(tree).get(0));
        tree.getAsJsonObject("actions")
            .getAsJsonObject("test")
            .add("samples", shortTrack);
        reject(tree, "actions.test.samples");
        tree = fixture();
        samples(tree).get(0)
            .getAsJsonObject()
            .addProperty("bladeDrawn", "false");
        reject(tree, "actions.test.samples[0].bladeDrawn");
        tree = fixture();
        samples(tree).get(0)
            .getAsJsonObject()
            .add("handR", vector(0, 0));
        reject(tree, "actions.test.samples[0].handR");
        tree = fixture();
        samples(tree).get(0)
            .getAsJsonObject()
            .addProperty("torsoYaw", "1");
        reject(tree, "actions.test.samples[0].torsoYaw");
    }

    @Test
    public void nearUnitValuesAreNormalizedOnLoad() {
        JsonObject tree = fixture();
        samples(tree).get(0)
            .getAsJsonObject()
            .getAsJsonObject("sword")
            .add("q", vector(0, 0, 0, 1.0005));
        samples(tree).get(0)
            .getAsJsonObject()
            .getAsJsonObject("baked")
            .add("armR", vector(0, 0, -1.005));
        Pose pose = new Pose();
        track(tree).sample(0, pose);
        assertArrayEquals(new float[] { 0, 0, 0, 1 }, pose.swordQ, 0);
        assertArrayEquals(new float[] { 0, 0, -1 }, pose.armR, 0);
    }
}
