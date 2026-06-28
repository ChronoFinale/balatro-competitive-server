package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.auth.Account;
import com.balatro.engine.auth.AccountStore;
import com.balatro.engine.rank.RankingService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The ranked ladder: a recorded result moves MMR, bumps W/L, persists, and the leaderboard reflects it.
 *  Also: accounts written before ranked existed deserialize to defaults rather than failing. */
class RankingServiceTest {

    @Test
    void recordResultMovesMmrBumpsRecordAndPersists(@TempDir Path dir) {
        AccountStore store = new AccountStore(dir);
        RankingService ranking = new RankingService(store);

        ranking.recordResult("alice", "bob"); // alice beat bob

        Account a = store.get("alice");
        Account b = store.get("bob");
        assertThat(a.wins()).isEqualTo(1);
        assertThat(a.losses()).isZero();
        assertThat(a.gamesPlayed()).isEqualTo(1);
        assertThat(a.mmr()).isGreaterThan(Account.DEFAULT_MMR); // winner climbs
        assertThat(b.losses()).isEqualTo(1);
        assertThat(b.mmr()).isLessThan(Account.DEFAULT_MMR);    // loser drops
        // zero-sum from an even start
        assertThat(a.mmr() - Account.DEFAULT_MMR).isCloseTo(Account.DEFAULT_MMR - b.mmr(),
                org.assertj.core.api.Assertions.offset(0.1));

        // Persisted: a fresh store reloaded from disk sees the same MMR.
        AccountStore reloaded = new AccountStore(dir);
        reloaded.loadAll();
        assertThat(reloaded.get("alice").mmr()).isEqualTo(a.mmr());
        assertThat(reloaded.get("alice").wins()).isEqualTo(1);
    }

    @Test
    void leaderboardRanksByMmrAndOnlyIncludesPlayers(@TempDir Path dir) {
        AccountStore store = new AccountStore(dir);
        RankingService ranking = new RankingService(store);
        store.ensure("idle", "idle"); // never played -> excluded from the board

        ranking.recordResult("alice", "bob");
        ranking.recordResult("alice", "bob"); // alice pulls further ahead

        assertThat(ranking.top(10)).extracting(Account::id).containsExactly("alice", "bob");
    }

    @Test
    void preRankedAccountJsonDeserializesToDefaults(@TempDir Path dir) throws Exception {
        // An account file from before ranked existed — no mmr/volatility/wins/losses/gamesPlayed.
        Files.writeString(dir.resolve("dev_legacy.json"),
                "{\"id\":\"dev:legacy\",\"provider\":\"dev\",\"providerUserId\":\"legacy\","
                        + "\"displayName\":\"Legacy\",\"createdAt\":12345}");

        AccountStore store = new AccountStore(dir);
        store.loadAll();
        Account a = store.get("dev:legacy");
        assertThat(a).isNotNull();
        assertThat(a.mmr()).isEqualTo(Account.DEFAULT_MMR);
        assertThat(a.gamesPlayed()).isZero();
        assertThat(a.displayName()).isEqualTo("Legacy");

        // Re-persisting upgrades the file with the new fields.
        store.save(a);
        assertThat(Files.readString(dir.resolve("dev_legacy.json"))).contains("\"mmr\"");
    }
}
