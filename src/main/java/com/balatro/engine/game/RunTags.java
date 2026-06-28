package com.balatro.engine.game;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Seal;
import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.rng.RngSources;
import com.balatro.grammar.Effect;
import java.util.List;

/**
 * Tag application — skip-blind tags + seals that grant content. Tags resolve their data {@link Effect}s
 * through the shared run-loop interpreter ({@link RunLoopRules#applyRunLoopEffect}); IMMEDIATE tags fire on
 * claim, the rest are held and resolved at their timing (NEXT_BLIND / ON_SHOP / ON_BOSS_DEFEAT). Extracted
 * from {@code Run} (the orchestrator); leans on Run's package-internal toolkit (`runLoopContext`,
 * `nextShowableVoucher`, `jokerPoolForShop`, `apply`) + the shop slots it adds free items into.
 */
final class RunTags {

    private RunTags() {}

    /** Grant a tag, honoring a held Double Tag (which duplicates the next tag gained). */
    static void grantTag(Run r, String key) {
        if (key == null) return;
        int copies = r.state.tags.remove("tag_double") ? 2 : 1; // Double Tag duplicates the next tag
        for (int i = 0; i < copies; i++) applyTag(r, key);
    }

    /** Apply a tag: IMMEDIATE effects resolve now; the rest are held for their trigger
     *  (ON_SHOP / ON_BOSS_DEFEAT / NEXT_BLIND), resolved at those moments. */
    private static void applyTag(Run r, String key) {
        if (TagCatalog.timing(key) == TagCatalog.Timing.IMMEDIATE) {
            applyImmediateTag(r, key);
        } else {
            r.state.tags.add(key);
        }
    }

    /** IMMEDIATE tags: resolve their data {@link Effect}s on claim (Economy/Speed/Handy/Garbage/Orbital/Top-Up). */
    private static void applyImmediateTag(Run r, String key) {
        applyTagEffects(r, TagCatalog.get(key));
    }

    /** Apply a tag's data effects through the run-loop interpreter (null/effectless tags are a no-op). */
    private static void applyTagEffects(Run r, TagCatalog.Tag tag) {
        if (tag == null) return;
        EvaluationContext ctx = r.runLoopContext();
        for (Effect e : tag.effects()) RunLoopRules.applyRunLoopEffect(r, e, ctx);
    }

    /** NEXT_BLIND tags, applied at blind start (Juggle: +3 hand size — a data AdjustHandSize effect). */
    static void applyBlindTags(Run r) {
        applyHeldTagsOfTiming(r, TagCatalog.Timing.NEXT_BLIND);
    }

    /** ON_SHOP tags resolve as data when the shop opens (packs / free-jokers / voucher / coupon / d6). */
    static void applyShopTags(Run r) {
        if (r.shop == null) return;
        applyHeldTagsOfTiming(r, TagCatalog.Timing.ON_SHOP);
    }

    /** Resolve and consume every held tag of {@code timing}, applying its data {@link Effect}s through the
     *  shared run-loop interpreter. The one place held tags turn into behaviour. */
    private static void applyHeldTagsOfTiming(Run r, TagCatalog.Timing timing) {
        List<String> held = r.state.tags.stream().filter(t -> TagCatalog.timing(t) == timing).toList();
        for (String key : held) {
            r.state.tags.remove(key); // consume one instance
            applyTagEffects(r, TagCatalog.get(key));
        }
    }

    /** Voucher Tag: draw the next voucher from the same game-long voucher queue and add it as an extra
     *  shop slot (BMP: get_next_voucher_key(true) + card_limit + 1). Persists across rerolls this visit. */
    static void addTagVoucher(Run r) {
        String key = r.nextShowableVoucher();
        if (key == null) return;
        r.tagVouchers.add(key);
        r.shop.addVoucher(key);
    }

    /** Add a free Joker of the given rarity to the shop, drawn from a tag-only off-shop queue. */
    static void addFreeJoker(Run r, com.balatro.grammar.Rarity rarity) {
        List<String> pool = JokerLibrary.keysByRarity(rarity);
        if (pool.isEmpty()) return;
        String key = r.state.queues.queue(RngSources.tagJoker(rarity.wire()), rng -> pool.get(rng.nextInt(pool.size()))).next();
        var info = JokerLibrary.create(key).info();
        r.shop.items().add(new Shop.Item(Shop.Kind.JOKER, key, info.name(), info.description(), 0, info.rarity(), Edition.NONE));
    }

    /** Add a free base Joker with a forced edition (Foil/Holo/Poly/Negative Tag). */
    static void addFreeEditionedJoker(Run r, Edition ed) {
        String key = Shop.drawJoker(r.state.queues, r.jokerPoolForShop(), java.util.Set.of(), new java.util.HashSet<>(), false);
        var info = JokerLibrary.create(key).info();
        r.shop.items().add(new Shop.Item(Shop.Kind.JOKER, key, info.name(), info.description(), 0, info.rarity(), ed));
    }

    /** ON_BOSS_DEFEAT tags resolve as data after a boss is beaten (Investment: +$25 each). */
    static void applyBossDefeatTags(Run r) {
        applyHeldTagsOfTiming(r, TagCatalog.Timing.ON_BOSS_DEFEAT);
        // Anaglyph Deck: gain a Double Tag after defeating each Boss Blind (back.lua:111-120).
        r.state.tags.addAll(r.deckType.onBossDefeatTags());
    }

    /** Blue Seal: each held Blue-sealed card creates the Planet for this round's last-played hand, room permitting. */
    static void applyBlueSeals(Run r) {
        if (r.state.lastPlayedHandType == null) return; // nothing played this round (can't clear a blind anyway)
        String planet = PlanetCatalog.forHand(r.state.lastPlayedHandType);
        if (planet == null) return;
        for (Card c : r.state.hand) {
            if (c.seal != Seal.BLUE) continue;
            if (r.state.consumables.size() >= r.state.consumableSlots) return; // out of room: the rest fizzle
            r.state.consumables.add(planet);
        }
    }

    /** Purple Seal: each purple-sealed card discarded creates a random Tarot, room permitting. */
    static void applyPurpleSeals(Run r, int count) {
        for (int i = 0; i < count; i++) {
            r.apply(new com.balatro.engine.exec.Command.Create(
                    new com.balatro.grammar.CreateSpec(com.balatro.grammar.CreateSpec.Kind.TAROT)));
        }
    }
}
