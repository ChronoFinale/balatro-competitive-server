import { useState } from "react";
import { useStore } from "@tanstack/react-store";
import { store, send } from "./session";
import { content } from "./content";
import type { UiScreen, UiComponent } from "../../generated/content-types";

// A GENERIC renderer for server-driven UI: it interprets a UiScreen (data) into widgets, with no screen-
// specific code. Selects bind to client data sources; buttons send wire intents with $bind-resolved params;
// stats read session/view state. The same screen JSON will later drive a Lua renderer the same way.
export default function ScreenView({ screen, onNavigate }: { screen: UiScreen; onNavigate?: (id: string) => void }) {
  const [state, setState] = useState<Record<string, string>>({});
  const session = useStore(store);
  const data = useStore(content);

  // data lists a <select>/<stat> can bind to by name
  const sources: Record<string, string[]> = {
    rulesets: session.rulesets,
    decks: data.DECKS.map((d) => d.name),
  };
  const statValue = (key: string) => String((session as unknown as Record<string, unknown>)[key] ?? "—");

  const resolveParams = (params: Record<string, unknown> = {}): Record<string, unknown> => {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(params)) {
      out[k] = typeof v === "string" && v.startsWith("$") ? state[v.slice(1)] : v;
    }
    return out;
  };

  const render = (c: UiComponent, i: number) => {
    const a = c as Record<string, unknown>; // envelope: narrow on `type`, read the rest dynamically
    switch (c.type) {
      case "label":
        return <p key={i} className="stat">{String(a.text)}</p>;
      case "select": {
        const opts = sources[String(a.source)] ?? [];
        const bind = String(a.bind);
        const val = state[bind] ?? opts[0] ?? "";
        return (
          <label key={i} className="row">
            <span className="stat">{String(a.label)}</span>
            <select value={val} onChange={(e) => setState((s) => ({ ...s, [bind]: e.target.value }))}>
              {opts.map((o) => <option key={o} value={o}>{o}</option>)}
            </select>
          </label>
        );
      }
      case "button":
        return (
          <button key={i} onClick={() => send({ type: String(a.intent), ...resolveParams(a.params as Record<string, unknown>) })}>
            {String(a.text)}
          </button>
        );
      case "stat":
        return <div key={i} className="stat">{String(a.label)}: {statValue(String(a.source))}</div>;
      case "input": {
        const bind = String(a.bind);
        return (
          <label key={i} className="row">
            <span className="stat">{String(a.label)}</span>
            <input value={state[bind] ?? ""} placeholder={String(a.placeholder ?? "")}
              onChange={(e) => setState((s) => ({ ...s, [bind]: e.target.value }))} />
          </label>
        );
      }
      case "nav":
        return <button key={i} className="alt" onClick={() => onNavigate?.(String(a.screen))}>{String(a.text)}</button>;
      default:
        return <div key={i} className="stat">[unknown component: {c.type}]</div>;
    }
  };

  return (
    <div className="panel">
      <h2>{screen.title}</h2>
      {screen.components.map(render)}
    </div>
  );
}
