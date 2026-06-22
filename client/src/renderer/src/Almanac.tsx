import { useState } from "react";
// Rendered ENTIRELY from the generated content module — no server round-trip. The data is the DSL,
// compiled to typed TS (./generated/content.ts), checked against the generated interfaces at build.
import {
  DECKS, BOSSES, TAGS, VOUCHERS, CONSUMABLES, PLANETS, HAND_SCORES, BUNDLES,
} from "../../generated/content";

const TABS = ["Decks", "Bosses", "Planets", "Hands", "Vouchers", "Tags", "Consumables", "Rulesets"] as const;
type Tab = (typeof TABS)[number];

export default function Almanac({ onClose }: { onClose: () => void }) {
  const [tab, setTab] = useState<Tab>("Decks");
  return (
    <div className="panel almanac">
      <div className="row">
        {TABS.map((t) => (
          <button key={t} className={"alt tiny" + (tab === t ? " on" : "")} onClick={() => setTab(t)}>{t}</button>
        ))}
        <button className="alt tiny" onClick={onClose}>✕ Close</button>
      </div>

      {tab === "Decks" && rows(DECKS.map((d) => [d.name, d.description ?? ""]))}
      {tab === "Bosses" && rows(BOSSES.map((b) => [b.name, `${b.effect} · ×${b.reqMult} · ante ${b.minAnte}${b.finisher ? " · finisher" : ""}`]))}
      {tab === "Planets" && rows(PLANETS.map((p) => [p.name, `levels ${p.hand}`]))}
      {tab === "Hands" && rows(HAND_SCORES.map((h) => [h.display, `${h.baseChips} chips × ${h.baseMult} mult (+${h.chipsPerLevel}/+${h.multPerLevel} per level)`]))}
      {tab === "Vouchers" && rows(VOUCHERS.map((v) => [v.name, `$${v.cost} · ${v.description}`]))}
      {tab === "Tags" && rows(TAGS.map((t) => [t.name, t.description]))}
      {tab === "Consumables" && rows(CONSUMABLES.map((c) => [`${c.name} (${c.type})`, c.description]))}
      {tab === "Rulesets" && rows(BUNDLES.map((b) => [b.name, `${b.mode} · ${b.overlays.length ? b.overlays.join("+") : "vanilla"} · ${b.variant}`]))}
    </div>
  );
}

function rows(items: [string | undefined, string | undefined][]) {
  return (
    <div className="almanac-list">
      {items.map(([name, detail], i) => (
        <div key={i} className="offer">
          <b>{name ?? "?"}</b>
          {detail && <span className="stat"> — {detail}</span>}
        </div>
      ))}
    </div>
  );
}
