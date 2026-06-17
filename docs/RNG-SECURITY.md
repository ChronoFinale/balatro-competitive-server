# RNG security model

How randomness must work for a **server-authoritative, open-source, competitive** Balatro engine to be
cheat-proof. Read alongside the RNG section of `CLAUDE.md` and `rng/RngSource.java`.

The one-line thesis: **security comes from a high-entropy secret key fed through a cryptographic PRF —
not from hiding the seed, not from hiding the code, and explicitly NOT from the vanilla Balatro PRNG,
which is unprivatizable and breakable by design.**

---

## 1. Threat model

**Assets**
- *Unpredictability:* no player learns future randomness (deck order, shop, boss, joker/edition drops,
  enhancements, soul/pack contents) beyond what the rules legitimately reveal.
- *Unbiasability:* no party can steer the randomness toward a favorable outcome before it is fixed.
- *Verifiable integrity:* after a match, anyone can prove the server did not adaptively manipulate
  outcomes.

**Attacker capabilities (assume all of these)**
- Full source access — Kerckhoffs's principle; the engine and PRNG are public.
- Unlimited matches; full observation of their own `ClientView` stream.
- Large offline precompute (seed-finders already evaluate billions of seeds).
- May start/abandon matches (seed-shopping) unless prevented.
- In ranked, shares one seed with the opponent and wants the future the opponent doesn't have.

**Trust boundary**
- The server process and its secret store (HSM / secrets manager) are trusted to keep the per-match key
  secret *during* a match. If the operator exfiltrates its own key, crypto cannot help — that is an
  operational/integrity problem (see §8).

**Out of scope:** server memory compromise, out-of-band human collusion (shared screen), TLS/transport
(assumed; `ClientView` must travel encrypted and be the *only* output — no debug field may leak key,
seed, counter, or deck order).

---

## 2. Why the vanilla design is breakable (and seed-finders prove it)

Today randomness is keyed entirely by an 8-character seed string:

```
pseudoseed(key) = pseudohash(key + seed)   // BalatroPrng — pure IEEE-754 float math, NOT a crypto hash
draw            = LuaJitRandom (TW223)      // a Tausworthe generator, NOT a CSPRNG
```

The entire game tree is protected by a ~`2^41` secret (8 chars over a ~35-symbol alphabet) run through
a public, non-cryptographic mixer. That is equivalent to a 41-bit key with a known keystream algorithm.
Two **independent** attacks follow, and they need **different** fixes — conflating them is the trap:

| | Attack | Mechanism | Fix |
|---|---|---|---|
| **A1** | Seed identification (key recovery by enumeration) | Precompute "first N observable events → seed" over all `2^41` seeds via the public engine; match the victim's observed deals/shop; the seed collapses in a handful of observations; the entire remaining game is then known. | **Entropy:** ≥128-bit key. No table is buildable at `2^128`. |
| **A2** | Analytic state recovery (cryptanalysis) | Even with a huge seed, a weak generator (LCG / Tausworthe / a near-linear float hash) can be *solved* for internal state from enough outputs, then run forward. No enumeration needed. | **Strength:** a PRF / CSPRNG, not a big seed on a weak generator. |

Note the composition requirement: a PRF with a 41-bit key is still brute-forceable (`2^41` HMACs), and a
256-bit key on TW223 is still potentially state-recoverable. **You need both — high entropy AND a
cryptographic generator.** Together, the keystream is computationally indistinguishable from random
given the outputs, which is exactly what kills A1 and A2 simultaneously.

> We do not claim a specific break of `pseudohash`/TW223. The point is the conservative posture: these
> primitives were designed for *shareable, reproducible fun seeds*, not to resist an adversary. Never
> make competitive integrity depend on a non-CSPRNG not being broken.

**The unavoidable tension:** you cannot have *both* bit-exact-vanilla RNG *and* cryptographic
unpredictability — vanilla's PRNG *is* the thing seed-finders eat. The two roles must split (§7).

---

## 3. Design principles

