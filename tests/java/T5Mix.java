import app.sesdefteri.dsp.*;
import java.io.File;

/** Background music under speech with automatic ducking. */
public class T5Mix extends TestUtil {
    static final int fs = 48000;

    static Pipeline.FileTemp bed(Wav m) throws Exception {
        Pipeline.FileTemp t = new Pipeline.FileTemp(File.createTempFile("bed", ".pcm"), m.data.length);
        Node.run(m.data, t, 4096);
        return t;
    }

    public static void main(String[] a) throws Exception {
        final Wav v = wav("t_clean.wav");
        Wav m = wav("t_bed.wav");
        double[][] S = T2Enhance.segs();
        Node.Collect col = new Node.Collect(2);
        Pipeline.Result r = Pipeline.mix(new Pipeline.Source() { public void run(Node s) throws Exception { Node.run(v.data, s, 4096); } },
                1, fs, bed(m), -6, -14, "youtube", File.createTempFile("mix", ".pcm"), col, null);
        float[][] y = col.result();
        check("output stereo (music is stereo), same length as voice", y.length == 2 && y[0].length == v.data[0].length, y.length + " ch, " + y[0].length);
        double L = Loudness.of(y, fs), tp = TruePeak.db(TruePeak.measure(y));
        check("mix at -14 LUFS, <= -1 dBTP", Math.abs(L + 14) < 0.5 && tp <= -0.95, f(L) + " LUFS, " + f(tp) + " dBTP");
        float[] g = r.mix.gainTrace.a;
        int gn = r.mix.gainTrace.n;
        double inS = 0, nS = 0, inG = 0, nG = 0;
        for (double[] s : S) for (int i = (int) (s[0] * 100) + 30; i < (int) (s[1] * 100) - 10 && i < gn; i++) { inS += g[i]; nS++; }
        double[][] G = T2Enhance.gaps(S);
        for (double[] s : G) if (s[1] - s[0] > 1.5) for (int i = (int) ((s[0] + 0.9) * 100); i < (int) ((s[1] - 0.35) * 100) && i < gn; i++) { inG += g[i]; nG++; }
        double duckDepth = inS / nS - inG / Math.max(1, nG);
        check("music ducked ~14 dB under speech", Math.abs(duckDepth + 14) < 2.5, f(duckDepth) + " dB (" + (int) nG + " gap samples)");
        int early = 0;
        for (double[] s : S) { int i = (int) (s[0] * 100); if (i < gn && g[i] < -6) early++; }
        check("music already down when each sentence starts (look-ahead)", early >= S.length - 1, early + " of " + S.length);
        // voice is not delayed relative to the original
        int best = 0; double bv = -1;
        for (int lag = -300; lag <= 300; lag += 1) {
            double acc = 0;
            for (int i = fs * 10; i < fs * 20; i += 2) acc += (y[0][i] + y[1][i]) * v.data[0][i + lag];
            if (acc > bv) { bv = acc; best = lag; }
        }
        check("voice stays in time (lag 0)", best == 0, "lag " + best);
        // looping music without a level jump: voice silent, 200 s
        final float[][] silent = new float[1][fs * 200];
        Node.Collect c2 = new Node.Collect(2);
        Pipeline.mix(new Pipeline.Source() { public void run(Node s) throws Exception { Node.run(silent, s, 4096); } },
                1, fs, bed(m), -6, -14, "youtube", File.createTempFile("mix", ".pcm"), c2, null);
        float[][] z = c2.result();
        double mn = 1e9, mxv = -1e9;
        for (int sec = 5; sec < 190; sec++) {
            float[][] w = { java.util.Arrays.copyOfRange(z[0], sec * fs, (sec + 3) * fs), java.util.Arrays.copyOfRange(z[1], sec * fs, (sec + 3) * fs) };
            double l = Loudness.of(w, fs);
            mn = Math.min(mn, l); mxv = Math.max(mxv, l);
        }
        check("looped music keeps a steady level (3 s windows within 3 LU)", mxv - mn < 3, "range " + f(mxv - mn) + " LU");
        done();
    }
}
