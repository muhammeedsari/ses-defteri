import app.sesdefteri.dsp.*;
public class T9Plo extends TestUtil {
    public static void main(String[] a) throws Exception {
        Wav pl = wav("t_plosive.wav"), cl = wav("t_clean.wav"); int fs = 48000;
        int[] pp = T2Enhance.readInts("t_plosive.json");
        for (int mode = 0; mode < 3; mode++) {
            float[] P = chain(pl.data[0], mode), C = chain(cl.data[0], mode);
            double eIn = 0, eOut = 0, eLo = 0;
            float[] d = new float[P.length]; for (int i = 0; i < P.length; i++) d[i] = P[i] - C[i];
            float[] dl = T2Enhance.band(d, 0, 160);
            for (int p : pp) for (int i = p; i < p + 2880; i++) { eIn += Math.pow(pl.data[0][i] - cl.data[0][i], 2); eOut += d[i] * d[i]; eLo += dl[i] * dl[i]; }
            System.out.printf("mode %d: reduction %.1f dB, residual below 160 Hz %.0f%%%n", mode, 10 * Math.log10(eIn / eOut), 100 * eLo / eOut);
        }
    }
    static float[] chain(float[] x, int mode) throws Exception {
        if (mode == 0) return x;
        Stages.Filters fl = new Stages.Filters(1, 48000, 75, null);
        if (mode == 1) return Node.apply(new float[][]{x}, fl, fl, 1)[0];
        Stages.PlosiveTamer pt = new Stages.PlosiveTamer(1, 48000, 0.9); fl.to(pt);
        return Node.apply(new float[][]{x}, fl, pt, 1)[0];
    }
}
