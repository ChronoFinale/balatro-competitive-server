// Enforces the preview-mirror invariant: the client `preview.js` must reproduce the
// server ScoringEngine's chips/mult/score for every supported scenario. The Java test
// `PreviewFixtureGenerator` writes `build/preview-fixtures.json` (server result as the
// oracle, serialized exactly as the ClientView sends them); this script runs the real
// `preview.js` against each fixture and asserts equality.
//
// Run:  node preview.test.mjs        (after ./gradlew test --tests '*PreviewFixtureGenerator')
// Exit: 0 = all supported fixtures match; 1 = a mismatch (preview diverged from the server).
//
// A fixture whose preview returns null is "unsupported" (probabilistic/native effect) and
// legitimately falls back to the server at runtime — counted and skipped, not failed.

import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const { BalatroPreview } = require(join(here, 'src/main/resources/public/preview.js'));

const fixturesPath = join(here, 'build/preview-fixtures.json');
let fixtures;
try {
  fixtures = JSON.parse(readFileSync(fixturesPath, 'utf8'));
} catch {
  console.error(`Missing ${fixturesPath}\nRun:  ./gradlew test --tests 'com.balatro.engine.PreviewFixtureGenerator'`);
  process.exit(2);
}

const EPS = 1e-6;
const near = (a, b) => Math.abs(a - b) <= EPS + EPS * Math.abs(b);

let passed = 0;
let unsupported = 0;
const failures = [];

for (const fx of fixtures) {
  const got = BalatroPreview.previewScore(fx.played, fx.held, fx.jokers, fx.run);
  if (got === null) { unsupported++; continue; }
  const e = fx.expected;
  if (near(got.chips, e.chips) && near(got.mult, e.mult) && near(got.score, e.score)) {
    passed++;
  } else {
    failures.push({ name: fx.name, got, expected: e });
  }
}

for (const f of failures) {
  console.error(
    `FAIL ${f.name}: preview chips=${f.got.chips} mult=${f.got.mult} score=${f.got.score} ` +
    `!= server chips=${f.expected.chips} mult=${f.expected.mult} score=${f.expected.score}`);
}

console.log(`preview-mirror: ${passed} matched, ${unsupported} unsupported (server-only), ${failures.length} failed`);
process.exit(failures.length ? 1 : 0);
