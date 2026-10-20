import app.sesdefteri.dsp.*;
import java.util.*;

/** End-to-end checks of the automatic enhancement on recordings with known problems. */
public class T2Enhance extends TestUtil {
    static int fs = 48000;

    static class Out { float[][] y; Pipeline.Result r; }

    static Out run(float[][] x, boolean voice, String target, double[] learned) throws Exception {
        Enhancer.Options o = new Enhancer.Options();
        o.voice = voice; o.target = target; o.learnedBands = learned;
        Node.Collect col = new Node.Collect(x.length);
        Out out = new Out();
        out.r = Pipeline.enhance(x, fs, o, col);
        out.y = col.result();
        System.out.println("    plan " + out.r.plan.notes + "  report " + out.r.json.replaceAll(",\"tone\".*", "}"));
        return out;
    }

    static double[][] segs() throws Exception {
        String s = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(W + "t_segments.json")), "UTF-8");
        ArrayList<double[]> l = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[([0-9.]+), ([0-9.]+),").matcher(s);
        while (m.find()) l.add(new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) });
        return l.toArray(new double[0][]);
    }

    /** energy (dB) of x in [a,b) seconds, with the output shifted by trim */
    static double energy(float[] x, double a, double b, double shift) {
        int i0 = (int) Math.max(0, (a - shift) * fs), i1 = (int) Math.min(x.length, (b - shift) * fs);
        return db(rms(x, i0, i1));
    }

    static float[] band(float[] x, double lo, double hi) {
        float[] y = x.clone();
        if (lo > 0) { Biquad a = Biquad.highpass(1, fs, lo, 0.7071), b = Biquad.highpass(1, fs, lo, 0.7071); a.process(0, y, 0, y.length); b.process(0, y, 0, y.length); }
        if (hi > 0) { Biquad a = Biquad.lowpass(1, fs, hi, 0.7071), b = Biquad.lowpass(1, fs, hi, 0.7071); a.process(0, y, 0, y.length); b.process(0, y, 0, y.length); }
        return y;
    }

    /** gaps between sentences (seconds), shrunk by 0.15 s on both sides */
    static double[][] gaps(double[][] s) {
        ArrayList<double[]> g = new ArrayList<>();
        for (int i = 0; i + 1 < s.length; i++) if (s[i + 1][0] - s[i][1] > 0.5) g.add(new double[] { s[i][1] + 0.15, s[i + 1][0] - 0.15 });
        return g.toArray(new double[0][]);
    }

    static double avgEnergy(float[] x, double[][] rs, double shift) {
        double e = 0, n = 0;
        for (double[] r : rs) { int i0 = (int) ((r[0] - shift) * fs), i1 = (int) ((r[1] - shift) * fs); for (int i = Math.max(0, i0); i < Math.min(x.length, i1); i++) { e += x[i] * x[i]; n++; } }
        return 10 * Math.log10(e / Math.max(1, n) + 1e-20);
    }

    public static void main(String[] args) throws Exception {
        double[][] S = segs();
        double[][] G = gaps(S);
        Wav clean = wav("t_clean.wav");

        System.out.println("[clean speech]");
        Out c = run(clean.data, true, "youtube", null);
        double tp = TruePeak.db(TruePeak.measure(c.y));
        double L = Loudness.of(c.y, fs);
        check("loudness = -14 LUFS (YouTube)", Math.abs(L + 14) < 0.5, f(L));
        check("true peak <= -1 dBTP", tp <= -0.95, f(tp));
        check("no noise reduction on clean audio", c.r.plan.noisePower == null, "");
        check("no de-esser on natural speech", c.r.plan.deessThr < 0, "sib98 " + f(c.r.plan.analysis.sibP98));
        check("no hum detected", c.r.plan.notches.length == 0, "");
        double ts = (double) c.r.plan.trimStart / fs, te = (double) (c.r.plan.analysis.samples - c.r.plan.trimEnd) / fs;
        double lead = S[0][0], tail = clean.data[0].length / (double) fs - S[S.length - 1][1];
        check("leading silence trimmed to ~0.3 s", Math.abs((lead - ts) - 0.3) < 0.12, "removed " + f(ts) + " s of " + f(lead));
        check("trailing silence trimmed to ~0.5 s", Math.abs((tail - te) - 0.5) < 0.15, "removed " + f(te) + " s of " + f(tail));
        check("length = original - trims", c.y[0].length == c.r.plan.trimEnd - c.r.plan.trimStart, "");

        System.out.println("[podcast / whatsapp targets]");
        Out pc = run(clean.data, true, "podcast", null);
        double Lp = Loudness.of(pc.y, fs);
        check("podcast = -16 LUFS", Math.abs(Lp + 16) < 0.5, f(Lp));
        Wav dm = wav("t_dualmono.wav");
        Out wa = run(dm.data, true, "whatsapp", null);
        double Lw = Loudness.of(wa.y, fs), tw = TruePeak.db(TruePeak.measure(wa.y));
        check("whatsapp = -16 LUFS, mono, <= -1.5 dBTP", Math.abs(Lw + 16) < 0.5 && wa.y.length == 1 && tw <= -1.45, f(Lw) + " LUFS, " + wa.y.length + " ch, " + f(tw) + " dBTP");

        System.out.println("[noisy speech: pink noise at 15 dB SNR + 50 Hz hum]");
        Wav noisy = wav("t_noisy.wav");
        Out nz = run(noisy.data, true, "youtube", null);
        check("noise reduction switched on", nz.r.plan.noisePower != null, "depth " + f(nz.r.plan.nrDb) + " dB");
        double shift = (double) nz.r.plan.trimStart / fs;
        // compare noise in gaps relative to speech level, before and after
        double inSpeech = avgEnergy(noisy.data[0], S, 0), inGap = avgEnergy(noisy.data[0], G, 0);
        double outSpeech = avgEnergy(nz.y[0], S, shift), outGap = avgEnergy(nz.y[0], G, shift);
        double before = inSpeech - inGap, after = outSpeech - outGap;
        check("speech-to-noise in pauses improved >= 10 dB", after - before >= 10, f(before) + " dB -> " + f(after) + " dB");
        // speech fidelity of the noise-reduction step itself (zero-phase gains, so a waveform comparison is fair)
        Spectral nrOnly = new Spectral(1, fs).noise(nz.r.plan.noisePower, nz.r.plan.nrDb, nz.r.plan.overSub);
        float[][] den = Node.apply(noisy.data, nrOnly, nrOnly, 1);
        double sBefore = fidelity(clean.data[0], noisy.data[0], S, 0), sAfter = fidelity(clean.data[0], den[0], S, 0);
        check("speech itself gets cleaner (SNR vs clean reference +2.5 dB)", sAfter - sBefore >= 2.5, f(sBefore) + " dB -> " + f(sAfter) + " dB");
        System.out.println("[mains hum on a quiet recording]");
        Wav hum = wav("t_hum.wav");
        Out ho = run(hum.data, true, "youtube", null);
        check("50 Hz hum detected", ho.r.plan.notches.length > 0 && ho.r.plan.notches[0] == 50, Arrays.toString(ho.r.plan.notches));
        double hs = (double) ho.r.plan.trimStart / fs;
        double gH = avgEnergy(ho.y[0], S, hs) - avgEnergy(hum.data[0], S, 0);
        double humIn = avgEnergy(band(hum.data[0], 40, 120), G, 0), humOut = avgEnergy(band(ho.y[0], 40, 120), G, hs) - gH;
        check("hum in pauses reduced >= 20 dB", humOut - humIn <= -20, f(humOut - humIn) + " dB");
        System.out.println("[clicks]");
        Wav ck = wav("t_clicks.wav");
        Out cko = run(ck.data, true, "youtube", null);
        int rep = cko.r.report.clicks.repaired;
        check("clicks repaired (40 injected)", rep >= 36 && rep <= 60, rep + " repaired");
        Out cleanClicks = c;
        check("few false repairs on clean speech", c.r.report.clicks.repaired <= 15, c.r.report.clicks.repaired + " on 107 s of clean speech");
        // residual at click positions, repair step alone (compared with the clean original)
        int[] pos = readInts("t_clicks.json");
        Stages.ClickRepair crOnly = new Stages.ClickRepair(1, fs, 10);
        float[][] fixd = Node.apply(ck.data, crOnly, crOnly, 1);
        double eDiff = 0, eClk = 0;
        for (int p : pos) for (int i = p - 3; i < p + 6; i++) { eDiff += Math.pow(fixd[0][i] - clean.data[0][i], 2); eClk += Math.pow(ck.data[0][i] - clean.data[0][i], 2); }
        check("click energy removed >= 20 dB", 10 * Math.log10(eClk / eDiff) >= 20, f(10 * Math.log10(eClk / eDiff)) + " dB");
        System.out.println("[plosive pops]");
        Wav pl = wav("t_plosive.wav");
        Out plo = run(pl.data, true, "youtube", null);
        check("plosive tamer active", plo.r.report.plosive != null && plo.r.report.plosive.active > 0, "");
        int[] pp = readInts("t_plosive.json");
        float[] procP = plosiveChain(pl.data[0]), procC = plosiveChain(clean.data[0]);
        // low-band level at each pop compared with the same spot without the pop
        float[] lP0 = band(pl.data[0], 0, 160), lC0 = band(clean.data[0], 0, 160), lP = band(procP, 0, 160), lC = band(procC, 0, 160);
        double e0 = 0, c0 = 0, e1 = 0, c1 = 0;
        for (int p : pp) for (int i = p; i < p + 2880; i++) { e0 += lP0[i] * lP0[i]; c0 += lC0[i] * lC0[i]; e1 += lP[i] * lP[i]; c1 += lC[i] * lC[i]; }
        double exB = 10 * Math.log10(e0 / c0), exA = 10 * Math.log10(e1 / c1);
        check("pops: low-band excess cut to <= 3 dB", exA <= 3, "excess " + f(exB) + " dB -> " + f(exA) + " dB");
        float[] bIn = band(clean.data[0], 120, 300), bOut = band(procC, 120, 300);
        double vi = 0, vo = 0;
        for (double[] sg : S) for (int i = (int) (sg[0] * fs); i < (int) (sg[1] * fs); i++) { vi += bIn[i] * bIn[i]; vo += bOut[i] * bOut[i]; }
        check("tamer rarely acts on clean speech", plosiveActive(clean.data[0]) < fs * 0.5, "active " + f(plosiveActive(clean.data[0]) / (double) fs) + " s of 107 s");
        check("voice body (120-300 Hz) kept within 1 dB", Math.abs(10 * Math.log10(vo / vi)) < 1, f(10 * Math.log10(vo / vi)) + " dB");

        System.out.println("[harsh s/sh]");
        Wav sb = wav("t_sibilant.wav");
        Out sbo = run(sb.data, true, "youtube", null);
        check("de-esser switched on for harsh sibilants", sbo.r.plan.deessThr > 0, "sib98 " + f(sbo.r.plan.analysis.sibP98));
        Stages.DeEsser de = new Stages.DeEsser(1, fs, sbo.r.plan.deessThr, sbo.r.plan.deessMaxDb);
        float[][] des = Node.apply(sb.data, de, de, 1);
        double hiIn = sibLevel(sb.data[0], S), hiOut = sibLevel(des[0], S), hiClean = sibLevel(clean.data[0], S);
        check("loud s peaks pulled down >= 5 dB toward natural", hiIn - hiOut >= 5 && hiOut > hiClean - 3, f(hiIn) + " -> " + f(hiOut) + " dBFS (natural " + f(hiClean) + ")");
        System.out.println("[channels]");
        String[][] cases = { { "t_dualmono.wav", "identical" }, { "t_onesilent.wav", "right-silent" }, { "t_onebroken.wav", "left-broken" }, { "t_inverted.wav", "inverted" } };
        for (String[] cs : cases) {
            Wav w = wav(cs[0]);
            Analyzer an = new Analyzer(2, fs); Node.run(w.data, an, 4096);
            Analyzer.Analysis a = an.finish();
            check(cs[0] + " -> " + cs[1], cs[1].equals(a.channelReason), "got '" + a.channelReason + "' corr " + f(a.corr) + " flat " + f(a.flatL) + "/" + f(a.flatR));
        }
        Wav inv = wav("t_inverted.wav");
        Out io = run(inv.data, true, "youtube", null);
        check("inverted stereo becomes full-level mono", io.y.length == 1 && Math.abs(Loudness.of(io.y, fs) + 14) < 0.5, io.y.length + " ch");
        Wav mu = wav("t_music120.wav");
        Analyzer an = new Analyzer(2, fs); Node.run(mu.data, an, 4096);
        check("real stereo music stays stereo", an.finish().channelMode == Stages.ChannelMap.KEEP, "");

        System.out.println("[tone / Auto-EQ]");
        Analyzer ac = new Analyzer(1, fs); Node.run(clean.data, ac, 4096);
        Analyzer.Analysis aClean = ac.finish();
        double[] cleanTone = Enhancer.bandsOf(aClean.ltasPower, fs, aClean.fftSize);
        for (String fn : new String[] { "t_muffled.wav", "t_bright.wav" }) {
            Wav m = wav(fn);
            Out mo = run(m.data, true, "youtube", cleanTone);         // learned profile = the speaker's normal tone
            double[] tb = mo.r.plan.measuredBands;
            Analyzer a2 = new Analyzer(1, fs); Node.run(mo.y, a2, 4096);
            Analyzer.Analysis aa = a2.finish();
            double[] real = Enhancer.bandsOf(aa.ltasPower, fs, aa.fftSize);
            double dB = dev(tb, cleanTone), dA = dev(real, cleanTone);
            check(fn + ": tone moved toward the learned voice", mo.r.plan.eqBandsDb != null && dA < dB * 0.5, "mean deviation " + f(dB) + " dB -> " + f(dA) + " dB");
        }
        Out same = run(clean.data, true, "youtube", cleanTone);
        check("no EQ when tone already matches", same.r.plan.eqBandsDb == null, "");

        System.out.println("[levelling]");
        float[] uneven = clean.data[0].clone();
        Random R = new Random(3);
        for (double[] s : S) { double gg = Math.pow(10, (R.nextDouble() * 16 - 8) / 20); for (int i = (int) (s[0] * fs); i < (int) (s[1] * fs); i++) uneven[i] *= gg; }
        Out ue = run(new float[][] { uneven }, true, "youtube", null);
        double stdIn = sentenceStd(uneven, S, 0), stdOut = sentenceStd(ue.y[0], S, (double) ue.r.plan.trimStart / fs);
        check("compressor switched on for uneven levels", ue.r.plan.compRatio > 1, "speech std " + f(ue.r.plan.analysis.speechStdDb));
        check("sentence level spread reduced >= 30 %", stdOut < stdIn * 0.7, f(stdIn) + " dB -> " + f(stdOut) + " dB");
        done();
    }

    static double dev(double[] a, double[] b) {
        double s = 0; int n = 0;
        for (int i = 0; i < a.length; i++) if (Enhancer.BANDS[i] >= 160 && Enhancer.BANDS[i] <= 8000) { s += Math.abs(a[i] - b[i]); n++; }
        return s / n;
    }

    static long plosiveActive(float[] x) throws Exception {
        Stages.Filters fl = new Stages.Filters(1, fs, 75, null);
        Stages.PlosiveTamer pt = new Stages.PlosiveTamer(1, fs, 1.8);
        fl.to(pt);
        Node.apply(new float[][] { x }, fl, pt, 1);
        return pt.active;
    }

    static float[] plosiveChain(float[] x) throws Exception {
        Stages.Filters fl = new Stages.Filters(1, fs, 75, null);
        Stages.PlosiveTamer pt = new Stages.PlosiveTamer(1, fs, 1.8);
        fl.to(pt);
        return Node.apply(new float[][] { x }, fl, pt, 1)[0];
    }

    /** 95th percentile level (dBFS) of 5 ms windows in the 5-10 kHz band inside sentences */
    static double sibLevel(float[] x, double[][] S) {
        float[] hi = band(x, 5000, 10000);
        ArrayList<Double> r = new ArrayList<>();
        for (double[] s : S) for (int i = (int) (s[0] * fs); i + 240 < (int) (s[1] * fs); i += 240) r.add(db(rms(hi, i, i + 240)));
        Collections.sort(r);
        return r.get((int) (r.size() * 0.95));
    }

    static double sentenceStd(float[] x, double[][] S, double shift) {
        double[] l = new double[S.length];
        double m = 0;
        for (int i = 0; i < S.length; i++) { l[i] = avgEnergy(x, new double[][] { S[i] }, shift); m += l[i]; }
        m /= S.length;
        double v = 0;
        for (double d : l) v += (d - m) * (d - m);
        return Math.sqrt(v / S.length);
    }

    /** level (dB) of the loudest 5 % of 5 ms windows in the 5-10 kHz band relative to the full band */
    static double sibRatio(float[] x, double[][] S, double shift) {
        float[] hi = band(x, 5000, 10000);
        ArrayList<Double> r = new ArrayList<>();
        int w = 240;
        for (double[] s : S) for (int i = (int) ((s[0] - shift) * fs); i + w < (int) ((s[1] - shift) * fs); i += w) {
            double a = rms(hi, i, i + w), b = rms(x, i, i + w);
            if (b > 1e-4) r.add(db(a / b));
        }
        Collections.sort(r);
        return r.get((int) (r.size() * 0.95));
    }

    /** SNR (dB) of y against reference x inside sentences, after least-squares gain matching */
    static double fidelity(float[] x, float[] y, double[][] S, double shift) {
        double xy = 0, yy = 0;
        for (double[] s : S) for (int i = (int) (s[0] * fs); i < (int) (s[1] * fs); i++) { int j = i - (int) (shift * fs); xy += x[i] * y[j]; yy += y[j] * y[j]; }
        double g = xy / yy, e = 0, r = 0;
        for (double[] s : S) for (int i = (int) (s[0] * fs); i < (int) (s[1] * fs); i++) { int j = i - (int) (shift * fs); double d = x[i] - g * y[j]; e += d * d; r += x[i] * x[i]; }
        return 10 * Math.log10(r / e);
    }

    static double[] readDoubles(String f) throws Exception {
        String s = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(W + f)));
        String[] p = s.replaceAll("[\\[\\] ]", "").split(",");
        double[] r = new double[p.length];
        for (int i = 0; i < p.length; i++) r[i] = Double.parseDouble(p[i]);
        return r;
    }

    static int[] readInts(String f) throws Exception {
        String s = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(W + f)));
        String[] p = s.replaceAll("[\\[\\] ]", "").split(",");
        int[] r = new int[p.length];
        for (int i = 0; i < p.length; i++) r[i] = Integer.parseInt(p[i]);
        return r;
    }
}
