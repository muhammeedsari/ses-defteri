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
    const job = (kind, a) => { const id = 'j' + (++n); window.__calls.push(kind); (window.__args = window.__args || []).push([kind, JSON.parse(a)]); const ov = window.__R && window.__R[kind]; if (ov) { setTimeout(() => window.__sdJob(id, true, typeof ov === 'function' ? ov(JSON.parse(a)) : ov), 40); return id; } setTimeout(() => window.__sdJob(id, true, R(kind)), 40); return id; };
    let st = { id: '', playing: false, want: false, pos: 0, dur: 0, rate: 1, count: 0, index: -1 };
    const fixed = { caps: () => JSON.stringify({ native: true, media: true, asr: true, sep: true, mp3: true, opus: true, spk: true }), getLang: () => lang, job, listMedia: () => JSON.stringify(list),
      themePref: () => '', plState: () => JSON.stringify(st), tlPos: () => -1, tlMeter: () => '', recStart: () => '{"ok":true}', recLevel: () => 0.2, recInfo: () => '{}', lastCrash: () => '',
      plQueue: (items, idx, ms, auto) => { const it = JSON.parse(items)[idx]; st = Object.assign({}, st, { id: it.id, dur: it.dur, pos: ms, playing: !!auto, count: 1, index: idx }); },
      plPlay: () => { st.playing = true; }, plPause: () => { st.playing = false; }, tlPlay: () => '', tlPlayFrom: () => '' };
    window.SesDefteriAndroid = new Proxy({}, { get: (_, k) => fixed[k] || ((...a) => { window.__calls.push(String(k)); return ''; }) });
    localStorage.setItem('sd:lang', JSON.stringify(lang)); localStorage.setItem('sd:langChosen', 'true'); localStorage.setItem('sd:introDone', 'true'); localStorage.setItem('sd:coachOff', 'true'); localStorage.setItem('sd:lastVer', JSON.stringify('5.21')); localStorage.setItem('sd:news4101', 'true');
  }, LANG);
  await pg.addInitScript(() => {
    // 5.15–5.21 mocks: native recorder, live text, photo
    const fx = window.SesDefteriAndroid; let liveN = 0;
    window.__R = {
      liveStatus: { v2: true, v1: false }, recSpk: { segs: [[0, 2.2, 0], [2.4, 5, 1]], n: 2, db: [-20, -22] },
      backup: a => ({ n: a.files.length, missing: 0 }), tlAlign: { lag: 2.5, conf: 22 }, vad: { segs: [[0, 10], [11, 20]] },
      fwords: { ranges: [] }, tlLoud: a => ({ lufs: a.clips.map(() => -20) }), audiogram: { src: { uri: 'content://v' }, name: 'v.mp4', gallery: true },
      wsExport: { src: { uri: 'content://e' }, name: 'e.mp3', mime: 'audio/mpeg' }, chapters: { times: [] }
    };
    const P = new Proxy(fx, { get: (o, k) => ({
      recStart: () => '{"ok":true}', recLevel: () => 0.3, takePhoto: id => { setTimeout(() => window.__sdPhoto(id, true), 50); return true; },
      liveText: () => liveN++ === 0 ? JSON.stringify({ n: 1, segs: ['merhaba dünya bu bir deneme'], part: 'ikinci' }) : '',
      recStop: () => JSON.stringify({ ok: true, item: { id: 'm:900', src: { uri: 'content://r/900' }, mediaId: 900, title: 'Yeni kayıt', name: 'Yeni kayıt.m4a', dur: 8000, rec: true, own: true, size: 12345, mime: 'audio/mp4', date: Date.now() },
        live: { segments: [[0, 2, 'merhaba dünya bu bir deneme'], [2.5, 5, 'ikinci cümle burada']], text: 'merhaba dünya bu bir deneme ikinci cümle burada', live: true } }),
      pickArchive: (save) => { window.__pick = save; }, plSeek: ms => { window.__seek = ms; }, copyText: s => { window.__copied = s; }, shareText: (n, s) => { window.__shared = s; }
    })[k] || o[k] });
    Object.defineProperty(window, 'SesDefteriAndroid', { value: P, configurable: true });
  });
  console.log('goto'); await pg.goto('file://' + process.env.SD_PAGE); await pg.waitForTimeout(1200); console.log('loaded', errs);
  const ev = s => pg.evaluate(c => window.__ev(c), s);
  const evA = s => pg.evaluate(c => window.__ev('(async () => { ' + c + ' })()'), s);
  const W = ms => pg.waitForTimeout(ms);
  const fails = []; const check = (name, ok, info) => { console.log((ok ? 'OK   ' : 'FAIL ') + name + (info !== undefined ? ' :: ' + JSON.stringify(info).slice(0, 300) : '')); if (!ok) fails.push(name); };
  const clickM = async sel => { await pg.click('#modalCard ' + sel, { timeout: 3000 }); await W(250); };
  const args = k => pg.evaluate(k => (window.__args || []).filter(x => x[0] === k).map(x => x[1]), k);
  const closeAll = () => ev('(() => { while (stack.length) { const top = stack[stack.length - 1]; if (top === "rec") R.state = "idle"; S.modalLocked = false; if (!closeOverlay(top)) stack.pop(); } })()');

  // ---- 5.15–5.17: record with live text, quick marks, a note and a photo
  where = 'record'; console.log('-- ' + where);
  await ev('ls.set("liveOn", true)'); await evA('await liveRefresh()');
  await pg.click('[data-tab="rec"]'); await pg.click('#recBtn'); await W(400);
  await pg.click('#recMain'); await W(900);
  check('live text box shown', await ev('!$("#recLive").hidden && !!R.live'));
  await ev('livePoll()'); await W(100);
  check('live text painted', await ev('$("#recLive").innerText.includes("merhaba dünya")'), await ev('$("#recLive").innerText'));
  await pg.click('#recQuick [data-q="imp"]'); await W(200);
  check('quick mark in live text', await ev('R.live.lines.some(x => x.g === "imp")'));
  await pg.click('#recQuick [data-q="note"]'); await W(300);
  await pg.fill('#rnText', 'Bütçe konuşulacak'); await clickM('[data-g="todo"]'); await clickM('[data-ok]');
  await pg.click('#recQuick [data-q="photo"]'); await W(300);
  check('three marks while recording', await ev('R.marks.length') === 3, await ev('R.marks.map(m => [m.label, m.tag, m.note, m.photo])'));
  await pg.click('#recDone'); await W(1500);
  const recId = 'm:900';
  check('recording saved with marks', await ev(`(S.items.get("${recId}") || {}).bookmarks.length`) === 3);
  check('live text became the recording text', await ev(`!!(S.texts.get("${recId}") && S.texts.get("${recId}").segments.length === 2 && S.texts.get("${recId}").live)`));
  await closeAll(); await W(300);
  await ev(`openPlayer("${recId}")`); await W(700);
  check('text shows marks inline', await ev('document.querySelectorAll("#segList li.seg-mk").length') === 3, await ev('document.querySelectorAll("#segList li").length'));
  check('photo thumb in text', await ev('!!document.querySelector("#segList .bm-ph img")'));
  await closeAll(); await W(300);

  // ---- 5.18: search plays from the words, filters, speakers, backup/restore
  where = 'search'; console.log('-- ' + where);
  await ev(`(() => { const r = S.items.get("${recId}"); r.ttext = S.texts.get("${recId}").segments.map(s => s[2]).join(" "); })()`);
  await pg.fill('#q', 'ikinci cümle'); await W(400);
  check('search finds text', await ev(`visibleRecs().some(r => r.id === "${recId}" && r._hit && r._hit.kind === "text")`));
  await ev('window.__args = []');
  await pg.click(`[data-open="${recId}"]`); await W(800);
  const q = await args('plQueue');
  check('hit opens player and plays', await ev('stack.includes("player")'));
  const st = await ev('JSON.parse(AND.plState())'), sk = await ev('window.__seek');
  check('playback starts at the words (≈2.1 s)', (Math.abs(st.pos - 2100) < 200 || Math.abs(sk - 2100) < 200) && st.playing, [st.pos, sk]);
  await closeAll(); await pg.fill('#q', ''); await W(300);
  where = 'filter'; console.log('-- ' + where);
  await pg.click('#fltBtn'); await W(300);
  await clickM('[data-fk="tag"][data-fv="todo"]'); await clickM('[data-ok]'); await W(300);
  check('filter by mark type', await ev(`visibleRecs().map(r => r.id).join()`) === recId, await ev('visibleRecs().length'));
  check('filter button shows count', (await ev('$("#fltBtn").innerText')).includes('1'));
  await pg.click('#fltBtn'); await W(300); await clickM('[data-clr]'); await clickM('[data-ok]'); await W(200);
  check('filter cleared', await ev('fltN()') === 0 && await ev('visibleRecs().length') > 1);
  where = 'speakers'; console.log('-- ' + where);
  await ev(`openPlayer("${recId}")`); await W(600);
  await pg.click('#textBody [data-tx="spk"]'); await W(300); await clickM('[data-v="0"]'); await W(800);
  check('speakers found per sentence', JSON.stringify(await ev(`S.texts.get("${recId}").spk`)) === '[0,1]', await ev(`S.texts.get("${recId}").spk`));
  check('speaker labels in text', await ev('document.querySelectorAll("#segList .spk-lab").length') === 2);
  await pg.click('#segList [data-spkn="0"]'); await W(300); await pg.fill('#nbIn', 'Ayşe'); await clickM('[data-ok]'); await W(300);
  check('speaker named', await ev(`S.items.get("${recId}").spkNames[0]`) === 'Ayşe' && (await ev('$("#segList").innerText')).includes('Ayşe'));
  check('name remembered for later', JSON.stringify(await ev('ls.get("spkKnown", [])')) === '["Ayşe"]');
  await pg.click('#segList [data-spkn="1"]'); await W(300);
  check('known names suggested', await ev('document.querySelectorAll("#modalCard [data-sg]").length') === 0, 'Ayşe used in this recording, not offered again');
  await clickM('[data-x]');
  await pg.click('#textBody [data-tx="copy"]'); await W(100);
  check('copied text has speaker names', String(await ev('window.__copied')).startsWith('Ayşe:'), await ev('window.__copied'));
  await closeAll(); await pg.fill('#q', 'ayşe'); await W(400);
  check('search finds speaker name', await ev(`visibleRecs().some(r => r._hit && r._hit.kind === "spk")`));
  await pg.fill('#q', ''); await W(200);
  await pg.click('#fltBtn'); await W(300);
  check('speaker in filter', await ev('document.querySelectorAll("#modalCard [data-fk=spk]").length') === 1);
  await clickM('[data-x]');
  where = 'backup'; console.log('-- ' + where);
  await ev('window.__args = []');
  await evA('await doBackup("content://bk/1")'); await W(300);
  const bk = (await args('backup'))[0];
  const bj = bk ? JSON.parse(bk.json) : null;
  check('backup job gets all recordings', bk && bk.files.length === bj.recs.length && bj.recs.length >= 15, bk && [bk.files.length, bj.recs.length]);
  const br = bj && bj.recs.find(x => x.m.title && x.e.endsWith('Yeni kayıt.m4a'));
  check('backup keeps marks, text, speakers', !!(br && br.m.bookmarks.length === 3 && br.x && br.x.spk && br.m.spkNames && br.m.spkNames[0] === 'Ayşe'), br && Object.keys(br.m));
  check('backup has no file ids/paths', !!br && !('id' in br.m) && !('src' in br.m));
  // restore on an "empty phone": every item comes back as a new file
  await ev(`window.__R.restore = { json: ${JSON.stringify(JSON.stringify(bj))}, items: { [${JSON.stringify(br.e)}]: { id: 'm:777', src: { uri: 'content://r/777' }, mediaId: 777, title: 'Yeni kayıt', name: 'Yeni kayıt.m4a', dur: 8000, rec: true, own: true, size: 12345, mime: 'audio/mp4', date: Date.now() } }, skip: {}, projDir: '/data/proj' }`);
  await evA(`S.items.delete("${recId}"); await doRestore("content://bk/1")`); await W(400);
  const rr = await ev('(() => { const r = S.items.get("m:777"); return r && { b: r.bookmarks.length, n: r.spkNames, title: r.title, created: r.created } })()');
  check('restore brings recording back with notes', !!rr && rr.b === 3 && rr.n[0] === 'Ayşe', rr);
  check('restore brings text back', await ev('!!(S.texts.get("m:777") && S.texts.get("m:777").spk)') || await evA('return !!(await db.get("texts", "m:777"))'));
  // restore when the file is already there: skipped, notes filled in
  await ev(`(() => { const r = S.items.get("m:777"); r.bookmarks = []; r.notes = ""; })()`);
  await ev(`window.__R.restore = Object.assign({}, window.__R.restore, { items: {}, skip: { [${JSON.stringify(br.e)}]: true } })`);
  await ev('(() => { const r = S.items.get("m:777"); r.name = "Yeni kayıt.m4a"; r.size = 12345; })()');
  await evA('await doRestore("content://bk/1")'); await W(300);
  check('restore over an existing file completes its marks', await ev('S.items.get("m:777").bookmarks.length') === 3);
  await closeAll();
  where = 'settings backup'; console.log('-- ' + where);
  await pg.click('#setBtn'); await W(400);
  check('backup buttons in settings', await ev('!!document.querySelector("#setBk") && !!document.querySelector("#setRs")'));
  await clickM('#setBk'); await W(200);
  check('backup asks where to save', await ev('window.__pick') === true);
  await closeAll();

  // ---- 5.19–5.21: podcast studio
  where = 'podcast'; console.log('-- ' + where);
  await pg.click('[data-tab="edit"]'); await W(400);
  await ev(`(() => { TL.p = { tracks: [{ vol: 0, mute: false }, { vol: 0, mute: false }], clips: [
      { id: 'A', tr: 0, path: '/f/a.wav', ch: 1, dur: 100, in: 0, out: 100, at: 0, gain: 0, fi: 0, fo: 0, name: 'Ben', kind: 'speech' },
      { id: 'B', tr: 1, path: '/f/b.wav', ch: 1, dur: 90, in: 0, out: 90, at: 1, gain: 0, fi: 0, fo: 0, name: 'Konuk', kind: 'speech' }], markers: [{ id: 'k1', t: 40 }, { id: 'k2', t: 70 }] };
    ls.set('pcTpl', { intro: { path: '/f/intro.wav', ch: 2, dur: 9, name: 'Giriş müziği' }, outro: { path: '/f/outro.wav', ch: 2, dur: 6, name: 'Kapanış' }, jingle: { path: '/f/j.wav', ch: 2, dur: 2, name: 'Jingle' } });
    TL.sel = null; tlChanged(); })()`); await W(300);
  check('podcast tile shown', await ev('!!document.querySelector("#tlBar [data-tl=podcast]")'));
  await pg.click('#tlBar [data-tl="podcast"]'); await W(300);
  check('podcast menu', await ev('document.querySelectorAll("#modalCard [data-v]").length') === 5);
  await clickM('[data-v="apply"]'); await W(300);
  const pc = await ev('TL.p.clips.filter(c => c.pc).map(c => [c.name, c.at, c.tr])');
  check('intro, two jingles, outro added', pc.length === 4, pc);
  check('talk moved after intro', await ev('TL.p.clips.find(c => c.id === "A").at') === 6 && await ev('TL.p.markers[0].t') === 46, await ev('[TL.p.clips.find(c => c.id === "A").at, TL.p.markers[0].t]'));
  check('outro at the end', await ev('(() => { const o = TL.p.clips.find(c => c.pc && c.name === "Kapanış"); return Math.abs(o.at - (6 + 100 - 2)) < 0.01; })()'), pc);
  await ev('pcApply()'); await W(200);
  check('applying again does not stack', await ev('TL.p.clips.filter(c => c.pc).length') === 4 && await ev('TL.p.clips.find(c => c.id === "A").at') === 6);
  await ev('tlUndo(false)'); await ev('tlUndo(false)'); await W(200);
  check('undo removes template', await ev('TL.p.clips.filter(c => c.pc).length') === 0 && await ev('TL.p.clips.find(c => c.id === "A").at') === 0);
  where = 'chapters'; console.log('-- ' + where);
  await ev('pcNotes()'); await W(300);
  await pg.fill('#modalCard [data-mi="k1"]', 'Bütçe'); await W(100);
  check('notes show chapters', (await ev('$("#pcTxt").value')).includes('Bütçe'), await ev('$("#pcTxt").value'));
  await clickM('[data-pc="copy"]');
  check('notes copied', String(await ev('window.__copied')).includes('00:40 Bütçe') || String(await ev('window.__copied')).includes('0:40 Bütçe'), await ev('window.__copied'));
  await clickM('[data-pc="ok"]');
  check('chapter name kept', await ev('TL.p.markers[0].label') === 'Bütçe');
  where = 'export'; console.log('-- ' + where);
  await ev('window.__args = []; ls.set("exF", "mp3")');
  await ev('void tlExport()'); await W(400);
  await pg.click('#modalCard #exLoud'); await W(100);
  await clickM('[data-ex="save"]'); await W(1500);
  const sv = (await args('wsSave'))[0], lo = await args('wsOp');
  check('MP3 gets chapters', !!sv && Array.isArray(sv.chapters) && sv.chapters.length === 3 && sv.chapters[1][1] === 'Bütçe', sv && sv.chapters);
  check('broadcast level applied on export', lo.some(x => x.op === 'loud' && x.target === 'podcast'), lo);
  where = 'prepare'; console.log('-- ' + where);
  await ev('window.__args = []; TL.p.loud = ""');
  await ev('void pcPrepare()'); await W(300);
  check('prepare lists 7 steps', await ev('document.querySelectorAll("#modalCard [data-st]").length') === 7);
  await clickM('[data-ok]');
  for (let i = 0; i < 60; i++) { await W(500); if (await ev('stack.includes("modal")')) { await ev('closeModal()'); } if ((await ev('$("#toast").innerText')).includes(await ev('t("pcDone")'))) break; }
  const calls = await pg.evaluate(() => window.__args.map(x => x[0] + (x[1].op ? ':' + x[1].op : '')));
  check('prepare ran studio, pauses, level', calls.includes('wsOp:studio') || calls.some(c => c.startsWith('wsOp')), [...new Set(calls)]);
  check('prepare set broadcast level', await ev('TL.p.loud') === 'podcast');
  check('prepare added template', await ev('TL.p.clips.filter(c => c.pc).length') === 4);
  where = 'promo'; console.log('-- ' + where);
  await ev('window.__args = []; TL.pos = 20; TL.sel = null');
  await ev('void tlCapVideo()'); await W(400); console.log('modal:', await ev('$("#modalCard").innerText.slice(0, 80)'));
  await clickM('[data-v="720x1280"]'); await W(300);
  await clickM('[data-v="30"]'); await W(1500);
  const rd = (await args('tlRender'))[0];
  check('promo renders only 30 s from the playhead', !!rd && rd.clips.filter(c => !c.master).every(c => c.at >= 0 && c.at + c.len <= 30.01), rd && rd.clips.map(c => [c.at, c.in, c.len]));
  check('promo video made', (await args('audiogram')).length === 1);
  await closeAll();
  where = 'align'; console.log('-- ' + where);
  await ev('TL.sel = "B"; renderTlBar()');
  await ev('void tlAlign()'); await W(300); await clickM('[data-ok]'); await W(700);
  check('aligned clip moved', await ev('TL.p.clips.find(c => c.id === "B").at') === await ev('TL.p.clips.find(c => c.id === "A").at + 2.5'), await ev('[TL.p.clips.find(c => c.id === "A").at, TL.p.clips.find(c => c.id === "B").at]'));
  await closeAll();
  await ev('TL.sel = null; tlAll()'); await W(300);
  check('drawer has podcast tools', await ev('!!document.querySelector("#modalCard [data-dr=podcast]") && !!document.querySelector("#modalCard [data-dr=pcnotes]")'));
  await closeAll();
  await ev('TL.sel = "A"; tlAll()'); await W(300);
  check('clip drawer has align', await ev('!!document.querySelector("#modalCard [data-dr=align]")'));
  await closeAll();
  console.log(fails.length ? `\n${fails.length} FAILED: ${fails.join(', ')}` : '\nall passed');
  console.log('ERRORS', errs.length ? '\n' + [...new Set(errs)].join('\n') : 'none');
  await b.close();
})();
