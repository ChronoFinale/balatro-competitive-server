package com.balatro.engine.game;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.consumable.Consumable;
import com.balatro.engine.consumable.TarotCatalog;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.i18n.Loc;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerDisplay;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.joker.def.DataJoker;
import com.balatro.engine.net.CardView;
import com.balatro.engine.net.ClientView;
import com.balatro.grammar.JokerDef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@link ClientView} — the safe, read-only projection of authoritative {@link Run} state the client
 * may render (spec §8). Extracted from {@code Run} (which stays the mutation orchestrator): this is pure
 * projection, no state change, so it lives here as static builders taking the {@code Run}. The render text is
 * localized to {@code run.viewLocale}; numbers are single-sourced from the boss/content data.
 */
final class RunView {

    private RunView() {}

    /** The safe projection of authoritative state the client may render (spec §8). */
    static ClientView build(Run r) {
        List<CardView> handView = new ArrayList<>();
        for (Card c : r.state.hand) handView.add(CardView.of(c));
        Map<String, Object> handLevels = new LinkedHashMap<>();
        for (HandType t : HandType.values()) handLevels.put(t.display, r.state.handLevel(t));
        boolean inShop = r.phase == Run.Phase.SHOP && r.shop != null;
        int rerollCost = inShop ? r.rerollCost() : 0;

        return new ClientView(r.ante, r.blind.display, r.requirement, r.state.roundScore,
                r.state.handsLeft, r.state.discardsLeft, r.state.money, r.state.handSize,
                r.phase.name(), handView, jokerView(r), shopItemsView(r), rerollCost,
                r.boss != null ? r.boss.name() : null, r.boss != null ? bossText(r, r.boss) : null,
                consumablesView(r), handLevels, deckStatsView(r), countersView(r), shopVouchersView(r),
                shopPacksView(r), openPackView(r),
                r.stake.display, r.deckType.name(), r.boss != null ? r.boss.key() : null);
    }

    private static JokerDef defFor(Joker j) {
        return (j instanceof DataJoker dj) ? dj.def() : null;
    }

    /** The boss's effect text in {@code run.viewLocale}: the localized template filled with the boss's own data
     *  (numbers single-sourced). For "en" this equals {@code boss.effect()}. */
    private static String bossText(Run r, BossBlind b) {
        double rm = b.reqMult();
        Object req = rm == Math.rint(rm) ? (Object) (long) rm : (Object) rm;
        return Loc.fill(r.viewLocale, b.key(), java.util.Map.of(
                "reqMult", req, "minAnte", b.minAnte(), "reward", b.reward()));
    }

    /** A localized description for a content key in {@code run.viewLocale}, falling back to {@code original}
     *  when neither the locale nor English has text (e.g. a custom joker not in localization). */
    private static String locDesc(Run r, String key, String original) {
        String t = Loc.text(r.viewLocale, key);
        return t.isEmpty() ? original : t;
    }

    /** The owned Jokers, each as a render map (or just a face-down placeholder under Amber Acorn) with the
     *  data def + live state so the client can run an instant local preview. */
    private static List<Map<String, Object>> jokerView(Run r) {
        List<Map<String, Object>> jokerView = new ArrayList<>();
        for (int i = 0; i < r.state.jokers().size(); i++) {
            Joker j = r.state.jokers().get(i);
            var info = j.info();
            Map<String, Object> jv = new LinkedHashMap<>();
            if (r.jokersHidden) {
                // Amber Acorn: the Joker is face down — reveal nothing but a placeholder card back. The
                // server still scores it; the client just can't see which Joker sits in which (shuffled) slot.
                jv.put("faceDown", true);
                jokerView.add(jv);
                continue;
            }
            jv.put("key", info.key());
            jv.put("name", info.name());
            jv.put("description", locDesc(r, info.key(), info.description()));
            jv.put("rarity", info.rarity());
            jv.put("cost", info.cost()); // for Swashbuckler's sell-value sum on the client
            jv.put("x", info.atlasX());
            jv.put("y", info.atlasY());
            // Built-in joker display: the joker's current live value (server-computed, no mod).
            jv.put("display", JokerDisplay.currentValue(r.state.jokers(), i, r.state));
            // The joker's edition (Foil/Holo/Poly/Negative) so the client can render it — e.g. a Hex/Wheel
            // polychrome on an owned joker. Omitted when base (NONE).
            var edition = r.state.jokerEdition(j);
            if (edition != null && edition != com.balatro.engine.card.Edition.NONE) jv.put("edition", edition.name());
            // Visible stickers (Eternal/Perishable/Rental) so the client can badge them like the real game.
            for (var sticker : com.balatro.content.Sticker.values()) {
                if (Boolean.TRUE.equals(r.state.jokerState(j).get(sticker.flag()))) jv.put(sticker.flag(), true);
            }
            // The joker's data definition + live state, so the client can compute an
            // instant local score preview (interpreting the same JokerDef the server uses).
            JokerDef def = defFor(j);
            if (def != null) jv.put("def", def);
            jv.put("state", r.state.jokerState(j));
            jokerView.add(jv);
        }
        return jokerView;
    }

