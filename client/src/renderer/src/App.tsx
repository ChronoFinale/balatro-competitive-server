import { useEffect, useState } from "react";
import { useStore } from "@tanstack/react-store";
import { store, connect, newRun, send, previewCards } from "./session";
import type { CardView } from "./types";

const RANK: Record<string, string> = {
  TWO: "2", THREE: "3", FOUR: "4", FIVE: "5", SIX: "6", SEVEN: "7", EIGHT: "8",
  NINE: "9", TEN: "10", JACK: "J", QUEEN: "Q", KING: "K", ACE: "A",
};
const SUIT: Record<string, string> = { SPADES: "♠", HEARTS: "♥", CLUBS: "♣", DIAMONDS: "♦" };
const RED = new Set(["HEARTS", "DIAMONDS"]);
const fmt = (n: number) => (Number.isInteger(n) ? String(n) : String(Math.round(n * 100) / 100));

export default function App() {
  const status = useStore(store, (s) => s.status);
  const conn = useStore(store, (s) => s.conn);
  const toast = useStore(store, (s) => s.toast);

  return (
    <div className="app">
      <h1>
        BALATRO · COMPETITIVE
        <span className="conn">{conn && `● ${conn}`}</span>
      </h1>
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
  return (
    <div className="panel row">
      <button onClick={() => newRun()}>Solo Run</button>
      <span className="stat">Multiplayer lobby UI is next.</span>
    </div>
  );
}

function Game() {
  const v = useStore(store, (s) => s.view);
  const preview = useStore(store, (s) => s.preview);
  const banner = useStore(store, (s) => s.banner);
  const [sel, setSel] = useState<Set<number>>(new Set());
  const [targeting, setTargeting] = useState<number | null>(null);
  const [targets, setTargets] = useState<Set<number>>(new Set());

  // Reset transient selection whenever a fresh view arrives for a new phase/hand.
  useEffect(() => {
    setSel(new Set());
    setTargeting(null);
    setTargets(new Set());
  }, [v?.phase, v?.handsLeft, v?.discardsLeft]);

  if (!v) return <div className="panel">Loading…</div>;
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

      {inBlind && (
        <div className="panel">
          <div className="stat">Hand</div>
          <div className="hand">
            {v.hand.map((c, i) => {
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
      )}

      {inShop && <Shop />}
      {banner && <div className="panel banner">{banner}</div>}
    </>
  );
}

function Card({ c, picked, onClick }: { c: CardView; picked: boolean; onClick: () => void }) {
  return (
    <div className={"card" + (RED.has(c.suit) ? " red" : "") + (picked ? " sel" : "")} onClick={onClick}>
      <div className="r">{RANK[c.rank] ?? "?"}</div>
      <div className="s">{SUIT[c.suit] ?? "?"}</div>
      {c.enhancement !== "NONE" && <div className="enh">{c.enhancement[0]}</div>}
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
  return (
    <div className="panel">
      <div className="stat">Shop</div>
      <div className="row">
        {(v.shop ?? []).map((it, i) => (
          <Offer key={"j" + i} name={(it.edition && it.edition !== "NONE" ? `✦ ` : "") + it.name}
            desc={`${it.description ?? ""} ${it.rarity ?? ""}`} cost={it.cost} label="Buy"
            onBuy={() => send({ type: "buyJoker", index: i })} />
        ))}
        {(v.shopPlanets ?? []).map((it, i) => (
          <Offer key={"p" + i} name={"🪐 " + it.name} desc={it.description} cost={it.cost} label="Buy"
            onBuy={() => send({ type: "buyPlanet", index: i })} />
        ))}
        {(v.shopConsumables ?? []).map((it, i) => (
          <Offer key={"c" + i} name={"🎴 " + it.name} desc={it.description} cost={it.cost} label="Buy"
            onBuy={() => send({ type: "buyConsumable", index: i })} />
        ))}
        {v.shopVoucher && (
          <Offer accent name={"🎟 " + v.shopVoucher.name} desc={v.shopVoucher.description}
            cost={v.shopVoucher.cost} label="Buy" onBuy={() => send({ type: "buyVoucher" })} />
        )}
      </div>
      <div className="row" style={{ marginTop: 12 }}>
        <button className="alt" onClick={() => send({ type: "reroll" })}>Reroll (${v.rerollCost})</button>
        <div className="spacer" />
        <button onClick={() => send({ type: "proceed" })}>Next Blind →</button>
      </div>
    </div>
  );
}
