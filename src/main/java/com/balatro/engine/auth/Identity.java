package com.balatro.engine.auth;

/** The identity an OAuth provider returns after a successful login. */
public record Identity(String providerUserId, String displayName) {}
