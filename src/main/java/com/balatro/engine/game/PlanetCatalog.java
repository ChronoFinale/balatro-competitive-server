package com.balatro.engine.game;

import com.balatro.engine.hand.HandType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Planet consumables — each levels one poker hand. Data only (key, name, target
 * hand); the level increments live on {@link HandType}. Mirrors Balatro's planets
 * (incl. the secret three for the hidden hands).
 */
public final class PlanetCatalog {

    private PlanetCatalog() {}

    public record Planet(String key, String name, HandType hand) {
        public String description() {
            return "Levels up " + hand.display;
        }
    }

    public static final int COST = 3;

    private static final Map<String, Planet> BY_KEY = new LinkedHashMap<>();

    static {
        add("c_pluto", "Pluto", HandType.HIGH_CARD);
        add("c_mercury", "Mercury", HandType.PAIR);
        add("c_uranus", "Uranus", HandType.TWO_PAIR);
        add("c_venus", "Venus", HandType.THREE_OF_A_KIND);
        add("c_saturn", "Saturn", HandType.STRAIGHT);
        add("c_jupiter", "Jupiter", HandType.FLUSH);
        add("c_earth", "Earth", HandType.FULL_HOUSE);
        add("c_mars", "Mars", HandType.FOUR_OF_A_KIND);
        add("c_neptune", "Neptune", HandType.STRAIGHT_FLUSH);
        add("c_planet_x", "Planet X", HandType.FIVE_OF_A_KIND);
        add("c_ceres", "Ceres", HandType.FLUSH_HOUSE);
        add("c_eris", "Eris", HandType.FLUSH_FIVE);
    }

    private static void add(String key, String name, HandType hand) {
        BY_KEY.put(key, new Planet(key, name, hand));
    }

    public static Planet get(String key) {
        return BY_KEY.get(key);
    }

    public static List<String> keys() {
        return new ArrayList<>(BY_KEY.keySet());
    }

    /** The Planet key that levels {@code hand} (Blue Seal / Telescope), or null if none maps to it. */
    public static String forHand(HandType hand) {
        for (Planet p : BY_KEY.values()) {
            if (p.hand() == hand) return p.key();
        }
        return null;
    }

    /**
     * The "secret" planets (Planet X / Ceres / Eris) for the hidden hands. BMP gates these behind having
     * played their hand at least once — they are UNAVAILABLE in pools until then (get_current_pool
     * softlock, common_events.lua:2008-2011). The other nine planets are always available.
     */
    private static final java.util.Set<String> SOFTLOCKED =
            java.util.Set.of("c_planet_x", "c_ceres", "c_eris");

    public static boolean isSoftlocked(String key) {
        return SOFTLOCKED.contains(key);
    }

    /** Whether {@code key} may currently appear: always, unless it's softlocked and its hand is unplayed. */
    public static boolean available(String key, java.util.Set<HandType> playedHands) {
        Planet p = BY_KEY.get(key);
        if (p == null) {
            return false;
        }
        return !SOFTLOCKED.contains(key) || (playedHands != null && playedHands.contains(p.hand()));
    }
}
