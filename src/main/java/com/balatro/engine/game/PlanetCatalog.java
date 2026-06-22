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
        // Runtime loads from /content/planets.json; authored() is the codegen source + fallback.
        List<Planet> planets;
        try {
            planets = com.balatro.engine.content.ContentStore.planets();
        } catch (RuntimeException e) {
            planets = authored();
        }
        for (Planet p : planets) BY_KEY.put(p.key(), p);
    }

    /** The DSL authoring source for {@code content/planets.json} (also the fallback). */
    public static List<Planet> authored() {
        return List.of(
                new Planet("c_pluto", "Pluto", HandType.HIGH_CARD),
                new Planet("c_mercury", "Mercury", HandType.PAIR),
                new Planet("c_uranus", "Uranus", HandType.TWO_PAIR),
                new Planet("c_venus", "Venus", HandType.THREE_OF_A_KIND),
                new Planet("c_saturn", "Saturn", HandType.STRAIGHT),
                new Planet("c_jupiter", "Jupiter", HandType.FLUSH),
                new Planet("c_earth", "Earth", HandType.FULL_HOUSE),
                new Planet("c_mars", "Mars", HandType.FOUR_OF_A_KIND),
                new Planet("c_neptune", "Neptune", HandType.STRAIGHT_FLUSH),
                new Planet("c_planet_x", "Planet X", HandType.FIVE_OF_A_KIND),
                new Planet("c_ceres", "Ceres", HandType.FLUSH_HOUSE),
                new Planet("c_eris", "Eris", HandType.FLUSH_FIVE));
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
