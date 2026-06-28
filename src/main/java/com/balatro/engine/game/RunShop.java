package com.balatro.engine.game;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import com.balatro.engine.consumable.TarotCatalog;
import com.balatro.engine.eval.ModifyFolder;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.rng.RngSource;
import com.balatro.engine.rng.RngSources;
import com.balatro.grammar.Trigger;
import com.balatro.grammar.Value;
import java.util.ArrayList;
import java.util.List;

/**
 * The run-side shop flow — generation, buy/sell/reroll, vouchers, and booster packs. Distinct from the
 * {@code Shop}/{@code ShopEconomy}/{@code ShopConfig} data classes (which compute what's offered); this is the
 * lifecycle that mutates the run when the player shops. Extracted from {@code Run} (the orchestrator); the
 * cleanest island (forward-only edges: → {@link RunTags#applyShopTags} on shop-open, → {@link RunLoopRules} on
 * sell). Run's kept helpers {@code price}/{@code rerollCost} (which {@code RunView} reads) call back into the
 * package-private {@link #shopEconomy}/{@link #freeRerollsThisShop} here.
 */
final class RunShop {

    private RunShop() {}

    static Shop generateShop(Run r) {
        java.util.Set<String> owned = new java.util.HashSet<>();
        for (Joker j : r.state.jokers()) owned.add(j.key());
        owned.addAll(r.state.vouchers); // skip vouchers you already own too (distinct v_ namespace)
        ShopEconomy econ = shopEconomy(r); // Overstock slots + Hone/Glow Up edition odds, derived from vouchers
        rollAnteVoucherIfNeeded(r, owned);
        Shop s = Shop.generate(r.state.queues, econ.slots(), r.jokerPoolForShop(), owned,
                shopConfig(r).allowDuplicates(), econ.editionMultiplier(), econ.polyMultiplier(),
                r.anteVoucher, playedHands(r), econ.spectralWeight(), econ.tarotWeight(), econ.planetWeight(),
                econ.playingCardWeight(), econ.playingCardsEnhanced());
        // Re-add any Voucher-Tag vouchers so they persist across rerolls within this shop visit.
        for (String tv : r.tagVouchers) {
            if (!r.state.vouchers.contains(tv)) s.addVoucher(tv);
        }
        applyShopStickers(r, s);
        return s;
    }

