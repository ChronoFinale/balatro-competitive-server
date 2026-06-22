import { useEffect, useState } from "react";
import { useStore } from "@tanstack/react-store";
// Reads from the reactive content store: bundled content instantly, then overlaid by a server delta-sync on
// open — so new content/balance from the server shows up without rebuilding the client (Tier-1 auto-update).
import { content, syncFromServer } from "./content";

const SERVER = "http://localhost:28788"; // dev default; production would come from config

const TABS = ["Jokers", "Decks", "Bosses", "Planets", "Hands", "Vouchers", "Tags", "Consumables", "Rulesets"] as const;
type Tab = (typeof TABS)[number];

export default function Almanac({ onClose }: { onClose: () => void }) {
  const [tab, setTab] = useState<Tab>("Jokers");
  const data = useStore(content);
  const [sync, setSync] = useState("syncing…");

  useEffect(() => {
    syncFromServer(SERVER)
      .then((r) => setSync(`server v${r.version.slice(0, 8)} · ${r.changed.length} files`))
      .catch(() => setSync("offline — bundled content"));
  }, []);

  return (
    <div className="panel almanac">
      <div className="row">
        {TABS.map((t) => (
          <button key={t} className={"alt tiny" + (tab === t ? " on" : "")} onClick={() => setTab(t)}>{t}</button>
        ))}
        <button className="alt tiny" onClick={onClose}>✕ Close</button>
        <span className="stat">{sync}</span>
      </div>

      {tab === "Jokers" && rows(data.JOKERS.map((j) => [`${j.name} (${j.rarity}, $${j.cost})`, `${j.description} · ${j.rules.length} rule${j.rules.length === 1 ? "" : "s"}`]))}
      {tab === "Decks" && rows(data.DECKS.map((d) => [d.name, d.description ?? ""]))}
      {tab === "Bosses" && rows(data.BOSSES.map((b) => [b.name, `${b.effect} · ×${b.reqMult} · ante ${b.minAnte}${b.finisher ? " · finisher" : ""}`]))}
      {tab === "Planets" && rows(data.PLANETS.map((p) => [p.name, `levels ${p.hand}`]))}
      {tab === "Hands" && rows(data.HAND_SCORES.map((h) => [h.display, `${h.baseChips} chips × ${h.baseMult} mult (+${h.chipsPerLevel}/+${h.multPerLevel} per level)`]))}
      {tab === "Vouchers" && rows(data.VOUCHERS.map((v) => [v.name, `$${v.cost} · ${v.description}`]))}
      {tab === "Tags" && rows(data.TAGS.map((t) => [t.name, t.description]))}
      {tab === "Consumables" && rows(data.CONSUMABLES.map((c) => [`${c.name} (${c.type})`, c.description]))}
      {tab === "Rulesets" && rows(data.BUNDLES.map((b) => [b.name, `${b.mode} · ${b.overlays.length ? b.overlays.join("+") : "vanilla"} · ${b.variant}`]))}
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
