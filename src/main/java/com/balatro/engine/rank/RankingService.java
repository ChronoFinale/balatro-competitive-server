package com.balatro.engine.rank;

import com.balatro.engine.auth.Account;
import com.balatro.engine.auth.AccountStore;
import java.util.Comparator;
import java.util.List;

/**
 * The ranked ladder: applies {@link Elo} (Elowen) to a finished match and persists the result, and serves
 * the leaderboard. Keys on the socket's {@code playerId} (= an OAuth account id, or a dev-login username);
 * {@link AccountStore#ensure} finds or creates the account either way, so every competitor has a durable
 * ranked record. The only durable store is {@link AccountStore} — no separate ranking store.
 */
public final class RankingService {

    private final AccountStore accounts;

    public RankingService(AccountStore accounts) {
        this.accounts = accounts;
    }

    /** Record a decisive 1v1 result: update both players' MMR/volatility, bump W/L + games, persist both. */
    public synchronized void recordResult(String winnerId, String loserId) {
        if (winnerId == null || loserId == null || winnerId.equals(loserId)) return;
        Account winner = accounts.ensure(winnerId, winnerId);
        Account loser = accounts.ensure(loserId, loserId);
        Elo.Result r = Elo.apply(
                new Elo.Rating(winner.mmr(), winner.volatility()),
                new Elo.Rating(loser.mmr(), loser.volatility()));
        accounts.save(winner.withResult(true, r.winner().mmr(), r.winner().volatility()));
        accounts.save(loser.withResult(false, r.loser().mmr(), r.loser().volatility()));
    }

    /** The top {@code n} ranked accounts (those who've played), strongest MMR first. */
    public List<Account> top(int n) {
        return accounts.all().stream()
                .filter(a -> a.gamesPlayed() > 0)
                .sorted(Comparator.comparingDouble(Account::mmr).reversed()
                        .thenComparing(Account::displayName))
                .limit(Math.max(0, n))
                .toList();
    }
}
