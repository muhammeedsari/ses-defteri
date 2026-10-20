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
        File out = newFile(c);
        JSONObject rep = new JSONObject();
        int ch = inf.ch, fs = inf.rate;
        try {
            if ("insert".equals(op)) return insert(c, in, inf, out, a, p, cancel);
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
                    Analyzer.Analysis an = analyze(in, inf, p, cancel);
                    double nr = Math.max(6, Math.min(18, (42 - an.snr) * 0.55 + 6));
                    Spectral s = new Spectral(ch, fs).noise(an.noisePower, nr, 1.0);
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
                    Stages.PlosiveTamer pt = new Stages.PlosiveTamer(ch, fs, 1.8);
                    m = new Meter(ch, fs);
                    hp.to(pt).to(m);
                    head = hp;
                    plos.set(pt);
                    slice = new double[] { 0, 1 };
                    break;
                }
                case "deess": {
                    Analyzer.Analysis an = analyze(in, inf, p, cancel);
                    Stages.DeEsser d = new Stages.DeEsser(ch, fs, Math.pow(10, (an.speechDb - 12) / 20), 9);
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
                    double[] g = new double[Enhancer.BANDS.length];
                    double mean = 0;
                    for (int i = 0; i < g.length; i++) {
                        double s = 0, w = 0;
                        for (int j = Math.max(0, i - 1); j <= Math.min(g.length - 1, i + 1); j++) { double ww = j == i ? 2 : 1; s += (target[j] - measured[j]) * ww; w += ww; }
                        double v = 0.8 * s / w, max = Enhancer.BANDS[i] < 160 ? 0 : Enhancer.BANDS[i] > 8000 ? 6 : 8;
                        g[i] = Math.max(-8, Math.min(max, v));
                        mean += Math.abs(g[i]) / g.length;
                    }
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
                    m = new Meter(ch, fs);
                    head = new Stages.Compressor(ch, fs, an.speechMeanDb + 2, 2.5);
                    head.to(m);
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
                case "mix":
                    return mix(c, in, inf, out, a, p, cancel);
                default:
                    throw new IllegalArgumentException("op " + op);
            }
            m.to(new Studio.WavNode(out, fs));
            wav(in, inf, p, slice, cancel).run(head);
            Analyzer after = tees.get();
            if (after != null) { rep.put("after", Math.round(after.finish().noiseDb)); tees.remove(); }
            Stages.ClickRepair cr = clicks.get();
            if (cr != null) { rep.put("count", cr.repaired); clicks.remove(); }
            Stages.PlosiveTamer pt = plos.get();
            if (pt != null) { rep.put("seconds", Math.round(pt.active * 10.0 / fs) / 10.0); plos.remove(); }
            Stages.DeEsser d = dees.get();
            if (d != null) { rep.put("seconds", Math.round(d.active * 10.0 / fs) / 10.0); dees.remove(); }
            rep.remove("_after");
            return done(out, m, rep);
        } catch (Exception e) {
            out.delete();
            throw e;
        }
    }

    // per-job holders for the stage objects whose counters go into the report
    private static final ThreadLocal<Analyzer> tees = new ThreadLocal<>();
    private static final ThreadLocal<Stages.ClickRepair> clicks = new ThreadLocal<>();
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
        Studio.Fmt f = Studio.probe(c, src);
        File out = newFile(c);
        Node tail = new Studio.WavNode(out, rate);
        Node head = tail;
        if (f.rate != rate) { Resampler r = new Resampler(ch, f.rate, rate); r.to(head); head = r; }
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
            if (fm.rate != inf.rate) { Resampler r = new Resampler(mch, fm.rate, inf.rate); r.to(mt); head = r; }
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

    /* ---------- save the current step as a real file ---------- */

    static JSONObject save(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        File in = new File(a.getString("path"));
        Info inf = info(in);
        String name = Studio.safe(a.optString("name", "Ses"));
        boolean wavFmt = "wav".equals(a.optString("format", "m4a"));
        JSONObject item;
        if (wavFmt) {
            MediaLib.Target tg = MediaLib.create(c, name + ".wav", "audio/x-wav");
            item = MediaLib.copyInTarget(c, tg, in);
            if (item == null) { MediaLib.discard(c, tg); throw new IllegalStateException("publish"); }
        } else {
            File out = new File(dir(c), "e" + System.nanoTime() + ".m4a");
            try {
                Studio.EncNode enc = new Studio.EncNode(out, inf.rate, inf.ch > 1 ? 192000 : 128000);
                wav(in, inf, p, new double[] { 0, 0.95 }, cancel).run(enc);
                MediaLib.Target tg = MediaLib.create(c, name + ".m4a", "audio/mp4");
                item = MediaLib.copyInTarget(c, tg, out);
                if (item == null) { MediaLib.discard(c, tg); throw new IllegalStateException("publish"); }
            } finally {
                out.delete();
            }
        }
        JSONObject res = new JSONObject();
        res.put("item", item);
        if (a.has("replace")) res.put("replaced", MediaLib.deleteOwn(c, a.getJSONObject("replace")));
        return res;
    }
}
