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
            planets = com.balatro.content.PlanetDefs.authored(); // content authored in com.balatro.content.PlanetDefs
        }
        for (Planet p : planets) BY_KEY.put(p.key(), p);
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
