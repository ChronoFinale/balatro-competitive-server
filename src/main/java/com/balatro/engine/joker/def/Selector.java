package com.balatro.engine.joker.def;

import com.balatro.dsl.*;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Who an {@link Effect} targets — the missing piece that lets a card effect address something other than
 * the implicit scoring focus. Jokers score against the focus (the scored card) and pass no selector; a
 * consumable says explicitly which cards or jokers its effect hits. Pure data — the action interpreter in
 * {@code Run} resolves a selector against the live run (hand, RNG, queues); serialized with a {@code "type"}
 * discriminator like {@link Effect}/{@link Condition}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Selector.Focus.class, name = "focus"),
    @JsonSubTypes.Type(value = Selector.Selected.class, name = "selected"),
    @JsonSubTypes.Type(value = Selector.AllInHand.class, name = "allInHand"),
    @JsonSubTypes.Type(value = Selector.RandomInHand.class, name = "randomInHand"),
    @JsonSubTypes.Type(value = Selector.RandomJoker.class, name = "randomJoker"),
})
public sealed interface Selector {

    /** The scored card in focus — a joker's implicit target while a hand is counting up (Midas, Vampire). */
    record Focus() implements Selector {}

    /** The cards the player chose as targets (resolved by uid) — Magician, Hanged Man, Death, Cryptid. */
    record Selected() implements Selector {}

    /** Every card currently in hand — Sigil / Ouija convert the whole hand. */
    record AllInHand() implements Selector {}

    /** {@code count} random cards drawn from hand — Immolate / Familiar / Grim destroy at random. */
    record RandomInHand(int count) implements Selector {}

    /** One random owned joker — Wheel of Fortune / Ectoplasm / Hex / Ankh. */
    record RandomJoker() implements Selector {}
}
