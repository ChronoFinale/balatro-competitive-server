package com.balatro.engine.auth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The set of OAuth providers the server offers. The {@code mock} provider is
 * always available (dev/test); real providers (Google, Discord, …) auto-register
 * when their client id/secret are configured via env vars — so deployments add
 * "sign in with X" just by setting credentials, and players use whatever they
 * want. No real provider is required for the server to run.
 */
public final class ProviderRegistry {

    private final Map<String, OAuthProvider> providers = new LinkedHashMap<>();

    public ProviderRegistry() {
        register(new MockProvider());
        GenericOAuthProvider.fromEnv(GenericOAuthProvider.GOOGLE).ifPresent(this::register);
        GenericOAuthProvider.fromEnv(GenericOAuthProvider.DISCORD).ifPresent(this::register);
    }

    public void register(OAuthProvider p) {
        providers.put(p.name(), p);
    }

    public Optional<OAuthProvider> get(String name) {
        return Optional.ofNullable(providers.get(name));
    }

    public List<String> names() {
        return new ArrayList<>(providers.keySet());
    }
}