    /** Roll stake stickers (eternal/perishable/rental, 30% each) onto this shop's joker slots. */
    private static void applyShopStickers(Run r, Shop s) {
        if (!(r.stake.eternalsInShop() || r.stake.perishablesInShop() || r.stake.rentalsInShop())) return;
        double chance = com.balatro.content.Sticker.STICKER_CHANCE;
        var q = r.state.queues.queue(RngSources.JOKER_STICKER, rng -> new boolean[]{
                rng.nextDouble() < chance,   // eternal roll
                rng.nextDouble() < chance,   // perishable roll (gated: not if eternal)
                rng.nextDouble() < chance}); // rental roll
        var items = s.items();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).kind() != Shop.Kind.JOKER) continue;
            boolean[] roll = q.next();
            boolean eternal = r.stake.eternalsInShop() && roll[0];
            boolean perishable = !eternal && r.stake.perishablesInShop() && roll[1];
            boolean rental = r.stake.rentalsInShop() && roll[2];
            if (eternal || perishable || rental) {
                items.set(i, items.get(i).withStickers(eternal, perishable, rental));
            }
        }
    }

    /** Stamp a bought/created joker with its stake stickers (server-only state read by sell/score/economy). */
    private static void applyStickersToJoker(Run r, Joker j, boolean eternal, boolean perishable, boolean rental) {
        var st = r.state.jokerState(j);
        if (eternal) st.put(com.balatro.content.Sticker.ETERNAL.flag(), true);
        if (perishable) {
            st.put(com.balatro.content.Sticker.PERISHABLE.flag(), true);
            st.put("perishTally", com.balatro.content.Sticker.PERISHABLE_ROUNDS);
        }
        if (rental) st.put(com.balatro.content.Sticker.RENTAL.flag(), true);
    }

    /** Hand types played at least once this run — gates the softlocked secret-hand planets. */
    private static java.util.Set<HandType> playedHands(Run r) {
        java.util.Set<HandType> s = java.util.EnumSet.noneOf(HandType.class);
        for (var e : r.state.handTypePlays.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) s.add(e.getKey());
        }
        return s;
    }

    /** Decide the ante's single voucher once per ante (the BMP get_next_voucher_key model). */
    private static void rollAnteVoucherIfNeeded(Run r, java.util.Set<String> ownedUnused) {
        if (r.anteVoucherAnte == r.ante) return;
        r.anteVoucherAnte = r.ante;
        r.anteVoucher = r.nextShowableVoucher();
    }

    /** The effective shop rules, derived from owned jokers (Showman/Astronomer/Chaos). */
    private static ShopConfig shopConfig(Run r) {
        return ShopConfig.resolve(r.state.jokers());
    }

    /** The effective shop economy, derived from owned vouchers (Overstock/Clearance/Reroll/Hone). */
    static ShopEconomy shopEconomy(Run r) {
        return ShopEconomy.resolve(r.state.vouchers, r.deckType.mods()); // deck mods carry Ghost's Spectral rate
    }

    /** True if {@code cost} is affordable given the current debt floor. */
    static boolean canAfford(Run r, int cost) {
        return r.state.money - cost >= r.minMoney();
    }

    static void enterShopTags(Run r) {
        r.couponActive = false;
        r.d6Active = false;
        RunTags.applyShopTags(r);
    }

    /** Regenerate shop stock, reset the free reroll, roll booster packs, and resolve shop tags —
     *  the common "open the post-blind shop" sequence. (The caller sets {@code phase} itself.) */
    static void refreshShopStock(Run r) {
        r.shop = generateShop(r);
        r.freeRerollsUsedThisShop = 0;
        r.rerollsThisShop = 0; // reroll cost escalation resets each new shop
        rollShopPacks(r);
        enterShopTags(r);
    }

    /**
     * Buy the shop slot at {@code index}, whatever its type: a Joker is added to the joker row (carrying any
     * rolled edition), a Tarot/Planet is added to your consumables. Returns null on success, else a reason.
     */
    static String buyShopItem(Run r, int index) {
        if (r.phase != Run.Phase.SHOP || r.shop == null) return "not in shop";
        if (index < 0 || index >= r.shop.items().size()) return "invalid shop slot";
        Shop.Item item = r.shop.items().get(index);
        switch (item.kind()) {
            case JOKER -> {
                if (!canAfford(r, r.price(item.cost()))) return "not enough money";
                // A Negative joker grants its own slot, so it never fails the slots-full check.
                boolean negative = item.edition() == Edition.NEGATIVE;
                if (!negative && r.state.jokers().size() >= r.state.jokerSlots) return "joker slots full";
                spend(r, r.price(item.cost()));
                Joker bought = JokerLibrary.create(item.key(), r.ruleset.jokerVariant());
                r.state.addJoker(bought);
                if (item.edition() != Edition.NONE) r.state.setJokerEdition(bought, item.edition());
                applyStickersToJoker(r, bought, item.eternal(), item.perishable(), item.rental());
            }
            case TAROT, PLANET -> {
                if (r.state.consumables.size() >= r.state.consumableSlots) return "no consumable slots";
                // Astronomer makes Planets free.
                int cost = (item.kind() == Shop.Kind.PLANET && shopConfig(r).planetsFree()) ? 0 : r.price(item.cost());
                if (!canAfford(r, cost)) return "not enough money";
                spend(r, cost);
                r.state.consumables.add(item.key());
            }
            case PLAYING_CARD -> { // Magic Trick / Illusion: the card joins your deck permanently
                if (!canAfford(r, r.price(item.cost()))) return "not enough money";
                spend(r, r.price(item.cost()));
                r.composition.add(item.card().copy());
            }
            default -> { /* SPECTRAL shop items (Ghost Deck) are handled by their own path */ }
        }
        r.shop.items().remove(index);
        return null;
    }

    /** Buy the offered voucher at {@code index} (the shop can hold several when a Voucher Tag added one). */
    static String buyVoucher(Run r, int index) {
        if (r.phase != Run.Phase.SHOP || r.shop == null) return "no voucher offered";
        java.util.List<String> offered = r.shop.vouchers();
        if (index < 0 || index >= offered.size()) return "no voucher offered";
        String key = offered.get(index);
        var v = VoucherCatalog.get(key);
        if (!canAfford(r, r.price(v.cost()))) return "not enough money";
        spend(r, r.price(v.cost()));
        grantVoucher(r, v.key());
        r.shop.removeVoucher(key);
        r.tagVouchers.remove(key);
        if (key.equals(r.anteVoucher)) r.anteVoucher = null; // bought — don't re-offer in this ante's later shops
        return null;
    }

    /** Add a voucher to the owned set; passive effects are read reactively by folding owned vouchers' Modifys. */
    static void grantVoucher(Run r, String key) {
        r.state.vouchers.add(key);
        r.recomputeSlots(); // Crystal Ball/Omen Globe (consumable) + Antimatter (joker), folded from ownership
    }

    /** Pay {@code cost} from money and record it as shop spend this ante (feeds Penny Pincher). */
    static void spend(Run r, int cost) {
        r.state.money -= cost;
        r.state.shopSpentThisAnte += cost;
    }

    /** Sell the joker at the given slot (shop or during a blind). Returns null on success. */
    static String sellJoker(Run r, int index) {
        if (r.phase != Run.Phase.SHOP && r.phase != Run.Phase.BLIND_ACTIVE && r.phase != Run.Phase.BLIND_SELECT) {
            return "cannot sell now";
        }
        if (index < 0 || index >= r.state.jokers().size()) return "invalid joker";
        if (r.state.jokerFlag(r.state.jokers().get(index), "eternal")) {
            return "eternal jokers cannot be sold";
        }
        Joker sold = r.state.jokers().remove(index);
        r.state.cardsSoldSinceLastPvp++; // feeds the opponent's Taxes joker
        int bonus = r.state.jokerInt(sold, "sellBonus", 0);
        r.state.money += Math.max(1, sold.info().cost() / 2) + bonus; // sell value (+ Egg/Gift bonus)
        // The sold joker's own SELL_SELF rules: Invisible (duplicate a joker), Luchador (disable boss),
        // Diet Cola (create a tag) — all data now, fired through the run-loop interpreter.
        RunLoopRules.raiseSelfSellRules(r, sold);
        // A card was sold: remaining jokers react (Campfire gains x0.25 each), and the boss reacts
        // (Verdant Leaf: DisableBoss) — both through the same SELL_CARD trigger.
        GameEvents.raise(Trigger.SELL_CARD, r.state, r.rng, null);
        RunLoopRules.raiseBossRules(r, Trigger.SELL_CARD, null);
        return null;
    }

    /** Free rerolls granted this shop, folded from everything owned (Chaos = +1 via its FREE_REROLLS Modify). */
    static int freeRerollsThisShop(Run r) {
        return (int) ModifyFolder.fold(0, Value.Var.FREE_REROLLS, r.resourceMods());
    }

    /** Reroll the shop offerings. Returns null on success, else a reason. */
    static String reroll(Run r) {
        if (r.phase != Run.Phase.SHOP || r.shop == null) return "not in shop";
        // Chaos the Clown (and any future free-reroll source): the first N rerolls each shop are free.
        boolean free = r.freeRerollsUsedThisShop < freeRerollsThisShop(r);
        int cost = free ? 0 : r.rerollCost();
        if (!canAfford(r, cost)) return "not enough money";
        spend(r, cost);
        if (free) {
            r.freeRerollsUsedThisShop++; // a free reroll does NOT escalate the next cost
        } else {
            r.rerollsThisShop++;         // each PAID reroll raises the next one's cost by $1 (D6's $0 rerolls included)
        }
        r.couponActive = false;      // the free "initial" cards are gone once you reroll
        r.shop = generateShop(r);    // advances the same game-long queue, skipping owned
        return null;
    }

    /** Perkeo: leaving the shop, a SHOP_EXIT joker rule duplicates a random held consumable (data). */
    static void applyShopExit(Run r) {
        RunLoopRules.raiseJokerRules(r, Trigger.SHOP_EXIT);
    }

    /** Roll this shop's two booster packs from the game-long packs queue (kept across rerolls). */
    private static void rollShopPacks(Run r) {
        r.shopPacks.clear();
        r.openPack = null;
        r.packPicksLeft = 0;
        var q = r.state.queues.queue(RngSources.PACKS, rng -> PackCatalog.roll(rng.nextDouble()));
        r.shopPacks.add(q.next());
        r.shopPacks.add(q.next());
    }

    /** Open a shop pack: pay, remove it, reveal its contents (from the Pack queues + Soul queue). */
    static String openPack(Run r, int index) {
        if (r.phase != Run.Phase.SHOP) return "not in shop";
        if (r.openPack != null) return "finish the open pack first";
        if (index < 0 || index >= r.shopPacks.size()) return "no such pack";
        PackCatalog.Pack pack = r.shopPacks.get(index);
        if (!canAfford(r, r.price(pack.cost()))) return "not enough money";
        spend(r, r.price(pack.cost()));
        r.shopPacks.remove(index);
        GameEvents.raise(Trigger.OPEN_BOOSTER, r.state, r.rng, null); // Hallucination etc. (Up-Top queue)
        r.openPack = revealPack(r, pack);
        r.packPicksLeft = pack.choose();
        return null;
    }

    /** Reveal a pack's cards from its dedicated Pack queue, rolling the Soul queue per consumable slot. */
    private static java.util.List<Run.RevealedItem> revealPack(Run r, PackCatalog.Pack pack) {
        java.util.List<Run.RevealedItem> out = new ArrayList<>();
        int n = pack.shown();
        switch (pack.kind()) {
            case ARCANA -> fillConsumables(r, out, n, RngSources.packContent("pack:tarot"),
                    TarotCatalog.tarotKeys(), "c_the_soul", "Tarot", k -> true);
            case SPECTRAL -> fillConsumables(r, out, n, RngSources.packContent("pack:spectral"),
                    TarotCatalog.spectralKeys(), "c_the_soul", "Spectral", k -> true);
            case CELESTIAL -> {
                int fill = n;
                // Telescope: guarantee your most-played hand's Planet is in the pack.
                if (r.voucherFold(Value.Var.CELESTIAL_MOST_PLAYED, 0) >= 1) {
                    String mp = r.mostPlayedPlanetKey();
                    if (mp != null) { out.add(new Run.RevealedItem("Planet", mp, null)); fill--; }
                }
                fillConsumables(r, out, fill, RngSources.packContent("pack:planet"),
                        PlanetCatalog.keys(), "c_black_hole", "Planet", k -> PlanetCatalog.available(k, playedHands(r)));
            }
            case BUFFOON -> {
                // Shares the shop joker rarity sub-queues (opening a Buffoon consumes those jokers).
                java.util.Set<String> offered = new java.util.HashSet<>();
                for (int i = 0; i < n; i++) {
                    out.add(new Run.RevealedItem("JOKER",
                            Shop.drawJoker(r.state.queues, r.jokerPoolForShop(), java.util.Set.of(), offered, false), null));
                }
            }
            case STANDARD -> {
                Rank[] ranks = Rank.values();
                Suit[] suits = Suit.values();
                var q = r.state.queues.queue(RngSources.PACK_CARD,
                        rng -> new Card(ranks[rng.nextInt(ranks.length)], suits[rng.nextInt(suits.length)],
                                Enhancement.NONE, Edition.NONE, Seal.NONE));
                for (int i = 0; i < n; i++) out.add(new Run.RevealedItem("CARD", null, q.next()));
            }
        }
        return out;
    }

    /** Fill {@code n} consumable slots from the pack queue, rolling the Soul queue per slot. */
    private static void fillConsumables(Run r, java.util.List<Run.RevealedItem> out, int n, RngSource contentSrc,
            List<String> pool, String soulKey, String soulType, java.util.function.Predicate<String> available) {
        var q = r.state.queues.queue(contentSrc, rng -> pool.get(rng.nextInt(pool.size())));
        // Per-content-type soul stream (BMP keys it 'soul_'..type), checked per slot at ~0.3%.
        var soulQ = r.state.queues.queue(RngSources.SOUL.sub(soulType), rng -> rng.nextDouble() < 0.003);
        for (int i = 0; i < n; i++) {
            if (soulQ.next()) out.add(new Run.RevealedItem("CONSUMABLE", soulKey, null));
            else out.add(new Run.RevealedItem("CONSUMABLE", q.nextWhere(available), null)); // skip softlocked planets
        }
    }

    /** Take one revealed card from the open pack into your inventory/deck. */
    static String pickPackItem(Run r, int index) {
        if (r.openPack == null) return "no open pack";
        if (index < 0 || index >= r.openPack.size()) return "no such pack card";
        if (r.packPicksLeft <= 0) return "no picks left";
        Run.RevealedItem it = r.openPack.get(index);
        switch (it.type()) {
            case "JOKER" -> {
                if (r.state.jokers().size() >= r.state.jokerSlots) return "joker slots full";
                r.state.addJoker(JokerLibrary.create(it.key(), r.ruleset.jokerVariant()));
            }
            case "CARD" -> {
                Card c = it.card().copy();
                r.composition.add(c);
                r.state.hand.add(c);
            }
            default -> { // CONSUMABLE (Tarot/Planet/Spectral)
                if (r.state.consumables.size() >= r.state.consumableSlots) return "no consumable slots";
                r.state.consumables.add(it.key());
            }
        }
        r.openPack.remove(index);
        if (--r.packPicksLeft <= 0) { // all picks used -> pack closes
            r.openPack = null;
        }
        return null;
    }

    /** Skip the rest of the open pack (counts as a skip for Red Card). */
    static String skipPack(Run r) {
        if (r.openPack == null) return "no open pack";
        r.openPack = null;
        r.packPicksLeft = 0;
        GameEvents.raise(Trigger.SKIP_BOOSTER, r.state, r.rng, null); // Red Card gains +Mult
        return null;
    }
}
