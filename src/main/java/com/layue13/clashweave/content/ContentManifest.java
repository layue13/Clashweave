package com.layue13.clashweave.content;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Computed once at load time. Object fields are sorted; ordered arrays retain declaration order. */
public final class ContentManifest {

    public enum Comparison {
        MATCH,
        GAMEPLAY_MISMATCH,
        PRESENTATION_ONLY_DIFF
    }

    public final String gameplayHash, presentationHash;

    ContentManifest(JsonElement gameplay, JsonElement presentation) {
        gameplayHash = hash(gameplay);
        presentationHash = hash(presentation);
    }

    public Comparison compare(ContentManifest other) {
        if (!gameplayHash.equals(other.gameplayHash)) return Comparison.GAMEPLAY_MISMATCH;
        return presentationHash.equals(other.presentationHash) ? Comparison.MATCH : Comparison.PRESENTATION_ONLY_DIFF;
    }

    static String canonical(JsonElement value) {
        if (value.isJsonNull()) return "null";
        if (value.isJsonObject()) {
            Map<String, JsonElement> sorted = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject()
                .entrySet()) sorted.put(entry.getKey(), entry.getValue());
            StringBuilder result = new StringBuilder("{");
            for (Map.Entry<String, JsonElement> entry : sorted.entrySet()) {
                if (result.length() > 1) result.append(',');
                result.append(new Gson().toJson(entry.getKey()))
                    .append(':')
                    .append(canonical(entry.getValue()));
            }
            return result.append('}')
                .toString();
        }
        if (value.isJsonArray()) {
            StringBuilder result = new StringBuilder("[");
            for (JsonElement element : value.getAsJsonArray()) {
                if (result.length() > 1) result.append(',');
                result.append(canonical(element));
            }
            return result.append(']')
                .toString();
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isNumber()) return new BigDecimal(primitive.getAsString()).stripTrailingZeros()
            .toPlainString();
        return primitive.toString();
    }

    private static String hash(JsonElement value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(canonical(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