1. **Kerckhoffs:** security rests on the key, never the secrecy of the algorithm or the seed *value*.
   Privatising the RNG repo is security-through-obscurity and buys nothing (the vanilla algorithm is the
   shipped game's, already public; your derivation leaks on first decompile).
2. **The secret is a key (a value), not code.** A 256-bit `master_secret` in a secrets manager / HSM.
   The entire engine stays open source.
3. **PRF, not PRNG.** Every draw is the output of a keyed pseudorandom function. Observing outputs
   reveals nothing about the key or any unobserved draw.
4. **Determinism is preserved.** Given the key, every draw is recomputable — so replay, variance
   reduction ("The Order"), and post-hoc verification all still work. We swap the *primitive*, not the
   model.

---

## 4. Construction

### 4.1 Key hierarchy

```
master_secret                              256-bit, long-lived, in HSM/secrets-manager, never logged
match_key   = HMAC(master_secret, match_id ‖ player_nonces)     per-match, server-only during play
stream_key(ctx) = HKDF-Expand(match_key, domain_sep(ctx))       per-RngSource-context sub-key
draw_i(ctx)     = PRF(stream_key(ctx), counter_i)               i-th value of that stream (uniform bytes)
```

- `HMAC-SHA-256` / `HKDF-SHA-256` are the boring, correct defaults. A stream cipher in counter mode
  (ChaCha20 / AES-CTR) keyed by `stream_key` is an equally fine source for `draw_i`.
- `match_id ‖ player_nonces`: bind the key to the specific match and to each player's contributed nonce
  (§5) so the server cannot pre-grind a favorable key and a player cannot bias it.

### 4.2 Mapping the model onto contexts

The existing `RngSource` model maps cleanly — only the mixer changes:

- **Scope** (GAME_LONG / PER_ANTE / PER_BLIND) and **PvpMode** (PER_HAND) → fields of `ctx`.
- **Selection** (SEQUENTIAL / COMPOSITION / WEIGHTED_COUNT) → how `draw_i` is consumed from the stream.
- **"The Order" variance reduction & PvP fairness** → unchanged: identical `ctx` for both players yields
  the same stream, so shared-seed symmetry holds. The variance structure lives in `ctx` derivation,
  which is independent of the primitive swap.

### 4.3 Distribution mapping (don't reintroduce bias)

- Uniform index in `[0,n)`: **rejection sampling** or wide-reduction over the uniform bytes — never raw
  `mod n` (modulo bias is observable and a security engineer will flag it).
- Shuffles: Fisher–Yates driven by the stream.
- Weighted/`WEIGHTED_COUNT`: standard inverse-CDF over uniform draws.

### 4.4 Domain separation

`domain_sep(ctx)` must be **unambiguous** — length-prefixed or strictly delimited encoding of every
field (`matchId`, scope, ante, blind, streamId, pvpHand, counter). Two different contexts must never
alias to the same `stream_key` (e.g. ante-1 boss stream vs ante-11 shop stream). This gives independent
sub-streams: outputs of one reveal nothing about another (PRF), a form of intra-match compartmentation.

---

## 5. Fairness protocol (commit–reveal)

Goal: prove the **server** did not adaptively choose outcomes, and prevent any single party (server
included) from biasing the key.

**Pre-match (commit phase) — all commitments exchanged before any reveal:**
- Server: pick `r_s`, publish `c_s = H(r_s)` (and a binding `commit = H(match_key ‖ match_id)`).
- Each player: pick `r_p`, publish `c_p = H(r_p)`.

**Match start:** players reveal `r_p`. `match_key` incorporates `master_secret`, `match_id`, and the
`r_p` — so the server can compute it (it has `master_secret`) but **players cannot** (they lack
`master_secret`), preserving in-match unpredictability. The server is bound by `commit`, published
before play.

**Post-match (reveal phase):** server reveals `match_key` (and `r_s`). Anyone verifies:
1. `H(match_key ‖ match_id) == commit` — the key was fixed before play (no adaptive manipulation).
2. Re-running the **public** engine on `match_key` + the match's intent log reproduces every
   `ClientView` the player saw.

**What this buys / doesn't:**
- ✅ Server cannot adapt outcomes after commit; players can verify the math; key is unbiasable as long
  as one honest party committed blind.
- ✅ Revealing one match's `match_key` tells you nothing about another (each is an independent PRF
  output of `master_secret`); `master_secret` itself is never revealed.
