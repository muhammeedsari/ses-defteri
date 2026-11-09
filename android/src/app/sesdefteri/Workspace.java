package app.sesdefteri;

import android.content.Context;

import app.sesdefteri.dsp.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The "Edit sound" workspace. Every step reads one 16-bit WAV from the cache and writes the next one,
 * so each stage can be played, compared and undone, and nothing is re-encoded until the user saves.
 */
final class Workspace {
    private Workspace() { }

    static File dir(Context c) {
        File d = new File(c.getCacheDir(), "ws");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File newFile(Context c) { return new File(dir(c), "s" + System.nanoTime() + ".wav"); }

    /** our own WAV files always have the canonical 44-byte header */
    static final class Info { int ch, rate; long frames; }

    static Info info(File f) throws Exception {
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            byte[] h = new byte[44];
            r.readFully(h);
            java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(h).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            Info i = new Info();
            i.ch = b.getShort(22);
            i.rate = b.getInt(24);
            i.frames = (r.length() - 44) / (2L * i.ch);
            return i;
        } finally {
            r.close();
        }
    }

    /** streams a workspace WAV as planar floats */
    static Pipeline.Source wav(final File f, final Info in, final AudioTools.Progress prog, final double[] slice, final AtomicBoolean cancel) {
        return new Pipeline.Source() {
            @Override public void run(Node sink) throws Exception {
                final int BL = 4096;
                float[][] x = new float[in.ch][BL];
                byte[] rb = new byte[BL * in.ch * 2];
                long done = 0;
                InputStream is = new BufferedInputStream(new FileInputStream(f), 1 << 16);
                try {
                    if (is.skip(44) != 44) throw new IllegalStateException("wav");
                    while (done < in.frames) {
                        if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                        int n = (int) Math.min(BL, in.frames - done), need = n * in.ch * 2, got = 0;
                        while (got < need) { int r = is.read(rb, got, need - got); if (r < 0) break; got += r; }
                        n = got / (in.ch * 2);
                        if (n <= 0) break;
                        int q = 0;
                        for (int i = 0; i < n; i++) for (int c = 0; c < in.ch; c++, q += 2) x[c][i] = (short) ((rb[q] & 0xff) | (rb[q + 1] << 8)) / 32768f;
                        sink.push(x, n);
                        done += n;
                        if (prog != null && slice != null && (done & 0x3ffff) < BL) prog.on(slice[0] + (slice[1] - slice[0]) * done / in.frames);
                    }
                } finally {
                    is.close();
                }
                sink.end();
            }
        };
    }

    /** pass-through meter: loudness, loudness range, sample peak */
    static final class Meter extends Node {
        final Loudness l;
        double peak;

        Meter(int ch, int rate) { l = new Loudness(ch, rate); }

        @Override public void push(float[][] b, int n) throws Exception {
            l.measure(b, n);
            for (float[] c : b) for (int i = 0; i < n; i++) { float a = Math.abs(c[i]); if (a > peak) peak = a; }
            next.push(b, n);
        }
    }

    static Analyzer.Analysis analyze(File f, Info in, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        Analyzer an = new Analyzer(in.ch, in.rate);
        wav(f, in, p, new double[] { 0, 0.3 }, cancel).run(an);
        return an.finish();
    }

    /** noise spectrum of a stretch the user marked as "only noise" (mean power over all its frames) */
    static double[] learnNoise(File in, Info inf, double t0, double t1, AtomicBoolean cancel) throws Exception {
        if (t1 - t0 < 0.2) return null;
        Analyzer an = new Analyzer(inf.ch, inf.rate);
        Keep k = new Keep(new JSONArray().put(new JSONArray().put(t0 * 1000).put(t1 * 1000)), inf.rate);
        k.to(an);
        wav(in, inf, null, new double[] { 0, 1 }, cancel).run(k);
        Analyzer.Analysis r = an.finish();
        double[] p = r.noisePower, l = r.ltasPower;
        if (p == null) return null;
        double[] o = new double[p.length];
        for (int i = 0; i < o.length; i++) o[i] = Math.max(p[i] * 1.4, l != null && l.length == o.length && l[i] > 0 ? l[i] : 0);
        return o;
    }

    static JSONObject done(File out, Meter m, JSONObject report) throws Exception {
        Info i = info(out);
        JSONObject o = new JSONObject();
        JSONObject src = new JSONObject();
        src.put("path", out.getAbsolutePath());
        o.put("src", src);
        o.put("path", out.getAbsolutePath());
        o.put("dur", Math.round(i.frames * 1000.0 / i.rate));
        o.put("ch", i.ch);
        o.put("rate", i.rate);
        double lufs = m.l.integrated();
        o.put("lufs", Double.isInfinite(lufs) ? -99 : Math.round(lufs * 10) / 10.0);
        o.put("lra", Math.round(m.l.range() * 10) / 10.0);
        o.put("peak", Math.round(20 * Math.log10(Math.max(1e-9, m.peak)) * 10) / 10.0);
        o.put("report", report == null ? new JSONObject() : report);
        return o;
    }

    /* ---------- start: any audio → workspace WAV ---------- */

    static JSONObject init(Context c, JSONObject src, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        clean(c, null);
        Studio.Fmt f = Studio.probe(c, src);
        File out = newFile(c);
        Meter m = new Meter(f.ch, f.rate);
        m.to(new Studio.WavNode(out, f.rate));
        Studio.source(c, src, f, p, new double[][] { { 0, 1 } }, cancel).run(m);
        return done(out, m, null);
    }

