package com.layue13.clashweave.client.pose;

import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Validated version-two tracks. Parsing allocates once; a resolved Track owns its packed samples. */
public final class TrackData {

    private final Map<String, Track> tracks;

    private TrackData(Map<String, Track> tracks) {
        this.tracks = Collections.unmodifiableMap(tracks);
    }

    /** Does not close the caller's reader. Invalid data reports its JSON path. */
    public static TrackData parse(Reader reader) {
        JsonElement tree;
        try {
            tree = new JsonParser().parse(reader);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("$: invalid JSON", e);
        }
        JsonObject root = object(tree, "$");
        JsonElement version = field(root, "version", "version");
        number(version, "version");
        // Version 3 adds the torso lean and side bend; version 2 tracks stand upright.
        boolean torso = version.getAsDouble() == 3;
        if (!torso && version.getAsDouble() != 2) throw error("version", "expected 2 or 3");
        JsonObject actions = object(field(root, "actions", "actions"), "actions");
        if (actions.entrySet()
            .isEmpty()) throw error("actions", "expected nonempty object");
        Map<String, Track> tracks = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : actions.entrySet()) {
            String path = "actions." + entry.getKey();
            JsonObject action = object(entry.getValue(), path);
            JsonArray samples = array(field(action, "samples", path + ".samples"), path + ".samples");
            if (samples.size() < 2) throw error(path + ".samples", "expected at least 2 samples");
            float[] times = new float[samples.size()];
            float[] values = new float[samples.size() * Track.STRIDE];
            for (int i = 0; i < times.length; i++) {
                String at = path + ".samples[" + i + "]";
                JsonObject sample = object(samples.get(i), at);
                times[i] = number(field(sample, "t", at + ".t"), at + ".t");
                if (i == 0 ? sample.get("t")
                    .getAsDouble() != 0 : times[i] <= times[i - 1])
                    throw error(at + ".t", "expected zero start and strictly increasing ticks");
                int offset = i * Track.STRIDE;
                JsonObject sword = object(field(sample, "sword", at + ".sword"), at + ".sword");
                vector(sword, "p", at + ".sword", 3, 0, values, offset + Track.SWORD_P);
                vector(sword, "q", at + ".sword", 4, .001, values, offset + Track.SWORD_Q);
                vector(sample, "handR", at, 3, 0, values, offset + Track.HAND_R);
                JsonObject hand = object(field(sample, "handL", at + ".handL"), at + ".handL");
                vector(hand, "p", at + ".handL", 3, 0, values, offset + Track.HAND_L);
                JsonElement mode = field(hand, "mode", at + ".handL.mode");
                if (!mode.isJsonPrimitive() || !mode.getAsJsonPrimitive()
                    .isString()
                    || !(mode.getAsString()
                        .equals("free")
                        || mode.getAsString()
                            .equals("grip")))
                    throw error(at + ".handL.mode", "expected grip or free");
                values[offset + Track.HAND_L_FREE] = mode.getAsString()
                    .equals("free") ? 1 : 0;
                JsonObject baked = object(field(sample, "baked", at + ".baked"), at + ".baked");
                vector(baked, "armR", at + ".baked", 3, .01, values, offset + Track.ARM_R);
                vector(baked, "armL", at + ".baked", 3, .01, values, offset + Track.ARM_L);
                vector(baked, "legR", at + ".baked", 3, .01, values, offset + Track.LEG_R);
                vector(baked, "legL", at + ".baked", 3, .01, values, offset + Track.LEG_L);
                vector(sample, "pelvis", at, 3, 0, values, offset + Track.PELVIS);
                vector(sample, "footR", at, 2, 0, values, offset + Track.FOOT_R);
                vector(sample, "footL", at, 2, 0, values, offset + Track.FOOT_L);
                values[offset + Track.TORSO_YAW] = number(
                    field(sample, "torsoYaw", at + ".torsoYaw"),
                    at + ".torsoYaw");
                if (torso) {
                    values[offset + Track.TORSO_LEAN] = number(
                        field(sample, "torsoLean", at + ".torsoLean"),
                        at + ".torsoLean");
                    values[offset + Track.TORSO_SIDE] = number(
                        field(sample, "torsoSide", at + ".torsoSide"),
                        at + ".torsoSide");
                }
                JsonElement drawn = field(sample, "bladeDrawn", at + ".bladeDrawn");
                if (!drawn.isJsonPrimitive() || !drawn.getAsJsonPrimitive()
                    .isBoolean()) throw error(at + ".bladeDrawn", "expected boolean");
                values[offset + Track.BLADE_DRAWN] = drawn.getAsBoolean() ? 1 : 0;
            }
            tracks.put(entry.getKey(), new Track(times, values));
        }
        return new TrackData(tracks);
    }

    public Track track(String localActionId) {
        return tracks.get(localActionId);
    }

    public boolean has(String localActionId) {
        return tracks.containsKey(localActionId);
    }

    /** Unmodifiable action IDs in input order. */
    public Set<String> actionIds() {
        return tracks.keySet();
    }

    private static IllegalArgumentException error(String path, String message) {
        return new IllegalArgumentException(path + ": " + message);
    }

    private static JsonElement field(JsonObject object, String name, String path) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) throw error(path, "missing field");
        return value;
    }

    private static JsonObject object(JsonElement value, String path) {
        if (!value.isJsonObject()) throw error(path, "expected object");
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonElement value, String path) {
        if (!value.isJsonArray()) throw error(path, "expected array");
        return value.getAsJsonArray();
    }

    private static float number(JsonElement value, String path) {
        if (!value.isJsonPrimitive()) throw error(path, "expected number");
        double d;
        try {
            d = value.getAsDouble();
        } catch (RuntimeException e) {
            throw error(path, "expected number");
        }
        if (!Double.isFinite(d) || !Float.isFinite((float) d)) throw error(path, "not finite");
        if (!value.getAsJsonPrimitive()
            .isNumber()) throw error(path, "expected number");
        return (float) d;
    }

    private static void vector(JsonObject object, String name, String path, int count, double tolerance, float[] out,
        int offset) {
        path += "." + name;
        JsonArray array = array(field(object, name, path), path);
        if (array.size() != count) throw error(path, "expected " + count + " components");
        double length = 0;
        for (int i = 0; i < count; i++) {
            out[offset + i] = number(array.get(i), path + "[" + i + "]");
            length += (double) out[offset + i] * out[offset + i];
        }
        if (tolerance > 0) {
            length = Math.sqrt(length);
            if (Math.abs(length - 1) > tolerance) throw error(path, "expected unit norm");
            for (int i = 0; i < count; i++) out[offset + i] /= length;
        }
    }
}
