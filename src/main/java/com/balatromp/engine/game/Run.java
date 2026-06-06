package com.balatromp.engine.game;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.game.Blinds.BlindType;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.intent.IntentHandler;
import com.balatromp.engine.intent.IntentResult;
import com.balatromp.engine.joker.Joker;
import com.balatromp.engine.net.CardView;
import com.balatromp.engine.net.ClientView;
import com.balatromp.engine.net.ServerUpdate;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ReplayEntry;
import com.balatromp.engine.state.Deck;
import com.balatromp.engine.state.Ruleset;
import com.balatromp.engine.state.RunState;
import java.util.ArrayList;
import java.util.List;

/**
 * The single-player run loop, ruleset-driven — the spine a competitive match is
 * built on. Authoritative: it owns the seed, the blind requirement, and phase
 * transitions; the client only sends intents through {@link #play(Intent)}.
 *
 * Flow: BLIND_ACTIVE (play/discard until score >= requirement, or hands run out)
 *       -> win: award economy, SHOP -> proceed() advances Small->Big->Boss->ante+1
 *       -> beat winAnte's boss: RUN_WON;  hands exhausted under requirement: RUN_LOST.
 *
 * The shop is a transition stub for now (proceed() just advances) — next increment.
 */
public final class Run {

    public enum Phase { BLIND_ACTIVE, SHOP, RUN_WON, RUN_LOST }

    public final Ruleset ruleset;
    public final RunState state = new RunState();
    public final RandomStreams rng;

    private final IntentHandler intents = new IntentHandler();

    public int ante = 1;
    public BlindType blind = BlindType.SMALL;
    public long requirement;
    public Phase phase;

    public Run(Ruleset ruleset, String seed) {
        this(ruleset, seed, Deck.standard(), List.of());
    }

    public Run(Ruleset ruleset, String seed, Deck deck, List<Joker> jokers) {
        this.ruleset = ruleset;
        this.rng = new RandomStreams(seed);
        state.money = ruleset.startingMoney();
        state.handSize = ruleset.handSize();
        state.deck = deck;
        state.rng = rng;
        for (Joker j : jokers) state.addJoker(j);
        deck.shuffle(rng);
        startBlind();
    }

    private void startBlind() {
        state.roundScore = 0;
        state.handsLeft = ruleset.hands();
        state.discardsLeft = ruleset.discards();
        state.hand.clear();
        state.deck.drawTo(state.hand, state.handSize);
        requirement = Blinds.requirement(ante, blind, ruleset);
        phase = Phase.BLIND_ACTIVE;
    }

    /** Process a client intent; advances win/lose state after a hand is played. */
    public IntentResult play(Intent intent) {
        if (phase != Phase.BLIND_ACTIVE) {
            return IntentResult.rejected("not in an active blind");
        }
        IntentResult result = intents.handle(state, rng, intent);
        if (!result.ok()) return result;

        if (intent instanceof Intent.PlayHand) {
            if (state.roundScore >= requirement) {
                winBlind();
            } else if (state.handsLeft <= 0) {
                phase = Phase.RUN_LOST;
            }
        }
        return result;
    }

    private void winBlind() {
        // Economy: blind reward + interest ($1 per $5 held, capped at $5) + joker/gold payouts.
        int interest = Math.min(5, state.money / 5);
        state.money += blind.reward + interest;
        GameEvents.endOfRound(state, rng);
        phase = Phase.SHOP;
    }

    /** Client-facing entry: validate+apply an intent, return the authoritative update. */
    public ServerUpdate submit(Intent intent) {
        IntentResult r = play(intent);
        List<ReplayEntry> replay = (r.score() != null) ? r.score().replayLog() : List.of();
        return new ServerUpdate(r.ok(), r.error(), view(), replay);
    }

    /** The safe projection of authoritative state the client may render (spec §8). */
    public ClientView view() {
        List<CardView> handView = new ArrayList<>();
        for (Card c : state.hand) handView.add(CardView.of(c));
        List<String> jokerNames = new ArrayList<>();
        for (Joker j : state.jokers()) jokerNames.add(j.name());
        return new ClientView(ante, blind.display, requirement, state.roundScore,
                state.handsLeft, state.discardsLeft, state.money, state.handSize,
                phase.name(), handView, jokerNames);
    }

    /** Leave the (stubbed) shop and advance to the next blind / ante / win. */
    public void proceed() {
        if (phase != Phase.SHOP) return;
        switch (blind) {
            case SMALL -> blind = BlindType.BIG;
            case BIG -> blind = BlindType.BOSS;
            case BOSS -> {
                if (ruleset.winAnte() > 0 && ante >= ruleset.winAnte()) {
                    phase = Phase.RUN_WON;
                    return;
                }
                ante++;
                blind = BlindType.SMALL;
            }
        }
        startBlind();
    }
}