- ❌ Crypto does **not** stop a malicious operator from *leaking its own `match_key`* to a colluding
  player mid-match. That is an operational/integrity problem (HSM access control, audit, the operator's
  own incentive not to destroy their game) — not something a protocol fixes. State this plainly.

**Verification needs an authenticated transcript.** "Replay matches what I saw" is vacuous unless the
player holds a record of what the server *actually sent*. Sign each `ServerUpdate` (or Merkle-log the
stream and commit to the root) so the server cannot later repudiate its messages. The client retains
this transcript for the dispute/verify path.

---

## 6. Seed-shopping & operational defense-in-depth

- `match_key` is server-bound and player-uncomputable, so **abandon-and-restart yields a fresh,
  unpredictable match** — there is no known space to shop.
- In ranked, the shared seed is **symmetric**: a "good seed" helps both players equally, so fishing for
  variance confers no edge by construction.
- Defense-in-depth anyway: matchmaking, entry stakes, rate limits on match creation, abandonment
  penalties.

---

## 7. The vanilla PRNG's role: validation oracle, never competitive RNG

`rng/vanilla/` (the bit-exact `BalatroPrng` / `LuaJitRandom`) stays — but strictly as a **reference
oracle** for the thin-client / BalatroBot diff harness: run with *known* seeds in dev to prove the real
Balatro renderer is faithful to the engine. It is never on the competitive path. This resolves the
"I need bit-exact to validate the bridge" requirement without it ever being the security boundary.

The codebase therefore bifurcates the RNG:

| | Competitive RNG | Vanilla oracle (`rng/vanilla/`) |
|---|---|---|
| Purpose | Cheat-proof live randomness | Validate the thin client renders correctly |
| Primitive | Keyed PRF / CSPRNG (HMAC/HKDF/ChaCha) | `pseudohash` + TW223 (bit-exact Balatro) |
| Entropy | 256-bit `master_secret`-derived key | Known 8-char seed (on purpose) |
| Exposure | Key secret until post-match reveal | Fully public, dev-only |

---

## 8. What this model does NOT solve (honest boundary)

- A compromised/malicious **operator** leaking its own `match_key`. Mitigation is operational (HSM,
  least-privilege, audit), plus the operator's incentive; not crypto.
- Out-of-band human collusion.
- Bugs that leak future state into `ClientView` (deck order, unrevealed shop). This is an *info-hiding*
  invariant enforced by `ClientView`'s structure and tests — orthogonal to the PRNG, but it must hold or
  none of the above matters.

---

## 9. Implementation checklist

- [ ] Add a `CryptoRngBackend` behind the `RngSource` model: `match_key`/`stream_key` derivation
      (HKDF-SHA-256), `draw_i = PRF(stream_key, counter)`, rejection-sampled mappings. Keep `RngSource`'s
      Scope/PvpMode/Selection API unchanged.
- [ ] `master_secret` from env/secrets-manager; never construct a competitive `Run` from a low-entropy
      string seed (the current `new Run(ruleset, "BRIDGE001")` path is dev-only).
- [ ] Commitment published at match creation; `match_key` reveal gated on match finalization.
- [ ] Sign / Merkle-commit each `ServerUpdate`; client retains transcript.
- [ ] Ship a standalone verifier (key + intent log + public engine → recomputed `ClientView`s).
- [ ] Audit `ClientView` for any field that leaks seed/key/counter/deck order (info-hiding invariant).
- [ ] Keep `rng/vanilla/` strictly out of the competitive path. *(Add the architecture/lint test
      **together with** `CryptoRngBackend` — it's a regression tripwire for later, and there is nothing
      to assert until a separate competitive path actually exists. Today everything uses the vanilla
      PRNG, so this item is premature on its own.)*

