package com.balatro.engine.joker;

import com.balatro.engine.exec.Command;
import java.util.List;

/**
 * What a joker contributes at a scoring moment: typed {@link Contribution}s (the count-up) plus already-
 * resolved {@link Command}s (the side-effects — destroy/copy/level/self-destruct/grant/mutate). The typed
 * replacement for the old {@code JokerEffect} mutable bag and its {@code extra} chain: the scoring axes are
 * modeled once as {@code Contribution}s, and the bag's action booleans/fields become {@code Command}s.
 *
 * <p>{@code calculate()} returns this; {@code ScoringEngine} folds the contributions and applies the
 * scoring-time commands.
 */
public record JokerResult(List<Contribution> contributions, List<Command> commands) {

    public static final JokerResult EMPTY = new JokerResult(List.of(), List.of());
}