    /** The shop's mixed main slots (Joker / Tarot / Planet), or null when not in the shop. {@code kind}
     *  tells the client how to render/label and which buy path {@code buyShopItem} takes. */
    private static List<Map<String, Object>> shopItemsView(Run r) {
        if (r.phase != Run.Phase.SHOP || r.shop == null) return null;
        List<Map<String, Object>> shopView = new ArrayList<>();
        for (Shop.Item it : r.shop.items()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", it.kind().name());
            m.put("key", it.key());
            m.put("name", it.name());
            m.put("description", locDesc(r, it.key(), it.description()));
            m.put("cost", it.cost());
            m.put("rarity", it.rarity());
            m.put("edition", it.edition().name());
            if (it.eternal()) m.put("eternal", true);
            if (it.perishable()) m.put("perishable", true);
            if (it.rental()) m.put("rental", true);
            shopView.add(m);
        }
        return shopView;
    }

    /** Held consumables (Tarot/Planet) as render maps. */
    private static List<Map<String, Object>> consumablesView(Run r) {
        List<Map<String, Object>> consumables = new ArrayList<>();
        for (String key : r.state.consumables) {
            PlanetCatalog.Planet p = PlanetCatalog.get(key);
            if (p != null) {
                consumables.add(Map.of("key", key, "name", p.name(), "description", locDesc(r, key, p.description())));
                continue;
            }
            Consumable c = TarotCatalog.get(key);
            if (c != null) {
                consumables.add(Map.of("key", key, "name", c.name(), "description", locDesc(r, key, c.description()),
                        "maxTargets", c.maxTargets()));
            }
        }
        return consumables;
    }

