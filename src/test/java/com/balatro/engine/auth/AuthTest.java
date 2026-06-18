package com.balatro.engine.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.net.AuthService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Account linkage: a provider identity maps to a durable account, and a reusable
 * session token round-trips that account. The mock provider exercises the flow
 * without external credentials.
 */
class AuthTest {

    @Test
    void mockProviderTurnsACodeIntoAnIdentity() {
        Identity id = new MockProvider().exchange("alice", "/cb");
        assertThat(id.providerUserId()).isEqualTo("alice");
        assertThat(id.displayName()).isEqualTo("alice");
    }

    @Test
    void accountUpsertIsIdempotentAndPersists(@TempDir Path dir) {
        AccountStore store = new AccountStore(dir);
        Account a1 = store.upsert("mock", new Identity("u123", "Alice"));
        Account a2 = store.upsert("mock", new Identity("u123", "Alice")); // same person logs in again
        assertThat(a2.id()).isEqualTo(a1.id());
        assertThat(a1.id()).isEqualTo("mock:u123");

        AccountStore reopened = new AccountStore(dir);
        reopened.loadAll();
        assertThat(reopened.get(a1.id())).isNotNull();
        assertThat(reopened.get(a1.id()).displayName()).isEqualTo("Alice");
    }

    @Test
    void differentProvidersAreDifferentAccounts(@TempDir Path dir) {
        AccountStore store = new AccountStore(dir);
        Account google = store.upsert("google", new Identity("123", "Bob"));
        Account discord = store.upsert("discord", new Identity("123", "Bob"));
        assertThat(google.id()).isNotEqualTo(discord.id());
    }

    @Test
    void sessionTokenRoundTripsTheAccount() {
        AuthService auth = new AuthService("test-secret");
        String token = auth.issueForAccount("mock:u123", "Alice");
        assertThat(auth.verify(token)).isEqualTo("mock:u123"); // reused token -> same account
        assertThat(auth.displayName(token)).isEqualTo("Alice");
        assertThat(auth.verify("garbage")).isNull();
    }
}
