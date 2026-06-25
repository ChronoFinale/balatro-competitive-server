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
    @JsonSubTypes.Type(value = Selector.Discarded.class, name = "discarded"),
    @JsonSubTypes.Type(value = Selector.Self.class, name = "self"),
    @JsonSubTypes.Type(value = Selector.OtherJoker.class, name = "otherJoker"),
    @JsonSubTypes.Type(value = Selector.LastConsumable.class, name = "lastConsumable"),
    @JsonSubTypes.Type(value = Selector.RandomConsumable.class, name = "randomConsumable"),
    @JsonSubTypes.Type(value = Selector.Bound.class, name = "bound"),
    @JsonSubTypes.Type(value = Selector.Others.class, name = "others"),
})
public sealed interface Selector {

    /** The scored card in focus — a joker's implicit target while a hand is counting up (Midas, Vampire,
     *  Sixth Sense's destroy). */
    record Focus() implements Selector {}

    /** The cards the player chose as targets (resolved by uid) — Magician, Hanged Man, Death, Cryptid. */
    record Selected() implements Selector {}

    /** Every card currently in hand — Sigil / Ouija convert the whole hand. */
    record AllInHand() implements Selector {}

    /** {@code count} random cards drawn from hand — Immolate / Familiar / Grim destroy at random. */
    record RandomInHand(int count) implements Selector {}

    /** One random owned joker — Wheel of Fortune / Ectoplasm / Hex / Ankh. */
    record RandomJoker() implements Selector {}

    /** The cards being discarded this event — Trading Card destroys the discard (PRE_DISCARD). */
    record Discarded() implements Selector {}

    /** The joker emitting the effect — it consumes itself (Gros Michel, Ice Cream, Ramen, Pizza on PvP end). */
    record Self() implements Selector {}

    /** Another owned joker, chosen by {@code scope} ({@code "RIGHT_NEIGHBOR"} = Ceremonial Dagger,
     *  {@code "RANDOM_OTHER"} = Madness); {@code gainMult} rides the Ceremonial case (gain 2× the victim's
     *  sell value as Mult). Resolved by Run's blind-select joker-destruction machinery. */
    record OtherJoker(String scope, boolean gainMult) implements Selector {}

    /** The last Tarot/Planet used this run (The Fool copies it). */
    record LastConsumable() implements Selector {}

    /** A random held consumable (Perkeo duplicates it as a slot-cap-ignoring Negative copy at shop exit). */
    record RandomConsumable() implements Selector {}

    /** The joker previously bound under {@code name} by a {@link Effect.Bind} earlier in this effect list —
     *  so several effects act on the SAME single pick (the regex-backreference of the grammar). Pure
     *  reference, not a value: it names a noun, never a number, and is single-assignment + action-scoped. */
    record Bound(String name) implements Selector {}

    /** Every owned joker EXCEPT the one bound under {@code name} — the complement of a {@link Bound} pick
     *  (Ankh/Hex: destroy all jokers but the one being copied/editioned). */
    record Others(String name) implements Selector {}
}
