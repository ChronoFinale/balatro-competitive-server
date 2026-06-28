package com.balatro.engine.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Persistent account storage — one JSON file per account under a directory
 * (default {@code web-assets/accounts}, git-ignored). {@link #upsert} maps a
 * provider identity to a durable {@link Account} (creating it on first login,
 * updating the display name otherwise), so the same person is the same account
 * across sessions and devices. Loaded at startup.
 */
public final class AccountStore {

    private final Path dir;
    private final ObjectMapper json = new ObjectMapper();
    private final ConcurrentHashMap<String, Account> byId = new ConcurrentHashMap<>();

    public AccountStore(Path dir) {
        this.dir = dir;
    }

    public void loadAll() {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(this::loadOne);
        } catch (IOException e) {
            throw new UncheckedIOException("listing " + dir, e);
        }
    }

    private void loadOne(Path file) {
        try {
            Account a = json.readValue(Files.readAllBytes(file), Account.class);
            byId.put(a.id(), a);
        } catch (IOException e) {
            System.err.println("skipping unreadable account " + file + ": " + e.getMessage());
        }
    }

    /** Find or create the account for a provider identity; updates a changed display name (ranked state kept). */
    public synchronized Account upsert(String provider, Identity identity) {
        String id = Account.idFor(provider, identity.providerUserId());
        Account existing = byId.get(id);
        if (existing != null) {
            if (!existing.displayName().equals(identity.displayName())) {
                existing = new Account(id, provider, identity.providerUserId(),
                        identity.displayName(), existing.createdAt(),
                        existing.mmr(), existing.volatility(), existing.wins(),
                        existing.losses(), existing.gamesPlayed());
                persist(existing);
            }
            return existing;
        }
        Account created = new Account(id, provider, identity.providerUserId(),
                identity.displayName(), System.currentTimeMillis());
        persist(created);
        return created;
    }

    /** Find an account by id, or create a minimal one (used by ranked, keyed on the socket's playerId —
     *  found for OAuth accounts, created for dev logins that never went through {@link #upsert}). */
    public synchronized Account ensure(String id, String displayName) {
        Account a = byId.get(id);
        if (a != null) return a;
        Account created = new Account(id, "ranked", id, displayName, System.currentTimeMillis());
        persist(created);
        return created;
    }

    public Account get(String id) {
        return byId.get(id);
    }

    /** Every known account (for the leaderboard). */
    public java.util.Collection<Account> all() {
        return byId.values();
    }

    /** Persist an updated account (e.g. a new MMR after a match). */
    public void save(Account a) {
        persist(a);
    }

    private void persist(Account a) {
        byId.put(a.id(), a);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(a.id().replaceAll("[^A-Za-z0-9_.-]", "_") + ".json"),
                    json.writeValueAsBytes(a));
        } catch (IOException e) {
            throw new UncheckedIOException("saving account " + a.id(), e);
        }
    }
}
