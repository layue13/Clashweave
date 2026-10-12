package com.layue13.clashweave.content;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Gson 2.2.4 (bundled with Minecraft 1.7.10) lacks public deepCopy, JsonObject.size/keySet, parseReader, JsonArray.set.
 */
final class JsonCompat {

    private JsonCompat() {}

    /**
     * JsonParser is always lenient, so "[,", unquoted names and trailing commas parse silently into nulls. Read the
     * text once, reject anything a strict reader rejects with a message, then build the tree.
     */
    static JsonElement parse(Reader reader) {
        String text;
        try {
            java.io.StringWriter buffer = new java.io.StringWriter();
            char[] chunk = new char[4096];
            int count;
            while ((count = reader.read(chunk)) >= 0) buffer.write(chunk, 0, count);
            text = buffer.toString();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("unreadable: " + e.getMessage(), e);
        }
        try {
            com.google.gson.stream.JsonReader strict = new com.google.gson.stream.JsonReader(
                new java.io.StringReader(text));
            strict.setLenient(false);
            strict.skipValue();
            if (strict.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("text after the top-level value");
        } catch (java.io.IOException | RuntimeException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        return new JsonParser().parse(text);
    }

    static JsonObject copy(JsonObject source) {
        return copy((JsonElement) source).getAsJsonObject();
    }

    static JsonElement copy(JsonElement source) {
        if (source.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : source.getAsJsonObject()
                .entrySet()) result.add(entry.getKey(), copy(entry.getValue()));
            return result;
        }
        if (source.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement element : source.getAsJsonArray()) result.add(copy(element));
            return result;
        }
        // Primitives are immutable in Gson; null is a singleton.
        return source.isJsonNull() ? JsonNull.INSTANCE : source;
    }

    static int size(JsonObject object) {
        return object.entrySet()
            .size();
    }

    static List<String> keys(JsonObject object) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) result.add(entry.getKey());
        return result;
    }
}
