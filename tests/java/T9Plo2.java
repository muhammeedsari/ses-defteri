import app.sesdefteri.dsp.*;
public class T9Plo2 extends TestUtil {
    public static void main(String[] a) throws Exception {
        Wav pl = wav("t_plosive.wav"), cl = wav("t_clean.wav");
        int[] pp = T2Enhance.readInts("t_plosive.json");
        for (double r : new double[]{0.9, 1.3, 1.8, 2.5, 3.5}) {
            float[] P = ch(pl.data[0], r), C = ch(cl.data[0], r);
            float[] lP0 = T2Enhance.band(pl.data[0], 0, 160), lC0 = T2Enhance.band(cl.data[0], 0, 160), lP = T2Enhance.band(P, 0, 160), lC = T2Enhance.band(C, 0, 160);
            double e0=0,c0=0,e1=0,c1=0;
            for (int p : pp) for (int i = p; i < p + 2880; i++) { e0 += lP0[i]*lP0[i]; c0 += lC0[i]*lC0[i]; e1 += lP[i]*lP[i]; c1 += lC[i]*lC[i]; }
            System.out.printf("ratio %.1f: excess %.2f dB, active on clean %.2f s%n", r, 10*Math.log10(e1/c1), act / 48000.0);
        }
    }
    static long act;
    static float[] ch(float[] x, double r) throws Exception {
        Stages.Filters fl = new Stages.Filters(1, 48000, 75, null); Stages.PlosiveTamer pt = new Stages.PlosiveTamer(1, 48000, r); fl.to(pt);
        float[] y = Node.apply(new float[][]{x}, fl, pt, 1)[0]; act = pt.active; return y;
    }
}
