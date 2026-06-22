import { useEffect, useState } from "react";
import { useStore } from "@tanstack/react-store";
import { store, connect, newRun, send, previewCards } from "./session";
import type { CardView } from "./types";
import Almanac from "./Almanac";
import ScreenView from "./ScreenView";
import { content } from "./content";
// Generated from the server's own enums (see generateContent) — these maps are now exhaustive and
// drift-proof: add a Suit/Rank on the server and the client fails to compile until it's handled.
import type { Suit, Rank } from "../../generated/content-types";

const RANK: Record<Rank, string> = {
  TWO: "2", THREE: "3", FOUR: "4", FIVE: "5", SIX: "6", SEVEN: "7", EIGHT: "8",
  NINE: "9", TEN: "10", JACK: "J", QUEEN: "Q", KING: "K", ACE: "A",
};
const SUIT: Record<Suit, string> = { SPADES: "♠", HEARTS: "♥", CLUBS: "♣", DIAMONDS: "♦" };
const RED = new Set<Suit>(["HEARTS", "DIAMONDS"]);
const RID: Record<Rank, number> = {
  TWO: 2, THREE: 3, FOUR: 4, FIVE: 5, SIX: 6, SEVEN: 7, EIGHT: 8,
  NINE: 9, TEN: 10, JACK: 11, QUEEN: 12, KING: 13, ACE: 14,
};
const SUIT_ORDER: Record<Suit, number> = { SPADES: 0, HEARTS: 1, CLUBS: 2, DIAMONDS: 3 };
const fmt = (n: number) => (Number.isInteger(n) ? String(n) : String(Math.round(n * 100) / 100));

