package com.balatromp.engine.auth;

/**
 * A persistent player account, keyed by the external provider + the provider's
 * user id, so the same person maps to the same account across logins regardless
 * of which provider they use. {@code id} is {@code provider:providerUserId}
 * (stable + derivable); it's the subject of the session token.
 */
public record Account(String id, String provider, String providerUserId, String displayName, long createdAt) {

    public static String idFor(String provider, String providerUserId) {
        return provider + ":" + providerUserId;
    }
}
