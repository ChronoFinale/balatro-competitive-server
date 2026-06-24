package com.balatro.engine.card;

import com.balatro.engine.joker.Trigger;
import com.balatro.engine.joker.def.Condition;
import com.balatro.engine.joker.def.Effect;
import com.balatro.engine.joker.def.Odds;
import com.balatro.engine.joker.def.Rule;
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
            Enhancement.STONE, List.of(Effect.chips(n(50))),  // +50 Chips (the no-rank/always-scores part is structural)
            // Glass x-mult reads GLASS_MULT (2.0 vanilla / 1.5 ranked-MP); the 1-in-4 shatter stays structural.
            Enhancement.GLASS, List.of(Effect.xMult(new Value.RunVar(Value.Var.GLASS_MULT, 0, 1))));

    /** Card edition scoring effects — fully data: Foil +50 chips, Holo +10 mult, Poly x1.5 mult. */
    public static final Map<Edition, List<Effect>> EDITION = Map.of(
            Edition.FOIL, List.of(Effect.chips(n(50))),
            Edition.HOLOGRAPHIC, List.of(Effect.mult(n(10))),
            Edition.POLYCHROME, List.of(Effect.xMult(n(1.5))));

    /** Held-in-hand enhancement scoring (Steel x1.5 mult while the card is held, not played). */
    public static final Map<Enhancement, List<Effect>> HELD = Map.of(
            Enhancement.STEEL, List.of(Effect.xMult(n(1.5))));

    /** Seal scoring effects — Gold = +$3 when the card scores (credited at end of scoring, like any
     *  dollars effect). Red (retrigger), Blue (held->planet) and Purple (discard->tarot) live elsewhere:
     *  Red is a retrigger pass, Blue/Purple are run-loop, not per-card scoring. */
    public static final Map<Seal, List<Effect>> SEAL = Map.of(
            Seal.GOLD, List.of(Effect.dollars(n(3))),
            Seal.RED, List.of(Effect.retriggers(n(1)))); // applied in the retrigger pass, not the score pass

    /** A "1 in {@code denom}" gate rolled on a dedicated stream (preserving Balatro's per-effect queue). */
    private static Condition chanceOn(int denom, String stream) {
        return new Condition.Chance(new Odds(1, denom), stream, stream);
    }

    /** Probabilistic enhancement scoring — Lucky: 1-in-5 for +20 Mult (lucky_mult stream), 1-in-15 for +$20
     *  (lucky_money stream). Order matters (mult queue advances before money), so the list order is fixed.
     *  The Lucky Cat counter (luckyTriggersTotal) is bumped per proc by the scorer — the one coupling left. */
    public static final Map<Enhancement, List<Rule>> PROBABILISTIC = Map.of(
            Enhancement.LUCKY, List.of(
                    new Rule(Trigger.ON_SCORED, chanceOn(5, "lucky_mult"), List.of(Effect.mult(n(20)))),
                    new Rule(Trigger.ON_SCORED, chanceOn(15, "lucky_money"), List.of(Effect.dollars(n(20))))));
}
