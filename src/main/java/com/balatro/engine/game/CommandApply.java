package com.balatro.engine.game;

import com.balatro.engine.card.Card;
import com.balatro.engine.consumable.Creation;
import com.balatro.engine.exec.Command;
import com.balatro.engine.joker.JokerLibrary;

/**
 * Applies a resolved {@link Command} to a {@link Run}'s authoritative state — the run-loop side of the
 * action model. The {@code Effect} grammar is declarative; interpreting it yields a concrete {@code Command}
 * ("destroy these two cards"), which this applies. Each case ONLY mutates; the trace is the structured command
 * itself (the engine never builds a human string — presentation/localization is the client's job, reading the
 * command's typed fields, the same discipline as {@code ClientView}/{@code ReplayEntry}).
 *
 * <p>Extracted from {@code Run} (the orchestrator) so the single mutation switch lives in one focused place.
 * Scoring-time commands (DestroyScored/CopyScored/…) are applied by {@code ScoringEngine} at the scoring
 * moment, never the run loop — they throw here if they ever reach it.
 */
final class CommandApply {

    private CommandApply() {}

    static void apply(Run r, Command cmd) {
        switch (cmd) {
            case Command.Money m -> {
                int floor = r.minMoney();
                int v = (int) Math.round(m.amount());
                r.state.money = switch (m.op()) {
                    case ADD -> Math.max(floor, r.state.money + v);          // gains / The Tooth uses SUBTRACT
                    case SUBTRACT -> Math.max(floor, r.state.money - v);
                    case MULTIPLY -> Math.max(0, (int) Math.round(r.state.money * m.amount()));
                    case DIVIDE -> m.amount() == 0 ? r.state.money : Math.max(0, (int) Math.round(r.state.money / m.amount()));
                    case SET -> Math.max(0, v);                             // The Ox: set to $0
                    case POWER, MAX, MIN -> throw new IllegalStateException(m.op() + " is not a money operation");
                };
            }
            case Command.HandSize h -> r.state.handSize += h.delta();
            case Command.EditionJoker ej -> r.state.setJokerEdition(ej.target(), ej.edition());
            case Command.CopyJoker cj ->
                    r.state.addJoker(JokerLibrary.create(cj.source().key(), cj.variant())); // copy has no edition
            // Hex/Ankh: destroy every joker but the kept one — except Eternal jokers, which can't be destroyed
            // (same protection honored at sell and the joker-effect/boss destroy sites).
            case Command.DestroyOtherJokers d -> r.state.jokers().removeIf(j -> j != d.keep() && !r.state.jokerFlag(j, "eternal"));
            case Command.CopyConsumable cc -> {
                if (cc.slotPolicy() == Command.CopyConsumable.SlotPolicy.IGNORE_CAP
                        || r.state.consumables.size() < r.state.consumableSlots) r.state.consumables.add(cc.key());
            }
            case Command.DestroyCards dc -> {
                dc.cards().forEach(t -> t.destroyed = true);
                r.composition.removeIf(x -> x.destroyed);
                r.state.hand.removeIf(x -> x.destroyed);
            }
            case Command.MutateCards mc -> mc.cards().forEach(t -> mc.mod().applyTo(t));
            case Command.Create cr -> Creation.apply(r.state, cr.spec(), r.state.queues);
            case Command.AddCardsToDeck ac -> {
                for (Card made : ac.cards()) { r.composition.add(made); r.state.hand.add(made); }
            }
            case Command.OverwriteCard ow -> {
                Card t = ow.target(), s = ow.source();
                t.rank = s.rank; t.suit = s.suit; t.enhancement = s.enhancement; t.edition = s.edition; t.seal = s.seal;
            }
            case Command.LevelHand lvl -> {
                for (int i = 0; i < Math.abs(lvl.levels()); i++) {
                    if (lvl.levels() < 0) r.state.levelDownHand(lvl.hand()); else r.state.levelUpHand(lvl.hand());
                }
            }
            // Scoring-time commands are applied by ScoringEngine at the scoring moment, never by the run loop.
            case Command.DestroyScored _,
                 Command.DestroyEventCards _,
                 Command.CopyScored _,
                 Command.MutateScoredCard _,
                 Command.DestroySelf _,
                 Command.GrantDiscards _ ->
                    throw new IllegalStateException("scoring-time command applied by ScoringEngine, not Run: " + cmd);
        }
    }
}
