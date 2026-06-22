// Reactive content store: starts from the BUNDLED generated content (instant, offline-safe), then overlays
// whatever the server's delta-sync returns at runtime. UI reads from here, so new content/balance from the
// server appears without rebuilding the client (Tier-1 auto-update).
import { Store } from "@tanstack/store";
import * as bundled from "../../generated/content";
import { syncContent, type ContentCache, type SyncResult } from "./contentSync";
import type {
  JokerDef, DeckType, BossBlind, Tag, Voucher, Consumable, Planet, HandScore, RulesetBundle,
} from "../../generated/content-types";

export interface ContentData {
  version: string; // "bundled" until a server sync replaces it
  JOKERS: readonly JokerDef[];
  DECKS: readonly DeckType[];
  BOSSES: readonly BossBlind[];
  TAGS: readonly Tag[];
  VOUCHERS: readonly Voucher[];
  CONSUMABLES: readonly Consumable[];
  PLANETS: readonly Planet[];
  HAND_SCORES: readonly HandScore[];
  BUNDLES: readonly RulesetBundle[];
}

export const content = new Store<ContentData>({
  version: "bundled",
  JOKERS: bundled.JOKERS,
  DECKS: bundled.DECKS,
  BOSSES: bundled.BOSSES,
  TAGS: bundled.TAGS,
  VOUCHERS: bundled.VOUCHERS,
  CONSUMABLES: bundled.CONSUMABLES,
  PLANETS: bundled.PLANETS,
  HAND_SCORES: bundled.HAND_SCORES,
  BUNDLES: bundled.BUNDLES,
});

// Manifest file path -> the store array it parses into.
const FILE_TO_KEY: Record<string, keyof ContentData> = {
  "rulesets/vanilla.json": "JOKERS",
  "content/decks.json": "DECKS",
  "content/bosses.json": "BOSSES",
  "content/tags.json": "TAGS",
  "content/vouchers.json": "VOUCHERS",
  "content/consumables.json": "CONSUMABLES",
  "content/planets.json": "PLANETS",
  "content/hand-scores.json": "HAND_SCORES",
};

/**
 * Delta-sync content from the server and overlay it onto the store. Pass the persisted {@link ContentCache}
 * in and persist {@link SyncResult.cache} out so repeat syncs only fetch what changed.
 */
export async function syncFromServer(baseUrl: string, cache: ContentCache = {}): Promise<SyncResult> {
  const result = await syncContent(baseUrl, cache);
  const patch: Partial<ContentData> = { version: result.version };
  for (const [path, text] of Object.entries(result.files)) {
    const key = FILE_TO_KEY[path];
    if (key) (patch as Record<string, unknown>)[key] = JSON.parse(text);
  }
  content.setState((s) => ({ ...s, ...patch }));
  return result;
}
