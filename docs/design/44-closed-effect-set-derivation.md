# 44 — Deriving the closed effect set (by enumeration)

> We stop guessing the verb set and **derive it.** Every card's effect already is one of a *closed set of
> typed constructs* in the code — `EffectTemplate.Op`, `Consumable.Effect`, the `BossBlind` fields,
> `RunMod`, the seal/enhancement/edition tables. Bucketing those types covers **every card by
> construction.** Each row lands in: a **verb**, a **status-slot** (the commitment), or **not a state
> delta** (a constraint or a reactive interception — a different binding kind).

## The buckets that fell out

### ① `Modify(selector.slot, op, value)` — the workhorse

Everything that writes a number or a property. The enumeration shows how much lands here:

| Source construct | becomes |
|---|---|
| `CHIPS / MULT / XMULT / POW_MULT / HELD_MULT` | `Modify(scoring.<x>, add\|mul)` |
| `DOLLARS`, seal Gold, enh Gold(held), Generate.money, boss `dollarsPerCard`, Penny Pincher | `Modify(run.money, add\|set\|×)` |
| `LEVEL_UP_HAND`, Planets, Black Hole, **The Arm (down)** | `Modify(pokerHand[..].level, add ±)` |
| `MUTATE_CARD`, Tarot `Enhance`, Vampire(strip), Hiker(+chips), seal-add | `Modify(card.<enhancement\|edition\|seal\|rank\|suit\|chips>, set\|add)` |
| `Consumable.JokerEdition`, Wheel/Hex/Ectoplasm | `Modify(joker.edition, set)` |
| `Consumable.ConvertHand` (Sigil/Ouija) | `Modify(cardsMatching(inHand).<suit\|rank>, set)` (bulk) |
| `Consumable.LevelAllHands` | `Modify(allPokerHands.level, add)` (bulk) |
| `mods` (Value.Var): hands, discards, handSize, slots, rates, interestCap, minMoney | `Modify(run.<var>)` |
| boss `halveBase`, `reqMult`, `mods` (set hands/discards/size) | `Modify(scoring.* \| blind.requirement \| run.*)` |
| enh Bonus/Mult/Stone, edition Foil/Holo/Poly, enh Steel(held) | `Modify(scoring.<chips\|mult\|xmult>)` |
| Oops! All 6s | `Modify(run.probabilityNumerator, ×2)` |
| Crimson Heart, Chicot, Luchador, boss debuffs, face-down, Cerulean forced-select | `Modify(<joker\|card>.<status>, set)` — **needs status slots, see ③** |
| Hook (discard 2), Serpent (draw 3), forced discard | `Modify(card.location, set, …)` — **zones are slots, see ④** |

**Verdict:** `Modify` is most of the game — *if* we accept status slots and zone-as-slot.

### ② `Create(spec)` — new entity
`CREATE` (8 Ball/Cartomancer/Riff-Raff/Marble/Certificate), all generative tarots/spectrals, `Generate.add`,
seal Blue/Purple (create Planet/Tarot), tags that spawn packs/jokers, Diet Cola / Anaglyph (create Tag).

### ③ `Destroy(selector)` — permanent removal (raises `CARD_DESTROYED`)
`DESTROY_SCORED/DISCARDED` (Sixth Sense/Trading), Tarot `Destroy` (Hanged Man), `Generate.destroyInHand`
(Immolate/Familiar/Grim), Glass shatter, Hex/Ankh/Madness/Ceremonial (destroy jokers), self-destruct
(Gros Michel, Mr Bones consume, Invisible). *Distinct from a discard — destroy leaves the deck forever.*

### ④ `Copy(selector → dest)` — duplicate
`COPY_SCORED` (DNA → deck), `CopySelected` (Cryptid → deck), `CopyRandomJoker` (Ankh → row), Invisible
(joker → row), Perkeo (consumable → slot), `CopyLastConsumable` (Fool). **`OverwriteSelected` (Death)** =
copy one card's attributes onto another — a *copy of slots*, so it lives here too.

### ⑤ `Retrigger(selector, n)`
`REPETITIONS`, seal Red, Hack/Mime/Sock&Buskin/Dusk/Seltzer. The one control-flow verb.

## What the enumeration *forced into the open*

These are the things "five verbs" was hiding. Each is a real commitment or a real gap:

