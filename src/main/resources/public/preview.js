// Client-side score preview — an instant, local projection of the score for the
// current card selection, so the UI updates with zero server round-trips. It is a
// faithful port of the server's deterministic scoring path that INTERPRETS the same
// JokerDef data the server sends (logic stays data; only the pipeline + a small
// algebra interpreter live here). The server is still authoritative on Play, so this
// can never cheat; it's validated against the server /preview oracle (see preview.test.mjs).
//
// Probabilistic effects (Chance/Random) and a few native jokers aren't modeled here;
// callers should fall back to the server /preview for hands involving them.
(function (root) {
  'use strict';

  const RANK = { // name -> {id, chips}
    TWO: [2, 2], THREE: [3, 3], FOUR: [4, 4], FIVE: [5, 5], SIX: [6, 6], SEVEN: [7, 7],
    EIGHT: [8, 8], NINE: [9, 9], TEN: [10, 10], JACK: [11, 10], QUEEN: [12, 10],
    KING: [13, 10], ACE: [14, 11],
  };
  const RANK_ORDER = ['TWO', 'THREE', 'FOUR', 'FIVE', 'SIX', 'SEVEN', 'EIGHT', 'NINE', 'TEN', 'JACK', 'QUEEN', 'KING', 'ACE'];
  const HAND = { // display -> {chips, mult, lChips, lMult}
    'High Card': [5, 1, 10, 1], 'Pair': [10, 2, 15, 1], 'Two Pair': [20, 2, 20, 1],
    'Three of a Kind': [30, 3, 20, 2], 'Straight': [30, 4, 30, 3], 'Flush': [35, 4, 15, 2],
    'Full House': [40, 4, 25, 2], 'Four of a Kind': [60, 7, 30, 3], 'Straight Flush': [100, 8, 40, 4],
    'Five of a Kind': [120, 12, 35, 3], 'Flush House': [140, 14, 40, 4], 'Flush Five': [160, 16, 50, 3],
  };

  const id = (c) => RANK[c.rank][0];
  const baseChips = (c) => c.enhancement === 'STONE' ? 0 : RANK[c.rank][1];
  const isStone = (c) => c.enhancement === 'STONE';
  const isFace = (c) => !isStone(c) && (c.rank === 'JACK' || c.rank === 'QUEEN' || c.rank === 'KING');
  // Face-ness honouring Pareidolia (ctx.allFaces); Stone cards never count.
  const faceOf = (c, ctx) => !!c && !isStone(c) && (isFace(c) || (ctx && ctx.allFaces));
  const isSuit = (c, s) => !isStone(c) && c.suit === s;

  // --- hand evaluation (port of HandEvaluator) ------------------------------
  // mods: {fourFingers, shortcut, smeared} — global modifiers from owned jokers.
  function evaluateHand(played, mods) {
    mods = mods || {};
    const ranked = played.filter((c) => !isStone(c));
    const count = {};
    for (const c of ranked) count[id(c)] = (count[id(c)] || 0) + 1;

    let hi5 = -1, hi4 = -1, hi3 = -1, triples = 0;
    const pairs = [];
    for (let i = 14; i >= 2; i--) {
      const n = count[i] || 0;
      if (n >= 5 && hi5 < 0) hi5 = i;
      if (n >= 4 && hi4 < 0) hi4 = i;
      if (n >= 3 && hi3 < 0) hi3 = i;
      if (n >= 3) triples++;
      if (n >= 2) pairs.push(i);
    }
    const flushCards = flushCardsOf(ranked, mods), straightCards = straightCardsOf(ranked, mods);
    const flush = flushCards.length > 0, straight = straightCards.length > 0;
    const five = hi5 >= 0, four = hi4 >= 0, three = hi3 >= 0;
    const fullHouse = triples >= 1 && pairs.length >= 2;
    const twoPair = pairs.length >= 2, pair = pairs.length > 0;

    const cardsOf = (rid) => played.filter((c) => !isStone(c) && id(c) === rid);
    if (five && flush) return res('Flush Five', played);
    if (fullHouse && flush) return res('Flush House', played);
    if (five) return res('Five of a Kind', cardsOf(hi5));
    if (straight && flush) return res('Straight Flush', straightCards);
    if (four) return res('Four of a Kind', cardsOf(hi4));
    if (fullHouse) return res('Full House', played);
    if (flush) return res('Flush', flushCards);
    if (straight) return res('Straight', straightCards);
    if (three) return res('Three of a Kind', cardsOf(hi3));
    if (twoPair) return res('Two Pair', cardsOf(pairs[0]).concat(cardsOf(pairs[1])));
    if (pair) return res('Pair', cardsOf(pairs[0]));
    let hiCard = null;
    for (const c of ranked) if (!hiCard || id(c) > id(hiCard)) hiCard = c;
    return res('High Card', hiCard ? [hiCard] : []);
  }
  const res = (type, scoring) => ({ type, scoring });

  // Union the global hand modifiers from the owned jokers' defs (mirrors HandMods.from).
  function activeMods(jokers) {
    const m = { fourFingers: false, shortcut: false, smeared: false, pareidolia: false, splash: false };
    for (const j of jokers || []) {
      for (const mod of (j.def && j.def.handMods) || []) {
        if (mod === 'FOUR_FINGERS') m.fourFingers = true;
        else if (mod === 'SHORTCUT') m.shortcut = true;
        else if (mod === 'SMEARED') m.smeared = true;
        else if (mod === 'PAREIDOLIA') m.pareidolia = true;
        else if (mod === 'SPLASH') m.splash = true;
      }
    }
    return m;
  }

  const group = (s, mods) => mods.smeared ? ((s === 'HEARTS' || s === 'DIAMONDS') ? 'RD' : 'BK') : s;
  function flushCardsOf(cards, mods) {
    const need = mods.fourFingers ? 4 : 5;
    if (cards.length < need) return [];
    const c = {};
    for (const x of cards) { const k = group(x.suit, mods); c[k] = (c[k] || 0) + 1; }
    for (const k of Object.keys(c)) {
      if (c[k] >= need) return cards.filter((x) => group(x.suit, mods) === k);
    }
    return [];
  }
  function straightCardsOf(cards, mods) {
    const need = mods.fourFingers ? 4 : 5;
    if (cards.length < need) return [];
    const present = {};
    let ace = false;
    for (const x of cards) { present[id(x)] = true; if (id(x) === 14) ace = true; }
    if (ace) present[1] = true;
    const gap = mods.shortcut ? 2 : 1;
    for (let s = 1; s <= 14; s++) {
      if (!present[s]) continue;
      const ranks = [s];
      let last = s;
      for (let next = s + 1; next <= 14 && ranks.length < need; next++) {
        if (!present[next]) continue;
        if (next - last > gap) break;
        ranks.push(next); last = next;
      }
      if (ranks.length >= need) {
        const out = [];
        for (const rank of ranks) {
          const wanted = rank === 1 ? 14 : rank;
          const card = cards.find((x) => id(x) === wanted && !out.includes(x));
          if (card) out.push(card);
        }
        return out;
      }
    }
    return [];
  }

  // --- algebra interpreter (Condition / Value / EffectTemplate / DataJoker) --
  function condTest(cond, ctx) {
    const c = ctx.scoredCard;
    switch (cond.type) {
      case 'always': return true;
      case 'scoredSuit': return !!c && isSuit(c, cond.suit);
      case 'scoredParity': {
        if (!c || isStone(c)) return false;
        const r = id(c);
        const even = r === 2 || r === 4 || r === 6 || r === 8 || r === 10;
        const odd = r === 3 || r === 5 || r === 7 || r === 9 || r === 14;
        return cond.even ? even : odd;
      }
      case 'scoredIsFace': return faceOf(c, ctx);
      case 'scoredRankBetween': return !!c && !isStone(c) && id(c) >= cond.min && id(c) <= cond.max;
      case 'scoredFirst': return !!c && ctx.scoring.length > 0 && ctx.scoring[0] === c;
      case 'scoredEnhancement': return !!c && c.enhancement === cond.enhancement;
      case 'scoredEdition': return !!c && c.edition === cond.edition;
      case 'scoredSeal': return !!c && c.seal === cond.seal;
      case 'handContainsPair': return handContains(ctx.handType, 'PAIR');
      case 'handContains': return handContains(ctx.handType, cond.hand);
      case 'handIs': return ctx.handType === cond.hand;
      case 'playedCount': return cmp(cond.cmp, ctx.played.length, cond.n);
      case 'scoringAnyFace': return ctx.scoring.some((x) => faceOf(x, ctx));
      case 'scoringContainsSuit': return ctx.scoring.some((x) => isSuit(x, cond.suit));
      case 'scoredFirstFace': {
        if (!faceOf(c, ctx)) return false;
        const firstFace = ctx.scoring.find((x) => faceOf(x, ctx));
        return firstFace === c;
      }
      case 'valueAtLeast': {
        const rv = valResolve(cond.value, ctx);
        return rv === null ? null : rv >= cond.min;
      }
      case 'heldAllSuits': return (ctx.held || []).every((x) => cond.suits.some((s) => isSuit(x, s)));
      case 'moneyAtLeast': return ctx.run.money >= cond.min;
      case 'handsLeft': return cmp(cond.cmp, ctx.run.handsLeft, cond.n);
      case 'discardsLeft': return cmp(cond.cmp, ctx.run.discardsLeft, cond.n);
      case 'ante': return cmp(cond.cmp, ctx.run.ante, cond.n);
      case 'stateAtLeast': return (ctx.state[cond.var] || 0) >= cond.min;
      case 'and': return cond.all.every((x) => condTest(x, ctx));
      case 'or': return cond.any.some((x) => condTest(x, ctx));
      case 'not': return !condTest(cond.inner, ctx);
      case 'chance': return null; // probabilistic — signal "unsupported" -> caller falls back
      default: return null;
    }
  }
  function cmp(op, a, b) { return op === 'LTE' ? a <= b : op === 'GTE' ? a >= b : a === b; }

  // The handType-contains relation (mirrors HandType.contains).
  function handContains(top, part) {
    const m = {
      PAIR: ['PAIR', 'TWO_PAIR', 'FULL_HOUSE', 'FLUSH_HOUSE'],
      TWO_PAIR: ['TWO_PAIR', 'FULL_HOUSE', 'FLUSH_HOUSE'],
      THREE_OF_A_KIND: ['THREE_OF_A_KIND', 'FULL_HOUSE', 'FLUSH_HOUSE'],
      STRAIGHT: ['STRAIGHT', 'STRAIGHT_FLUSH'],
      FLUSH: ['FLUSH', 'STRAIGHT_FLUSH', 'FLUSH_HOUSE', 'FLUSH_FIVE'],
      FULL_HOUSE: ['FULL_HOUSE', 'FLUSH_HOUSE'],
      FOUR_OF_A_KIND: ['FOUR_OF_A_KIND'],
      STRAIGHT_FLUSH: ['STRAIGHT_FLUSH'],
      FIVE_OF_A_KIND: ['FIVE_OF_A_KIND', 'FLUSH_FIVE'],
      FLUSH_HOUSE: ['FLUSH_HOUSE'], FLUSH_FIVE: ['FLUSH_FIVE'], HIGH_CARD: [],
    };
    const topKey = handKey(top);
    if (part === 'HIGH_CARD') return true;
    return (m[part] || []).includes(topKey);
  }
  const handKey = (display) => Object.keys(KEY).find((k) => KEY[k] === display) || display;
  const KEY = {
    HIGH_CARD: 'High Card', PAIR: 'Pair', TWO_PAIR: 'Two Pair', THREE_OF_A_KIND: 'Three of a Kind',
    STRAIGHT: 'Straight', FLUSH: 'Flush', FULL_HOUSE: 'Full House', FOUR_OF_A_KIND: 'Four of a Kind',
    STRAIGHT_FLUSH: 'Straight Flush', FIVE_OF_A_KIND: 'Five of a Kind', FLUSH_HOUSE: 'Flush House', FLUSH_FIVE: 'Flush Five',
  };

  function valResolve(v, ctx) {
    switch (v.type) {
      case 'const': return v.amount;
      case 'state': return v.base + v.scale * (ctx.state[v.var] || 0);
      case 'runVar': {
        const r = ctx.run, map = { MONEY: r.money, HANDS_LEFT: r.handsLeft, DISCARDS_LEFT: r.discardsLeft, HAND_SIZE: r.handSize, ANTE: r.ante };
        return v.base + v.scale * (map[v.which] || 0);
      }
      case 'runVarStep': {
        const r = ctx.run, map = { MONEY: r.money, HANDS_LEFT: r.handsLeft, DISCARDS_LEFT: r.discardsLeft, HAND_SIZE: r.handSize, ANTE: r.ante };
        return v.per === 0 ? v.base : v.base + v.scale * Math.floor((map[v.which] || 0) / v.per);
      }
      case 'count': {
        const src = v.source === 'PLAYED' ? ctx.played : v.source === 'HELD' ? ctx.held : ctx.scoring;
        let n = 0;
        for (const card of src) if (condTest(v.match, Object.assign({}, ctx, { scoredCard: card }))) n++;
        return v.base + v.scale * n;
      }
      case 'stat': return v.base + v.scale * statCount(v, ctx);
      case 'random': return null; // probabilistic -> fall back
      default: return null;
    }
  }
  function statCount(v, ctx) {
    const d = ctx.run.deck || {};
    switch (v.which) {
      case 'DECK_SIZE': return d.size || 0;
      case 'DECK_REMAINING': return d.remaining || 0;
      case 'ENHANCED_CARD_COUNT': return Object.values(d.enhancements || {}).reduce((a, b) => a + b, 0);
      case 'DECK_ENH_COUNT': return (d.enhancements || {})[v.enhancement] || 0;
      case 'OWNED_JOKERS': return ctx.run.jokerCount;
      case 'EMPTY_JOKER_SLOTS': return Math.max(0, 5 - ctx.run.jokerCount);
      case 'CARDS_BELOW_FULL': return Math.max(0, 52 - (d.size || 0));
      default: return 0;
    }
  }

  // Apply an EffectTemplate to the accumulator; returns false if unsupported (fall back).
  function effApply(acc, eff, ctx) {
    if (eff.op === 'MUTATE_CARD') return applyExtra(acc, eff, ctx); // no numeric in preview
    const val = valResolve(eff.value, ctx);
    if (val === null) return false; // probabilistic value -> unsupported
    switch (eff.op) {
      case 'CHIPS': acc.chips += val; break;
      case 'MULT': acc.mult += val; break;
      case 'HELD_MULT': acc.mult += val; break;
      case 'XMULT': if (val !== 1) acc.mult *= val; break;
      case 'XCHIPS': if (val !== 1) acc.chips = Math.round(acc.chips * val); break;
      case 'POW_MULT': if (val !== 1) acc.mult = Math.pow(acc.mult, val); break;
      case 'DOLLARS': break; // no money in scoring math
      case 'REPETITIONS': break; // handled by the retrigger pass
      default: return false;
    }
    return applyExtra(acc, eff, ctx);
  }
  function applyExtra(acc, eff, ctx) {
    return eff.extra ? effApply(acc, eff.extra, ctx) : true;
  }

  // DataJoker.calculate for a phase: first matching rule's effect (mutations skipped in preview).
  function jokerEffect(joker, ctx) {
    if (!joker.def) return { ok: false };
    for (const r of joker.def.rules || []) {
      if (r.when !== ctx.phase) continue;
      const t = condTest(r.condition, ctx);
      if (t === null) return { ok: false }; // unsupported condition (e.g. chance)
      if (t) return { ok: true, effect: r.effect };
    }
    return { ok: true, effect: null };
  }
  function repetitions(joker, ctx) {
    if (!joker.def) return { reps: 0, ok: true };
    let reps = 0;
    for (const r of joker.def.rules || []) {
      if (r.when !== ctx.phase || r.effect.op !== 'REPETITIONS') continue;
      const t = condTest(r.condition, ctx);
      if (t === null) return { reps: 0, ok: false };
      if (t) { const v = valResolve(r.effect.value, ctx); reps += Math.round(v); }
    }
    return { reps, ok: true };
  }

  // --- the pipeline (port of ScoringEngine, deterministic preview path) -----
  // jokers: [{key, def, state}], run: {money,handsLeft,discardsLeft,ante,handSize,jokerCount,deck,handLevels}
  // returns {chips, mult, score} or null if any joker is unsupported (caller falls back to server).
  function previewScore(played, held, jokers, run) {
    let unsupported = false;
    const mods = activeMods(jokers);
    const hr = evaluateHand(played, mods);
    const scoring = hr.scoring.slice();
    for (const c of played) if ((isStone(c) || mods.splash) && !scoring.includes(c)) scoring.push(c);
    scoring.sort((a, b) => played.indexOf(a) - played.indexOf(b));

    const [bChips, bMult, lc, lm] = HAND[hr.type];
    const lvl = (run.handLevels && run.handLevels[hr.type]) ? run.handLevels[hr.type] : 1;
    const acc = { chips: bChips + (lvl - 1) * lc, mult: bMult + (lvl - 1) * lm };

    const baseCtx = (phase, scoredCard) => ({
      phase, scoredCard, played, held, scoring, handType: handKey(hr.type), run,
      allFaces: mods.pareidolia, // Pareidolia: face conditions treat every card as a face
      state: {}, // set per joker
    });
    const runJokerPass = (phase, scoredCard) => {
      for (const j of jokers) {
        const ctx = baseCtx(phase, scoredCard);
        ctx.state = j.state || {};
        const e = jokerEffect(j, ctx);
        if (!e.ok) { unsupported = true; continue; }
        if (e.effect && !effApply(acc, e.effect, ctx)) unsupported = true;
      }
    };

    runJokerPass('BEFORE', null);
    runJokerPass('INITIAL_SCORING_STEP', null);

    for (const card of scoring) {
      if (card.debuffed) continue;
      let reps = 1;
      if (card.seal === 'RED') reps += 1;
      for (const j of jokers) {
        const ctx = baseCtx('REPETITION_PLAYED', card); ctx.state = j.state || {};
        const r = repetitions(j, ctx);
        if (!r.ok) unsupported = true; else reps += r.reps;
      }
      for (let i = 0; i < reps; i++) {
        applyCardScored(acc, card);
        runJokerPass('ON_SCORED', card);
      }
    }

    for (const card of held) {
      if (card.debuffed) continue;
      let reps = 1;
      if (card.seal === 'RED') reps += 1;
      for (const j of jokers) {
        const ctx = baseCtx('REPETITION_HELD', card); ctx.state = j.state || {};
        const r = repetitions(j, ctx);
        if (!r.ok) unsupported = true; else reps += r.reps;
      }
      for (let i = 0; i < reps; i++) {
        if (card.enhancement === 'STEEL') acc.mult *= 1.5;
        runJokerPass('ON_HELD', card);
      }
    }

    runJokerPass('JOKER_MAIN', null);
    runJokerPass('ON_OTHER_JOKER', null);
    runJokerPass('FINAL_SCORING_STEP', null);

    if (unsupported) return null; // a joker used a probabilistic/native effect -> server fallback
    return { type: hr.type, chips: acc.chips, mult: acc.mult, score: acc.chips * acc.mult };
  }

  function applyCardScored(acc, card) {
    let chips = baseChips(card);
    if (card.enhancement === 'STONE') chips += 50;
    if (card.enhancement === 'BONUS') chips += 30;
    chips += card.permaChips || 0;
    acc.chips += chips;
    if (card.permaMult) acc.mult += card.permaMult;
    if (card.enhancement === 'MULT') acc.mult += 4;
    if (card.enhancement === 'GLASS') acc.mult *= 2; // no break in preview
    if (card.edition === 'FOIL') acc.chips += 50;
    else if (card.edition === 'HOLOGRAPHIC') acc.mult += 10;
    else if (card.edition === 'POLYCHROME') acc.mult *= 1.5;
    // LUCKY / GOLD seal are probabilistic / economy -> no scoring contribution here
  }

  root.BalatroPreview = { previewScore, evaluateHand };
})(typeof module !== 'undefined' && module.exports ? module.exports : (this.window || globalThis));
