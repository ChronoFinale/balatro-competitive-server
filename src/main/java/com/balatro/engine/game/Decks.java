package com.balatro.engine.game;

import com.balatro.engine.game.DeckCatalog.Composition;
import com.balatro.engine.game.DeckCatalog.DeckType;
import com.balatro.engine.joker.def.Modify;
import com.balatro.engine.joker.def.Value;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link DeckType}s — a deck reads as the handful of things it actually changes,
 * instead of a wall of positional constructor args. Same data, far more legible.
 *
 * <pre>{@code
 * Decks.of("d_blue", "Blue Deck").desc("+1 hand each round").hands(1).build()
 * Decks.of("d_ghost", "Ghost Deck").desc("Spectrals in the shop").spectralRate(2).startsWith("c_hex").build()
 * }</pre>
 */
public final class Decks {

    private final String key;
    private final String name;
    private String desc = "";
    private final List<Modify> resourceMods = new ArrayList<>();
    private boolean greenEconomy = false;
    private Composition composition = Composition.STANDARD;
    private final List<String> vouchers = new ArrayList<>();
    private final List<String> consumables = new ArrayList<>();
    private int spectralRate = 0;
    private boolean balanceChipsMult = false;
    private int blindSizeMult = 1;
    private final List<String> onBossDefeatTags = new ArrayList<>();

    private Decks(String key, String name) {
        this.key = key;
        this.name = name;
    }

    public static Decks of(String key, String name) { return new Decks(key, name); }

    public Decks desc(String d) { this.desc = d; return this; }

    public Decks hands(int n) { resourceMods.add(Modify.add(Value.Var.HANDS_LEFT, n)); return this; }

    public Decks discards(int n) { resourceMods.add(Modify.add(Value.Var.DISCARDS_LEFT, n)); return this; }

    public Decks jokerSlots(int n) { resourceMods.add(Modify.add(Value.Var.JOKER_SLOTS, n)); return this; }

    public Decks money(int n) { resourceMods.add(Modify.add(Value.Var.MONEY, n)); return this; }

    public Decks handSize(int n) { resourceMods.add(Modify.add(Value.Var.HAND_SIZE, n)); return this; }

    public Decks consumableSlots(int n) { resourceMods.add(Modify.add(Value.Var.CONSUMABLE_SLOTS, n)); return this; }

    public Decks greenEconomy() {
        this.greenEconomy = true;                                      // $2/hand, $1/discard payout rates
        resourceMods.add(Modify.min(Value.Var.INTEREST_CAP, 0));      // ...and no interest (caps it at 0, MIN beats any voucher MAX)
        return this;
    }

    // composition shortcuts (read nicer than .composition(Composition.X))
    public Decks noFaces() { this.composition = Composition.NO_FACES; return this; }

    public Decks checkered() { this.composition = Composition.CHECKERED; return this; }

    public Decks erratic() { this.composition = Composition.ERRATIC; return this; }

    public Decks startsWithVouchers(String... v) { this.vouchers.addAll(List.of(v)); return this; }

    public Decks startsWith(String... consumables) { this.consumables.addAll(List.of(consumables)); return this; }

    /** Ghost: Spectral cards appear in the shop at this weight. */
    public Decks spectralRate(int rate) { this.spectralRate = rate; return this; }

    /** Plasma: average chips & mult before the final score. */
    public Decks balancesChipsAndMult() { this.balanceChipsMult = true; return this; }

    /** Plasma: blind requirements x this. */
    public Decks blindSizeMult(int mult) { this.blindSizeMult = mult; return this; }

    /** Anaglyph: grant these tags after each Boss defeat. */
    public Decks tagAfterBoss(String... tags) { this.onBossDefeatTags.addAll(List.of(tags)); return this; }

    public DeckType build() {
        return new DeckType(key, name, desc, List.copyOf(resourceMods),
                greenEconomy, composition,
                List.copyOf(vouchers), List.copyOf(consumables),
                spectralRate, balanceChipsMult, blindSizeMult, List.copyOf(onBossDefeatTags));
    }
}
