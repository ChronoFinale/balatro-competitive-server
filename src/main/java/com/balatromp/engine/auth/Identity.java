package com.balatromp.engine.auth;

/** The identity an OAuth provider returns after a successful login. */
public record Identity(String providerUserId, String displayName) {}
