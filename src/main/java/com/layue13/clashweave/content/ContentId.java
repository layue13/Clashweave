package com.layue13.clashweave.content;

/** Namespace identity; unqualified legacy action IDs belong to katana. */
public final class ContentId {

    private ContentId() {}

    public static String namespace(String value) {
        if (value == null || !value.matches("[a-z0-9_]+"))
            throw new IllegalArgumentException("expected lowercase namespace [a-z0-9_]+");
        return value;
    }

    public static String qualify(String namespace, String value) {
        namespace(namespace);
        if (value == null) throw new IllegalArgumentException("missing content ID");
        String result = value.indexOf(':') < 0 ? namespace + ":" + value : value;
        if (!result.matches("[a-z0-9_]+:[a-z0-9_]+(?:/[a-z0-9_]+)*")) throw new IllegalArgumentException(
            "expected namespace:path with lowercase letters, digits, underscore/slash");
        return result;
    }

    public static String legacy(String value) {
        return qualify("katana", value);
    }
}
