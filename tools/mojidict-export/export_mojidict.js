#!/usr/bin/env node
/**
 * Export the MOJi offline dictionary (Realm file, extracted from
 * assets/Database/zh-CN_ja/core-mojiread-sc-20241009.zip) to SQLite.
 *
 * Usage:
 *   node export_mojidict.js <input.realm> <output.db>
 *
 * Requirements: Node 18 (realm 12 native binding hangs on newer Node),
 * realm@12.15.0 with the darwin-arm64 napi-v6 prebuild installed
 * (see README.md), better-sqlite3.
 *
 * The script never modifies the input file: it works on copies.
 * Steps: probe schema on a throwaway copy -> learn the source schema
 * version -> open a working copy with the exact schema (upgrades the
 * realm file format in place) -> stream all rows into SQLite.
 */
'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const Realm = require('realm');
const Database = require('better-sqlite3');

const input = process.argv[2];
const output = process.argv[3];
if (!input || !output) {
  console.error('usage: node export_mojidict.js <input.realm> <output.db>');
  process.exit(1);
}
if (!fs.existsSync(input)) {
  console.error('input not found:', input);
  process.exit(1);
}

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'moji-export-'));
console.log('[info] work dir:', tmpDir);

function cleanProp(v) {
  const o = { type: v.type };
  if (v.objectType) o.objectType = v.objectType;
  if (v.optional) o.optional = true;
  if (v.indexed) o.indexed = true;
  return o;
}

// ---------------------------------------------------------- 1) probe schema
const probePath = path.join(tmpDir, 'probe.realm');
fs.copyFileSync(input, probePath);
const schemaSpecs = [];
{
  const probe = new Realm({
    path: probePath,
    schema: [],
    schemaVersion: 999999,
    onMigration: (oldRealm) => {
      for (const c of oldRealm.schema) {
        schemaSpecs.push({
          name: c.name,
          primaryKey: c.primaryKey || undefined,
          properties: JSON.parse(JSON.stringify(c.properties)),
        });
      }
    },
  });
  probe.close();
}
console.log('[info] schema classes:', schemaSpecs.map((s) => s.name).join(', '));

const defs = schemaSpecs.map((s) => ({
  name: s.name,
  ...(s.primaryKey ? { primaryKey: s.primaryKey } : {}),
  properties: Object.fromEntries(
    Object.entries(s.properties).map(([k, v]) => [k, cleanProp(v)])
  ),
}));

// --------------------------------------------------- 2) learn schema version
const verPath = path.join(tmpDir, 'verprobe.realm');
fs.copyFileSync(input, verPath);
let fileVersion = 0;
try {
  new Realm({ path: verPath, schema: defs, schemaVersion: 0 }).close();
} catch (e) {
  const m = /less than last set version (\d+)/.exec(e.message);
  if (!m) throw e;
  fileVersion = parseInt(m[1], 10);
}
console.log('[info] source schema version:', fileVersion);

// ------------------------- 3) open working copy with the exact schema/version
const workPath = path.join(tmpDir, 'working.realm');
fs.copyFileSync(input, workPath);
const realm = new Realm({ path: workPath, schema: defs, schemaVersion: fileVersion });
for (const d of defs) {
  console.log(`[info] ${d.name}: ${realm.objects(d.name).length} rows`);
}

// ------------------------------------------------------- 4) write to sqlite
if (fs.existsSync(output)) fs.unlinkSync(output);
fs.mkdirSync(path.dirname(path.resolve(output)), { recursive: true });
const db = new Database(output);
db.pragma('journal_mode = OFF');
db.pragma('synchronous = OFF');

