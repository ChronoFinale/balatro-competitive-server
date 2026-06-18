package com.balatro.engine.auth;

/**
 * A pluggable OAuth login provider — Google, Discord, a dev mock, anything. The
 * server stays provider-agnostic: register a provider and players can "use
 * whatever they want" to sign in. Two steps: build the authorize URL the client
 * sends the user to, then exchange the returned code for the user's identity.
 */
public interface OAuthProvider {

    String name();

    /** The provider URL to send the user to (carries our {@code state} + redirect). */
    String authorizeUrl(String state, String redirectUri);

    /** Exchange the authorization {@code code} for the user's identity. */
    Identity exchange(String code, String redirectUri) throws Exception;
}