export default function App() {
  const status = useStore(store, (s) => s.status);
  const conn = useStore(store, (s) => s.conn);
  const toast = useStore(store, (s) => s.toast);
  const [almanac, setAlmanac] = useState(false);

  return (
    <div className="app">
      <h1>
        BALATRO · COMPETITIVE
        <span className="conn">{conn && `● ${conn}`}</span>
        <button className="alt tiny" onClick={() => setAlmanac((a) => !a)}>Almanac</button>
      </h1>
      {almanac && <Almanac onClose={() => setAlmanac(false)} />}
      {status === "login" && <Login />}
      {status === "menu" && <Menu />}
      {status === "game" && <Game />}
      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}

function Login() {
  const [user, setUser] = useState("player");
  return (
    <div className="panel row">
      <input value={user} onChange={(e) => setUser(e.target.value)} placeholder="username" />
      <button onClick={() => connect(user.trim() || "player")}>Connect</button>
    </div>
  );
}

function Menu() {
  // The menu is now server-driven UI: render the "menu" screen (data) via the generic ScreenView.
  const screens = useStore(content, (c) => c.SCREENS);
  const menu = screens.find((s) => s.id === "menu");
  return menu ? <ScreenView screen={menu} /> : <div className="panel row">Loading…</div>;
}

function Game() {
  const v = useStore(store, (s) => s.view);
  const preview = useStore(store, (s) => s.preview);
  const banner = useStore(store, (s) => s.banner);
  const [sel, setSel] = useState<Set<number>>(new Set());
  const [targeting, setTargeting] = useState<number | null>(null);
  const [targets, setTargets] = useState<Set<number>>(new Set());
  const [sort, setSort] = useState<"deal" | "rank" | "suit">("deal");

  // Reset transient selection whenever a fresh view arrives for a new phase/hand.
  useEffect(() => {
    setSel(new Set());
    setTargeting(null);
    setTargets(new Set());
  }, [v?.phase, v?.handsLeft, v?.discardsLeft]);

  if (!v) return <div className="panel">Loading…</div>;
  const inSelect = v.phase === "BLIND_SELECT";
  const inBlind = v.phase === "BLIND_ACTIVE";
  const inShop = v.phase === "SHOP";

  const toggleCard = (i: number) => {
    if (targeting !== null) {
      const max = v.consumables[targeting]?.maxTargets ?? 0;
      const next = new Set(targets);
      if (next.has(i)) next.delete(i);
      else if (next.size < max) next.add(i);
      setTargets(next);
      return;
    }
    const next = new Set(sel);
    if (next.has(i)) next.delete(i);
    else if (next.size < 5) next.add(i);
    setSel(next);
    previewCards([...next]);
  };

  const play = () => { send({ type: "playHand", cards: [...sel] }); setSel(new Set()); };
  const discard = () => { send({ type: "discard", cards: [...sel] }); setSel(new Set()); };

  return (
    <>
      <div className="panel row">
        <div>
          <div className="stat">
            {v.phase} · ante {v.ante} · {v.blind}
            {v.boss && ` ⚠ ${v.boss}: ${v.bossEffect}`}
          </div>
          <div className="big">
            {v.roundScore} / {v.requirement}
          </div>
          <div className="bar">
            <div style={{ width: `${Math.min(100, v.requirement ? (v.roundScore / v.requirement) * 100 : 0)}%` }} />
          </div>
        </div>
        <div className="spacer" />
        <div className="pill">Hands <b>{v.handsLeft}</b></div>
        <div className="pill">Discards <b>{v.discardsLeft}</b></div>
        <div className="pill">${v.money}</div>
        <div className="pill">Deck {v.deckStats?.remaining ?? "?"}/{v.deckStats?.size ?? "?"}</div>
        {Array.isArray(v.counters?.heldTags) && (v.counters!.heldTags as string[]).length > 0 && (
          <div className="pill" title={(v.counters!.heldTags as string[]).join(", ")}>
            🏷 {(v.counters!.heldTags as string[]).length}
          </div>
        )}
      </div>

      <div className="panel">
        <div className="stat">Jokers</div>
        <div className="row">
          {v.jokers.length ? v.jokers.map((j, i) => (
            <div key={i} className="jokerWrap" title={`${j.name}${j.description ? " — " + j.description : ""}`}>
              <div className="joker">
                {j.edition && j.edition !== "NONE" && <span className="ed">{j.edition[0]}</span>}
                {j.name}
                {j.display != null && j.display !== "" && <span className="jdisplay">{String(j.display)}</span>}
              </div>
              <button className="alt tiny" onClick={() => send({ type: "sellJoker", index: i })}>
                Sell ${Math.max(1, Math.floor((j.cost ?? 0) / 2))}
              </button>
            </div>
          )) : <span className="stat">none</span>}
        </div>
      </div>

      <div className="panel">
        <div className="stat">Consumables (click to use; 🎴 needs targets)</div>
        <div className="row">
          {v.consumables.length ? v.consumables.map((c, i) => {
            const mt = c.maxTargets ?? 0;
            return (
              <div key={i} className={"chip" + (targeting === i ? " active" : "")} title={c.description}
                onClick={() => {
                  if (mt === 0) { send({ type: "useConsumable", index: i }); return; }
                  setTargeting(targeting === i ? null : i);
                  setTargets(new Set());
                }}>
                {(mt ? "🎴 " : "🪐 ") + c.name + (mt ? ` (≤${mt})` : "")}
              </div>
            );
          }) : <span className="stat">none</span>}
        </div>
        {targeting !== null && v.consumables[targeting] && (
          <div className="row" style={{ marginTop: 8 }}>
            <span className="stat">
              Pick up to {v.consumables[targeting].maxTargets} card(s) — {targets.size} chosen
            </span>
            <button onClick={() => {
              send({ type: "useConsumable", index: targeting, targets: [...targets].map((k) => v.hand[k].uid) });
              setTargeting(null); setTargets(new Set());
            }}>Use</button>
            <button className="alt" onClick={() => { setTargeting(null); setTargets(new Set()); }}>Cancel</button>
          </div>
        )}
      </div>

      {inBlind && (() => {
        // Display order is client-side only; selection/play always use the original hand index.
        const order = v.hand.map((_, i) => i);
        // Face-down cards have null identity; sort them as 0 (they cluster, order is cosmetic anyway).
        const rid = (i: number) => { const r = v.hand[i].rank; return r ? RID[r] : 0; };
        const sord = (i: number) => { const s = v.hand[i].suit; return s ? SUIT_ORDER[s] : 0; };
        if (sort === "rank") order.sort((a, b) => rid(b) - rid(a) || sord(a) - sord(b));
        else if (sort === "suit") order.sort((a, b) => sord(a) - sord(b) || rid(b) - rid(a));
        const selecting = targeting !== null ? targets : sel;
        return (
          <div className="panel">
            <div className="row" style={{ justifyContent: "space-between" }}>
              <span className="stat">Hand · {selecting.size} selected</span>
              <span className="row">
                <button className={"alt tiny" + (sort === "rank" ? " on" : "")} onClick={() => setSort("rank")}>Rank</button>
                <button className={"alt tiny" + (sort === "suit" ? " on" : "")} onClick={() => setSort("suit")}>Suit</button>
                <button className={"alt tiny" + (sort === "deal" ? " on" : "")} onClick={() => setSort("deal")}>Dealt</button>
                <button className="alt tiny" disabled={!selecting.size}
                  onClick={() => { if (targeting !== null) setTargets(new Set()); else { setSel(new Set()); previewCards([]); } }}>
                  Clear
                </button>
              </span>
            </div>
            <div className="hand">
              {order.map((i) => {
                const c = v.hand[i];
                const picked = targeting !== null ? targets.has(i) : sel.has(i);
                return <Card key={c.uid} c={c} picked={picked} onClick={() => toggleCard(i)} />;
              })}
            </div>
            <div className="preview">
              {preview && sel.size > 0 ? `${fmt(preview.chips)} × ${fmt(preview.mult)} = ${fmt(preview.score)}` : ""}
            </div>
            <div className="row" style={{ marginTop: 12 }}>
              <button disabled={!(sel.size >= 1 && sel.size <= 5)} onClick={play}>Play Hand</button>
              <button className="alt" disabled={!(sel.size >= 1 && v.discardsLeft > 0)} onClick={discard}>Discard</button>
              <div className="spacer" />
              <button className="alt" onClick={() => newRun()}>New Run</button>
            </div>
          </div>
        );
      })()}

      {inSelect && (
        <div className="panel">
          <div className="stat">Ante {v.ante} — {v.blind}{v.boss ? ` ⚠ ${v.boss}: ${v.bossEffect}` : ""}</div>
          <div className="big" style={{ margin: "6px 0" }}>Requirement: {v.requirement}</div>
          {String(v.counters?.offeredTagName ?? "") && (
            <div className="stat">Skip reward: 🏷 {String(v.counters?.offeredTagName)}</div>
          )}
          <div className="row" style={{ marginTop: 8 }}>
            <button onClick={() => send({ type: "selectBlind" })}>Select (Play)</button>
            {!v.blind.includes("Boss") && (
              <button className="alt" onClick={() => send({ type: "skipBlind" })}>
                Skip → {String(v.counters?.offeredTagName ?? "Tag")}
              </button>
            )}
            <div className="spacer" />
            <button className="alt" onClick={() => newRun()}>New Run</button>
          </div>
        </div>
      )}
      {inShop && <Shop />}
      {v.openPack && <PackOpening />}
      {banner && <div className="panel banner">{banner}</div>}
    </>
  );
}

function PackOpening() {
  const v = useStore(store, (s) => s.view)!;
  const op = v.openPack;
  if (!op) return null;
  return (
    <div className="panel" style={{ outline: "2px solid var(--gold)" }}>
      <div className="row" style={{ justifyContent: "space-between" }}>
        <span className="stat">Booster pack — pick {op.picksLeft}</span>
        <button className="alt tiny" onClick={() => send({ type: "skipPack" })}>Skip</button>
      </div>
      <div className="row">
        {op.items.map((it, i) => (
          <div key={i} className="offer">
            <b>
              {it.type === "CARD"
                ? (it.rank ? RANK[it.rank] : "?") + (it.suit ? SUIT[it.suit] : "?")
                : (it.type === "JOKER" ? "🃏 " : "🎴 ") + it.name}
            </b>
            {it.description && <span className="stat">{it.description}</span>}
            <button onClick={() => send({ type: "pickPackItem", index: i })}>Pick</button>
          </div>
        ))}
      </div>
    </div>
  );
}

function Card({ c, picked, onClick }: { c: CardView; picked: boolean; onClick: () => void }) {
  return (
    <div className={"card" + (c.suit && RED.has(c.suit) ? " red" : "") + (picked ? " sel" : "")} onClick={onClick}>
      <div className="r">{c.rank ? RANK[c.rank] : "?"}</div>
      <div className="s">{c.suit ? SUIT[c.suit] : "?"}</div>
      {c.enhancement && c.enhancement !== "NONE" && <div className="enh">{c.enhancement[0]}</div>}
    </div>
  );
}

function Shop() {
  const v = useStore(store, (s) => s.view)!;
  const Offer = ({ name, desc, cost, label, onBuy, accent }: {
    name: string; desc?: string; cost: number; label: string; onBuy: () => void; accent?: boolean;
  }) => (
    <div className={"offer" + (accent ? " accent" : "")}>
      <b>{name}</b>
      <span className="stat">{desc}</span>
      <span className="stat">${cost}</span>
      <button disabled={v.money < cost} onClick={onBuy}>{label}</button>
    </div>
  );
  const icon: Record<string, string> = { JOKER: "🃏", TAROT: "🎴", PLANET: "🪐" };
  const reward = Number(v.counters?.cashOutReward ?? 0);
  const interest = Number(v.counters?.cashOutInterest ?? 0);
  return (
    <div className="panel">
      {reward + interest > 0 && (
        <div className="stat" style={{ color: "var(--gold)" }}>
          💰 Cash out: blind ${reward} + interest ${interest} = ${reward + interest}
        </div>
      )}
      <div className="stat">Shop · {v.shop?.length ?? 0} slots</div>
      <div className="row">
        {/* Mixed main slots: each is a joker, tarot, or planet from the master queue. */}
        {(v.shop ?? []).map((it, i) => (
          <Offer key={"s" + i}
            name={(it.edition && it.edition !== "NONE" ? "✦ " : "") + (icon[it.kind] ?? "") + " " + it.name}
            desc={`${it.description ?? ""} ${it.rarity ?? ""}`.trim()} cost={it.cost} label="Buy"
            onBuy={() => send({ type: "buyShopItem", index: i })} />
        ))}
        {(v.shopVouchers ?? []).map((vo, i) => (
          <Offer accent key={"voucher-" + i} name={"🎟 " + vo.name} desc={vo.description}
            cost={vo.cost} label="Buy" onBuy={() => send({ type: "buyVoucher", index: i })} />
        ))}
      </div>
      {/* Two booster packs (kept across rerolls). */}
      <div className="row" style={{ marginTop: 10 }}>
        {(v.packs ?? []).map((p, i) => (
          <Offer key={"pk" + i} name={"📦 " + p.name} desc={`pick ${p.choose} of ${p.shown}`} cost={p.cost}
            label="Open" onBuy={() => send({ type: "openPack", index: i })} />
        ))}
      </div>
      <div className="row" style={{ marginTop: 12 }}>
        <button className="alt" onClick={() => send({ type: "reroll" })}>Reroll (${v.rerollCost})</button>
        <div className="spacer" />
        <button onClick={() => send({ type: "proceed" })}>Next Blind →</button>
      </div>
    </div>
  );
}