db.exec(`
CREATE TABLE wort (
  object_id TEXT PRIMARY KEY,
  spell TEXT, pron TEXT, accent TEXT, romaji TEXT,
  excerpt TEXT, excerpt_b TEXT, tags TEXT,
  type INTEGER, sensitive_level INTEGER,
  is_free INTEGER, is_shared INTEGER, has_related INTEGER,
  status TEXT, v_tag TEXT, img_url TEXT,
  created_at INTEGER, updated_at INTEGER
);
CREATE TABLE details (
  object_id TEXT PRIMARY KEY,
  word_id TEXT, part_of_speech TEXT, jita INTEGER, katuyou INTEGER, status TEXT
);
CREATE TABLE subdetails (
  object_id TEXT PRIMARY KEY,
  rela_id TEXT, word_id TEXT, details_id TEXT,
  lang TEXT, title TEXT, context TEXT, conjunctions TEXT,
  lib_id TEXT, lib_text TEXT, status TEXT
);
CREATE TABLE example (
  object_id TEXT PRIMARY KEY,
  rela_id TEXT, word_id TEXT, subdetails_id TEXT,
  lang TEXT, title TEXT, src TEXT, notation_title TEXT,
  v_tag TEXT, status TEXT
);
CREATE INDEX idx_wort_spell ON wort(spell);
CREATE INDEX idx_details_word ON details(word_id);
CREATE INDEX idx_subdetails_word ON subdetails(word_id);
CREATE INDEX idx_subdetails_details ON subdetails(details_id);
CREATE INDEX idx_example_word ON example(word_id);
CREATE INDEX idx_example_subdetails ON example(subdetails_id);
`);

const listJoin = (v) => (v == null ? null : JSON.stringify(Array.from(v)));
const ts = (v) => (v == null ? null : v instanceof Date ? v.getTime() : new Date(v).getTime());
const b = (v) => (v == null ? null : v ? 1 : 0);

const insWort = db.prepare(`INSERT OR REPLACE INTO wort
 (object_id, spell, pron, accent, romaji, excerpt, excerpt_b, tags, type, sensitive_level,
  is_free, is_shared, has_related, status, v_tag, img_url, created_at, updated_at)
 VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`);
const insDetails = db.prepare(`INSERT OR REPLACE INTO details
 (object_id, word_id, part_of_speech, jita, katuyou, status) VALUES (?,?,?,?,?,?)`);
const insSub = db.prepare(`INSERT OR REPLACE INTO subdetails
 (object_id, rela_id, word_id, details_id, lang, title, context, conjunctions, lib_id, lib_text, status)
 VALUES (?,?,?,?,?,?,?,?,?,?,?)`);
const insEx = db.prepare(`INSERT OR REPLACE INTO example
 (object_id, rela_id, word_id, subdetails_id, lang, title, src, notation_title, v_tag, status)
 VALUES (?,?,?,?,?,?,?,?,?,?)`);

const t0 = Date.now();
db.transaction(() => {
  let n = 0;
  for (const w of realm.objects('Wort')) {
    insWort.run(
      w.objectId, w.spell, w.pron, w.accent, w.romaji_hepburn_CN,
      w.excerpt, w.excerptB, w.tags, w.type, w.sensitiveLevel,
      b(w.isFree), b(w.isShared), b(w.hasRelated), w.status, w.vTag, w.imgUrl,
      ts(w.createdAt), ts(w.updatedAt)
    );
    if (++n % 20000 === 0) console.log('[info] wort', n);
  }
  console.log('[info] wort total', n);
  n = 0;
  for (const d of realm.objects('Details')) {
    insDetails.run(d.objectId, d.wordId, listJoin(d.partOfSpeech), d.jita, d.katuyou, d.status);
    if (++n % 20000 === 0) console.log('[info] details', n);
  }
  console.log('[info] details total', n);
  n = 0;
  for (const s of realm.objects('Subdetails')) {
    insSub.run(s.objectId, s.relaId, s.wordId, s.detailsId, s.lang, s.title, s.context,
      listJoin(s.conjunctions), s.libId, s.libText, s.status);
    if (++n % 30000 === 0) console.log('[info] subdetails', n);
  }
  console.log('[info] subdetails total', n);
  n = 0;
  for (const e of realm.objects('Example')) {
    insEx.run(e.objectId, e.relaId, e.wordId, e.subdetailsId, e.lang, e.title, e.src,
      e.notationTitle, e.vTag, e.status);
    if (++n % 20000 === 0) console.log('[info] example', n);
  }
  console.log('[info] example total', n);
})();

db.exec('ANALYZE;');
db.close();
console.log('[done]', output, fs.statSync(output).size, 'bytes in', ((Date.now() - t0) / 1000).toFixed(1), 's');
// realm 12 on macOS occasionally hangs in Realm.close(); the file is fully
// written by this point, so exit explicitly instead of waiting for teardown.
try {
  realm.close();
} catch (e) {
  console.warn('[warn] realm.close failed (ignored):', e.message);
}
process.exit(0);
