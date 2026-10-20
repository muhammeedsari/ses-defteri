import app.sesdefteri.dsp.*;
public class T9Beat extends TestUtil {
    public static void main(String[] a) throws Exception {
        Wav w = wav("t_music120.wav");
        Analyzer an = new Analyzer(2, w.rate); Node.run(w.data, an, 4096);
        Analyzer.Analysis s = an.finish();
        float[] o = s.onset; double r = s.onsetRate;
        System.out.println("rate " + r + " n " + o.length);
        // print onset around first beats
        for (int i = 40; i < 160; i++) if (o[i] > 0) System.out.printf("%d(%.2fs):%.0f ", i, i / r, o[i]);
        System.out.println();
    }
}
