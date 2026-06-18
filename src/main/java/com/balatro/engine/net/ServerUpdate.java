package com.balatro.engine.net;

import com.balatro.engine.scoring.ReplayEntry;
import java.util.List;

/**
 * The server's authoritative response to a client intent — the one message type
 * that flows server -> client after an action.
 *
 * <p>{@code replay} is the scoring event stream the client *animates*; it's how
 * the snappy feel works (spec §3/§5): the client starts the animation instantly
 * on click and the authoritative numbers arrive here, well within the animation
 * window. {@code accepted=false} + {@code rejection} means the intent was invalid
 * and authoritative state is unchanged.
 */
public record ServerUpdate(
        boolean accepted,
        String rejection,
        ClientView view,
        List<ReplayEntry> replay) {
}