    /** Deck aggregates the client needs for deck-stat joker previews (size / remaining / enhancement counts). */
    private static Map<String, Object> deckStatsView(Run r) {
        Map<String, Object> deckStats = new LinkedHashMap<>();
        deckStats.put("size", r.state.deckComposition.size());
        deckStats.put("remaining", r.state.deck != null ? r.state.deck.remaining() : 0);
        Map<String, Integer> enh = new LinkedHashMap<>();
        for (Card c : r.state.deckComposition) {
            if (c.enhancement != Enhancement.NONE) {
                enh.merge(c.enhancement.name(), 1, Integer::sum);
            }
        }
        deckStats.put("enhancements", enh);
        // The full deck COMPOSITION, canonically sorted so it carries NO shuffle order, for the client's
        // deck-view UI. Composition is not secret -- the real game shows your whole deck; only the draw ORDER
        // stays hidden (that lives in r.state.deck, never sent). The info-hiding invariant is preserved.
        // Which cards are still in the DRAW PILE (remaining) vs already drawn/played this round -- so the
        // client's "remaining/unplayed" deck view greys the rest, like the real game. Membership only (a set
        // of uids), never order: knowing WHICH cards remain is vanilla (the deck view shows it); the shuffle
        // ORDER stays hidden.
        java.util.Set<java.util.UUID> inDeck = new java.util.HashSet<>();
        if (r.state.deck != null) for (Card c : r.state.deck.cards()) inDeck.add(c.uid);
        List<Map<String, Object>> cards = new ArrayList<>();
        r.state.deckComposition.stream()
                .sorted(java.util.Comparator.comparingInt((Card c) -> c.suit.ordinal()).thenComparingInt(c -> c.rank.ordinal()))
                .forEach(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("rank", c.rank.name());
                    m.put("suit", c.suit.name());
                    if (c.enhancement != Enhancement.NONE) m.put("enhancement", c.enhancement.name());
                    if (c.edition != com.balatro.engine.card.Edition.NONE) m.put("edition", c.edition.name());
                    if (c.seal != com.balatro.engine.card.Seal.NONE) m.put("seal", c.seal.name());
                    m.put("inDeck", inDeck.contains(c.uid)); // still in the draw pile (remaining) this round
                    cards.add(m);
                });
        deckStats.put("cards", cards);
        return deckStats;
    }

    /** The run-state counters the client mirrors for previews/screens (run totals, hand-type plays, round
     *  targets, cash-out breakdown, offered/held tags, opponent state). */
    private static Map<String, Object> countersView(Run r) {
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("HANDS_PLAYED_TOTAL", r.state.handsPlayedTotal);
        counters.put("ROUNDS_PLAYED", r.state.roundsPlayedTotal);
        counters.put("CARDS_DISCARDED_TOTAL", r.state.cardsDiscardedTotal);
        counters.put("LUCKY_TRIGGERS", r.state.luckyTriggersTotal);
        counters.put("DISCARDS_USED", r.state.discardsUsedThisRound);
        counters.put("HANDS_PLAYED", r.state.handsPlayedThisRound);
        Map<String, Object> typePlays = new LinkedHashMap<>();
        r.state.handTypePlays.forEach((t, n) -> typePlays.put(t.name(), n));
        counters.put("handTypePlays", typePlays);
        counters.put("handTypesThisRound", r.state.handTypesThisRound.stream().map(Enum::name).toList());
        // Per-round targets: emit each per-joker key (jokerKey:DOMAIN); enums (Suit/HandType) as names, rank
        // ids as ints. The client/preview reads the calculating joker's own key to match *IsTarget conditions.
        r.state.roundTargets.forEach((k, v) -> counters.put(k, v instanceof Enum<?> e ? e.name() : v));
        counters.put("OBELISK_STREAK", r.state.obeliskStreak);
        counters.put("BLINDS_SKIPPED", r.state.blindsSkipped);
        counters.put("inPvpBlind", r.state.inPvpBlind);
        counters.put("bossHalveBase", r.state.bossHalveBase); // The Flint: preview halves base too
        counters.put("multiplayer", r.state.capabilities.restrictedPools());
        // Cash-out breakdown (the end-of-round screen reads these when entering the shop).
        counters.put("cashOutReward", r.state.lastBlindReward);
        counters.put("cashOutInterest", r.state.lastInterest); // ACTUAL interest ($1/$5, capped)
        counters.put("cashOutHands", r.state.lastRoundMoney);  // per-remaining-hand/discard money
        // The tag offered for skipping this blind (shown on the Select/Skip screen).
        counters.put("offeredTag", r.state.offeredTag == null ? "" : r.state.offeredTag);
        counters.put("offeredTagName", r.state.offeredTag == null ? ""
                : (TagCatalog.get(r.state.offeredTag) != null ? TagCatalog.get(r.state.offeredTag).name() : r.state.offeredTag));
        counters.put("heldTags", new ArrayList<>(r.state.tags));
        counters.put("OPP_LIVES_BEHIND", Math.max(0, r.state.opponent.lives - r.state.myLives));
        counters.put("OPP_HANDS_LEFT", r.state.opponent.handsLeft);
        counters.put("OPP_CARDS_SOLD", r.state.opponent.cardsSold);
        // Joker-trigger events since the last view (Hallucination created X, etc.) -- DRAINED here so each
        // response carries only the new ones; the client logs them.
        if (!r.state.triggerLog.isEmpty()) {
            counters.put("events", new ArrayList<>(r.state.triggerLog));
            r.state.triggerLog.clear();
        }
        return counters;
    }

    /** The extra Voucher(s) offered in the shop (Voucher Tag / second slot), or null if none. */
    private static List<Map<String, Object>> shopVouchersView(Run r) {
        if (r.phase != Run.Phase.SHOP || r.shop == null || r.shop.vouchers().isEmpty()) return null;
        List<Map<String, Object>> shopVouchers = new ArrayList<>();
        for (String vk : r.shop.vouchers()) {
            var v = VoucherCatalog.get(vk);
            shopVouchers.add(Map.of("key", v.key(), "name", v.name(),
                    "description", locDesc(r, v.key(), v.description()), "cost", r.price(v.cost())));
        }
        return shopVouchers;
    }

    /** The shop's booster packs (kept across rerolls), or null when not in the shop. */
    private static List<Map<String, Object>> shopPacksView(Run r) {
        if (r.phase != Run.Phase.SHOP) return null;
        List<Map<String, Object>> packsView = new ArrayList<>();
        for (PackCatalog.Pack p : r.shopPacks) {
            packsView.add(Map.of("kind", p.kind().name(), "size", p.size().name(),
                    "name", p.displayName(), "cost", r.price(p.cost()),
                    "shown", p.shown(), "choose", p.choose()));
        }
        return packsView;
    }

    /** The currently-open booster pack's revealed items + picks left, or null if no pack is open. */
    private static Map<String, Object> openPackView(Run r) {
        if (r.openPack == null) return null;
        List<Map<String, Object>> items = new ArrayList<>();
        for (Run.RevealedItem it : r.openPack) items.add(revealedItemView(r, it));
        return Map.of("picksLeft", r.packPicksLeft, "items", items);
    }

    /** View of one revealed pack card: a consumable/joker (key+name+desc) or a playing card. */
    private static Map<String, Object> revealedItemView(Run r, Run.RevealedItem it) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", it.type());
        switch (it.type()) {
            case "JOKER" -> {
                var info = JokerLibrary.create(it.key()).info();
                m.put("key", it.key());
                m.put("name", info.name());
                m.put("description", locDesc(r, it.key(), info.description()));
            }
            case "CARD" -> {
                Card c = it.card();
                m.put("name", c.rank.name() + " of " + c.suit.name());
                m.put("rank", c.rank.name());
                m.put("suit", c.suit.name());
                m.put("enhancement", c.enhancement.name());
            }
            default -> { // CONSUMABLE
                PlanetCatalog.Planet p = PlanetCatalog.get(it.key());
                Consumable c = TarotCatalog.get(it.key());
                m.put("key", it.key());
                m.put("name", p != null ? p.name() : (c != null ? c.name() : it.key()));
                m.put("description", locDesc(r, it.key(),
                        p != null ? p.description() : (c != null ? c.description() : "")));
            }
        }
        return m;
    }
}
