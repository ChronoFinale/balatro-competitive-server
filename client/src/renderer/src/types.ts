// TypeScript mirror of the server's authoritative view contract
// (com.balatro.engine.net.ClientView / CardView). The renderer's only knowledge
// of game state — keep in sync with the Java records.
import type { Rank, Suit, Enhancement, Edition, Seal } from "../../generated/content-types";

export interface CardView {
  uid: string; // server card UUID (target handle)
  // Identity fields are null when faceDown (boss blinds: The House/Wheel/Mark/Fish). The server
  // withholds them so the player can't peek at what they're forced to play — render a card back.
  // Typed with the generated card enums so the wire contract can't drift from the server.
  rank: Rank | null;
  suit: Suit | null;
  enhancement: Enhancement | null;
  edition: Edition | null;
  seal: Seal | null;
  permaChips: number;
  permaMult: number;
  faceDown: boolean;
  forcedSelected: boolean; // Cerulean Bell: this card is locked into every played hand
}

export interface JokerView {
  key: string;
  name: string;
  description?: string;
  rarity?: string;
  cost?: number;
  display?: string | number;
  edition?: string;
}

/** A mixed shop slot — kind is JOKER | TAROT | PLANET (how to label it, which buy path it takes). */
export interface ShopItem {
  kind: "JOKER" | "TAROT" | "PLANET";
  key: string;
  name: string;
  cost: number;
  description?: string;
  rarity?: string;
  edition?: string;
}

export interface HeldConsumable {
  key: string;
  name: string;
  description?: string;
  maxTargets?: number;
}

export interface ShopVoucher {
  key: string;
  name: string;
  description?: string;
  cost: number;
}

/** A booster pack offered in the shop. */
export interface PackOffer {
  kind: string;
  size: string;
  name: string;
  cost: number;
  shown: number;
  choose: number;
}

/** A card revealed inside an opened pack. */
export interface PackItem {
  type: "JOKER" | "CARD" | "CONSUMABLE";
  key?: string;
  name: string;
  description?: string;
  rank?: Rank;
  suit?: Suit;
  enhancement?: Enhancement;
}

export interface OpenPack {
  picksLeft: number;
  items: PackItem[];
}

export interface ClientView {
  ante: number;
  blind: string;
  requirement: number;
  roundScore: number;
  handsLeft: number;
  discardsLeft: number;
  money: number;
  handSize: number;
  phase: string;
  hand: CardView[];
  jokers: JokerView[];
  shop: ShopItem[] | null;
  rerollCost: number;
  boss: string | null;
  bossEffect: string | null;
  consumables: HeldConsumable[];
  handLevels: Record<string, number>;
  deckStats: { size?: number; remaining?: number };
  counters: Record<string, unknown>;
  shopVouchers: ShopVoucher[] | null;
  packs: PackOffer[] | null;
  openPack: OpenPack | null;
}

export interface ServerMessage {
  type: string;
  seq?: number;
  accepted?: boolean;
  rejection?: string;
  view?: ClientView;
  replay?: { runningChips: number; runningMult: number }[];
  chips?: number;
  mult?: number;
  score?: number;
  playerId?: string;
  rulesets?: string[]; // names the server offers (curated + bundles + custom), sent on auth
  [k: string]: unknown;
}
