// Reactive content store: starts from the BUNDLED generated content (instant, offline-safe) OR the last
// server-synced content persisted across restarts, then overlays the server's delta-sync at runtime. UI reads
// from here, so new content/balance from the server appears without rebuilding the client (Tier-1 auto-update).
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

const BUNDLED: ContentData = {
  version: "bundled",
  JOKERS: bundled.JOKERS, DECKS: bundled.DECKS, BOSSES: bundled.BOSSES, TAGS: bundled.TAGS,
  VOUCHERS: bundled.VOUCHERS, CONSUMABLES: bundled.CONSUMABLES, PLANETS: bundled.PLANETS,
  HAND_SCORES: bundled.HAND_SCORES, BUNDLES: bundled.BUNDLES,
};

const CACHE_KEY = "balatro.contentCache";
const DATA_KEY = "balatro.contentData";

function lsGet(key: string): string | null {
  try { return typeof localStorage !== "undefined" ? localStorage.getItem(key) : null; } catch { return null; }
}
function lsSet(key: string, val: string): void {
  try { if (typeof localStorage !== "undefined") localStorage.setItem(key, val); } catch { /* quota/SSR: ignore */ }
}

// Hydrate from the last persisted sync if present (a restart starts already-updated, not back at bundled).
const persisted = lsGet(DATA_KEY);
export const content = new Store<ContentData>(persisted ? { ...BUNDLED, ...JSON.parse(persisted) } : BUNDLED);

/** The persisted per-file hash cache, so repeat syncs only fetch what changed — even across app restarts. */
export function loadCache(): ContentCache {
  const c = lsGet(CACHE_KEY);
  return c ? (JSON.parse(c) as ContentCache) : {};
}

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
 * Delta-sync content from the server, overlay it onto the store, and persist both the content and the hash
 * cache. Defaults to the persisted cache, so repeated calls (and restarts) only download changed files.
 */
export async function syncFromServer(baseUrl: string, cache: ContentCache = loadCache()): Promise<SyncResult> {
  const result = await syncContent(baseUrl, cache);
  const patch: Partial<ContentData> = { version: result.version };
  for (const [path, text] of Object.entries(result.files)) {
    const key = FILE_TO_KEY[path];
    if (key) (patch as Record<string, unknown>)[key] = JSON.parse(text);
  }
  content.setState((s) => ({ ...s, ...patch }));
  lsSet(CACHE_KEY, JSON.stringify(result.cache));
  lsSet(DATA_KEY, JSON.stringify(content.state)); // a restart starts from this synced snapshot
  return result;
}
