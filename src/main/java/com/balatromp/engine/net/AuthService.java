package com.balatromp.engine.net;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * Minimal session-token auth (HS256 JWT). Issues a signed token for a player id
 * and verifies it. This is the identity layer competitive/ranked needs.
 *
 * <p>For now {@code /login} issues a token for any username (dev mode). The
 * intended production path is to validate a <b>Steam auth ticket</b> at login —
 * which also proves game ownership — and issue the same JWT. Only the issuance
 * check changes; everything downstream (the verified player id on the socket)
 * stays the same.
 */
public final class AuthService {

    private static final String ISSUER = "balatro-competitive";
    private final Algorithm algorithm;

    /** Random per-process secret (tokens invalidate on restart). */
    public AuthService() {
        this(randomSecret());
    }

    public AuthService(String secret) {
        this.algorithm = Algorithm.HMAC256(secret);
    }

    public String issue(String playerId) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(playerId)
                .withIssuedAt(now)
                .withExpiresAt(now.plus(12, ChronoUnit.HOURS))
                .sign(algorithm);
    }

    /** Returns the player id if the token is valid, else null. */
    public String verify(String token) {
        try {
            return JWT.require(algorithm).withIssuer(ISSUER).build().verify(token).getSubject();
        } catch (JWTVerificationException e) {
            return null;
        }
    }

    private static String randomSecret() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }
}
