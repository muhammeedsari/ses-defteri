const { chromium, devices } = require('playwright'); const path = require('path');
const LANG = process.argv[2] || 'tr', FULL = process.argv[3] !== 'quick';
(async () => {
  const b = await chromium.launch(); const ctx = await b.newContext({ ...devices['Pixel 7'] }); const pg = await ctx.newPage();
  let where = 'load'; const errs = []; pg.on('pageerror', e => errs.push(where + ' :: ' + e.message.split('\n')[0]));
  pg.on('console', m => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errs.push(where + ' :: console ' + m.text().slice(0, 200)); });
  await pg.addInitScript(lang => {
    window.__calls = []; let n = 0;
    const b64 = len => btoa(String.fromCharCode(...Array.from({ length: len }, (_, i) => 60 + Math.round(50 * Math.abs(Math.sin(i / 7))))));
    const list = []; for (let i = 0; i < 30; i++) list.push({ id: 'm' + i, src: { uri: 'content://x/' + i }, mediaId: i, title: (i < 15 ? 'Kayıt ' : 'Şarkı ') + i, name: 'f' + i + '.m4a', dur: 60000 + i * 1000, music: i >= 15, rec: i < 15, own: i < 15, date: Date.now() - i * 1e7, artist: 'Sanatçı', album: 'Albüm ' + (i % 3), albumId: i % 3, folder: 'Music', mime: 'audio/mp4', size: 1e6 });
    const R = k => ({
      peaks: { rate: 20, scale: 'sqrt', dur: 60000, data: b64(1200) }, wsInit: { path: '/f/w.wav', dur: 60000, ch: 1, rate: 48000 },
      wsOp: { path: '/f/o.wav', dur: 60000, ch: 1, report: { reduction: 10 } }, tlAdd: { path: '/f/c' + n + '.wav', dur: 30000, ch: 2 },
      diagnose: { path: '/f/d.wav', dur: 60000, ch: 1, lufs: -22, lra: 5, peak: -3, clipped: 0, snr: 12, noise: -40, hum: 10, humHz: 50, clicks: 3, plosive: 1, sib: 3, channel: '', head: 0.6, tail: 2, eq: 3, speech: true },
      clean: { path: '/f/cl.wav', dur: 58000, lufs: -14, reports: { denoise: { before: -40, after: -60, reduction: 20 } } },
      transcribe: { segments: [[0, 2, 'Merhaba bu bir deneme.'], [2.5, 5, 'Şimdi ııı ikinci cümle.']], text: 'Merhaba bu bir deneme. Şimdi ııı ikinci cümle.', srt: '1\n00:00:00,000 --> 00:00:02,000\nMerhaba\n', lang: 'tr' },
      asrStatus: { base: true, small: false }, chapters: { times: [20, 40] }, fillers: { ranges: [[3.1, 3.5]] }, beats: { bpm: 100, times: [0, 0.6, 1.2, 1.8] },
      loudness: { lufs: -20, lra: 5, peak: -2 }, spectro: { img: b64(64 * 100), rows: 64, cols: 100, rate: 48000, fmax: 20000 },
      wsSave: { item: { id: 'new' + n, src: { uri: 'content://n/' + n }, name: 'yeni.m4a', title: 'Yeni', dur: 30000, rec: true, own: true } },
      tlSep: { voc: { path: '/f/v.wav', dur: 30000, ch: 2 }, acc: { path: '/f/a.wav', dur: 30000, ch: 2 } }, tlSpk: { segs: [[0, 5, 0], [5, 10, 1]], n: 2, db: [-20, -25] },
      ttsStatus: {}, tlSfx: { path: '/f/s.wav', dur: 2000, ch: 2 }, tlRender: { path: '/f/m.wav', dur: 30000, ch: 2 }, ringtone: { name: 'zil' }, roomTest: { noise: -50, verdict: 'ok' },
    })[k] || {};
    const job = (kind, a) => { const id = 'j' + (++n); window.__calls.push(kind); setTimeout(() => window.__sdJob(id, true, R(kind)), 40); return id; };
    let st = { id: '', playing: false, want: false, pos: 0, dur: 0, rate: 1, count: 0, index: -1 };
    const fixed = { caps: () => JSON.stringify({ native: true, media: true, asr: true, sep: true, mp3: true, opus: true, spk: true }), getLang: () => lang, job, listMedia: () => JSON.stringify(list),
      themePref: () => '', plState: () => JSON.stringify(st), tlPos: () => -1, tlMeter: () => '', recStart: () => '{"ok":true}', recLevel: () => 0.2, recInfo: () => '{}', lastCrash: () => '',
      plQueue: (items, idx, ms, auto) => { const it = JSON.parse(items)[idx]; st = Object.assign({}, st, { id: it.id, dur: it.dur, pos: ms, playing: !!auto, count: 1, index: idx }); },
      plPlay: () => { st.playing = true; }, plPause: () => { st.playing = false; }, tlPlay: () => '', tlPlayFrom: () => '' };
    window.SesDefteriAndroid = new Proxy({}, { get: (_, k) => fixed[k] || ((...a) => { window.__calls.push(String(k)); return ''; }) });
    localStorage.setItem('sd:lang', JSON.stringify(lang)); localStorage.setItem('sd:langChosen', 'true');
  }, LANG);
  await pg.goto('file://' + path.join(__dirname, 'index.html')); await pg.waitForTimeout(900);
  const ev = s => pg.evaluate(c => window.__ev(c), s);
  const cdp = await ctx.newCDPSession(pg);
  const tp = (type, x, y) => cdp.send('Input.dispatchTouchEvent', { type, touchPoints: type === 'touchEnd' ? [] : [{ x, y }] });
  const swipe = async (x, y, dy) => { await tp('touchStart', x, y); for (let k = 10; k <= dy; k += 10) { await tp('touchMove', x, y - k); await pg.waitForTimeout(6); } await tp('touchEnd'); await pg.waitForTimeout(400); };
  const out = []; const note = s => { out.push(s); };
  const overflow = async label => { const r = await pg.evaluate(() => { const w = innerWidth; const bad = []; for (const el of document.querySelectorAll('body *')) { if (!el.offsetParent && getComputedStyle(el).position !== 'fixed') continue; const r = el.getBoundingClientRect(); let p = el.parentElement, inScroll = false; while (p) { const o = getComputedStyle(p).overflowX; if (o === 'auto' || o === 'scroll' || o === 'hidden' || o === 'clip') { if (p !== document.body && p !== document.documentElement) inScroll = true; break; } p = p.parentElement; } if (!inScroll && r.width && r.right > w + 1) bad.push((el.id ? '#' + el.id : el.className || el.tagName).toString().slice(0, 30) + ' ' + Math.round(r.right)); } return [document.documentElement.scrollWidth, w, bad.slice(0, 6)]; }); if (r[0] > r[1] || r[2].length) note(`OVERFLOW ${label}: page ${r[0]}/${r[1]} ${r[2].join(', ')}`); };
  const rawKeys = async label => { const k = await pg.evaluate(() => { const txt = document.body.innerText; return (txt.match(/\b[a-z]+[A-Z][a-zA-Z]+\b/g) || []).filter(x => !/^(iPhone|macOS|YouTube|WhatsApp|TikTok|SoundCloud|GitHub|mp3|McAfee)$/.test(x)).slice(0, 8); }); if (k.length) note(`RAW KEYS ${label}: ${k.join(' ')}`); };
  const closeAll = async () => { for (let i = 0; i < 5; i++) { const open = await ev('stack.length'); if (!open) break; await ev('(() => { const top = stack[stack.length - 1]; if (top === "rec") { R.state = "idle"; } S.modalLocked = false; closeOverlay(top); })()'); await pg.waitForTimeout(300); } };
  const shot = n => pg.screenshot({ path: path.join(__dirname, `sw-${LANG}-${n}.png`) });
  // click every visible button inside a container, closing whatever opens
  const clickAll = async (label, sel, skip) => {
    const ids = await pg.evaluate(([sel, skip]) => { const r = []; document.querySelectorAll(sel).forEach((b, i) => { b.dataset.sw = i; const s = getComputedStyle(b); if (b.offsetParent && s.visibility !== 'hidden' && !b.disabled && !(skip && b.matches(skip))) r.push(i); }); return r; }, [sel, skip || '']);
    for (const i of ids) {
      const name = await pg.evaluate(i => { const b = document.querySelector(`[data-sw="${i}"]`); return b ? (b.id || b.dataset.tl || b.dataset.act || b.getAttribute('aria-label') || b.innerText || b.className).trim().slice(0, 30) : null; }, i);
      if (name == null) continue;
      where = `${label} > ${name}`;
      try { await pg.click(`[data-sw="${i}"]`, { timeout: 2000 }); } catch (e) { continue; }
      await pg.waitForTimeout(350);
      const modal = await ev('stack.includes("modal") ? document.querySelector("#modalCard").innerText.split("\\n")[0].slice(0, 40) : ""');
      if (modal) { await overflow(where + ' (dialog)'); await rawKeys(where + ' (dialog)'); }
      await closeAll();
      if (label.startsWith('player')) await ev('(() => { if (!stack.includes("player")) openPlayer(S.cur); })()');
      if (label.startsWith('edit')) await ev('(() => { if (S.tab !== "edit") document.querySelector("[data-tab=edit]").click(); })()');
      await pg.waitForTimeout(150);
    }
    return ids.length;
  };
  // tabs
  for (const tab of ['rec', 'music', 'edit']) {
    where = 'tab ' + tab; await pg.click(`[data-tab="${tab}"]`); await pg.waitForTimeout(500);
    await overflow('tab ' + tab); await rawKeys('tab ' + tab); await shot(tab);
    if (tab !== 'edit') { await pg.evaluate(() => scrollTo(0, 0)); await swipe(200, 650, 400); const y = await pg.evaluate(() => scrollY); if (y < 100) note(`SCROLL tab ${tab}: touch scroll did not move (${y})`); await pg.evaluate(() => scrollTo(0, 0)); }
  }
  if (!FULL) { console.log(out.join('\n') || 'clean'); console.log('ERRORS', errs.length ? '\n' + [...new Set(errs)].join('\n') : 'none'); await b.close(); return; }
  // header buttons, settings
  where = 'settings'; await pg.click('[data-tab="rec"]'); await pg.click('#setBtn'); await pg.waitForTimeout(400); await overflow('settings'); await rawKeys('settings'); await shot('settings');
  { const before = await ev('document.querySelector("#modalCard").scrollTop'); const bx = await pg.$eval('#modalCard', e => { const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }); await swipe(bx[0], bx[1] + 100, 300); const after = await ev('document.querySelector("#modalCard").scrollTop'); if (after <= before) note(`SCROLL settings dialog: touch scroll did not move (${before}->${after})`); }
  await closeAll();
  // chips / sort / import
  const nRec = await clickAll('rec tab', '#app button:not([data-tab]):not(.rec-open):not(#recBtn)', '[data-more],#setBtn');
  // player
  where = 'player open'; await pg.click('[data-tab="rec"]'); await pg.waitForTimeout(300); await pg.click('.rec-open'); await pg.waitForTimeout(800);
  await overflow('player'); await rawKeys('player'); await shot('player');
  { const bx = [200, 700]; const before = await ev('document.querySelector("#player").scrollTop'); await swipe(bx[0], bx[1], 400); const after = await ev('document.querySelector("#player").scrollTop'); if (after <= before) note(`SCROLL player sheet (finger on lower part): did not move (${before}->${after})`); await ev('document.querySelector("#player").scrollTop = 0'); }
  { const r = await pg.$eval('#plScroll', e => { const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2, r.height]; }); const before = await ev('document.querySelector("#player").scrollTop'); await swipe(r[0], r[1], 300); const after = await ev('document.querySelector("#player").scrollTop'); note(`INFO player: vertical swipe starting on the waveform scrolls the sheet: ${after > before ? 'yes' : 'NO'}`); await ev('document.querySelector("#player").scrollTop = 0'); }
  const nPl = await clickAll('player', '#player button', '#plBack,#plDelete');
  await closeAll();
  // recorder
  where = 'recorder'; await pg.click('[data-tab="rec"]'); await pg.click('#recBtn'); await pg.waitForTimeout(400); await overflow('recorder'); await rawKeys('recorder'); await shot('recorder');
  await pg.click('#recMain'); await pg.waitForTimeout(800); await overflow('recorder recording'); await shot('recording');
  await ev('(() => { R.state = "idle"; closeOverlay("rec"); })()'); await pg.waitForTimeout(300);
  // edit: add two clips, then every button with and without a selection
  where = 'edit'; await pg.click('[data-tab="edit"]'); await pg.waitForTimeout(300);
  await ev(`(() => { TL.p.clips.push({ id: 'A', tr: 0, path: '/f/a.wav', ch: 2, dur: 30, in: 0, out: 30, at: 0, gain: 0, fi: 0, fo: 0, name: 'A' }, { id: 'B', tr: 1, path: '/f/b.wav', ch: 2, dur: 20, in: 0, out: 20, at: 5, gain: 0, fi: 0, fo: 0, name: 'B' }); TL.p.tracks.push({ vol: 0, mute: false }); tlCommit(tlSnap()); renderEdit(); })()`);
  await pg.waitForTimeout(300); await overflow('edit with clips'); await rawKeys('edit'); await shot('edit');
  const nEd = await clickAll('edit (nothing selected)', '#editTab button', '[data-tl="new"],[data-tl="rec"],[data-tl="play"]');
  await ev('(() => { TL.sel = "A"; renderTlBar(); drawTl(); })()'); await pg.waitForTimeout(200); await shot('edit-sel');
  const nEd2 = await clickAll('edit (clip selected)', '#tlBar button', '[data-tl="del"],[data-tl="cut"],[data-tl="done"],[data-tl="punch"]');
  // effect list of a clip
  await ev('(() => { TL.sel = "A"; renderTlBar(); })()'); await pg.click('#tlBar [data-tl="fx"]').catch(() => {}); await pg.waitForTimeout(300);
  const nFx = await clickAll('edit effects', '#tlFxList button, #modalCard button[data-v]');
  await closeAll();
  console.log(`buttons pressed: rec ${nRec}, player ${nPl}, edit ${nEd}+${nEd2}+${nFx}`);
  console.log(out.join('\n') || 'no layout problems');
  console.log('ERRORS', errs.length ? '\n' + [...new Set(errs)].join('\n') : 'none');
  await b.close();
})();
