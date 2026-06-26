package com.balatro.grammar;

/**
 * The booster-pack kinds — a closed grammar set mirroring {@code PackCatalog.Kind}. Kept in the grammar (not
 * the game layer) so the model stays dependency-free; the engine resolves the name at the boundary.
 */
public enum PackKind { ARCANA, CELESTIAL, SPECTRAL, BUFFOON, STANDARD }
