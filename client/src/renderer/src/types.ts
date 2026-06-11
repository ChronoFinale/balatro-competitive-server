// TypeScript mirror of the server's authoritative view contract
// (com.balatromp.engine.net.ClientView / CardView). The renderer's only knowledge
// of game state — keep in sync with the Java records.

export interface CardView {
  uid: number;
  rank: string;
  suit: string;
  enhancement: string;
  edition: string;
  seal: string;
  permaChips: number;
  permaMult: number;
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

export interface ShopItem {
  key: string;
  name: string;
  cost: number;
  description?: string;
  rarity?: string;
  edition?: string;
}

export interface ShopPlanet {
  key: string;
  name: string;
  cost: number;
  description?: string;
}

export interface ShopConsumable {
  key: string;
  name: string;
  cost: number;
  description?: string;
  maxTargets?: number;
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
  shopPlanets: ShopPlanet[] | null;
  shopConsumables: ShopConsumable[] | null;
  consumables: HeldConsumable[];
  handLevels: Record<string, number>;
  deckStats: { size?: number; remaining?: number };
  counters: Record<string, unknown>;
  shopVoucher: ShopVoucher | null;
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
  [k: string]: unknown;
}
