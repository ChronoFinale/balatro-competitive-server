package com.balatro.content;

import com.balatro.engine.game.PlanetCatalog.Planet;
import com.balatro.engine.hand.HandType;
import java.util.List;

/** The planet CONTENT (one per poker hand), compiled to {@code content/planets.json}. */
public final class PlanetDefs {

    private PlanetDefs() {}

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
}
