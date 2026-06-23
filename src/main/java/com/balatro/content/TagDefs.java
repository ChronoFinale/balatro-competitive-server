package com.balatro.content;

import com.balatro.engine.game.TagCatalog.Tag;
import com.balatro.engine.game.TagCatalog.Timing;
import com.balatro.engine.i18n.Loc;
import java.util.ArrayList;
import java.util.List;

/** The tag CONTENT, compiled to {@code content/tags.json}. Descriptions come from localization (en.json). */
public final class TagDefs {

    private TagDefs() {}

    public static List<Tag> authored() {
        List<Tag> t = new ArrayList<>();
        add(t, "tag_uncommon", "Uncommon Tag", true, Timing.ON_SHOP);
        add(t, "tag_rare", "Rare Tag", true, Timing.ON_SHOP);
        add(t, "tag_negative", "Negative Tag", false, Timing.ON_SHOP);
        add(t, "tag_foil", "Foil Tag", true, Timing.ON_SHOP);
        add(t, "tag_holo", "Holographic Tag", true, Timing.ON_SHOP);
        add(t, "tag_polychrome", "Polychrome Tag", true, Timing.ON_SHOP);
        add(t, "tag_investment", "Investment Tag", true, Timing.ON_BOSS_DEFEAT);
        add(t, "tag_voucher", "Voucher Tag", true, Timing.ON_SHOP);
        add(t, "tag_boss", "Boss Tag", true, Timing.HELD);
        add(t, "tag_standard", "Standard Tag", false, Timing.ON_SHOP);
        add(t, "tag_charm", "Charm Tag", true, Timing.ON_SHOP);
        add(t, "tag_meteor", "Meteor Tag", false, Timing.ON_SHOP);
        add(t, "tag_buffoon", "Buffoon Tag", false, Timing.ON_SHOP);
        add(t, "tag_ethereal", "Ethereal Tag", false, Timing.ON_SHOP);
        add(t, "tag_coupon", "Coupon Tag", true, Timing.ON_SHOP);
        add(t, "tag_double", "Double Tag", true, Timing.HELD);
        add(t, "tag_juggle", "Juggle Tag", true, Timing.NEXT_BLIND);
        add(t, "tag_d_six", "D6 Tag", true, Timing.ON_SHOP);
        add(t, "tag_economy", "Economy Tag", true, Timing.IMMEDIATE);
        add(t, "tag_skip", "Speed Tag", true, Timing.IMMEDIATE);
        add(t, "tag_orbital", "Orbital Tag", false, Timing.IMMEDIATE);
        add(t, "tag_handy", "Handy Tag", false, Timing.IMMEDIATE);
        add(t, "tag_garbage", "Garbage Tag", false, Timing.IMMEDIATE);
        add(t, "tag_top_up", "Top-Up Tag", false, Timing.IMMEDIATE);
        return t;
    }

    private static void add(List<Tag> t, String key, String name, boolean ante1, Timing timing) {
        t.add(new Tag(key, name, Loc.text(key), ante1, timing));
    }
}