---

## 10. Rationale & performance (plain language)

### Why Balatro's seed is so tiny — and why that's *correct* for Balatro

A Balatro seed is a **share code, not a password.** Think of a Wordle number or a YouTube video ID:
something a human types, screenshots, and posts so a friend gets the *exact same thing*. You want those
**short on purpose.** `7LB2WVPK` (8 characters) is about the most a person will reliably copy by hand.

Two CS-level reasons it's fine there and fatal here:

1. **A password needs to be unguessable; a share code doesn't.** Balatro is single-player — there is no
   opponent, so guessing a seed only "cheats" yourself. When there's nothing to defend, the seed is just
   a label for a run. The moment money/ranked is on the line, that same label becomes a *key*, and keys
   have a completely different job: stay unguessable.
2. **"How many possible seeds" is an entropy question.** Bits of entropy = how many coin-flips of
   randomness. 8 characters ≈ **41 bits** ≈ flipping a coin 41 times ≈ ~2 *trillion* possibilities.
   - To a *human* that's effectively infinite variety — you'll never see the same run twice. Perfect for
     a single-player game.
   - To a *computer* checking billions per second, 2 trillion is small: you can try **every single one**
     in minutes-to-hours. That's literally what seed-finder tools do. A 41-bit secret is a 4-digit bike
     lock — fine for a gym locker, useless for a bank.

   **256 bits** is the bank vault: more possibilities than there are atoms in the observable universe. No
   computer that will ever exist can try them all. Nothing changed about the *idea* — we just made the
   space so large that "try them all" stops being a strategy.

So Balatro didn't make a mistake. It solved *"give players a shareable, reproducible, varied run"* — and
short seeds are the **right** answer to that. Our mistake would be reusing that answer for a *different*
question (*"keep an adversary from predicting the future"*). Same tool, wrong job.

### Will real crypto slow the game down? No — and here's the intuition

**How often do we roll dice?** Over an entire 8-ante run (which takes a human the better part of an
hour), the engine needs maybe a few thousand random values total. Dice rolls are **rare and
human-paced** — they happen when you draw, shop, or fight a boss, not in a tight loop.

**How expensive is one crypto dice-roll?** Modern CPUs have *dedicated hardware instructions* for SHA
and AES — these primitives run at multiple **gigabytes per second**. One roll is a few hundred
*nanoseconds* (a nanosecond is to a second what a second is to ~32 years). Multiply the cost of one roll
by every roll in a whole match and you get a *fraction of a second of CPU*, smeared across an hour of
play. It's a drop in a swimming pool — it cannot show up in a profiler. Your scoring code (BigNum math,
three joker passes) does far more work per hand than all the RNG in the match combined.

**One trick to keep it cheap (the CS bit):** don't re-derive a key for every roll. Derive a stream's key
**once**, then each roll is just "bump a counter and encrypt it" (counter-mode, like ChaCha20-CTR).
Grind the coffee beans once, pour many cups — don't regrind per cup. This turns per-roll cost into a
single cheap block-cipher step.

**A free bonus you actually gain:** computers don't all agree on decimal (floating-point) math — the
last tiny digit can differ between machines, which is a nightmare when *everyone must reproduce the
exact same game*. Balatro's `pseudohash` is built on that shaky float math. Crypto primitives work on
**whole numbers and bytes**, which are *identical on every machine, forever*. So switching to a crypto
PRF doesn't just add security — it makes "everyone computes the same game" **more** reliable than the
float-based original, not less.

**The only place crypto is "slow":** if *we* ever want to brute-force millions of seeds ourselves (e.g.
to design daily challenges or analyze balance), crypto is deliberately too slow to enumerate — that's the
whole point. But that job runs on the fast, enumerable **vanilla oracle** (§7), never on the live path.
The split that protects players also keeps our dev tooling fast.
