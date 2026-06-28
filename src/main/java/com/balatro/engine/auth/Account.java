package com.balatro.engine.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A persistent player account, keyed by the external provider + the provider's
 * user id, so the same person maps to the same account across logins regardless
 * of which provider they use. {@code id} is {@code provider:providerUserId}
 * (stable + derivable); it's the subject of the session token.
 *
 * <p>Ranked state ({@code mmr}/{@code volatility}/{@code wins}/{@code losses}/
 * {@code gamesPlayed}) lives here too — {@link AccountStore} is the only durable
 * store. Accounts written before ranked existed have none of those fields; the
 * {@link #fromJson} creator defaults them (mmr {@value #DEFAULT_MMR}, rest 0), so
 * old files deserialize cleanly and gain the fields on the next write.
 */
public record Account(String id, String provider, String providerUserId, String displayName, long createdAt,
                      double mmr, int volatility, int wins, int losses, int gamesPlayed) {

    /** Where every account starts on the (Elowen) MMR ladder. */
    public static final double DEFAULT_MMR = 1000.0;

    /** A brand-new account with default ranked state. */
    public Account(String id, String provider, String providerUserId, String displayName, long createdAt) {
        this(id, provider, providerUserId, displayName, createdAt, DEFAULT_MMR, 0, 0, 0, 0);
    }

    /** Tolerant deserializer: ranked fields absent in pre-ranked JSON default in rather than failing. */
    @JsonCreator
    static Account fromJson(
            @JsonProperty("id") String id,
            @JsonProperty("provider") String provider,
            @JsonProperty("providerUserId") String providerUserId,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("createdAt") long createdAt,
            @JsonProperty("mmr") Double mmr,
            @JsonProperty("volatility") Integer volatility,
            @JsonProperty("wins") Integer wins,
            @JsonProperty("losses") Integer losses,
            @JsonProperty("gamesPlayed") Integer gamesPlayed) {
        return new Account(id, provider, providerUserId, displayName, createdAt,
                mmr == null ? DEFAULT_MMR : mmr,
                volatility == null ? 0 : volatility,
                wins == null ? 0 : wins,
                losses == null ? 0 : losses,
                gamesPlayed == null ? 0 : gamesPlayed);
    }

    /** This account after a recorded match: new rating, +1 win or loss, +1 game. (The caller — the ranking
     *  service — computes the rating so {@code auth} stays independent of the rank package.) */
    public Account withResult(boolean won, double newMmr, int newVolatility) {
        return new Account(id, provider, providerUserId, displayName, createdAt,
                newMmr, newVolatility, wins + (won ? 1 : 0), losses + (won ? 0 : 1), gamesPlayed + 1);
    }

    public static String idFor(String provider, String providerUserId) {
        return provider + ":" + providerUserId;
    }
}
