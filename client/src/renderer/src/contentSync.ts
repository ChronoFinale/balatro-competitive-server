// Client content auto-update: fetch the server's delta-sync manifest, compare per-file sha256 against the
// local cache, and download only what changed. Pure planning (planSync) is separated from IO (syncContent)
// so the diff is trivially testable. The server is the source of truth (see GameServer /content endpoints).

export interface ManifestFile { path: string; sha256: string; bytes: number; }
export interface Manifest { version: string; files: ManifestFile[]; }

/** Local record of what we already have: path -> sha256 we last downloaded. */
export type ContentCache = Record<string, string>;

/** Pure: the files to (re)download given the server manifest and the local cache (changed or missing). */
export function planSync(manifest: Manifest, cache: ContentCache): string[] {
  return manifest.files.filter((f) => cache[f.path] !== f.sha256).map((f) => f.path);
}

export interface SyncResult {
  version: string;
  changed: string[];                 // paths downloaded this run
  cache: ContentCache;               // updated cache to persist
  files: Record<string, string>;     // path -> downloaded content (for changed files)
}

type Getter = (url: string) => Promise<Response>;

/**
 * Fetch the manifest, download changed files, and return the new cache + the fetched content. Pass the
 * persisted {@link ContentCache} in; persist {@link SyncResult.cache} out. The caller applies the files by
 * tier (data: hand to the renderer; assets: hot-load; mod code: flag a restart).
 */
export async function syncContent(
  baseUrl: string,
  cache: ContentCache,
  get: Getter = fetch,
): Promise<SyncResult> {
  const manifest = (await (await get(`${baseUrl}/content/manifest`)).json()) as Manifest;
  const changed = planSync(manifest, cache);
  const next: ContentCache = { ...cache };
  const files: Record<string, string> = {};
  for (const path of changed) {
    files[path] = await (await get(`${baseUrl}/content/file?path=${encodeURIComponent(path)}`)).text();
    const entry = manifest.files.find((f) => f.path === path);
    if (entry) next[path] = entry.sha256;
  }
  return { version: manifest.version, changed, cache: next, files };
}
