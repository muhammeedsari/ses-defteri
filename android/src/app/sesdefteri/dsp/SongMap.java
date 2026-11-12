package app.sesdefteri.dsp;

import java.util.ArrayList;
import java.util.List;

/**
 * 5.7: the chords and the sections (intro, verse, chorus, bridge, outro) of a song, without a model.
 * Chroma (60 Hz – 2 kHz) and bass chroma (40–250 Hz) from 8192-point spectra every 2048 samples, plus six band levels
 * for the sound's colour. Chords: per beat, 24 major / minor triads (bass on the root counts extra) smoothed with Viterbi.
 * Sections: bar features → self-similarity → novelty at the changes → segments grouped by how alike they sound; the most
 * repeated loud group is the chorus, other repeats are verses, the rest intro / bridge / outro by position.
 * Passes the audio on unchanged.
 */
public final class SongMap extends Node {
    // 5.14.3: the analysis always runs at 24 kHz (the file is resampled inside), so 8 kHz and 96 kHz files get the same
    // frequency and time resolution as 48 kHz ones had; half a frame of silence in front puts frame 0 on the very start
    static final int N = 4096, HOP = 1024, BANDS = 6;
    static final double R = 24000;
    static final String[] NAMES = { "C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B" };
    final Fft fft = new Fft(N);
    final double[] win = Fft.hann(N, false), re = new double[N], im = new double[N];
    final int[] pc, bpc, band;
    final float[] buf = new float[N];
    final double fs;
    int fill = N / 2;
    final Resampler rs;
    float[][] mono = new float[1][0];
    final List<double[]> frames = new ArrayList<>();   // 12 chroma, 12 bass, 6 bands, 1 level

    public SongMap(double inRate) {
        this.fs = R;
        if (Math.abs(inRate - R) > 0.5) {
            rs = new Resampler(1, inRate, R);
            rs.to(new Node() { @Override public void push(float[][] b, int n) { feed(b[0], n); } @Override public void end() { } });
        } else rs = null;
        pc = new int[N / 2]; bpc = new int[N / 2]; band = new int[N / 2];
        double[] edges = { 60, 250, 500, 1000, 2000, 4000, 12000 };
        for (int k = 0; k < N / 2; k++) {
            double f = k * fs / N;
            int p = f < 30 ? -1 : (int) Math.floorMod(Math.round(12 * Math.log(f / 440.0) / Math.log(2)) + 9L, 12L);
            pc[k] = f < 60 || f > 2000 ? -1 : p;
            bpc[k] = f < 40 || f > 250 ? -1 : p;
            band[k] = -1;
            for (int b = 0; b < BANDS; b++) if (f >= edges[b] && f < edges[b + 1]) band[k] = b;
        }
    }

    @Override public void push(float[][] b, int n) throws Exception {
        int ch = b.length;
        if (mono[0].length < n) mono = new float[1][n];
        float[] m = mono[0];
        for (int i = 0; i < n; i++) { float s = 0; for (int c = 0; c < ch; c++) s += b[c][i]; m[i] = s / ch; }
        if (rs != null) rs.push(mono, n); else feed(m, n);
        if (next != null) next.push(b, n);
    }

    private void feed(float[] m, int n) {
        for (int i = 0; i < n; i++) {
            buf[fill++] = m[i];
            if (fill == N) { frame(); System.arraycopy(buf, HOP, buf, 0, N - HOP); fill = N - HOP; }
        }
    }

    private void frame() {
        for (int k = 0; k < N; k++) { re[k] = buf[k] * win[k]; im[k] = 0; }
        fft.transform(re, im, false);
        double[] v = new double[12 + 12 + BANDS + 1];
        double tot = 0;
        for (int k = 1; k < N / 2; k++) {
            double p = re[k] * re[k] + im[k] * im[k], m = Math.sqrt(Math.sqrt(p));
            if (pc[k] >= 0) v[pc[k]] += m;
            if (bpc[k] >= 0) v[12 + bpc[k]] += m;
            if (band[k] >= 0) v[24 + band[k]] += p;
            tot += p;
        }
        for (int b = 0; b < BANDS; b++) v[24 + b] = 10 * Math.log10(v[24 + b] + 1e-9);
        v[24 + BANDS] = 10 * Math.log10(tot + 1e-9);
        frames.add(v);
    }

    /** frame index → its centre in seconds */
    double ft(int i) { return i * HOP / fs; }

    public static final class Result {
        public final List<Object[]> chords = new ArrayList<>();     // {time, name}
        public final List<Object[]> sections = new ArrayList<>();   // {start, end, kind, group}
    }

