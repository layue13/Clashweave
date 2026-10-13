package com.layue13.clashweave.content;

/**
 * The connect-time content check. Gameplay content must be identical, because the client predicts and renders from
 * the same timings the server enforces: a client with different gameplay data is refused. A difference in
 * presentation data only changes how that client looks, never what happens, so it is allowed and logged.
 */
public final class Handshake {

    private Handshake() {}

    /** Server-side verdict for the hashes a client presented. A missing or malformed hash never matches. */
    public static ContentManifest.Comparison check(String serverGameplay, String serverPresentation,
        String clientGameplay, String clientPresentation) {
        if (clientGameplay == null || !clientGameplay.equals(serverGameplay))
            return ContentManifest.Comparison.GAMEPLAY_MISMATCH;
        return clientPresentation != null && clientPresentation.equals(serverPresentation)
            ? ContentManifest.Comparison.MATCH
            : ContentManifest.Comparison.PRESENTATION_ONLY_DIFF;
    }
}
