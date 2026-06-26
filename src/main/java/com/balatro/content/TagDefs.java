package com.balatro.content;

import com.balatro.engine.game.TagCatalog.Tag;
import com.balatro.engine.game.TagCatalog.Timing;
import com.balatro.engine.i18n.Loc;
import com.balatro.engine.card.Edition;
import com.balatro.grammar.Effect;
import com.balatro.grammar.Hand;
import com.balatro.grammar.Modify;
import com.balatro.grammar.PackKind;
import com.balatro.grammar.PackSize;
import com.balatro.grammar.Value;
import java.util.ArrayList;
import java.util.List;

/** The tag CONTENT, compiled to {@code content/tags.json}. Descriptions come from localization (en.json). */
public final class TagDefs {

    private TagDefs() {}

    public static List<Tag> authored() {
        List<Tag> t = new ArrayList<>();
        // Free-joker tags are data: a free Joker (by rarity, or random with an edition) in the next shop.
        add(t, "tag_uncommon", "Uncommon Tag", true, Timing.ON_SHOP, joker("Uncommon"));
        add(t, "tag_rare", "Rare Tag", true, Timing.ON_SHOP, joker("Rare"));
        add(t, "tag_negative", "Negative Tag", false, Timing.ON_SHOP, editioned(Edition.NEGATIVE));
        add(t, "tag_foil", "Foil Tag", true, Timing.ON_SHOP, editioned(Edition.FOIL));
        add(t, "tag_holo", "Holographic Tag", true, Timing.ON_SHOP, editioned(Edition.HOLOGRAPHIC));
        add(t, "tag_polychrome", "Polychrome Tag", true, Timing.ON_SHOP, editioned(Edition.POLYCHROME));
        add(t, "tag_investment", "Investment Tag", true, Timing.ON_BOSS_DEFEAT,
                new Effect.Write(new Modify(Value.Var.MONEY, Effect.Operation.ADD, new Value.Const(25))));
        add(t, "tag_boss", "Boss Tag", true, Timing.HELD);
        // Pack tags are data: a booster pack appears in the next shop — AddPack(kind, size).
        add(t, "tag_standard", "Standard Tag", false, Timing.ON_SHOP, pack(PackKind.STANDARD, PackSize.MEGA));
        add(t, "tag_charm", "Charm Tag", true, Timing.ON_SHOP, pack(PackKind.ARCANA, PackSize.MEGA));
        add(t, "tag_meteor", "Meteor Tag", false, Timing.ON_SHOP, pack(PackKind.CELESTIAL, PackSize.MEGA));
        add(t, "tag_buffoon", "Buffoon Tag", false, Timing.ON_SHOP, pack(PackKind.BUFFOON, PackSize.MEGA));
        add(t, "tag_ethereal", "Ethereal Tag", false, Timing.ON_SHOP, pack(PackKind.SPECTRAL, PackSize.NORMAL));
        add(t, "tag_coupon", "Coupon Tag", true, Timing.ON_SHOP, new Effect.ShopFlag(Effect.ShopFlag.Flag.COUPON));
        add(t, "tag_double", "Double Tag", true, Timing.HELD);
        add(t, "tag_juggle", "Juggle Tag", true, Timing.NEXT_BLIND, new Effect.Write(Modify.add(Hand.SIZE, 3)));
        add(t, "tag_d_six", "D6 Tag", true, Timing.ON_SHOP, new Effect.ShopFlag(Effect.ShopFlag.Flag.D6));
        add(t, "tag_voucher", "Voucher Tag", true, Timing.ON_SHOP, new Effect.AddShopVoucher());
        // Economy: gain min(money, 40) — AdjustMoney(ADD, clamp(money, 0, 40)) — a double, capped at +$40.
        add(t, "tag_economy", "Economy Tag", true, Timing.IMMEDIATE,
                new Effect.Write(new Modify(Value.Var.MONEY, Effect.Operation.ADD,
                        new Value.Clamp(new Value.RunVar(Value.Var.MONEY, 0, 1), 0, 40))));
        // The money tags are data: gain $ per run-state quantity — AdjustMoney(ADD, runVar * scale).
        add(t, "tag_skip", "Speed Tag", true, Timing.IMMEDIATE, gain(Value.Var.BLINDS_SKIPPED, 5));
        add(t, "tag_orbital", "Orbital Tag", false, Timing.IMMEDIATE, new Effect.LevelHands(
                Effect.LevelHands.Scope.MOST_PLAYED, new com.balatro.grammar.Value.Const(3)));
        add(t, "tag_handy", "Handy Tag", false, Timing.IMMEDIATE, gain(Value.Var.HANDS_PLAYED_TOTAL, 1));
        add(t, "tag_garbage", "Garbage Tag", false, Timing.IMMEDIATE, gain(Value.Var.CARDS_DISCARDED_TOTAL, 1));
        // Top-Up: create up to 2 free Common Jokers for the player (real game: spawn_jokers = 2). One
        // Create verb — a JOKER spec drawn from the TOPUP queue with no dedup (the spawn order is raw).
        add(t, "tag_top_up", "Top-Up Tag", false, Timing.IMMEDIATE,
                new Effect.Create(com.balatro.grammar.CreateSpec.jokers(
                        2, "Common", com.balatro.grammar.CreateSpec.JokerStream.TOPUP, false)));
        return t;
    }

    /** Gain ${@code scale} per unit of a run-state variable (Speed/Handy/Garbage). */
    private static Effect gain(Value.Var which, double scale) {
        return new Effect.Write(new Modify(Value.Var.MONEY, Effect.Operation.ADD, new Value.RunVar(which, 0, scale)));
    }

    /** A booster pack in the next shop (Charm/Meteor/Buffoon/Standard/Ethereal). */
    private static Effect pack(PackKind kind, PackSize size) {
        return new Effect.AddPack(kind, size);
    }

    /** A free Joker of a rarity in the next shop (Rare/Uncommon) — Create into the SHOP, by rarity. */
    private static Effect joker(String rarity) {
        return new Effect.Create(com.balatro.grammar.CreateSpec.shopJoker(
                rarity, com.balatro.engine.card.Edition.NONE));
    }

    /** A free random Joker with an edition in the next shop (Foil/Holo/Polychrome/Negative). */
    private static Effect editioned(com.balatro.engine.card.Edition ed) {
        return new Effect.Create(com.balatro.grammar.CreateSpec.shopJoker(null, ed));
    }

    private static void add(List<Tag> t, String key, String name, boolean ante1, Timing timing, Effect... effects) {
        t.add(new Tag(key, name, Loc.text(key), ante1, timing, List.of(effects)));
    }
}