    /** beats in seconds (may be empty: then a steady 0.5 s grid), dur the length in seconds */
    public Result finish(double[] beats, double dur) {
        Result res = new Result();
        int nf = frames.size();
        if (nf < 8) return res;
        // the beat grid, with its edges
        List<Double> g = new ArrayList<>();
        if (beats == null || beats.length < 8) for (double x = 0; x < dur; x += 0.5) g.add(x);
        else { if (beats[0] > 0.3) g.add(0.0); for (double x : beats) if (x < dur) g.add(x); }
        g.add(dur);
        int nb = g.size() - 1;
        double[][] bf = new double[nb][];
        int fi = 0;
        double top = -200;
        for (double[] v : frames) top = Math.max(top, v[24 + BANDS]);
        for (int b = 0; b < nb; b++) {
            double a = g.get(b), e = g.get(b + 1);
            double[] s = new double[frames.get(0).length];
            int m = 0;
            while (fi < nf && ft(fi) < a) fi++;
            for (int i = fi; i < nf && ft(i) < e; i++, m++) { double[] v = frames.get(i); for (int k = 0; k < s.length; k++) s[k] += v[k]; }
            if (m == 0) { int i = Math.min(nf - 1, fi); System.arraycopy(frames.get(i), 0, s, 0, s.length); m = 1; }
            for (int k = 0; k < s.length; k++) s[k] /= m;
            bf[b] = s;
        }
        // ---- chords
        final int S = 25;   // 24 triads + no chord
        double[][] em = new double[nb][S];
        for (int b = 0; b < nb; b++) {
            double[] v = bf[b];
            double cs = 0, bs = 0;
            for (int k = 0; k < 12; k++) { cs += v[k]; bs += v[12 + k]; }
            boolean quiet = v[24 + BANDS] < top - 35 || cs <= 0;
            for (int r = 0; r < 12; r++) for (int q = 0; q < 2; q++) {
                double c0 = v[r] / cs, c1 = v[(r + (q == 0 ? 4 : 3)) % 12] / cs, c2 = v[(r + 7) % 12] / cs;
                double off = 0;
                for (int k = 0; k < 12; k++) off += v[k] / cs;
                off = (off - c0 - c1 - c2) / 9;
                double bass = bs > 0 ? v[12 + r] / bs : 0;
                em[b][r * 2 + q] = quiet ? -1 : (c0 + c1 + c2) / 3 - off + 0.25 * bass - (q == 1 ? 0.002 : 0);
            }
            em[b][24] = quiet ? 1 : 0.02;
        }
        int[] path = viterbi(em, 0.04);
        String last = null;
        for (int b = 0; b < nb; b++) {
            int s = path[b];
            String nm = s == 24 ? "" : NAMES[s / 2] + (s % 2 == 1 ? "m" : "");
            if (!nm.equals(last)) { res.chords.add(new Object[] { Math.round(g.get(b) * 100) / 100.0, nm }); last = nm; }
        }
        // ---- sections, on bars of 4 beats
        int nbar = nb / 4;
        if (nbar < 12) { res.sections.add(new Object[] { 0.0, dur, "verse", 0 }); return res; }
        double[][] x = new double[nbar][12 + BANDS];
        double[] lvl = new double[nbar];
        double[] mu = new double[BANDS], sd = new double[BANDS];
        for (int i = 0; i < nbar; i++) {
            double[] c = new double[30];
            for (int j = 0; j < 4; j++) for (int k = 0; k < 30; k++) c[k] += bf[i * 4 + j][k];
            double nn = 0;
            for (int k = 0; k < 12; k++) nn += c[k] * c[k];
            nn = Math.sqrt(nn) + 1e-12;
            for (int k = 0; k < 12; k++) x[i][k] = c[k] / nn;
            for (int k = 0; k < BANDS; k++) { x[i][12 + k] = c[24 + k] / 4; mu[k] += x[i][12 + k] / nbar; }
            double l = 0; for (int j = 0; j < 4; j++) l += bf[i * 4 + j][24 + BANDS]; lvl[i] = l / 4;
        }
        for (int i = 0; i < nbar; i++) for (int k = 0; k < BANDS; k++) sd[k] += Math.pow(x[i][12 + k] - mu[k], 2) / nbar;
        for (int k = 0; k < BANDS; k++) sd[k] = Math.sqrt(sd[k]) + 1e-6;
        for (int i = 0; i < nbar; i++) for (int k = 0; k < BANDS; k++) x[i][12 + k] = (x[i][12 + k] - mu[k]) / sd[k] * 0.25;
        double[][] ss = new double[nbar][nbar];
        double mean = 0;
        for (int i = 0; i < nbar; i++) for (int j = 0; j < nbar; j++) {
            double d = 0;
            for (int k = 0; k < 12 + BANDS; k++) d += (x[i][k] - x[j][k]) * (x[i][k] - x[j][k]);
            ss[i][j] = Math.exp(-d);
            mean += ss[i][j];
        }
        mean /= (double) nbar * nbar;
        // novelty: a checkerboard kernel of 4 + 4 bars along the diagonal
        final int W = 4;
        double[] nov = new double[nbar + 1];
        for (int t = 1; t < nbar; t++) {
            double s = 0;
            for (int a = -W; a < W; a++) for (int b = -W; b < W; b++) {
                int i = t + a, j = t + b;
                if (i < 0 || j < 0 || i >= nbar || j >= nbar) continue;
                double w = Math.exp(-((a + 0.5) * (a + 0.5) + (b + 0.5) * (b + 0.5)) / (2.0 * W * W / 4));
                s += ((a < 0) == (b < 0) ? w : -w) * ss[i][j];
            }
            nov[t] = s;
        }
        double nm = 0, nsd = 0;
        for (int t = 1; t < nbar; t++) nm += nov[t] / (nbar - 1);
        for (int t = 1; t < nbar; t++) nsd += Math.pow(nov[t] - nm, 2) / (nbar - 1);
        double thr = nm + 0.3 * Math.sqrt(nsd);
        List<Integer> cuts = new ArrayList<>();
        cuts.add(0);
        for (int t = 2; t < nbar - 1; t++) {
            boolean peak = nov[t] > thr;
            for (int d = -3; d <= 3 && peak; d++) if (d != 0 && t + d > 0 && t + d < nbar && nov[t + d] > nov[t]) peak = false;
            if (peak && t - cuts.get(cuts.size() - 1) >= 4 && nbar - t >= 2) cuts.add(t);
        }
        cuts.add(nbar);
        int ns = cuts.size() - 1;
        // groups: start-aligned similarity of two segments against the song's average
        int[] grp = new int[ns];
        int ng = 0;
        for (int s = 0; s < ns; s++) {
            grp[s] = -1;
            double best = 0;
            for (int r = 0; r < s; r++) {
                if (grp[r] == -1) continue;
                int a0 = cuts.get(r), b0 = cuts.get(s), L = Math.min(cuts.get(r + 1) - a0, cuts.get(s + 1) - b0);
                double v = 0;
                for (int k = 0; k < L; k++) v += ss[a0 + k][b0 + k];
                v = v / L;
                double lr = (cuts.get(r + 1) - a0) / (double) (cuts.get(s + 1) - b0);
                if (lr > 2.2 || lr < 1 / 2.2) continue;
                if (v > best && v > mean + 0.6 * (1 - mean)) { best = v; grp[s] = grp[r]; }
            }
            if (grp[s] == -1) grp[s] = ng++;
        }
        // naming
        int[] cnt = new int[ng];
        double[] gl = new double[ng];
        for (int s = 0; s < ns; s++) {
            cnt[grp[s]]++;
            double l = 0; for (int i = cuts.get(s); i < cuts.get(s + 1); i++) l += lvl[i];
            gl[grp[s]] += l / (cuts.get(s + 1) - cuts.get(s));
        }
        int chorus = -1;
        double cb = -1e9;
        for (int q = 0; q < ng; q++) if (cnt[q] >= 2) { double sc = gl[q] / cnt[q] + 1.5 * cnt[q]; if (sc > cb) { cb = sc; chorus = q; } }
        for (int s = 0; s < ns; s++) {
            int q = grp[s];
            String kind;
            if (q == chorus) kind = "chorus";
            else if (cnt[q] >= 2 && !(cnt[q] == 2 && grp[0] == q && grp[ns - 1] == q)) kind = "verse";   // the same sound only at both ends: intro and outro
            else if (s == 0) kind = "intro";
            else if (s == ns - 1) kind = "outro";
            else kind = chorus < 0 ? "verse" : "bridge";
            double a = g.get(cuts.get(s) * 4), e = s == ns - 1 ? dur : g.get(cuts.get(s + 1) * 4);
            res.sections.add(new Object[] { Math.round(a * 100) / 100.0, Math.round(e * 100) / 100.0, kind, q });
        }
        return res;
    }

    static int[] viterbi(double[][] em, double pen) {
        int n = em.length, S = em[0].length;
        double[] d = em[0].clone();
        int[][] bk = new int[n][S];
        for (int t = 1; t < n; t++) {
            int bi = 0;
            for (int s = 1; s < S; s++) if (d[s] > d[bi]) bi = s;
            double[] nd = new double[S];
            for (int s = 0; s < S; s++) {
                double stay = d[s], jump = d[bi] - pen;
                if (stay >= jump) { nd[s] = stay + em[t][s]; bk[t][s] = s; } else { nd[s] = jump + em[t][s]; bk[t][s] = bi; }
            }
            d = nd;
        }
        int[] p = new int[n];
        int s = 0;
        for (int k = 1; k < S; k++) if (d[k] > d[s]) s = k;
        for (int t = n - 1; t >= 0; t--) { p[t] = s; s = bk[t][s]; }
        return p;
    }
}
