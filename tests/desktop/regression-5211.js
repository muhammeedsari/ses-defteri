// Runs real functions from body.html, with only Android/DOM dependencies replaced.
const fs = require('fs'), path = require('path'), vm = require('vm'), assert = require('assert/strict');
const root = path.resolve(__dirname, '../..'), web = path.join(root, 'web');
const source = fs.readFileSync(path.join(web, 'body.html'), 'utf8').replace(/\r\n/g, '\n');
function section(start, end) {
  const a = source.indexOf(start), b = source.indexOf(end, a + start.length);
  assert(a >= 0 && b > a, start); return source.slice(a, b);
}
function context(extra = {}) { return vm.createContext({ console, setInterval, clearInterval, ...extra }); }
const json = x => JSON.parse(JSON.stringify(x));
function deferred(id) { let resolve, reject; const p = new Promise((a, b) => { resolve = a; reject = b; }); p.jobId = id; return { p, resolve, reject }; }
async function main() {
  let syntax = 0;
  for (const file of fs.readdirSync(web).filter(x => x.endsWith('.js'))) { new vm.Script(fs.readFileSync(path.join(web, file), 'utf8'), { filename: file }); syntax++; }
  for (const match of source.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)) if (match[1].trim()) { new vm.Script(match[1]); syntax++; }
  console.log(`PASS ${syntax} JavaScript syntax checks`);
  const manifest = fs.readFileSync(path.join(root, 'android/AndroidManifest.xml'), 'utf8');
  const version = source.match(/const VERSION = '([^']+)'/)[1];
  assert(manifest.includes(`android:versionName="${version}"`));
  const assembled = fs.readFileSync(path.join(web, 'head.html'), 'utf8') + fs.readFileSync(path.join(web, 'extra.css'), 'utf8') + '</style>\n\n' + fs.readFileSync(path.join(web, 'body.html'), 'utf8');
  assert.equal(fs.readFileSync(path.join(web, 'page.html'), 'utf8'), assembled);
  assert(fs.readFileSync(path.join(root, 'android/build_apk.py'), 'utf8').includes('"i18n70.js"'));
  console.log('PASS version, generated page and translation build list are consistent');

  const clip = { path: '/old/final.wav', src: '/old/src.wav', prev: '/old/prev.wav', hcOut: '/old/final.wav', hc: [{ key: '/old/src.wav|{"op":"denoise"}', path: '/old/cache.wav' }], fx: [{ b: { path: '/old/history.wav', src: '/old/src.wav' } }] };
  let backed, saved;
  const c = context({ TL: { p: { clips: [clip] } }, VERSION: 'test', recItems: () => [], toast() {}, hideToast() {}, t: x => x, arProg() {},
    ls: { get: k => k === 'proj' ? saved : k === 'pcTpl' ? { intro: { path: '/old/intro.wav' } } : [], set: (k, v) => { if (k === 'proj') saved = v; } }, S: { cats: [], items: new Map() },
    runJob: async (k, a) => { backed = a; return { missing: 0 }; }, projectFiles: undefined });
  vm.runInContext(section('function projectFiles(', '\nfunction archivePick'), c);
  vm.runInContext(section('const fileKey =', '\n// Visit file references'), c);
  vm.runInContext(section('async function doBackup(', '\nasync function doRestore'), c);
  await vm.runInContext('doBackup("backup")', c);
  const meta = JSON.parse(backed.json);
  assert.equal(backed.files.length, 6); assert.equal(meta.v, 2);
  assert.equal(clip.path, '/old/final.wav', 'backup must not mutate live project');
  assert.equal(meta.proj.clips[0].hc[0].key, meta.proj.clips[0].src + '|{"op":"denoise"}');
  console.log('PASS backup includes source, A/B, cache, nested history and template');
  const paths = Object.fromEntries(backed.files.map(([entry], i) => [entry, `/new/${i}.wav`]));
  Object.assign(c, { runJob: async () => ({ json: backed.json, items: {}, skip: {}, paths, projDir: '/new' }), tlLoad() {}, render() {}, db: { put: async () => {} } });
  c.TL.p = { clips: [] };
  vm.runInContext(section('async function doRestore(', '\n/* ---------- 2.2:'), c);
  await vm.runInContext('doRestore("backup")', c);
  assert(saved.clips[0].src.startsWith('/new/')); assert(saved.clips[0].fx[0].b.path.startsWith('/new/'));
  assert(saved.clips[0].hc[0].key.startsWith('/new/'));
  console.log('PASS restore rewrites every supported project reference and cache key');
  let acknowledgements = 0;
  Object.assign(c, { AND: { acknowledgeRestore: () => { acknowledgements++; return true; } }, metaOf: x => x, fromNative: (n, m) => ({ ...m, ...n }),
    HEAVY: ['denoise'], catById: () => null,
    receipt: { json: JSON.stringify({ app: 'sesdefteri', v: 2, recs: [{ e: 'audio/0/a.wav', m: {} }] }), items: { 'audio/0/a.wav': { id: 'restored', src: { path: '/new/a.wav' } } }, skip: {}, restoreToken: 'token' } });
  c.db.put = async () => { throw new Error('storage full'); };
  await assert.rejects(vm.runInContext('applyRestoreResult(receipt)', c), /storage full/); assert.equal(acknowledgements, 0);
  c.db.put = async () => {}; await vm.runInContext('applyRestoreResult(receipt)', c); assert.equal(acknowledgements, 1);
  await vm.runInContext('applyRestoreResult(receipt)', c); assert.equal(c.S.items.size, 1);
  console.log('PASS restore receipt is acknowledged only after durable metadata; replay is idempotent');
  c.TL.p = { clips: [] };
  c.legacyReceipt = { json: JSON.stringify({ app: 'sesdefteri', v: 1, recs: [], proj: { clips: [{ path: '/old/final.wav', src: '/old/missing.wav', rk: [{ k: 'denoise' }, { k: 'eq' }], fx: [{ b: { path: '/old/missing.wav' } }] }] } }), items: {}, skip: {}, paths: { 'proj/final.wav': '/new/final.wav' } };
  await vm.runInContext('applyRestoreResult(legacyReceipt)', c);
  assert.equal(saved.clips[0].path, '/new/final.wav'); assert.equal(saved.clips[0].src, undefined); assert.equal(saved.clips[0].fx, undefined);
  assert.deepEqual(json(saved.clips[0].rk), [{ k: 'eq' }]);
  console.log('PASS incomplete legacy project keeps its rendered sound without stale history paths');

  vm.runInContext(section('function remapMarks(', '\nasync function batchOp'), c);
  c.marks = [{ t: 5 }, { t: 15, photo: 'p.jpg' }, { t: 25 }, { t: 36 }, { t: 40 }];
  assert.deepEqual(json(vm.runInContext('remapMarks(marks, [[10000,20000],[30000,40000]])', c)), [{ t: 5, photo: 'p.jpg' }, { t: 16 }]);
  console.log('PASS trim/pause time mapping preserves surviving marks and removes cut marks');

  const jobs = [], CL = { session: 1, r: { id: 'A', src: {} } };
  let closed = 0;
  const clean = context({ CL, NATIVE: true, tlStop() {}, audio: { paused: true }, clCard() {}, esc: x => x, t: x => x, roomLearn() {}, clPlan() {}, kindOf: () => 'rec', ls: { get: () => 'youtube' }, pauseArgs: () => ({}),
    toast() {}, errText: e => e.message, closeModal() { closed++; }, $: () => null,
    runJob: () => { const d = deferred('job' + jobs.length); jobs.push(d); return d.p; } });
  vm.runInContext(section('function clJob(', '\n/** what the diagnosis'), clean);
  vm.runInContext(section('async function openClean(', '\nfunction clCard'), clean);
  const a = vm.runInContext('openClean({id:"A",src:{}}, false)', clean);
  const b = vm.runInContext('openClean({id:"B",src:{}}, false)', clean);
  const bId = CL.job; jobs[0].reject(new Error('late failure')); await a;
  assert.equal(CL.job, bId); assert.equal(CL.r.id, 'B'); assert.equal(closed, 0);
  jobs[1].resolve({ path: 'B.wav' }); await b; assert.equal(CL.diag.path, 'B.wav');
  const a2 = vm.runInContext('openClean({id:"A",src:{}}, false)', clean);
  const b2 = vm.runInContext('openClean({id:"B",src:{}}, false)', clean);
  jobs[2].resolve({ path: 'stale.wav' }); await a2; assert.equal(CL.diag, null);
  jobs[3].resolve({ path: 'B2.wav' }); await b2; assert.equal(CL.diag.path, 'B2.wav');
  const learned = deferred('profile'); CL.fixes = new Set(['eq']); CL.strength = 1; CL.purpose = 'youtube';
  Object.assign(clean, { CL_ORDER: ['eq'], S: { learnVoice: true }, CAPS: {}, roomArgs: () => ({}), purposeTarget: x => x, learnedProfile: () => learned.p });
  vm.runInContext(section('async function clRun(', '\nfunction clDone'), clean);
  const preparing = vm.runInContext('clRun()', clean); CL.session++; CL.r = { id: 'new' }; learned.resolve({}); await preparing;
  assert.equal(jobs.length, 4, 'old preparation started a job in the new session');
  console.log('PASS stale Clean success/failure/finally cannot alter a newer session');

  const heavy = section('  const heavy = async () => {', '\n  draw();\n  card.addEventListener');
  const rack = context({ c: { path: 'old.wav', rk: [{ v: 2 }] }, committed: { path: 'old.wav', rk: [{ v: 1 }] }, generation: 0, closed: false, TL: {}, tlDiff() {},
    card: { querySelector: () => ({}), classList: { add() {}, remove() {} } }, tlHeavyRun: async () => { throw new Error('injected'); }, draw() {}, live() {}, toast() {}, errText: e => e.message, t: x => x });
  vm.runInContext(heavy + '\nglobalThis.work = heavy;', rack);
  await rack.work(); assert.deepEqual(json(rack.c), { path: 'old.wav', rk: [{ v: 1 }] });
  const pending = deferred('heavy'); rack.tlHeavyRun = candidate => pending.p.then(() => { candidate.path = 'new.wav'; });
  rack.c.rk[0].v = 3; const late = rack.work(); rack.closed = true; rack.generation++; pending.resolve(); await late;
  assert.equal(rack.c.path, 'old.wav');
  rack.closed = false; rack.tlHeavyRun = async candidate => { candidate.path = 'success.wav'; };
  await rack.work(); assert.equal(rack.c.path, 'success.wav'); assert.equal(rack.committed.path, 'success.wav');
  console.log('PASS failed/cancelled/late computed effects preserve committed audio and settings');

  const locales = context({ window: {} }); vm.runInContext(fs.readFileSync(path.join(web, 'i18n70.js'), 'utf8'), locales);
  for (const lang of ['tr','en','de','fr','es','pt','ru','ar','zh']) for (const key of ['recoveryPending','archiveLimit','livePartial','jobsInterrupted','rsLegacy']) assert(locales.window.SD_I18N[lang][key]);
  console.log('PASS new messages exist in all nine languages');
}
main().catch(e => { console.error(e); process.exitCode = 1; });
