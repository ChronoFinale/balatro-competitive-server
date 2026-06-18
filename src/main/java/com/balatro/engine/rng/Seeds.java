package com.balatro.engine.rng;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Match-seed generation in Balatro's own seed space, so a seed our server issues is a <b>valid,
 * typeable Balatro seed</b>. Balatro's {@code random_string}/{@code generate_starting_seed}
 * (misc_functions.lua:270, game.lua:219) draw 8 characters from the alphabet {@code 1-9, A-N, P-Z} —
 * deliberately excluding the confusable {@code 0} and {@code O}. The seed <i>value</i> is entropy (there's
 * no "their seed" to match), but its FORMAT is — combined with the bit-identical seed→RNG-state derivation
 * (pseudohash, verified in BalatroPrngTest / Balatro4jCrossCheckTest), a match seed can be reproduced and
 * verified in real Balatro.
 */
public final class Seeds {

    private Seeds() {}

    /** Balatro's seed alphabet: digits 1-9, letters A-N and P-Z (no 0, no O). 34 characters. */
    public static final String ALPHABET = "123456789ABCDEFGHIJKLMNPQRSTUVWXYZ";

    /** Balatro seeds are 8 characters. */
    public static final int LENGTH = 8;

    /** A fresh random Balatro-format seed (thread-local entropy). */
    public static String random() {
        return random(ThreadLocalRandom.current());
    }

    /** A random Balatro-format seed from {@code rng} (testable with a fixed Random). */
    public static String random(Random rng) {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(rng.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Whether {@code seed} is a well-formed Balatro seed (length 8, all chars in {@link #ALPHABET}). */
    public static boolean isValid(String seed) {
        if (seed == null || seed.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < seed.length(); i++) {
            if (ALPHABET.indexOf(seed.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
