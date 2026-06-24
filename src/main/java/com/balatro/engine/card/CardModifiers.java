package com.balatro.engine.card;

import com.balatro.engine.joker.def.Effect;
import com.balatro.engine.joker.def.Value;
import java.util.List;
import java.util.Map;

/**
 * The scoring contribution of each card enhancement / edition, expressed as the SAME {@link Effect.Score}
 * vocabulary a joker uses — so the scorer interprets "Foil = +50 chips" exactly like "Banner = +N chips",
 * instead of a hard-coded {@code if (edition == FOIL) chips += 50} ladder. This is the first cut of the
 * Modifier-on-object primitive: a card modifier is data (rules), not a branch in the engine.
 *
 * <p>Only the deterministic chips/mult/xmult contributions live here. The non-scoring or probabilistic
 * parts stay in the scorer for now: Stone's "always scores / no rank+suit" (structural), Glass's
 * capability-dependent xmult + 1-in-4 shatter, Lucky's chance payouts, Steel's held-only xmult, and the
 * seal economy/run-loop effects (Gold $, Blue planet, Purple tarot, Red retrigger).
 */
public final class CardModifiers {

    private CardModifiers() {}

    private static Value n(double amount) {
        return new Value.Const(amount);
    }

    /** Played-card enhancement scoring effects (the additive ones; Glass/Steel/Lucky stay special). */
    public static final Map<Enhancement, List<Effect>> ENHANCEMENT = Map.of(
            Enhancement.BONUS, List.of(Effect.chips(n(30))),  // +30 Chips
            Enhancement.MULT, List.of(Effect.mult(n(4))),     // +4 Mult
            Enhancement.STONE, List.of(Effect.chips(n(50)))); // +50 Chips (the no-rank/always-scores part is structural)

    /** Card edition scoring effects — fully data: Foil +50 chips, Holo +10 mult, Poly x1.5 mult. */
    public static final Map<Edition, List<Effect>> EDITION = Map.of(
            Edition.FOIL, List.of(Effect.chips(n(50))),
            Edition.HOLOGRAPHIC, List.of(Effect.mult(n(10))),
            Edition.POLYCHROME, List.of(Effect.xMult(n(1.5))));
}