**A. Status slots (the commitment behind ①).** For `Modify` to absorb the status effects, these must be
real slots, and here is the **complete list** the content needs:
`card.{debuffed, faceDown, forcedSelected, isWild, location}`,
`joker.{disabled, occupiesSlot (Negative), eternal/perishable/rental}`,
`run.{outcome, probabilityNumerator}`, `pokerHand.level`. If a status isn't in this list, it's a gap.

**B. Zones are a slot.** `card.location ∈ {deck, hand, played, discard, removed}`. Draw = `Modify(location,
hand)`; **discard** = `Modify(location, discard)` (temporary — returns next round); destroy = ③ (permanent).
This is what makes Hook/Serpent and "first discard" fit. *Discard ≠ Destroy* — different target zone.

**C. `Reorder` collapses too.** First take was "a 6th verb." It isn't: a joker's **`position` is a slot**
(it has to be — slot order *is* scoring order), so Amber Acorn = `Modify(jokers.position, set, <shuffle>)`
+ `Modify(jokers.faceDown, set, true)`. The bijection invariant is an engine concern (the row is an
ordered list), not a new instruction — and it's doubly confirmed because the **player** reorders jokers by
drag-drop, which is the *same* `Modify(position)` via an intent. So: `position` joins the slot list, and
the verb set stays **five**.

**D. Two-slot ops: `Swap` / `Balance`.** Swap chips↔mult; average them (Plasma). Read two slots, write two.
Not a single `Modify`. Treat as a `Modify`-family op (`swap(slotA, slotB)`) or sugar-with-a-temp — minor,
but it doesn't reduce to the plain form.

**E. NOT state deltas — a different binding entirely:**
- **Constraints** — boss legality (Psychic "play 5", Mouth "one type", Eye "no repeats", Cerulean "must
  include the forced card"). These change *nothing*; they gate the **player's intent**. They are a
  `Condition` bound to an **intent**, not an `Effect` bound to a trigger. **A second binding kind.**
- **Interceptions** — Mr Bones "survive the loss," Luchador "disable boss on sell." These reduce to
  `Modify(outcome…)`/`Modify(boss.disabled…)` on a **reactive trigger** (`WOULD_FAIL_BLIND`, `SELL_SELF`).
  So they're not new verbs — they're new **triggers** whose effect is a normal `Modify`/`Destroy`.

## The derived closed set

After bucketing every effect construct in the codebase:

```
EFFECTS (state deltas, bound to a trigger):
  ● Modify(selector.slot, op, value)      — score, resources, hand-eval knobs, card/joker props & status,
                                            zones (location), and position (Reorder collapses here)
  ● Create(spec)
  ● Destroy(selector)
  ● Copy(selector → dest)                  — incl. Overwrite (copy of slots)
  ● Retrigger(selector, n)
  ◆ Swap/Balance                           — two-slot (relational) Modify variant; minor; 2 cards

REQUIRES (the commitment):
  – a fixed status-slot list (A) + card.location zones (B) + joker.position (C)

NOT EFFECTS (second binding kind — not verbs):
  – Constraints   : Condition bound to a player INTENT (boss legality, forced selection, eternal-can't-sell)
  – Interceptions : a normal Modify/Destroy on a reactive TRIGGER (Mr Bones, Luchador)
```

**Five verbs, derived.** The only thing that resists `Modify` is the relational two-slot `Swap/Balance`
(2 cards) — a decision, not a discovery.

So the honest answer to *"are we sure it's the closed set?"* — **five core verbs hold**, with two caveats
the enumeration exposed: **`Reorder`** is a real one-off the five didn't cover, and **constraints** are a
whole second binding kind (`Condition` on an `intent`) that isn't an effect at all. Everything else
collapses, *provided* we commit to the status-slot list (A) and zones-as-slot (B).

## What this buys us

- The verb set is now **derived, not asserted** — and it's small: 5 (+1 oddball +1 two-slot).
- We have the **complete status-slot list** and the **zone model** as concrete commitments to honor.
- We learned the model needs **two binding kinds**, not one: `Effect@Trigger` *and* `Condition@Intent`.
- `Reorder` and `Swap` are the only things that don't fit — small enough to make a deliberate call
  (6th verb vs escape) rather than discover late.
