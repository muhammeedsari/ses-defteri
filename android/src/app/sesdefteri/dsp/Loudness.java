package app.sesdefteri.dsp;

/**
 * Integrated loudness (ITU-R BS.1770-4 / EBU R128): K-weighting, 400 ms blocks with 75 % overlap,
 * absolute gate at -70 LUFS and relative gate at -10 LU. Also a pass-through Node so it can sit in a chain.
 */
public final class Loudness extends Node {
    private final int ch;
    private final Biquad shelf, hp;
    private final int subLen;
    private double acc;
    private int cnt;
    private double[] subs = new double[256];
    private int nSubs;
    private float[] tmp = new float[4096];

    public Loudness(int channels, double fs) {
        ch = channels;
        shelf = new Biquad(channels).setHighShelf(fs, 1500.0, 1 / Math.sqrt(2), 4.0);
        hp = new Biquad(channels).setHighpass(fs, 38.0, 0.5);
        subLen = (int) Math.round(fs * 0.1);
    }

    @Override public void push(float[][] b, int n) throws Exception {
        measure(b, n);
        if (next != null) next.push(b, n);
    }

    public void measure(float[][] b, int n) {
        if (tmp.length < n) tmp = new float[n];
        int done = 0;
        while (done < n) {
            int take = Math.min(n - done, subLen - cnt);
            for (int c = 0; c < ch; c++) {
                System.arraycopy(b[c], done, tmp, 0, take);
                shelf.process(c, tmp, 0, take);
                hp.process(c, tmp, 0, take);
                double s = 0;
                for (int i = 0; i < take; i++) s += (double) tmp[i] * tmp[i];
                acc += s;
            }
            cnt += take;
            done += take;
            if (cnt == subLen) {
                if (nSubs == subs.length) subs = java.util.Arrays.copyOf(subs, nSubs * 2);
                subs[nSubs++] = acc / subLen;
                acc = 0;
                cnt = 0;
            }
        }
    }

    /** integrated loudness in LUFS; -Infinity if everything is below the absolute gate */
    public double integrated() {
        int nb = nSubs - 3;
        if (nb <= 0) return Double.NEGATIVE_INFINITY;
        double[] z = new double[nb];
        for (int j = 0; j < nb; j++) z[j] = (subs[j] + subs[j + 1] + subs[j + 2] + subs[j + 3]) / 4;
        double sum = 0; int k = 0;
        for (double v : z) if (lufs(v) > -70) { sum += v; k++; }
        if (k == 0) return Double.NEGATIVE_INFINITY;
        double rel = lufs(sum / k) - 10;
        sum = 0; k = 0;
        for (double v : z) if (lufs(v) > -70 && lufs(v) > rel) { sum += v; k++; }
        return k == 0 ? Double.NEGATIVE_INFINITY : lufs(sum / k);
    }

    /** loudness range (EBU Tech 3342) in LU, from 3 s short-term blocks */
    public double range() {
        int nb = nSubs - 29;
        if (nb <= 0) return 0;
        double[] st = new double[nb];
        double run = 0;
        for (int i = 0; i < 30; i++) run += subs[i];
        for (int j = 0; j < nb; j++) {
            st[j] = run / 30;
            if (j + 30 < nSubs) run += subs[j + 30] - subs[j];
        }
        java.util.ArrayList<Double> keep = new java.util.ArrayList<>();
        double sum = 0;
        for (double v : st) if (lufs(v) > -70) { keep.add(lufs(v)); sum += v; }
        if (keep.isEmpty()) return 0;
        double rel = lufs(sum / keep.size()) - 20;
        java.util.ArrayList<Double> g = new java.util.ArrayList<>();
        for (double l : keep) if (l > rel) g.add(l);
        if (g.size() < 2) return 0;
        java.util.Collections.sort(g);
        return g.get((int) Math.round(0.95 * (g.size() - 1))) - g.get((int) Math.round(0.10 * (g.size() - 1)));
    }

    static double lufs(double ms) { return ms <= 0 ? Double.NEGATIVE_INFINITY : -0.691 + 10 * Math.log10(ms); }

    public static double of(float[][] x, double fs) {
        Loudness l = new Loudness(x.length, fs);
        int len = x[0].length;
        float[][] b = new float[x.length][4096];
        for (int p = 0; p < len; p += 4096) {
            int n = Math.min(4096, len - p);
            for (int c = 0; c < x.length; c++) System.arraycopy(x[c], p, b[c], 0, n);
            l.measure(b, n);
        }
        return l.integrated();
    }
}