    /** deletes workspace files except the listed ones */
    static void clean(Context c, JSONArray keep) {
        java.util.HashSet<String> k = new java.util.HashSet<>();
        if (keep != null) for (int i = 0; i < keep.length(); i++) k.add(keep.optString(i));
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) if (!k.contains(f.getAbsolutePath())) f.delete();
    }

    /* ---------- one step ---------- */

    static JSONObject op(Context c, JSONObject a, final AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        File in = new File(a.getString("path"));
        Info inf = info(in);
        String op = a.getString("op");
        if ("chain".equals(op)) return chain(c, in, a, p, cancel);
        if ("aipauses".equals(op) && a.has("talk")) return aiPauses(in, inf, new File(in.getParentFile(), "s" + System.nanoTime() + ".wav"), a, p, cancel);
        final boolean hybridGate = "aigate".equals(op);
        if ("aipauses".equals(op)) op = "cut";   // 4.9: the AI versions reuse the plain machinery with AI-found stretches
        else if ("aigate".equals(op) || "ailevel".equals(op) || "snddip".equals(op)) op = "gainmap";
        File out = new File(in.getParentFile(), "s" + System.nanoTime() + ".wav");
        JSONObject rep = new JSONObject();
        int ch = inf.ch, fs = inf.rate;
        final double str = Math.max(0.3, Math.min(1.6, a.optDouble("strength", 1)));   // Light 0.6 / Balanced 1 / Strong 1.4
        try {
            if ("insert".equals(op)) return insert(c, in, inf, out, a, p, cancel);
            if ("aidenoise".equals(op)) return aiDenoise(c, in, inf, out, str, a.optDouble("amb", -1), p, cancel);
            if ("hd".equals(op)) return hd(c, in, inf, out, a, p, cancel);
            if ("reverse".equals(op)) return reverse(in, inf, out, p, cancel);
            if ("gate".equals(op)) return gate(in, inf, out, str, p, cancel);
            if ("sepdenoise".equals(op)) return sepDenoise(c, in, inf, p, cancel);
            if ("pauses".equals(op)) return pauses(in, inf, out, a, p, cancel);
            if ("breaths".equals(op)) return breaths(in, inf, out, str, p, cancel);
            if ("declip".equals(op)) return declip(in, inf, out, p, cancel);
            if ("spaint".equals(op)) return spaint(in, inf, out, a, p, cancel);
            if ("aibreaths".equals(op)) return aiBreathsW(c, in, inf, out, a, str, p, cancel);
            if ("gainmap".equals(op)) {   // 4.9: a smooth gain per stretch: [[start s, end s, dB]…], 0 dB elsewhere (AI gate, speaker levelling)
                Meter gm = new Meter(ch, fs);
                GainMap g = new GainMap(ch, fs, a.getJSONArray("map"), a.optDouble("rest", 0), a.optDouble("ramp", 0.02));
                if (hybridGate) {   // 4.11: speech heard by the loudness method stays too (the detector can miss very soft endings);
                    // and long stretches at the room's own hiss level are quiet even inside a detector stretch (its tails, merged sentences)
                    Speech.Scan sc = Speech.scan(in, inf, null, cancel);
                    double gap = sc.speech - sc.noise, thr = sc.noise + Math.max(4, Math.min(12, gap * 0.3));
                    boolean[] spk = new boolean[sc.n], deep = deepQuiet(sc);
                    for (int k = 0; k < sc.n; k++) if (sc.lvl[k] >= thr) for (int j = Math.max(0, k - 2); j < Math.min(sc.n, k + 7); j++) spk[j] = true;   // 40 ms before, 120 ms after
                    g.adjust(spk, deep, sc.hop, a.optDouble("rest", 0));
                }
                g.to(gm).to(new Studio.WavNode(out, fs));
                wav(in, inf, p, new double[] { 0, 1 }, cancel).run(g);
                return done(out, gm, new JSONObject().put("count", a.getJSONArray("map").length()));
            }
            Node head;
            Meter m;
            double[] slice = { 0.3, 1 };
            switch (op) {
                case "cut": {
                    m = new Meter(ch, fs);
                    head = new Keep(a.getJSONArray("keep"), fs);
                    head.to(m);
                    slice = new double[] { 0, 1 };
                    break;
                }
                case "denoise": {
                    JSONArray lr0 = a.optJSONArray("learn");
                    if (lr0 != null && lr0.length() == 2 && lr0.getDouble(1) - lr0.getDouble(0) >= 0.2) {
                        // 4.2: Audacity-style: profile of the marked stretch, then a gate per frequency
                        app.sesdefteri.dsp.NoiseGate.Learner ln = new app.sesdefteri.dsp.NoiseGate.Learner(fs);
                        Keep kk = new Keep(new JSONArray().put(new JSONArray().put(lr0.getDouble(0) * 1000).put(lr0.getDouble(1) * 1000)), inf.rate);
                        kk.to(ln);
                        wav(in, inf, null, new double[] { 0, 1 }, cancel).run(kk);
                        double[] pr = ln.profile();
                        if (pr == null) throw new IllegalArgumentException("short");
                        double red = Math.max(3, Math.min(40, a.optDouble("red", 12))), sens = Math.max(0, Math.min(24, a.optDouble("sens", 6)));
                        app.sesdefteri.dsp.NoiseGate ng = new app.sesdefteri.dsp.NoiseGate(ch, fs, pr, red, sens);
                        Analyzer after = new Analyzer(ch, fs);
                        m = new Meter(ch, fs);
                        ng.to(new Tee(after)).to(m);
                        head = ng;
                        rep.put("learned", true);
                        rep.put("reduction", Math.round(red));
                        rep.put("before", Math.round(ln.rmsDb()));
                        rep.put("_after", true);
                        tees.set(after);
                        break;
                    }
                    Analyzer.Analysis an = analyze(in, inf, p, cancel);
                    double nr = Math.max(4, Math.min(24, (Math.max(6, Math.min(18, (42 - an.snr) * 0.55 + 6))) * str));
                    double[] prof = a.has("profile") ? Studio.doubles(a.optJSONArray("profile")) : null;
                    if (prof != null && prof.length == an.noisePower.length) rep.put("room", true); else prof = an.noisePower;
                    JSONArray lr = a.optJSONArray("learn");   // [from, to] seconds of noise only, picked by the user
                    if (lr != null && lr.length() == 2) {
                        double[] lp = learnNoise(in, inf, lr.getDouble(0), lr.getDouble(1), cancel);
                        if (lp != null && lp.length == an.noisePower.length) { prof = lp; rep.put("learned", true); nr = Math.min(30, nr + 4); }
                    }
                    Spectral s = new Spectral(ch, fs).noise(prof, nr, 1.0);
                    if (a.optBoolean("track", true)) s.tracking(fs);
                    Analyzer after = new Analyzer(ch, fs);
                    m = new Meter(ch, fs);
                    s.to(new Tee(after)).to(m);
                    head = s;
                    rep.put("reduction", Math.round(nr));
                    rep.put("before", Math.round(an.noiseDb));
                    rep.put("_after", true);
                    tees.set(after);
                    break;
                }
                case "hum": {
                    Analyzer.Analysis an = analyze(in, inf, p, cancel);
                    double[] hr = an.humRatioDb;
                    double h50 = Math.max(hr[0], hr[1]), h60 = Math.max(hr[2], hr[3]);
                    double base = h60 > h50 ? 60 : 50;
                    if (Math.max(h50, h60) < 3) { rep.put("none", true); return same(a, rep); }
                    double[] notch = { base, base * 2, base * 3, base * 4, base * 5 };
                    rep.put("hz", (int) base);
                    rep.put("strength", Math.round(Math.max(h50, h60)));
                    m = new Meter(ch, fs);
                    head = new Stages.Filters(ch, fs, 0, notch);
                    head.to(m);
                    break;
                }
                case "trim": {
                    Analyzer.Analysis an = analyze(in, inf, p, cancel);
                    long s = Math.max(0, an.activeStart - Math.round(0.3 * fs)), e = Math.min(an.samples, an.activeEnd + Math.round(0.5 * fs));
                    if (e - s < fs) { s = 0; e = an.samples; }
                    rep.put("start", Math.round(s * 100.0 / fs) / 100.0);
                    rep.put("end", Math.round((an.samples - e) * 100.0 / fs) / 100.0);
                    rep.put("keep", new JSONArray().put(new JSONArray().put(s * 1000.0 / fs).put(e * 1000.0 / fs)));   // 5.2: the same cut for the clip's other versions
                    m = new Meter(ch, fs);
                    head = new Stages.Trim(s, e);
                    head.to(m);
                    break;
                }
                case "clicks": {
                    final Stages.ClickRepair cr = new Stages.ClickRepair(ch, fs, 10);
                    m = new Meter(ch, fs);
                    cr.to(m);
                    head = cr;
                    clicks.set(cr);
                    slice = new double[] { 0, 1 };
                    break;
                }
                case "plosive": {
                    Stages.Filters hp = new Stages.Filters(ch, fs, 75, null);
                    Stages.PlosiveTamer pt = new Stages.PlosiveTamer(ch, fs, 1 + 0.8 * str);
                    m = new Meter(ch, fs);
                    hp.to(pt).to(m);
                    head = hp;
                    plos.set(pt);
                    slice = new double[] { 0, 1 };
                    break;
                }
                case "deess": {
                    Analyzer.Analysis an = analyze(in, inf, p, cancel);
                    Stages.DeEsser d = new Stages.DeEsser(ch, fs, Math.pow(10, (an.speechDb - 12) / 20), 9 * str);
                    m = new Meter(ch, fs);
                    d.to(m);
                    head = d;
                    dees.set(d);
                    break;
                }
                case "channels": {
                    if (ch < 2) { rep.put("reason", "mono"); return same(a, rep); }
                    Analyzer.Analysis an = analyze(in, inf, p, cancel);
                    int mode = an.channelMode == Stages.ChannelMap.KEEP ? Stages.ChannelMap.MONO : an.channelMode;
                    rep.put("reason", an.channelMode == Stages.ChannelMap.KEEP ? "mono" : an.channelReason);
                    m = new Meter(1, fs);
                    head = new Stages.ChannelMap(ch, mode);
                    head.to(m);
                    break;
                }
                case "eq": {
                    Analyzer.Analysis an = analyze(in, inf, p, cancel);
                    double[] measured = Enhancer.bandsOf(an.ltasPower, fs, an.fftSize);
                    double[] target = a.has("learned") && !a.isNull("learned") ? Studio.doubles(a.optJSONArray("learned")) : Enhancer.referenceBands();
                    double[] g = eqGains(measured, target, str);
                    double mean = 0;
                    for (double x : g) mean += Math.abs(x) / g.length;
                    if (mean < 0.5) { rep.put("none", true); return same(a, rep); }
                    Spectral s = new Spectral(ch, fs).eq(Enhancer.eqCurve(g, fs, Fft.nextPow2((int) Math.round(fs * 0.021))));
                    m = new Meter(ch, fs);
                    s.to(m);
                    head = s;
                    rep.put("mean", Math.round(mean * 10) / 10.0);
                    double[] after = new double[g.length];
                    for (int i = 0; i < g.length; i++) after[i] = measured[i] + g[i];
                    rep.put("tone", Studio.json(after));
                    break;
                }
                case "level": {
                    Analyzer.Analysis an = analyze(in, inf, p, cancel);
                    rep.put("lraBefore", Math.round(an.lra * 10) / 10.0);
                    if (an.lra < 3) { rep.put("none", true); return same(a, rep); }
                    // 3.4.6: a slow leveller instead of a compressor (that only pulled loud words down, so quiet sentences
                    // hardly came up): every sentence is brought to the speech level of the whole file, pauses keep the
                    // gain of the speech around them, then a light compressor and the limiter
                    Leveler lv = new Leveler(ch, fs, an.noiseDb, an.speechMeanDb, Math.min(1, 0.55 + 0.35 * str));
                    wav(in, inf, p, new double[] { 0.3, 0.6 }, cancel).run(lv.measure());
                    lv.plan();
                    rep.put("range", Math.round(lv.range * 10) / 10.0);
                    m = new Meter(ch, fs);
                    head = lv;
                    head.to(new Stages.Compressor(ch, fs, an.speechMeanDb + 6, 1.5 + 0.5 * str)).to(new Limiter(ch, fs, -1.0, 0)).to(m);
                    slice = new double[] { 0.6, 1 };
                    break;
                }
                case "loud": {
                    Enhancer.Target t = Enhancer.Target.of(a.optString("target", "youtube"));
                    double gain = loudGain(in, inf, t, p, cancel);
                    rep.put("target", t.id);
                    rep.put("gain", Math.round(gain * 10) / 10.0);
                    m = new Meter(ch, fs);
                    head = new Limiter(ch, fs, t.ceilingDb, gain);
                    head.to(m);
                    slice = new double[] { 0.6, 1 };
                    break;
                }
                case "mouth": {
                    // lip and tongue clicks: smaller and a little longer than vinyl clicks, so a more sensitive detector
                    // 4.2: only the band above 2.5 kHz is judged and lowered (dsp/MouthClick); the voice itself stays as it was
                    final app.sesdefteri.dsp.MouthClick mc = new app.sesdefteri.dsp.MouthClick(ch, fs, str);
                    m = new Meter(ch, fs);
                    mc.to(m);
                    head = mc;
                    mouths.set(mc);
                    slice = new double[] { 0, 1 };
                    break;
                }
                case "stereo": {
                    if (ch >= 2) { rep.put("none", true); return same(a, rep); }
                    m = new Meter(2, fs);
                    head = new Node() {
                        float[][] o = new float[2][0];
                        @Override public void push(float[][] b, int n) throws Exception {
                            if (o[0].length < n) o = new float[2][n];
                            System.arraycopy(b[0], 0, o[0], 0, n); System.arraycopy(b[0], 0, o[1], 0, n);
                            next.push(o, n);
                        }
                    };
                    head.to(m);
                    slice = new double[] { 0, 1 };
                    break;
                }
                case "eightd": case "widen": case "double": case "lofi": {
                    // 4.13 creative stereo effects (dsp/Fx): always two channels out
                    m = new Meter(2, fs);
                    if ("eightd".equals(op)) head = new app.sesdefteri.dsp.Fx.EightD(ch, fs, Math.max(2, Math.min(20, a.optDouble("period", 8))));
                    else if ("widen".equals(op)) head = new app.sesdefteri.dsp.Fx.Widen(ch, fs, Math.max(0.2, Math.min(1, str * 0.6)));
                    else if ("double".equals(op)) head = new app.sesdefteri.dsp.Fx.Double(ch, fs);
                    else head = new app.sesdefteri.dsp.Fx.LoFi(ch, fs);
                    head.to(new Limiter(2, fs, -1.0, 0)).to(m);
                    slice = new double[] { 0, 1 };
                    break;
                }
                case "dereverb": {
                    Spectral sp = new Spectral(ch, fs).dereverb(fs, a.optDouble("t60", 0.6), Math.max(0.5, Math.min(1.5, str)));
                    m = new Meter(ch, fs);
                    sp.to(m);
                    head = sp;
                    slice = new double[] { 0, 1 };
                    break;
                }
                case "comp": {
                    double thr = Math.max(-50, Math.min(-6, a.optDouble("thr", -24))), ratio = Math.max(1.2, Math.min(10, a.optDouble("ratio", 3)));
                    double makeup = Math.min(12, -thr * (1 - 1 / ratio) * 0.5);
                    m = new Meter(ch, fs);
                    head = new Stages.Compressor(ch, fs, thr, ratio);
                    head.to(new Limiter(ch, fs, -1.0, makeup)).to(m);
                    rep.put("makeup", Math.round(makeup * 10) / 10.0);
                    slice = new double[] { 0, 1 };
                    break;
                }
                case "reverb": {
                    m = new Meter(ch, fs);
                    head = new Stages.Reverb(ch, fs, a.optDouble("room", 0.5), a.optDouble("mix", 0.3));
                    head.to(new Limiter(ch, fs, -1.0, 0)).to(m);
                    slice = new double[] { 0, 1 };
                    break;
                }
                case "delay": {
                    m = new Meter(ch, fs);
                    head = new Stages.Delay(ch, fs, a.optDouble("ms", 300), a.optDouble("fb", 0.35), a.optDouble("mix", 0.35));
                    head.to(new Limiter(ch, fs, -1.0, 0)).to(m);
                    slice = new double[] { 0, 1 };
                    break;
                }
                case "eqpreset": {
                    double[] g = Studio.doubles(a.getJSONArray("gains"));
                    m = new Meter(ch, fs);
                    head = Stages.Filters.eq5(ch, fs, g);
                    head.to(new Limiter(ch, fs, -1.0, 0)).to(m);   // boosts must not clip
                    slice = new double[] { 0, 1 };
                    break;
                }
                case "speed": {
                    double sp = Math.max(0.5, Math.min(2, a.optDouble("speed", 1))), st = Math.max(-12, Math.min(12, a.optDouble("semitones", 0)));
                    m = new Meter(ch, fs);
                    TimePitch tp = new TimePitch(ch, fs, sp, st, a.optBoolean("formant", true));
                    rep.put("speed", sp);
                    rep.put("semitones", st);
                    tp.to(m);
                    head = tp;
                    slice = new double[] { 0, 1 };
                    break;
                }
                case "clarify": {
                    // 2.7 "make the voice clearer": measured against an average voice, then mud / presence / air / exciter, compressor, level
                    Analyzer.Analysis an = analyze(in, inf, p, cancel);
                    double[] mb = Enhancer.bandsOf(an.ltasPower, fs, an.fftSize), ref = Enhancer.referenceBands();
                    double mud = bandDiff(mb, ref, 200, 400), pres = bandDiff(mb, ref, 2000, 4000), air = bandDiff(mb, ref, 6300, 10000);
                    double mudDb = -Math.min(8, (1.5 + Math.max(0, mud) * 0.6) * str);
                    double presDb = Math.min(7, (1.5 + Math.max(0, -pres) * 0.6) * str);
                    double airDb = Math.min(5, (1 + Math.max(0, -air) * 0.4) * str);
                    m = new Meter(ch, fs);
                    head = new app.sesdefteri.dsp.Clarity(ch, fs, mudDb, presDb, airDb, 0.25 * str);
                    // the compressor and the mud cut take about 3 dB: given back before the limiter so the voice is not quieter than before
                    head.to(new Stages.Compressor(ch, fs, an.speechMeanDb + 4, 1.6 + 0.6 * str)).to(new Limiter(ch, fs, -1.0, 2.5 + 1.2 * str)).to(m);
                    rep.put("mud", Math.round(-mudDb * 10) / 10.0);
                    rep.put("pres", Math.round(presDb * 10) / 10.0);
                    rep.put("air", Math.round(airDb * 10) / 10.0);
                    break;
                }
                case "vchange": {
                    // 3.8 change the voice: pitch (semitones, formants follow unless kept), tempo, a character, room, gain
                    double sp = Math.max(0.25, Math.min(4, a.optDouble("speed", 1))), st = Math.max(-24, Math.min(24, a.optDouble("semitones", 0)));
                    String pr = a.optString("preset", "");
                    m = new Meter(ch, fs);
                    Node tail = null;
                    head = null;
                    if (Math.abs(sp - 1) > 1e-3 || Math.abs(st) > 1e-3) { head = tail = new TimePitch(ch, fs, sp, st, a.optBoolean("formant", false)); }
                    if (!pr.isEmpty()) { Node v = new app.sesdefteri.dsp.VoiceFx(ch, fs, pr); if (head == null) head = tail = v; else tail = tail.to(v); }
                    JSONObject rv = a.optJSONObject("rev");
                    if (rv != null && rv.optDouble("mix", 0) > 0) {
                        Node v = new Stages.Reverb(ch, fs, rv.optDouble("room", 0.5), rv.optDouble("mix", 0.3), rv.optDouble("soft", 0.3));
                        if (head == null) head = tail = v; else tail = tail.to(v);
                    }
                    Node lim = new Limiter(ch, fs, -1.0, Math.max(-12, Math.min(12, a.optDouble("gain", 0))));
                    if (head == null) head = lim; else tail.to(lim);
                    lim.to(m);
                    rep.put("speed", sp); rep.put("semitones", st);
                    slice = new double[] { 0, 1 };
                    break;
                }
                case "voicefx": {
                    String pr = a.optString("preset", "radio");
                    rep.put("preset", pr);
                    m = new Meter(ch, fs);
                    if ("high".equals(pr) || "deep".equals(pr)) head = new TimePitch(ch, fs, 1, "high".equals(pr) ? 5 : -5, false);
                    else head = new app.sesdefteri.dsp.VoiceFx(ch, fs, pr);
                    head.to(new Limiter(ch, fs, -1.0, 0)).to(m);
                    slice = new double[] { 0, 1 };
                    break;
                }
                case "autotune": {
                    // 3.3 pitch correction: the key is found from the sung notes unless chosen
                    int key = a.optInt("key", -1);
                    String sc = a.optString("scale", "major");
                    if (key < 0) {
                        AutoTune.KeyFinder kf = new AutoTune.KeyFinder(ch, fs);
                        wav(in, inf, p, new double[] { 0, 0.3 }, cancel).run(kf);
                        int[] k = kf.key();
                        if (k == null) { key = 0; sc = "chromatic"; } else { key = k[0]; sc = k[1] == 1 ? "minor" : "major"; }
                    }
                    String st = a.optString("style", "natural");
                    final AutoTune at = new AutoTune(ch, fs, key, sc, "robot".equals(st) ? 0 : "strong".equals(st) ? 0.03 : 0.1, "robot".equals(st) ? 1 : "strong".equals(st) ? 1 : 0.85);
                    rep.put("key", key); rep.put("scale", sc); rep.put("style", st);
                    m = new Meter(ch, fs);
                    head = at;
                    at.to(new Limiter(ch, fs, -1.0, 0)).to(m);
                    slice = new double[] { a.optInt("key", -1) < 0 ? 0.3 : 0, 1 };
                    autoTunes.set(at);
                    break;
                }
                case "mix":
                    return mix(c, in, inf, out, a, p, cancel);
                default:
                    throw new IllegalArgumentException("op " + op);
            }
            m.to(new Studio.WavNode(out, fs));
            wav(in, inf, p, slice, cancel).run(head);
            Analyzer after = tees.get();
            if (after != null) { rep.put("after", Math.round(after.finish().noiseDb)); tees.remove(); }
            AutoTune atn = autoTunes.get();
            if (atn != null) { rep.put("seconds", Math.round(atn.corrected * 10) / 10.0); autoTunes.remove(); }
            Stages.ClickRepair cr = clicks.get();
            if (cr != null) { rep.put("count", cr.repaired); clicks.remove(); }
            app.sesdefteri.dsp.MouthClick mcl = mouths.get();
            if (mcl != null) { rep.put("count", mcl.repaired); mouths.remove(); }
            Stages.PlosiveTamer pt = plos.get();
            if (pt != null) { rep.put("seconds", Math.round(pt.active * 10.0 / fs) / 10.0); plos.remove(); }
            Stages.DeEsser d = dees.get();
            if (d != null) { rep.put("seconds", Math.round(d.active * 10.0 / fs) / 10.0); dees.remove(); }
            rep.remove("_after");
            // nothing to fix in this recording: say so instead of handing back an identical copy
            if ((cr != null && cr.repaired == 0) || (mcl != null && mcl.repaired == 0) || (pt != null && pt.active < fs / 20) || (d != null && d.active < fs / 5)) {
                out.delete();
                rep.put("none", true);
                return same(a, rep);
            }
            return done(out, m, rep);
        } catch (Exception e) {
            out.delete();
            throw e;
        }
    }

    /* ---------- 1.9: shorter pauses, quieter breaths, repaired clipping ---------- */

    /** pauses longer than max become `keep` long (the middle is cut, the edges stay so speech keeps its rhythm) */
    /** gain in dB per 10 ms from a list of stretches, glided with a short ramp; a limiter-free soft ceiling keeps boosts from clipping */
    static final class GainMap extends Node {
        final int ch, hop; final float[] tbl; final double k; double g = 1; long pos;
        GainMap(int ch, int fs, JSONArray map, double rest, double ramp) throws Exception {
            this.ch = ch; hop = Math.max(1, fs / 100);
            double end = 0; for (int i = 0; i < map.length(); i++) end = Math.max(end, map.getJSONArray(i).getDouble(1));
            tbl = new float[(int) (end * 100) + 2];
            java.util.Arrays.fill(tbl, (float) Math.pow(10, rest / 20));
            for (int i = 0; i < map.length(); i++) {
                JSONArray x = map.getJSONArray(i);
                float v = (float) Math.pow(10, Math.max(-60, Math.min(18, x.getDouble(2))) / 20);
                for (int f = Math.max(0, (int) (x.getDouble(0) * 100)); f < Math.min(tbl.length, (int) Math.ceil(x.getDouble(1) * 100)); f++) tbl[f] = v;
            }
            this.restLin = (float) Math.pow(10, rest / 20);
            k = 1 - Math.exp(-1 / (Math.max(0.002, ramp) * fs));
        }
        final float restLin;
        /** speech[] = keep at 0 dB, deep[] = turn down to the rest level; both per scan frame of `hop` samples */
        void adjust(boolean[] speech, boolean[] deep, int frameHop, double rest) {
            float r = (float) Math.pow(10, rest / 20);
            for (int f = 0; f < tbl.length; f++) {
                int k = (int) ((long) f * hop / frameHop);
                if (k >= speech.length) break;
                if (speech[k]) tbl[f] = 1; else if (deep[k]) tbl[f] = Math.min(tbl[f], r);
            }
        }
        @Override public void push(float[][] b, int n) throws Exception {
            for (int i = 0; i < n; i++) {
                int f = (int) ((pos + i) / hop);
                double tgt = f < tbl.length ? tbl[f] : restLin;
                g += (tgt - g) * k;
                for (int c = 0; c < ch; c++) { double v = b[c][i] * g; b[c][i] = (float) (v > 0.9 ? 0.9 + 0.1 * Math.tanh((v - 0.9) / 0.1) : v < -0.9 ? -0.9 - 0.1 * Math.tanh((-v - 0.9) / 0.1) : v); }
            }
            pos += n;
            if (next != null) next.push(b, n);
        }
    }

    /** frames that sit at the room's hiss level (≤ noise + 3.5 dB) for at least 0.4 s: nobody speaks there, whatever the detector says */
    static boolean[] deepQuiet(Speech.Scan sc) {
        boolean[] d = new boolean[sc.n];
        double q = sc.noise + 3.5; int min = (int) Math.round(0.4 / Speech.FR);
        for (int k = 0; k < sc.n; ) {
            if (sc.lvl[k] > q) { k++; continue; }
            int s0 = k, miss = 0, e = k;
            while (k < sc.n && (sc.lvl[k] <= q || ++miss <= 1)) { if (sc.lvl[k] <= q) { e = k; miss = 0; } k++; }
            if (e - s0 + 1 >= min) for (int j = s0; j <= e; j++) d[j] = true;
            k = e + 1;
        }
        return d;
    }

    /** 4.11: pauses both methods agree on: below the strict hiss line, and outside detected speech (or a long hiss-level stretch).
       Longer than `max` → shortened to `keep`. a.talk = detector stretches [[s, e]…] seconds */
    static JSONObject aiPauses(File in, Info inf, File out, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        double max = Math.max(0.3, a.optDouble("max", 0.8)), keep = Math.min(max, Math.max(0.15, a.optDouble("keep", 0.45)));
        Speech.Scan sc = Speech.scan(in, inf, part(p, 0, 0.4), cancel);
        boolean[] vad = new boolean[sc.n], deep = deepQuiet(sc);
        JSONArray tk = a.getJSONArray("talk");
        for (int i = 0; i < tk.length(); i++) { JSONArray x = tk.getJSONArray(i); for (int k = Math.max(0, (int) (x.getDouble(0) / Speech.FR)); k < Math.min(sc.n, (int) Math.ceil(x.getDouble(1) / Speech.FR)); k++) vad[k] = true; }
        double q = sc.noise + 3.5;
        JSONArray ranges = new JSONArray(), cuts = new JSONArray();
        double pos = 0, outT = 0, saved = 0, dur = inf.frames / (double) inf.rate;
        int min = (int) Math.round(max / Speech.FR);
        for (int k = 0; k < sc.n; ) {
            boolean quiet = sc.lvl[k] <= q && (!vad[k] || deep[k]);
            if (!quiet) { k++; continue; }
            int s0 = k; while (k < sc.n && sc.lvl[k] <= q && (!vad[k] || deep[k])) k++;
            if (s0 == 0 || k >= sc.n || k - s0 < min) continue;   // the very start and end are left alone, like the plain tool
            double t0 = s0 * Speech.FR + keep / 2, t1 = k * Speech.FR - keep / 2;
            if (t1 - t0 < 0.05) continue;
            ranges.put(new JSONArray().put(pos * 1000).put(t0 * 1000));
            outT += t0 - pos;
            cuts.put(new JSONArray().put(Math.round(outT * 1000) / 1000.0).put(Math.round((t1 - t0) * 1000) / 1000.0));
            saved += t1 - t0; pos = t1;
        }
        JSONObject rep = new JSONObject();
        if (cuts.length() == 0) { rep.put("none", true); return same(a, rep); }
        ranges.put(new JSONArray().put(pos * 1000).put(dur * 1000 + 1000));
        Meter m = new Meter(inf.ch, inf.rate);
        Keep kk = new Keep(ranges, inf.rate);
        kk.to(m).to(new Studio.WavNode(out, inf.rate));
        wav(in, inf, part(p, 0.4, 1), new double[] { 0, 1 }, cancel).run(kk);
        rep.put("count", cuts.length()); rep.put("saved", Math.round(saved * 10) / 10.0); rep.put("cuts", cuts); rep.put("keep", ranges);
        return done(out, m, rep);
    }

    /** 4.11: breaths = the plain detector's candidates that Whisper hears no word in (the speech detector itself hears breaths
       as speech, so it cannot decide). Without the speech-to-text model this is the plain tool. */
    static JSONObject aiBreathsW(Context c, File in, Info inf, File out, JSONObject a, double str, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        Speech.Scan sc = Speech.scan(in, inf, part(p, 0, 0.3), cancel);
        java.util.ArrayList<int[]> l = Speech.breaths(sc), ok = new java.util.ArrayList<>();
        String model = c != null ? AsrModels.pick(c, a.optString("model", "")) : null;
        if (model != null && !l.isEmpty()) {
            int threads = Device.threads(4);
            app.sesdefteri.asr.Transcriber tr = new app.sesdefteri.asr.Transcriber(null, AsrModels.dir(c, model).getAbsolutePath(), model, a.optString("lang", ""), threads);
            RandomAccessFile r = new RandomAccessFile(in, "r");
            try {
                for (int i = 0; i < l.size(); i++) {
                    if (cancel.get()) throw new InterruptedException("cancel");
                    int[] b = l.get(i);
                    long f0 = Math.max(0, (long) ((b[0] * Speech.FR - 0.1) * inf.rate)), f1 = Math.min(inf.frames, (long) ((b[1] * Speech.FR + 0.1) * inf.rate));
                    float[][] x = readRegion(r, inf, f0, (int) (f1 - f0));
                    float[][] mono = new float[1][x[0].length];
                    for (int j = 0; j < mono[0].length; j++) { float v = 0; for (int ch = 0; ch < inf.ch; ch++) v += x[ch][j]; mono[0][j] = v / inf.ch; }
                    app.sesdefteri.dsp.Resampler rs = new app.sesdefteri.dsp.Resampler(1, (double) inf.rate, 16000.0);
                    Node.Collect col = new Node.Collect(1); rs.to(col); rs.push(mono, mono[0].length); rs.end();
                    if (hesitation(tr.recognize(col.result()[0]))) ok.add(b);
                    p.on(0.3 + 0.4 * (i + 1) / l.size());
                }
            } finally { r.close(); tr.release(); }
        } else ok = l;
        JSONObject rep = new JSONObject();
        if (ok.isEmpty()) { rep.put("none", true); return same(a, rep); }
        Meter m = new Meter(inf.ch, inf.rate);
        GainRegions gr = new GainRegions(ok, sc.hop, -(8 + 10 * str), (int) (inf.rate * 0.015));
        gr.to(m).to(new Studio.WavNode(out, inf.rate));
        wav(in, inf, part(p, 0.7, 1), new double[] { 0, 1 }, cancel).run(gr);
        rep.put("count", ok.size()); rep.put("checked", l.size());
        return done(out, m, rep);
    }

    static JSONObject pauses(File in, Info inf, File out, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        double max = Math.max(0.3, a.optDouble("max", 0.8)), keep = Math.min(max, Math.max(0.15, a.optDouble("keep", 0.45)));
        Speech.Scan sc = Speech.scan(in, inf, part(p, 0, 0.4), cancel);
        java.util.ArrayList<int[]> l = Speech.pauses(sc, max);
        JSONObject rep = new JSONObject();
        if (l.isEmpty()) { rep.put("none", true); return same(a, rep); }
        JSONArray ranges = new JSONArray(), cuts = new JSONArray();
        double pos = 0, outT = 0, saved = 0, dur = inf.frames / (double) inf.rate;
        for (int[] r : l) {
            double t0 = r[0] * Speech.FR + keep / 2, t1 = r[1] * Speech.FR - keep / 2;
            if (t1 - t0 < 0.05) continue;
            ranges.put(new JSONArray().put(pos * 1000).put(t0 * 1000));
            outT += t0 - pos;
            cuts.put(new JSONArray().put(Math.round(outT * 1000) / 1000.0).put(Math.round((t1 - t0) * 1000) / 1000.0));
            saved += t1 - t0;
            pos = t1;
        }
        ranges.put(new JSONArray().put(pos * 1000).put(dur * 1000 + 1000));
        Meter m = new Meter(inf.ch, inf.rate);
        Keep k = new Keep(ranges, inf.rate);
        k.to(m).to(new Studio.WavNode(out, inf.rate));
        wav(in, inf, part(p, 0.4, 1), new double[] { 0, 1 }, cancel).run(k);
        rep.put("count", cuts.length());
        rep.put("saved", Math.round(saved * 10) / 10.0);
        rep.put("cuts", cuts);
        rep.put("keep", ranges);
        return done(out, m, rep);
    }

    /** turns the found regions down by `db`, with short ramps */
    /** two passes: measure 100 ms levels, then apply a smooth gain curve that evens out sentences (see case "level") */
    static final class Leveler extends Node {
        final int ch, blk;
        final double floorDb, amount;
        double targetDb;
        final java.util.ArrayList<Float> db = new java.util.ArrayList<>();
        float[] gain;   // dB per block
        double range;   // largest boost − largest cut
        long pos;
        Leveler(int ch, int fs, double floorDb, double targetDb, double amount) {
            this.ch = ch; this.blk = Math.max(1, fs / 10); this.floorDb = floorDb; this.targetDb = targetDb; this.amount = amount;
        }
        Node measure() {
            return new Node() {
                double acc; int k;
                @Override public void push(float[][] x, int n) {
                    for (int i = 0; i < n; i++) {
                        double e = 0; for (int c = 0; c < ch; c++) e += x[c][i] * x[c][i];
                        acc += e / ch;
                        if (++k == blk) { db.add((float) (10 * Math.log10(acc / blk + 1e-12))); acc = 0; k = 0; }
                    }
                }
                @Override public void end() { if (k > 0) db.add((float) (10 * Math.log10(acc / k + 1e-12))); }
            };
        }
        void plan() {
            int n = db.size();
            gain = new float[n + 1];
            double speech = Math.max(floorDb + 10, targetDb - 22);
            // the goal is the file's own average speech level (in the same 100 ms measure), so the overall loudness stays
            { double sum = 0; int cnt = 0; for (float d : db) if (d > speech) { sum += Math.pow(10, d / 10); cnt++; } if (cnt > 0) targetDb = 10 * Math.log10(sum / cnt) + 1; }
            float[] raw = new float[n];
            boolean[] has = new boolean[n];
            final int W = 8;                                   // ±0.8 s
            for (int i = 0; i < n; i++) {
                double sum = 0; int cnt = 0;
                for (int j = Math.max(0, i - W); j <= Math.min(n - 1, i + W); j++) if (db.get(j) > speech) { sum += Math.pow(10, db.get(j) / 10); cnt++; }
                if (cnt >= 3) { raw[i] = (float) Math.max(-10, Math.min(12, (targetDb - 10 * Math.log10(sum / cnt)) * amount)); has[i] = true; }
            }
            // pauses take the gain of the nearest speech, never more than +6 dB so room noise stays down
            int first = -1;
            for (int i = 0; i < n && first < 0; i++) if (has[i]) first = i;
            if (first >= 0) {
                for (int i = 0; i < first; i++) raw[i] = Math.min(6, raw[first]);
                float last = raw[first];
                for (int i = first; i < n; i++) { if (has[i]) last = raw[i]; else raw[i] = Math.min(6, last); }
            }
            // smooth: 300 ms either way
            float mx = -99, mn = 99;
            for (int i = 0; i < n; i++) {
                double s = 0; int c = 0;
                for (int j = Math.max(0, i - 3); j <= Math.min(n - 1, i + 3); j++) { s += raw[j]; c++; }
                gain[i] = (float) (s / c);
                mx = Math.max(mx, gain[i]); mn = Math.min(mn, gain[i]);
            }
            gain[n] = n > 0 ? gain[n - 1] : 0;
            range = n > 0 ? mx - mn : 0;
        }
        @Override public void push(float[][] x, int n) throws Exception {
            for (int i = 0; i < n; i++, pos++) {
                int b = (int) (pos / blk);
                double f = (pos % blk) / (double) blk, g;
                if (b >= gain.length - 1) g = gain[gain.length - 1];
                else g = gain[b] + (gain[b + 1] - gain[b]) * f;
                float k = (float) Math.pow(10, g / 20);
                for (int c = 0; c < ch; c++) x[c][i] *= k;
            }
            next.push(x, n);
        }
    }

    static final class GainRegions extends Node {
        final long[] a, b;
        final double g;
        final int ramp;
        long pos;
        int i;
        GainRegions(java.util.ArrayList<int[]> l, int hop, double db, int ramp) {
            a = new long[l.size()]; b = new long[l.size()];
            for (int k = 0; k < a.length; k++) { a[k] = (long) l.get(k)[0] * hop; b[k] = (long) l.get(k)[1] * hop; }
            g = Math.pow(10, db / 20); this.ramp = ramp;
        }
        @Override public void push(float[][] x, int n) throws Exception {
            for (int k = 0; k < n; k++, pos++) {
                while (i < a.length && pos >= b[i] + ramp) i++;
                if (i >= a.length || pos < a[i] - ramp) continue;
                double d = Math.min(Math.min(pos - (a[i] - ramp), (b[i] + ramp) - pos), ramp) / (double) ramp;   // 0 at the ramp ends, 1 inside
                float gg = (float) (1 - (1 - g) * Math.max(0, Math.min(1, d)));
                for (float[] c : x) c[k] *= gg;
            }
            next.push(x, n);
        }
    }

    static JSONObject breaths(File in, Info inf, File out, double str, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        Speech.Scan sc = Speech.scan(in, inf, part(p, 0, 0.4), cancel);
        java.util.ArrayList<int[]> l = Speech.breaths(sc);
        JSONObject rep = new JSONObject();
        if (l.isEmpty()) { rep.put("none", true); return same(null, rep); }
        Meter m = new Meter(inf.ch, inf.rate);
        GainRegions gr = new GainRegions(l, sc.hop, -(8 + 10 * str), (int) (inf.rate * 0.015));   // 3.4.6: −18 dB (was −10, hard to hear)
        gr.to(m).to(new Studio.WavNode(out, inf.rate));
        wav(in, inf, part(p, 0.4, 1), new double[] { 0, 1 }, cancel).run(gr);
        rep.put("count", l.size());
        return done(out, m, rep);
    }

    /**
     * Clipped runs (|x| ≥ 0.985, up to 3 ms) are redrawn with a cubic through two good samples on each side,
     * then the whole file is scaled so the rebuilt peaks fit.
     */
    static JSONObject declip(File in, Info inf, File out, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        final int ch = inf.ch, B = 1 << 16, M = 256;
        final int maxRun = Math.max(8, inf.rate * 3 / 1000);
        RandomAccessFile r = new RandomAccessFile(in, "r");
        // 3.4.6: the clip level is the file's own flat top (a clipped file turned down to 0.95 was "not clipped" at 0.985)
        float CL = 0.985f;
        {
            byte[] bb = new byte[1 << 16];
            int top = 0; long topN = 0, left = inf.frames * ch * 2;
            r.seek(44);
            while (left > 0) {
                int n = r.read(bb, 0, (int) Math.min(bb.length, left));
                if (n <= 0) break;
                left -= n;
                for (int q = 0; q + 1 < n; q += 2) {
                    int v = Math.abs((short) ((bb[q] & 0xff) | (bb[q + 1] << 8)));
                    if (v > top + 3) { top = v; topN = 1; } else if (v >= top - 3) topN++;
                }
            }
            if (top > 8000 && topN >= 40) CL = Math.min(CL, (top - 40) / 32768f);
        }
        int[] runs = new int[1];
        double peak = 0;
        Meter m = null;
        try {
            for (int pass = 0; pass < 2; pass++) {
                // 3.4.6: turn down at most 4 dB and let a limiter hold the rebuilt peaks (a long clipped stretch made the
                // whole file 12 dB quieter)
                double gain = pass == 0 ? 1 : Math.max(Math.pow(10, -4 / 20.0), Math.min(1, 0.97 / Math.max(1e-6, peak)));
                Node sink = null;
                if (pass == 1) { m = new Meter(ch, inf.rate); Limiter lim = new Limiter(ch, inf.rate, -1.0, 0); lim.to(m).to(new Studio.WavNode(out, inf.rate)); sink = lim; }
                float[][] x = new float[ch][B + 2 * M], o = new float[ch][B];
                byte[] buf = new byte[(B + 2 * M) * ch * 2];
                for (long s = 0; s < inf.frames; s += B) {
                    if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                    long A = Math.max(0, s - M), E = Math.min(inf.frames, s + B + M);
                    int n = (int) (E - A), off = (int) (s - A), keep = (int) Math.min(B, inf.frames - s);
                    r.seek(44 + A * ch * 2);
                    r.readFully(buf, 0, n * ch * 2);
                    for (int i = 0, q = 0; i < n; i++) for (int c = 0; c < ch; c++, q += 2) x[c][i] = (short) ((buf[q] & 0xff) | (buf[q + 1] << 8)) / 32768f;
                    for (int c = 0; c < ch; c++) {
                        float[] v = x[c];
                        for (int i = 2; i < n - 2; i++) {
                            if (Math.abs(v[i]) < CL) continue;
                            int e = i;
                            while (e + 1 < n - 2 && Math.abs(v[e + 1]) >= CL && Math.signum(v[e + 1]) == Math.signum(v[i])) e++;
                            int len = e - i + 1;
                            if (len >= 2 && len <= maxRun && i - 2 >= 0 && e + 2 < n) {
                                // cubic through (−2, −1) on the left and (len, len+1) on the right, in local coordinates
                                double[] tx = { -2, -1, len, len + 1 }, ty = { v[i - 2], v[i - 1], v[e + 1], v[e + 2] };
                                for (int k = 0; k < len; k++) {
                                    double t = k, y = 0;
                                    for (int a2 = 0; a2 < 4; a2++) {
                                        double w = 1;
                                        for (int b2 = 0; b2 < 4; b2++) if (b2 != a2) w *= (t - tx[b2]) / (tx[a2] - tx[b2]);
                                        y += w * ty[a2];
                                    }
                                    double cap = CL * 2;   // a peak rebuilt at most 6 dB above the clip level
                                    if (Math.abs(y) > Math.abs(v[i + k]) && Math.signum(y) == Math.signum(v[i + k])) v[i + k] = (float) Math.max(-cap, Math.min(cap, y));
                                }
                                if (pass == 0 && i >= off && i < off + keep) runs[0]++;
                            }
                            i = e;
                        }
                        for (int i = off; i < off + keep; i++) { double a3 = Math.abs(v[i]); if (a3 > peak && pass == 0) peak = a3; }
                    }
                    if (pass == 1) {
                        for (int c = 0; c < ch; c++) for (int i = 0; i < keep; i++) o[c][i] = (float) (x[c][off + i] * gain);
                        sink.push(o, keep);
                    }
                    p.on(0.5 * pass + 0.5 * (s + keep) / inf.frames);
                }
                if (pass == 0 && runs[0] == 0) { JSONObject rep = new JSONObject(); rep.put("none", true); return same(null, rep); }
                if (pass == 1) sink.end();
            }
        } finally {
            r.close();
        }
        JSONObject rep = new JSONObject();
        rep.put("count", runs[0]);
        rep.put("gain", Math.round(20 * Math.log10(Math.max(Math.pow(10, -4 / 20.0), Math.min(1, 0.97 / Math.max(1e-6, peak)))) * 10) / 10.0);
        return done(out, m, rep);
    }

    /** hesitation sounds in any audio: [[start, end], …] in seconds */
    private static final java.util.regex.Pattern HES = java.util.regex.Pattern.compile(
        "(ı+|i+|e+h*|a+h*|ö+|ü+|o+h*|u+h*m*|h*m+|hı+|ha+|he+|er+m*|ehm|äh+m*|öh+m*|euh+|eh+|em+|ум+|эм+|э+|а+|м+|嗯+|呃+|啊+|额+|ах+|يعني|اه+|ام+)",
        java.util.regex.Pattern.UNICODE_CASE | java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Whisper's text for a snippet: nothing, or only hesitation sounds */
    static boolean hesitation(String t) {
        // 4.11: Whisper labels non-speech sounds instead of words: "[Müzik]", "(nefes)", "*öksürük*", "♪" are not speech
        t = t.replaceAll("\\[[^\\]]*\\]?|\\([^)]*\\)?|\\*[^*]*\\*?|♪", " ");
        String[] w = t.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\s]", " ").trim().split("\\s+");
        for (String x : w) if (!x.isEmpty() && !HES.matcher(x).matches()) return false;
        return true;
    }

    static JSONObject fillers(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        File w = new File(c.getCacheDir(), "fl" + System.nanoTime() + ".wav");
        try {
            decodeTo(c, a.getJSONObject("src"), 1, 16000, p, new double[] { 0, 0.7 }, cancel, w);
            Info inf = info(w);
            float[] x = new float[(int) inf.frames];
            byte[] b = new byte[x.length * 2];
            RandomAccessFile r = new RandomAccessFile(w, "r");
            try { r.seek(44); r.readFully(b); } finally { r.close(); }
            for (int i = 0; i < x.length; i++) x[i] = (short) ((b[2 * i] & 0xff) | (b[2 * i + 1] << 8)) / 32768f;
            JSONArray out = new JSONArray();
            String model = a.optBoolean("ai") ? AsrModels.pick(c, a.optString("model", "")) : null;
            if (model == null) {
                for (double[] q : Speech.fillers(x, 16000)) out.put(new JSONArray().put(Math.round(q[0] * 100) / 100.0).put(Math.round(q[1] * 100) / 100.0));
                p.on(1);
                return new JSONObject().put("ranges", out);
            }
            // 4.7: smart: a looser search finds more candidates, then Whisper listens to each one: no words (or only
            // "ıı / eee / hmm / um…") = a hesitation; real words = speech, kept
            java.util.ArrayList<double[]> cand = Speech.fillers(x, 16000, 24, 0.06);
            int threads = Device.threads(4);
            app.sesdefteri.asr.Transcriber tr = new app.sesdefteri.asr.Transcriber(null, AsrModels.dir(c, model).getAbsolutePath(), model, a.optString("lang", ""), threads);
            int kept = 0;
            try {
                for (int i = 0; i < cand.size(); i++) {
                    if (cancel.get()) throw new InterruptedException("cancel");
                    double[] q = cand.get(i);
                    int s0 = Math.max(0, (int) ((q[0] - 0.12) * 16000)), s1 = Math.min(x.length, (int) ((q[1] + 0.12) * 16000));
                    if (s1 - s0 < 1600) continue;
                    String t = tr.recognize(java.util.Arrays.copyOfRange(x, s0, s1));
                    if (!hesitation(t)) continue;
                    out.put(new JSONArray().put(Math.round(q[0] * 100) / 100.0).put(Math.round(q[1] * 100) / 100.0));
                    kept++;
                    p.on(0.7 + 0.3 * (i + 1) / cand.size());
                }
            } finally {
                tr.release();
            }
            p.on(1);
            return new JSONObject().put("ranges", out).put("ai", true).put("checked", cand.size());
        } finally {
            w.delete();
        }
    }

    /* 4.16: filler words ("şey, yani, hani…", "um, like, you know…"): Whisper gives no word times, so every sentence that has one
       is split at its small pauses and each piece is heard again on its own; a piece that is only filler words is listed */
    static final java.util.Map<String, String[]> FW = new java.util.HashMap<>();
    static {
        FW.put("tr", new String[] { "şey", "yani", "hani", "işte", "falan", "filan", "eee", "ııı" });
        FW.put("en", new String[] { "um", "uh", "umm", "uhm", "like", "you know", "i mean", "basically", "actually", "literally", "so" });
        FW.put("de", new String[] { "äh", "ähm", "also", "halt", "quasi", "sozusagen", "ne", "naja" });
        FW.put("fr", new String[] { "euh", "ben", "bah", "genre", "en fait", "du coup", "quoi", "bon" });
        FW.put("es", new String[] { "eh", "este", "o sea", "pues", "bueno", "como", "vale" });
        FW.put("pt", new String[] { "é", "tipo", "né", "então", "assim", "ahn" });
        FW.put("ru", new String[] { "ну", "вот", "типа", "как бы", "это", "короче", "значит", "э" });
        FW.put("ar", new String[] { "يعني", "طيب", "اه", "امم" });
        FW.put("zh", new String[] { "那个", "就是", "然后", "嗯", "呃" });
    }

    static java.util.List<String> fwList(String lang) {
        java.util.ArrayList<String> l = new java.util.ArrayList<>();
        String k = lang == null ? "" : lang.toLowerCase(java.util.Locale.ROOT);
        if (k.length() > 2) k = k.substring(0, 2);
        if (FW.containsKey(k)) l.addAll(java.util.Arrays.asList(FW.get(k)));
        else for (String[] v : FW.values()) l.addAll(java.util.Arrays.asList(v));
        return l;
    }

    static String norm(String t) { return (" " + t.replace('İ', 'i').toLowerCase(java.util.Locale.ROOT).replaceAll("\\p{M}", "").replaceAll("[^\\p{L}\\s]", " ").replaceAll("\\s+", " ").trim() + " "); }

    /** the text holds a filler word / the text is only filler words */
    static boolean hasFw(String t, java.util.List<String> l) { String n = norm(t); for (String w : l) if (n.contains(" " + w + " ")) return true; return false; }
    static boolean onlyFw(String t, java.util.List<String> l) {
        String n = norm(t);
        if (n.trim().isEmpty()) return false;
        for (String w : l) n = n.replace(" " + w + " ", " ").replace(" " + w + " ", " ");
        return n.trim().isEmpty();
    }

    /** {src:{path}, segs:[[s,e,text]…] (from the transcript), lang, model} → ranges [[s, e, text]] in file seconds */
    static JSONObject fillerWords(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        String model = AsrModels.pick(c, a.optString("model", ""));
        if (model == null) throw new IllegalStateException("asr-missing");
        java.util.List<String> fl = fwList(a.optString("lang", ""));
        JSONArray segs = a.getJSONArray("segs"), out = new JSONArray();
        java.util.ArrayList<double[]> todo = new java.util.ArrayList<>();
        for (int i = 0; i < segs.length(); i++) { JSONArray q = segs.getJSONArray(i); if (hasFw(q.getString(2), fl)) todo.add(new double[] { q.getDouble(0), q.getDouble(1) }); }
        if (todo.isEmpty()) return new JSONObject().put("ranges", out).put("checked", 0);
        File w = new File(c.getCacheDir(), "fw" + System.nanoTime() + ".wav");
        app.sesdefteri.asr.Transcriber tr = null;
        try {
            decodeTo(c, a.getJSONObject("src"), 1, 16000, p, new double[] { 0, 0.2 }, cancel, w);
            Info inf = info(w);
            RandomAccessFile r = new RandomAccessFile(w, "r");
            tr = new app.sesdefteri.asr.Transcriber(null, AsrModels.dir(c, model).getAbsolutePath(), model, a.optString("lang", ""), Device.threads(4));
            try {
                for (int t = 0; t < todo.size(); t++) {
                    double[] q = todo.get(t);
                    long f0 = Math.max(0, (long) (q[0] * 16000)), f1 = Math.min(inf.frames, (long) (q[1] * 16000));
                    if (f1 - f0 < 1600) continue;
                    float[] x = readRegion(r, inf, f0, (int) (f1 - f0))[0];
                    // 10 ms levels; pauses = 50 ms or more at least 20 dB under the sentence's loud part
                    int nf = x.length / 160;
                    float[] db = new float[nf];
                    float[] srt = new float[nf];
                    for (int i = 0; i < nf; i++) { double e = 0; for (int j = 0; j < 160; j++) { float v = x[i * 160 + j]; e += v * v; } srt[i] = db[i] = (float) (10 * Math.log10(e / 160 + 1e-12)); }
                    java.util.Arrays.sort(srt);
                    float thr = srt[(int) (nf * 0.9)] - 20;
                    java.util.ArrayList<int[]> pieces = new java.util.ArrayList<>();
                    int st = -1, quiet = 0;
                    for (int i = 0; i <= nf; i++) {
                        boolean loud = i < nf && db[i] > thr;
                        if (loud) { if (st < 0) st = i; quiet = 0; }
                        else if (st >= 0 && (++quiet >= 5 || i == nf)) { pieces.add(new int[] { st, i - quiet + 1 }); st = -1; quiet = 0; }
                    }
                    if (pieces.size() < 2 || pieces.size() > 40) continue;   // one piece = a whole sentence of fluent speech: not cut
                    for (int[] pc : pieces) {
                        if (cancel.get()) throw new InterruptedException("cancel");
                        int len = pc[1] - pc[0];
                        if (len < 12 || len > 160) continue;   // 0.12 – 1.6 s
                        int s0 = Math.max(0, pc[0] * 160 - 800), s1 = Math.min(x.length, pc[1] * 160 + 800);
                        String txt = tr.recognize(java.util.Arrays.copyOfRange(x, s0, s1));
                        if (!onlyFw(txt, fl)) continue;
                        double a0 = (f0 + pc[0] * 160) / 16000.0 - 0.03, a1 = (f0 + pc[1] * 160) / 16000.0 + 0.04;
                        out.put(new JSONArray().put(Math.round(a0 * 100) / 100.0).put(Math.round(a1 * 100) / 100.0).put(txt.trim()));
                    }
                    p.on(0.2 + 0.8 * (t + 1) / todo.size());
                }
            } finally { r.close(); }
        } finally {
            if (tr != null) tr.release();
            w.delete();
        }
        return new JSONObject().put("ranges", out).put("checked", todo.size());
    }

    /** a new recording made of the kept ranges of another (text-based editing) */
    static JSONObject keepRender(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        JSONObject item = AudioTools.render(c, a.getJSONObject("src"), a.getJSONArray("keep"), Studio.safe(a.optString("name", "Ses")) + ".m4a", p, cancel);
        return new JSONObject().put("item", item);
    }

    /** integrated loudness of any audio (for level matching in the player) */
    static JSONObject loudness(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        JSONObject src = a.getJSONObject("src");
        Studio.Fmt f = Studio.probe(c, src);
        Loudness l = new Loudness(f.ch, f.rate);
        l.to(new Sink());
        Studio.source(c, src, f, p, new double[][] { { 0, 1 } }, cancel).run(l);
        double v = l.integrated();
        JSONObject o = new JSONObject();
        o.put("lufs", Double.isInfinite(v) ? -70 : Math.round(v * 10) / 10.0);
        return o;
    }

    /** 4.14: loudness (LUFS) of the heard part of each timeline clip: [{path, in, len}] (seconds) → lufs[] (-70 = silent) */
    static JSONObject clipLoud(JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        JSONArray cl = a.getJSONArray("clips"), res = new JSONArray();
        for (int i = 0; i < cl.length(); i++) {
            JSONObject o = cl.getJSONObject(i);
            File f = new File(o.getString("path"));
            Info inf = info(f);
            long st = Math.max(0, Math.min(inf.frames, Math.round(o.optDouble("in", 0) * inf.rate)));
            long n = Math.min(inf.frames - st, Math.round(o.optDouble("len", 1e9) * inf.rate));
            Loudness l = new Loudness(inf.ch, inf.rate);
            l.to(new Sink());
            RandomAccessFile r = new RandomAccessFile(f, "r");
            try {
                final int BL = 8192;
                byte[] b = new byte[BL * inf.ch * 2];
                float[][] x = new float[inf.ch][BL];
                r.seek(44 + st * inf.ch * 2);
                for (long d = 0; d < n; ) {
                    if (cancel.get()) throw new InterruptedException();
                    int k = (int) Math.min(BL, n - d);
                    r.readFully(b, 0, k * inf.ch * 2);
                    for (int j = 0, q = 0; j < k; j++) for (int c = 0; c < inf.ch; c++, q += 2) x[c][j] = (short) ((b[q] & 0xff) | (b[q + 1] << 8)) / 32768f;
                    l.push(x, k);
                    d += k;
                }
            } finally {
                r.close();
            }
            double v = l.integrated();
            res.put(Double.isInfinite(v) || Double.isNaN(v) ? -70 : Math.round(v * 10) / 10.0);
            p.on((i + 1.0) / cl.length());
        }
        return new JSONObject().put("lufs", res);
    }

    /* ---------- 2.0: ringtone, chapters ---------- */

    static JSONObject ringtone(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        long st = Math.max(0, a.getLong("startMs")), len = Math.max(1000, Math.min(40000, a.getLong("lenMs")));
        JSONArray keep = new JSONArray().put(new JSONArray().put(st).put(st + len));
        String name = Studio.safe(a.optString("name", "Zil")) + ".m4a";
        JSONObject item = AudioTools.render(c, a.getJSONObject("src"), keep, MediaLib.createRingtone(c, name, "audio/mp4"), Math.min(1.0, len / 8000.0), true, p, cancel);
        JSONObject r = new JSONObject();
        r.put("item", item);
        return r;
    }

    /** chapter marks at the longest pauses, roughly every 1.5–5 minutes */
    static JSONObject chapters(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        File w = new File(c.getCacheDir(), "ch" + System.nanoTime() + ".wav");
        try {
            decodeTo(c, a.getJSONObject("src"), 1, 16000, p, new double[] { 0, 0.8 }, cancel, w);
            Info inf = info(w);
            Speech.Scan sc = Speech.scan(w, inf, part(p, 0.8, 1), cancel);
            java.util.ArrayList<int[]> ps = Speech.pauses(sc, 0.8);
            double dur = inf.frames / (double) inf.rate, last = 0, minGap = a.optDouble("min", 90), maxGap = a.optDouble("max", 300);
            JSONArray times = new JSONArray();
            while (true) {
                int[] best = null;
                for (int[] r : ps) {
                    double mid = (r[0] + r[1]) * Speech.FR / 2;
                    if (mid < last + minGap || mid > last + maxGap || mid > dur - 30) continue;
                    if (best == null || r[1] - r[0] > best[1] - best[0]) best = r;
                }
                if (best == null) {
                    // no pause in the window: move on so a long monologue still gets later chapters
                    if (last + maxGap >= dur - 30) break;
                    last += maxGap;
                    continue;
                }
                last = (best[0] + best[1]) * Speech.FR / 2;
                times.put(Math.round(last * 10) / 10.0);
            }
            JSONObject r = new JSONObject();
            r.put("times", times);
            return r;
        } finally {
            w.delete();
        }
    }

    /** noise removal with the voice-separation model: keeps the voice, drops everything else */
    /** DPDFNet speech denoiser (48 kHz mono model; other rates are converted there and back) */
    /** 4.17: a ready-made effect = several tools one after another, described as data: steps [{op, …args}] (web/fx-presets.js) */
    static JSONObject chain(Context c, File in, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        JSONArray st = a.getJSONArray("steps");
        File cur = in;
        JSONObject last = null;
        int n = st.length();
        try {
            for (int i = 0; i < n; i++) {
                JSONObject o = new JSONObject(st.getJSONObject(i).toString());
                if ("chain".equals(o.optString("op"))) continue;
                o.put("path", cur.getAbsolutePath());
                JSONObject r = op(c, o, part(p, (double) i / n, (double) (i + 1) / n), cancel);
                if (r.optBoolean("same")) continue;
                if (cur != in) cur.delete();
                cur = new File(r.getString("path"));
                last = r;
            }
        } catch (Exception e) {
            if (cur != in) cur.delete();
            throw e;
        }
        if (last == null) return same(a, new JSONObject().put("none", true));
        last.put("report", new JSONObject().put("steps", n));
        return last;
    }

    /** 4.13: the clip played backwards (read from the end in blocks; work files are always 44-byte-header 16-bit WAV) */
    static JSONObject reverse(File in, Info inf, File out, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        final int ch = inf.ch, B = 8192;
        Meter m = new Meter(ch, inf.rate);
        m.to(new Studio.WavNode(out, inf.rate));
        byte[] buf = new byte[B * ch * 2];
        float[][] f = new float[ch][B];
        RandomAccessFile r = new RandomAccessFile(in, "r");
        try {
            for (long end = inf.frames; end > 0; ) {
                if (cancel.get()) throw new InterruptedException();
                int n = (int) Math.min(B, end);
                end -= n;
                r.seek(44 + end * ch * 2);
                r.readFully(buf, 0, n * ch * 2);
                for (int i = 0; i < n; i++) for (int c = 0; c < ch; c++) {
                    int q = ((n - 1 - i) * ch + c) * 2;
                    f[c][i] = (short) ((buf[q] & 0xff) | (buf[q + 1] << 8)) / 32768f;
                }
                m.push(f, n);
                p.on(1 - (double) end / inf.frames);
            }
            m.end();
        } catch (Exception e) {
            out.delete();
            throw e;
        } finally {
            r.close();
        }
        return done(out, m, new JSONObject());
    }

    /** 4.12 HD voice: the original up to its own top frequency, the AI's high band above it; 48 kHz out */
    static JSONObject hd(Context c, File in, Info inf, File out, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        if (!Separator.available()) throw new IllegalStateException("sep-unavailable");
        if (!Hd.ready(c)) throw new IllegalStateException("hd-model");
        Analyzer.Analysis an = analyze(in, inf, part(p, 0, 0.1), cancel);
        // anything at all up to 15.5 kHz: already full; the crossover sits where the voice's own top end fades (25 dB down)
        double bw = Hd.bandwidth(an.ltasPower, inf.rate, an.fftSize, 25);
        JSONObject rep = new JSONObject().put("from", Math.round(bw / 100) / 10.0);
        double full = Hd.bandwidth(an.ltasPower, inf.rate, an.fftSize, 50);
        if (full >= 15500) { rep.put("none", true).put("from", Math.round(full / 100) / 10.0); return same(a, rep); }
        synchronized (Hd.class) {
            File d = Hd.dir(c);
            if (!Hd.open(new File(d, Hd.FILES[0]).getAbsolutePath(), new File(d, Hd.FILES[2]).getAbsolutePath(), Hd.threads())) throw new IllegalStateException("hd-model");
            try {
                Meter m = new Meter(inf.ch, Hd.RATE);
                Node head = new Hd.Stage(inf.ch, Math.max(3000, bw)), tail = head;
                if (inf.rate != Hd.RATE) { Resampler r = new Resampler(inf.ch, (double) inf.rate, (double) Hd.RATE); r.to(head); head = r; }
                tail.to(m).to(new Studio.WavNode(out, Hd.RATE));
                wav(in, inf, part(p, 0.1, 1), new double[] { 0, 1 }, cancel).run(head);
                return done(out, m, rep);
            } catch (Exception e) {
                out.delete();
                throw e;
            } finally {
                Hd.close();
            }
        }
    }

    /** amb ≥ 0: the share of the room sound kept (4.16 Studio voice slider), else from the strength */
    static JSONObject aiDenoise(Context c, File in, Info inf, File out, double str, double amb, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        Analyzer.Analysis b = analyze(in, inf, part(p, 0, 0.25), cancel);
        Denoiser.Par dn = null;
        try {
            // Light keeps ~25 % of the original (-12 dB), Balanced -23 dB, Strong -34 dB
            double lim = 12 + 22 * Math.max(0, Math.min(1, (str - 0.6) / 0.8));
            int ch = inf.ch, fs = inf.rate;
            Analyzer after = new Analyzer(ch, fs);
            Meter m = new Meter(ch, fs);
            dn = new Denoiser.Par(c, ch, amb >= 0 ? Math.max(0.02, Math.min(0.6, amb)) : Math.pow(10, -lim / 20));
            Node head = dn, tail = head;
            if (fs != Denoiser.RATE) {
                Resampler up = new Resampler(ch, (double) fs, (double) Denoiser.RATE), down = new Resampler(ch, (double) Denoiser.RATE, (double) fs);
                up.to(head); head.to(down); head = up; tail = down;
            }
            tail.to(new Tee(after)).to(m).to(new Studio.WavNode(out, fs));
            wav(in, inf, part(p, 0.25, 1), new double[] { 0, 1 }, cancel).run(head);
            double na = after.finish().noiseDb;
            JSONObject rep = new JSONObject();
            rep.put("before", Math.round(b.noiseDb));
            rep.put("after", Math.round(na));
            rep.put("reduction", Math.max(0, Math.round(b.noiseDb - na)));
            rep.put("ai", true);
            return done(out, m, rep);
        } catch (Exception e) {
            out.delete();
            throw e;
        } finally {
            if (dn != null) dn.close();
        }
    }

    static final class Gate extends Node {
        final int ch, LA, HOLD; final float closed, thr, eA, gA, gR;
        final float[][] dl; int dp, hold; long seen; float env, gain = 1; float[][] o = new float[1][0];
        Gate(int ch, int la, int holdN, float closed, float thr, float eA, float gA, float gR) {
            this.ch = ch; LA = la; HOLD = holdN; this.closed = closed; this.thr = thr; this.eA = eA; this.gA = gA; this.gR = gR;
            dl = new float[ch][la];
        }
        @Override public void push(float[][] b, int n) throws Exception {
            if (o[0].length < n) o = new float[ch][n];
            int k = 0;
            for (int i = 0; i < n; i++) {
                float pw = 0;
                for (int c = 0; c < ch; c++) pw += b[c][i] * b[c][i];
                env += (pw / ch - env) * eA;
                if (env > thr) hold = HOLD; else if (hold > 0) hold--;
                float tg = hold > 0 ? 1 : closed;
                gain += (tg - gain) * (tg > gain ? gA : gR);
                // the gain follows the newest audio, the output is LA samples older: the gate opens just before a word
                for (int c = 0; c < ch; c++) { float d = dl[c][dp]; dl[c][dp] = b[c][i]; o[c][k] = d * gain; }
                dp = (dp + 1) % LA;
                if (++seen > LA) k++;
            }
            if (k > 0) {
                next.push(o, k);
            }
        }
        @Override public void end() throws Exception {
            int left = (int) Math.min(LA, seen);
            if (left > 0) {
                if (o[0].length < left) o = new float[ch][left];
                int start = seen < LA ? 0 : dp;
                for (int i = 0; i < left; i++) for (int c = 0; c < ch; c++) o[c][i] = dl[c][(start + i) % LA] * gain;
                next.push(o, left);
            }
            next.end();
        }
    }

    /** noise removal with the voice separation model (Spleeter): keeps only the voice stem */
    static JSONObject sepDenoise(Context c, File in, Info inf, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        if (!Separator.available()) throw new IllegalStateException("sep-unavailable");
        Analyzer.Analysis b = analyze(in, inf, part(p, 0, 0.1), cancel);
        File vm = Separator.model(c, "vocals.fp16.onnx"), am = Separator.model(c, "accompaniment.fp16.onnx");
        File[] o = Separator.run(in, inf, vm, am, part(p, 0.1, 0.9), cancel);
        o[1].delete();
        try {
            Info oi = info(o[0]);
            Analyzer after = new Analyzer(oi.ch, oi.rate);
            Meter m = new Meter(oi.ch, oi.rate);
            Node t = new Tee(after);
            t.to(m).to(new Sink());
            wav(o[0], oi, part(p, 0.9, 1), new double[] { 0, 1 }, cancel).run(t);
            double na = after.finish().noiseDb;
            JSONObject rep = new JSONObject();
            rep.put("before", Math.round(b.noiseDb));
            rep.put("after", Math.round(na));
            rep.put("reduction", Math.max(0, Math.round(b.noiseDb - na)));
            rep.put("ai", true);
            return done(o[0], m, rep);
        } catch (Exception e) {
            o[0].delete();
            throw e;
        }
    }

    /** noise gate: between words the remaining hiss is pulled down (5 ms look-ahead, 120 ms hold) */
    static JSONObject gate(File in, Info inf, File out, double str, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        Speech.Scan sc = Speech.scan(in, inf, part(p, 0, 0.3), cancel);
        JSONObject rep = new JSONObject();
        double gap = sc.speech - sc.noise;
        if (gap < 6 || sc.noise < -80) { rep.put("none", true); return same(null, rep); }
        final double thrDb = sc.noise + Math.max(4, Math.min(12, gap * 0.3));
        final double range = Math.min(gap - 2, 8 + 10 * str);
        rep.put("range", Math.round(range));
        final int ch = inf.ch, fs = inf.rate, LA = Math.round(fs * 0.005f), HOLD = Math.round(fs * 0.12f);
        final float closed = (float) Math.pow(10, -range / 20), thr = (float) Math.pow(10, thrDb / 10);
        final float eA = (float) (1 - Math.exp(-1.0 / (0.01 * fs))), gA = (float) (1 - Math.exp(-1.0 / (0.003 * fs))), gR = (float) (1 - Math.exp(-1.0 / (0.15 * fs)));
        Node g = new Gate(ch, LA, HOLD, closed, thr, eA, gA, gR);
        Meter m = new Meter(ch, fs);
        g.to(m).to(new Studio.WavNode(out, fs));
        wav(in, inf, part(p, 0.3, 1), new double[] { 0, 1 }, cancel).run(g);
        return done(out, m, rep);
    }

    /** per-band correction towards the target tone (smoothed over neighbours, capped) */
    /* ---------- 3.0: spectral view and painted repair ---------- */

    static float[][] readRegion(RandomAccessFile r, Info inf, long from, int n) throws Exception {
        float[][] x = new float[inf.ch][n];
        byte[] b = new byte[n * inf.ch * 2];
        r.seek(44 + from * inf.ch * 2);
        r.readFully(b);
        for (int i = 0, q = 0; i < n; i++) for (int c = 0; c < inf.ch; c++, q += 2) x[c][i] = (short) ((b[q] & 0xff) | (b[q + 1] << 8)) / 32768f;
        return x;
    }

    static void writeRegion(RandomAccessFile r, Info inf, long from, float[][] x, int a, int z) throws Exception {
        byte[] b = new byte[(z - a) * inf.ch * 2];
        for (int i = a, q = 0; i < z; i++) for (int c = 0; c < inf.ch; c++, q += 2) {
            int v = (int) Math.round(Math.max(-1, Math.min(1, x[c][i])) * 32767);
            b[q] = (byte) v; b[q + 1] = (byte) (v >> 8);
        }
        r.seek(44 + (from + a) * inf.ch * 2);
        r.write(b);
    }

    /** a: {path, t0, t1 (seconds of the file), rows, cols} → spectrogram bytes (base64), row 0 = lowest */
    static JSONObject spectro(JSONObject a) throws Exception {
        File f = new File(a.getString("path"));
        Info inf = info(f);
        long s0 = Math.max(0, Math.round(a.getDouble("t0") * inf.rate)), s1 = Math.min(inf.frames, Math.round(a.getDouble("t1") * inf.rate));
        int n = (int) Math.max(1, s1 - s0), rows = Math.max(32, Math.min(256, a.optInt("rows", 160))), cols = Math.max(32, Math.min(1600, a.optInt("cols", 600)));
        RandomAccessFile r = new RandomAccessFile(f, "r");
        float[][] x;
        try { x = readRegion(r, inf, s0, n); } finally { r.close(); }
        float[] m = x[0];
        if (inf.ch > 1) { m = new float[n]; for (int i = 0; i < n; i++) m[i] = (x[0][i] + x[1][i]) * 0.5f; }
        JSONObject o = new JSONObject();
        o.put("img", android.util.Base64.encodeToString(SpecPaint.view(m, n, inf.rate, rows, cols), android.util.Base64.NO_WRAP));
        o.put("rows", rows); o.put("cols", cols); o.put("rate", inf.rate);
        o.put("fmax", Math.min(20000, inf.rate / 2));
        return o;
    }

    /** a.rects: [[t0, t1, f0, f1]] (seconds of the file, Hz); a.mode: "blend" (to the background) or "mute" */
    static JSONObject spaint(File in, Info inf, File out, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        JSONArray rs = a.getJSONArray("rects");
        boolean mute = "mute".equals(a.optString("mode"));
        InputStream is = new FileInputStream(in);
        java.io.OutputStream os = new java.io.FileOutputStream(out);
        try { byte[] b = new byte[1 << 16]; int k; while ((k = is.read(b)) > 0) os.write(b, 0, k); } finally { is.close(); os.close(); }
        int fs = inf.rate, pad = (int) (0.4 * fs);
        RandomAccessFile r = new RandomAccessFile(out, "rw");
        try {
            for (int i = 0; i < rs.length(); i++) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                JSONArray q = rs.getJSONArray(i);
                long b0 = Math.max(0, Math.round(q.getDouble(0) * fs)), b1 = Math.min(inf.frames, Math.round(q.getDouble(1) * fs));
                if (b1 - b0 < fs / 100) continue;
                long from = Math.max(0, b0 - pad), to = Math.min(inf.frames, b1 + pad);
                int n = (int) (to - from);
                if (n < 4 * SpecPaint.N) continue;
                float[][] x = readRegion(r, inf, from, n);
                float[][] y = SpecPaint.repair(x, n, fs, (int) (b0 - from), (int) (b1 - from), Math.min(q.getDouble(2), q.getDouble(3)), Math.max(q.getDouble(2), q.getDouble(3)), mute);
                int za = Math.max(SpecPaint.N, (int) (b0 - from) - SpecPaint.N), zz = Math.min(n - SpecPaint.N, (int) (b1 - from) + SpecPaint.N);
                if (zz > za) writeRegion(r, inf, from, y, za, zz);
                if (p != null) p.on(0.8 * (i + 1) / rs.length());
            }
        } finally {
            r.close();
        }
        Meter m = new Meter(inf.ch, fs);
        m.to(new Node() { @Override public void push(float[][] b, int n) { } });
        wav(out, inf, p, new double[] { 0.8, 1 }, cancel).run(m);
        JSONObject rep = new JSONObject();
        rep.put("n", rs.length());
        return done(out, m, rep);
    }

    /** mean of measured − reference over the bands between lo and hi (Hz) */
    static double bandDiff(double[] m, double[] ref, double lo, double hi) {
        double s = 0; int n = 0;
        for (int i = 0; i < Enhancer.BANDS.length; i++) if (Enhancer.BANDS[i] >= lo && Enhancer.BANDS[i] <= hi) { s += m[i] - ref[i]; n++; }
        return n == 0 ? 0 : s / n;
    }

    static double[] eqGains(double[] measured, double[] target, double str) {
        double[] g = new double[Enhancer.BANDS.length];
        for (int i = 0; i < g.length; i++) {
            double s = 0, w = 0;
            for (int j = Math.max(0, i - 1); j <= Math.min(g.length - 1, i + 1); j++) { double ww = j == i ? 2 : 1; s += (target[j] - measured[j]) * ww; w += ww; }
            double v = 0.8 * str * s / w, max = Enhancer.BANDS[i] < 160 ? 0 : Enhancer.BANDS[i] > 8000 ? 6 : 8;
            g[i] = Math.max(-8, Math.min(max, v));
        }
        return g;
    }

    /* ---------- "Clean": diagnosis of a recording, then all chosen fixes in one go ---------- */

    static File cleanDir(Context c) {
        File d = new File(c.getCacheDir(), "clean");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static AudioTools.Progress part(final AudioTools.Progress p, final double a, final double b) {
        return new AudioTools.Progress() { @Override public void on(double f) { p.on(a + (b - a) * Math.max(0, Math.min(1, f))); } };
    }

    static JSONObject diagnose(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        File d = cleanDir(c);
        File[] old = d.listFiles();
        if (old != null) for (File x : old) x.delete();
        JSONObject src = a.getJSONObject("src");
        Studio.Fmt f = Studio.probe(c, src);
        File w = new File(d, "o" + System.nanoTime() + ".wav");
        decodeTo(c, src, Math.min(2, f.ch), Timeline.RATE, p, new double[] { 0, 0.4 }, cancel, w);
        Info inf = info(w);
        int ch = inf.ch, fs = inf.rate;
        // pass 1: levels, noise, hum, channels, tone, loudness
        Analyzer an0 = new Analyzer(ch, fs);
        Loudness lo = new Loudness(ch, fs);
        Node t1 = new Tee(an0);
        t1.to(lo).to(new Sink());
        wav(w, inf, p, new double[] { 0.4, 0.7 }, cancel).run(t1);
        Analyzer.Analysis an = an0.finish();
        // pass 2: count clicks, plosives and harsh "s" with the same detectors the fixes use
        Stages.ClickRepair cr = new Stages.ClickRepair(ch, fs, 10);
        Stages.Filters hp = new Stages.Filters(ch, fs, 75, null);
        Stages.PlosiveTamer pt = new Stages.PlosiveTamer(ch, fs, 1.8);
        Stages.DeEsser de = new Stages.DeEsser(ch, fs, Math.pow(10, (an.speechDb - 12) / 20), 9);
        cr.to(new Sink());
        hp.to(pt).to(new Sink());
        de.to(new Sink());
        Node t2 = new Tee(cr);
        t2.to(new Tee(hp)).to(de);
        wav(w, inf, p, new double[] { 0.7, 1 }, cancel).run(t2);

        JSONObject o = new JSONObject();
        o.put("path", w.getAbsolutePath());
        o.put("dur", inf.frames * 1000.0 / fs);
        o.put("ch", ch);
        double lufs = lo.integrated();
        o.put("lufs", Double.isInfinite(lufs) ? -99 : Math.round(lufs * 10) / 10.0);
        o.put("lra", Math.round(lo.range() * 10) / 10.0);
        o.put("peak", Math.round(20 * Math.log10(Math.max(1e-9, an.peak)) * 10) / 10.0);
        o.put("clipped", an.clipped);
        o.put("snr", Math.round(an.snr));
        o.put("noise", Math.round(an.noiseDb));
        double[] hr = an.humRatioDb;
        double h50 = Math.max(hr[0], hr[1]), h60 = Math.max(hr[2], hr[3]);
        o.put("hum", Math.round(Math.max(h50, h60)));
        o.put("humHz", h60 > h50 ? 60 : 50);
        o.put("clicks", cr.repaired);
        o.put("plosive", Math.round(pt.active * 10.0 / fs) / 10.0);
        o.put("sib", Math.round(de.active * 10.0 / fs) / 10.0);
        o.put("channel", ch > 1 ? (an.channelMode == Stages.ChannelMap.KEEP ? "" : an.channelReason) : "");
        o.put("head", Math.round(an.activeStart * 10.0 / fs) / 10.0);
        o.put("tail", Math.round((an.samples - an.activeEnd) * 10.0 / fs) / 10.0);
        double[] g = eqGains(Enhancer.bandsOf(an.ltasPower, fs, an.fftSize), Enhancer.referenceBands(), 1);
        double mean = 0;
        for (double x : g) mean += Math.abs(x) / g.length;
        o.put("eq", Math.round(mean * 10) / 10.0);
        o.put("speech", an.speechFrames > 0);
        o.put("noiseP", Studio.json(an.noisePower));
        // long pauses and breaths (frame scan of the same file)
        Speech.Scan sc = Speech.scan(w, inf, null, cancel);
        double pmax = a.optDouble("pmax", 1.0);
        java.util.ArrayList<int[]> ps = Speech.pauses(sc, pmax);
        double extra = 0;
        for (int[] r : ps) extra += (r[1] - r[0]) * Speech.FR - a.optDouble("pkeep", 0.45);
        o.put("pauses", ps.size());
        o.put("pauseSec", Math.round(extra * 10) / 10.0);
        o.put("breaths", Speech.breaths(sc).size());
        return o;
    }

    /** runs the chosen fixes one after another on the diagnosed WAV; the original recording is never touched */
    static JSONObject clean(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        File in = new File(a.getString("path")), cur = in;
        JSONArray fixes = a.getJSONArray("fixes");
        JSONObject reports = new JSONObject(), last = null;
        double trimStart = 0;
        int n = fixes.length();
        try {
            for (int i = 0; i < n; i++) {
                String fx = fixes.getString(i);
                JSONObject o = new JSONObject();
                o.put("path", cur.getAbsolutePath());
                o.put("op", "denoise".equals(fx) && a.optBoolean("ai") ? "aidenoise" : fx);
                o.put("strength", a.optDouble("strength", 1));
                if ("loud".equals(fx)) o.put("target", a.optString("target", "youtube"));
                if ("pauses".equals(fx)) { o.put("max", a.optDouble("pmax", 0.8)); o.put("keep", a.optDouble("pkeep", 0.45)); }
                if ("eq".equals(fx) && a.has("learned")) o.put("learned", a.get("learned"));
                if ("denoise".equals(fx) && a.has("profile")) o.put("profile", a.get("profile"));
                if ("denoise".equals(fx) && a.has("amb")) o.put("amb", a.getDouble("amb"));
                JSONObject r = op(c, o, part(p, (double) i / n, (double) (i + 1) / n), cancel);
                if (r.optJSONObject("report") != null) reports.put(fx, r.getJSONObject("report"));
                if (r.optBoolean("same")) continue;
                if (cur != in) cur.delete();
                cur = new File(r.getString("path"));
                last = r;
                if ("trim".equals(fx)) trimStart = r.getJSONObject("report").optDouble("start", 0);
            }
        } catch (Exception e) {
            if (cur != in) cur.delete();
            throw e;
        }
        JSONObject res = last != null ? last : new JSONObject().put("path", in.getAbsolutePath()).put("dur", info(in).frames * 1000.0 / info(in).rate);
        res.put("reports", reports);
        res.put("trimStart", trimStart);
        return res;
    }

    // per-job holders for the stage objects whose counters go into the report
    private static final ThreadLocal<Analyzer> tees = new ThreadLocal<>();
    private static final ThreadLocal<Stages.ClickRepair> clicks = new ThreadLocal<>();
    private static final ThreadLocal<app.sesdefteri.dsp.MouthClick> mouths = new ThreadLocal<>();
    private static final ThreadLocal<AutoTune> autoTunes = new ThreadLocal<>();
    private static final ThreadLocal<Stages.PlosiveTamer> plos = new ThreadLocal<>();
    private static final ThreadLocal<Stages.DeEsser> dees = new ThreadLocal<>();

    static JSONObject same(JSONObject a, JSONObject rep) throws Exception {
        JSONObject o = new JSONObject();
        o.put("same", true);
        o.put("report", rep);
        return o;
    }

    /** copies audio to an analyzer on the side */
    static final class Tee extends Node {
        final Node side;
        Tee(Node side) { this.side = side; }
        @Override public void push(float[][] b, int n) throws Exception { side.push(b, n); next.push(b, n); }
        @Override public void end() throws Exception { side.end(); next.end(); }
    }

    /** keeps only the listed [ms, ms] ranges, with 8 ms fades at every join */
    static final class Keep extends Node {
        final long[] ks, ke;
        final int fade;
        long pos;
        int seg;
        float[][] out = new float[1][0];

        Keep(JSONArray keep, int fs) throws Exception {
            int n = keep.length();
            ks = new long[n]; ke = new long[n];
            for (int i = 0; i < n; i++) {
                JSONArray r = keep.getJSONArray(i);
                ks[i] = Math.round(r.getDouble(0) * fs / 1000.0);
                ke[i] = Math.round(r.getDouble(1) * fs / 1000.0);
            }
            fade = Math.max(1, (int) (fs * 0.008));
        }

        @Override public void push(float[][] b, int n) throws Exception {
            int ch = b.length;
            if (out.length != ch || out[0].length < n) out = new float[ch][n];
            int k = 0;
            for (int i = 0; i < n; i++, pos++) {
                while (seg < ks.length && pos >= ke[seg]) seg++;
                if (seg >= ks.length) break;
                if (pos < ks[seg]) continue;
                double g = 1;
                long a = pos - ks[seg], z = ke[seg] - pos;
                if (ks[seg] > 0 && a < fade) g = (double) a / fade;
                if (seg < ks.length - 1 && z < fade) g = Math.min(g, (double) z / fade);
                for (int c = 0; c < ch; c++) out[c][k] = (float) (b[c][i] * g);
                k++;
            }
            if (k > 0) next.push(out, k);
        }
    }

    /** gain that lands on the target loudness after the limiter (two quick measuring runs) */
    static double loudGain(File in, Info inf, Enhancer.Target t, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        Loudness l0 = new Loudness(inf.ch, inf.rate);
        l0.to(new Sink());
        wav(in, inf, p, new double[] { 0, 0.2 }, cancel).run(l0);
        double lufs = l0.integrated();
        if (Double.isInfinite(lufs)) return 0;
        double gain = Math.max(-30, Math.min(30, t.lufs - lufs));
        for (int it = 0; it < 2; it++) {
            Limiter lim = new Limiter(inf.ch, inf.rate, t.ceilingDb, gain);
            Loudness pr = new Loudness(inf.ch, inf.rate);
            lim.to(pr).to(new Sink());
            wav(in, inf, p, new double[] { 0.2 + 0.2 * it, 0.4 + 0.2 * it }, cancel).run(lim);
            double got = pr.integrated();
            if (Double.isInfinite(got) || Math.abs(t.lufs - got) < 0.2) break;
            gain = Math.max(-30, Math.min(36, gain + (t.lufs - got) * 1.1));
        }
        return gain;
    }

    static final class Sink extends Node {
        @Override public void push(float[][] b, int n) { }
        @Override public void end() { }
    }

    /** decodes another file into a WAV with the workspace's rate and channel count */
    static File decodeLike(Context c, JSONObject src, int ch, int rate, AudioTools.Progress p, double[] slice, AtomicBoolean cancel) throws Exception {
        return decodeTo(c, src, ch, rate, p, slice, cancel, newFile(c));
    }

    static File decodeTo(Context c, JSONObject src, final int ch, int rate, AudioTools.Progress p, double[] slice, AtomicBoolean cancel, File out) throws Exception {
        Studio.Fmt f = Studio.probe(c, src);
        Node tail = new Studio.WavNode(out, rate);
        Node head = tail;
        if (f.rate != rate) { Resampler r = new Resampler(ch, (double) f.rate, (double) rate); r.to(head); head = r; }
        if (f.ch != ch) {
            final int inCh = f.ch;
            final Node nx = head;
            head = new Node() {
                float[][] o = new float[ch][0];
                @Override public void push(float[][] b, int n) throws Exception {
                    if (o[0].length < n) o = new float[o.length][n];
                    for (int i = 0; i < n; i++) {
                        float s = 0;
                        for (int k = 0; k < inCh; k++) s += b[k][i];
                        for (int k = 0; k < o.length; k++) o[k][i] = o.length == 1 ? s / inCh : b[Math.min(k, inCh - 1)][i];
                    }
                    nx.push(o, n);
                }
                @Override public void end() throws Exception { nx.end(); }
            };
        }
        Studio.source(c, src, f, p, new double[][] { slice }, cancel).run(head);
        return out;
    }

    /* ---------- insert another recording at a position ---------- */

    static JSONObject insert(Context c, File in, Info inf, File out, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        File other = decodeLike(c, a.getJSONObject("other"), inf.ch, inf.rate, p, new double[] { 0, 0.5 }, cancel);
        try {
            Info oi = info(other);
            long at = Math.max(0, Math.min(inf.frames, Math.round(a.optDouble("posMs", inf.frames * 1000.0 / inf.rate) * inf.rate / 1000.0)));
            int fade = (int) (inf.rate * 0.01);
            Meter m = new Meter(inf.ch, inf.rate);
            m.to(new Studio.WavNode(out, inf.rate));
            // first part, the inserted audio, the rest; short fades so the joins don't click
            copy(in, inf, 0, at, 0, at < inf.frames ? fade : 0, m);
            copy(other, oi, 0, oi.frames, at > 0 ? fade : 0, at < inf.frames ? fade : 0, m);
            copy(in, inf, at, inf.frames, at > 0 ? fade : 0, 0, m);
            m.end();
            JSONObject rep = new JSONObject();
            rep.put("at", Math.round(at * 100.0 / inf.rate) / 100.0);
            rep.put("added", Math.round(oi.frames * 100.0 / oi.rate) / 100.0);
            return done(out, m, rep);
        } catch (Exception e) {
            out.delete();
            throw e;
        } finally {
            other.delete();
        }
    }

    /** pushes frames [from, to) of a workspace WAV, fading in/out over the given lengths */
    static void copy(File f, Info inf, long from, long to, int fadeIn, int fadeOut, Node sink) throws Exception {
        if (to <= from) return;
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            r.seek(44 + from * inf.ch * 2);
            final int BL = 4096;
            float[][] x = new float[inf.ch][BL];
            byte[] rb = new byte[BL * inf.ch * 2];
            long pos = from;
            while (pos < to) {
                int n = (int) Math.min(BL, to - pos);
                r.readFully(rb, 0, n * inf.ch * 2);
                int q = 0;
                for (int i = 0; i < n; i++) {
                    long k = pos + i;
                    double g = 1;
                    if (fadeIn > 0 && k - from < fadeIn) g = (double) (k - from) / fadeIn;
                    if (fadeOut > 0 && to - k <= fadeOut) g = Math.min(g, (double) (to - k - 1) / fadeOut);
                    for (int c = 0; c < inf.ch; c++, q += 2) x[c][i] = (float) ((short) ((rb[q] & 0xff) | (rb[q + 1] << 8)) / 32768f * g);
                }
                sink.push(x, n);
                pos += n;
            }
        } finally {
            r.close();
        }
    }

    /* ---------- background music with ducking ---------- */

    static JSONObject mix(Context c, File in, Info inf, File out, JSONObject a, final AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        JSONObject msrc = a.getJSONObject("music");
        Studio.Fmt fm = Studio.probe(c, msrc);
        int mch = Math.min(2, fm.ch);
        File mtemp = new File(dir(c), "m" + System.nanoTime() + ".pcm"), temp = new File(dir(c), "t" + System.nanoTime() + ".pcm");
        Pipeline.FileTemp mt = new Pipeline.FileTemp(mtemp, mch);
        try {
            Node head = mt;
            if (fm.rate != inf.rate) { Resampler r = new Resampler(mch, (double) fm.rate, (double) inf.rate); r.to(mt); head = r; }
            Studio.source(c, msrc, fm, p, new double[][] { { 0, 0.2 } }, cancel).run(head);
            int outCh = Math.max(inf.ch, mch);
            Meter m = new Meter(outCh, inf.rate);
            m.to(new Studio.WavNode(out, inf.rate));
            Pipeline.mix(wav(in, inf, p, new double[] { 0.2, 0.6 }, cancel), inf.ch, inf.rate, mt,
                a.optDouble("bedDb", -6), a.optDouble("duckDb", -14), a.optString("target", "youtube"), temp, m,
                new Pipeline.Progress() { @Override public void on(double x) { if (x >= 0.7) p.on(0.6 + 0.4 * (x - 0.7) / 0.3); } });
            JSONObject rep = new JSONObject();
            rep.put("bed", a.optDouble("bedDb", -6));
            rep.put("duck", a.optDouble("duckDb", -14));
            return done(out, m, rep);
        } catch (Exception e) {
            out.delete();
            throw e;
        } finally {
            mt.delete();
            temp.delete();
        }
    }

    /** export channel choice: "mono" / "stereo" make a converted copy (null = keep as is) */
    static File chans(Context c, File in, Info inf, String want) throws Exception {
        int to = "mono".equals(want) ? 1 : "stereo".equals(want) ? 2 : inf.ch;
        if (to == inf.ch) return null;
        File out = new File(dir(c), "x" + System.nanoTime() + ".wav");
        Studio.WavNode w = new Studio.WavNode(out, inf.rate);
        RandomAccessFile r = new RandomAccessFile(in, "r");
        try {
            r.seek(44);
            final int BL = 8192;
            byte[] b = new byte[BL * inf.ch * 2];
            float[][] o = new float[to][BL];
            for (long done = 0; done < inf.frames; ) {
                int n = (int) Math.min(BL, inf.frames - done);
                r.readFully(b, 0, n * inf.ch * 2);
                for (int i = 0, q = 0; i < n; i++) {
                    float v = 0;
                    for (int k = 0; k < inf.ch; k++, q += 2) v += (short) ((b[q] & 0xff) | (b[q + 1] << 8)) / 32768f;
                    v /= inf.ch;
                    for (int k = 0; k < to; k++) o[k][i] = v;
                }
                w.push(o, n);
                done += n;
            }
            w.end();
            return out;
        } catch (Exception e) {
            out.delete(); throw e;
        } finally {
            r.close();
        }
    }

    /* ---------- export: the current step as a file in the cache, for the share sheet ---------- */

    static JSONObject export(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        File src = new File(a.getString("path")), conv = chans(c, src, info(src), a.optString("chans", ""));
        try { return export0(c, a, conv != null ? conv : src, p, cancel); } finally { if (conv != null) conv.delete(); }
    }

    private static JSONObject export0(Context c, JSONObject a, File in, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        Info inf = info(in);
        File d = new File(c.getCacheDir(), "export");
        d.mkdirs();
        File[] old = d.listFiles();
        if (old != null) for (File x : old) x.delete();
        String fmt = fmt(a);
        boolean wavFmt = "wav".equals(fmt);
        File out = new File(d, Studio.safe(a.optString("name", "Ses")) + "." + fmt);
        if ("mp3".equals(fmt)) {
            mp3(in, inf, out, a.optInt("kbps", 192), p, cancel, a);
        } else if (wavFmt) {
            java.io.FileInputStream is = new java.io.FileInputStream(in);
            java.io.FileOutputStream os = new java.io.FileOutputStream(out);
            try { byte[] b = new byte[1 << 16]; int n; while ((n = is.read(b)) > 0) os.write(b, 0, n); } finally { is.close(); os.close(); }
        } else {
            encode(in, inf, out, fmt, p, new double[] { 0, 1 }, cancel, a.optInt("kbps", 0));
        }
        JSONObject res = new JSONObject();
        JSONObject src = new JSONObject();
        src.put("path", out.getAbsolutePath());
        res.put("src", src);
        res.put("name", out.getName());
        res.put("mime", wavFmt ? "audio/wav" : mime(fmt));
        return res;
    }

    /** wav / m4a / mp3 / flac / ogg (Opus, Android 10+); mp3 falls back to m4a where the encoder isn't shipped */
    static String fmt(JSONObject a) {
        String f = a.optString("format", "m4a");
        if ("mp3".equals(f) && !Mp3.available()) return "m4a";
        if ("ogg".equals(f) && android.os.Build.VERSION.SDK_INT < 29) return "m4a";
        return "wav".equals(f) || "mp3".equals(f) || "flac".equals(f) || "ogg".equals(f) ? f : "m4a";
    }

    static String mime(String fmt) {
        return "mp3".equals(fmt) ? "audio/mpeg" : "flac".equals(fmt) ? "audio/flac" : "ogg".equals(fmt) ? "audio/ogg" : "wav".equals(fmt) ? "audio/x-wav" : "audio/mp4";
    }

    /** m4a (AAC), flac or ogg (Opus at 48 kHz) from a 16-bit WAV */
    static void encode(File in, Info inf, File out, String fmt, AudioTools.Progress p, double[] range, AtomicBoolean cancel) throws Exception {
        encode(in, inf, out, fmt, p, range, cancel, 0);
    }

    /** kbps: 3.6 quality choice (stereo bit rate; mono gets 60 %), 0 = default */
    static void encode(File in, Info inf, File out, String fmt, AudioTools.Progress p, double[] range, AtomicBoolean cancel, int kbps) throws Exception {
        if ("flac".equals(fmt)) { wav(in, inf, p, range, cancel).run(new Studio.FlacNode(out, inf.rate)); return; }
        boolean op = "ogg".equals(fmt);
        int br = kbps > 0 ? (inf.ch > 1 ? kbps * 1000 : kbps * 600) : inf.ch > 1 ? (op ? 128000 : 192000) : (op ? 64000 : 128000);
        Studio.EncNode e = new Studio.EncNode(out, op ? 48000 : inf.rate, br);
        e.enc.opus = op;
        Node head = e;
        if (op && inf.rate != 48000) { Resampler r = new Resampler(inf.ch, (double) inf.rate, 48000.0); r.to(e); head = r; }
        wav(in, inf, p, range, cancel).run(head);
    }

    static void mp3(File in, Info inf, File out, int kbps, AudioTools.Progress p, AtomicBoolean cancel, JSONObject meta) throws Exception {
        long h = Mp3.init(inf.rate, inf.ch, kbps);
        if (h == 0) throw new IllegalStateException("mp3");
        java.io.OutputStream os = new java.io.BufferedOutputStream(new java.io.FileOutputStream(out), 1 << 16);
        if (meta != null) os.write(id3(meta.optString("title", ""), meta.optString("artist", ""), meta.optString("cover", "")));
        RandomAccessFile r = new RandomAccessFile(in, "r");
        try {
            r.seek(44);
            final int BL = 4096;
            byte[] b = new byte[BL * inf.ch * 2];
            short[] s = new short[BL * inf.ch];
            for (long done = 0; done < inf.frames; ) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                int n = (int) Math.min(BL, inf.frames - done), k = n * inf.ch;
                r.readFully(b, 0, k * 2);
                for (int i = 0; i < k; i++) s[i] = (short) ((b[2 * i] & 0xff) | (b[2 * i + 1] << 8));
                byte[] o = Mp3.encode(h, s, n, inf.ch);
                if (o != null) os.write(o);
                done += n;
                if (p != null && (done / BL) % 64 == 0) p.on((double) done / inf.frames);
            }
            os.write(Mp3.flush(h));
        } catch (Exception e) {
            os.close(); out.delete(); throw e;
        } finally {
            Mp3.close(h);
            r.close();
            try { os.close(); } catch (Exception ignored) { }
        }
    }

    /** ID3v2.3 tag: title, artist and a JPEG cover (UTF-16 text so Turkish letters survive) */
    static byte[] id3(String title, String artist, String coverB64) throws Exception {
        java.io.ByteArrayOutputStream f = new java.io.ByteArrayOutputStream();
        text(f, "TIT2", title);
        text(f, "TPE1", artist);
        if (coverB64 != null && !coverB64.isEmpty()) {
            byte[] img = android.util.Base64.decode(coverB64, android.util.Base64.DEFAULT);
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            b.write(0); b.write("image/jpeg".getBytes("ISO-8859-1")); b.write(0); b.write(3); b.write(0); b.write(img);
            frame(f, "APIC", b.toByteArray());
        }
        byte[] body = f.toByteArray();
        if (body.length == 0) return new byte[0];
        java.io.ByteArrayOutputStream t = new java.io.ByteArrayOutputStream();
        t.write("ID3".getBytes("ISO-8859-1")); t.write(3); t.write(0); t.write(0);
        int n = body.length;
        t.write((n >> 21) & 0x7f); t.write((n >> 14) & 0x7f); t.write((n >> 7) & 0x7f); t.write(n & 0x7f);
        t.write(body);
        return t.toByteArray();
    }

    private static void text(java.io.ByteArrayOutputStream f, String id, String v) throws Exception {
        if (v == null || v.isEmpty()) return;
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        b.write(1); b.write(0xff); b.write(0xfe); b.write(v.getBytes("UTF-16LE")); b.write(0); b.write(0);
        frame(f, id, b.toByteArray());
    }

    private static void frame(java.io.ByteArrayOutputStream f, String id, byte[] data) throws Exception {
        f.write(id.getBytes("ISO-8859-1"));
        int n = data.length;
        f.write((n >> 24) & 0xff); f.write((n >> 16) & 0xff); f.write((n >> 8) & 0xff); f.write(n & 0xff);
        f.write(0); f.write(0);
        f.write(data);
    }

    /* ---------- save the current step as a real file ---------- */

    static JSONObject save(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        File src = new File(a.getString("path")), conv = chans(c, src, info(src), a.optString("chans", ""));
        try { return save0(c, a, conv != null ? conv : src, p, cancel); } finally { if (conv != null) conv.delete(); }
    }

    private static JSONObject save0(Context c, JSONObject a, File in, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        Info inf = info(in);
        String name = Studio.safe(a.optString("name", "Ses"));
        String fmt = fmt(a);
        boolean wavFmt = "wav".equals(fmt);
        JSONObject item;
        if ("mp3".equals(fmt)) {
            File out = new File(dir(c), "e" + System.nanoTime() + ".mp3");
            try {
                mp3(in, inf, out, a.optInt("kbps", 192), p, cancel, a);
                MediaLib.Target tg = MediaLib.create(c, name + ".mp3", "audio/mpeg");
                item = MediaLib.copyInTarget(c, tg, out);
                if (item == null) { MediaLib.discard(c, tg); throw new IllegalStateException("publish"); }
            } finally {
                out.delete();
            }
        } else if (wavFmt) {
            MediaLib.Target tg = MediaLib.create(c, name + ".wav", "audio/x-wav");
            item = MediaLib.copyInTarget(c, tg, in);
            if (item == null) { MediaLib.discard(c, tg); throw new IllegalStateException("publish"); }
        } else {
            File out = new File(dir(c), "e" + System.nanoTime() + "." + fmt);
            try {
                encode(in, inf, out, fmt, p, new double[] { 0, 0.95 }, cancel, a.optInt("kbps", 0));
                MediaLib.Target tg = MediaLib.create(c, name + "." + fmt, mime(fmt));
                item = MediaLib.copyInTarget(c, tg, out);
                if (item == null) { MediaLib.discard(c, tg); throw new IllegalStateException("publish"); }
            } finally {
                out.delete();
            }
        }
        if (item != null && (a.has("title") || a.has("artist"))) MediaLib.tags(c, item, a.optString("title", ""), a.optString("artist", ""));
        JSONObject res = new JSONObject();
        res.put("item", item);
        if (a.has("replace")) res.put("replaced", MediaLib.deleteOwn(c, a.getJSONObject("replace")));
        return res;
    }
}
