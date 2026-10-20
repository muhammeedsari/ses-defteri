import app.sesdefteri.dsp.*;
import java.util.*;
public class T0Stats extends TestUtil {
    public static void main(String[] a) throws Exception {
        String[] fs = {"t_clean.wav","t_noisy.wav","t_sibilant.wav","t_muffled.wav","t_bright.wav","t_clicks.wav","t_plosive.wav","t_music120.wav","t_noise_only.wav"};
        for (String f : fs) { Wav w = wav(f); show(f, w.data, w.rate); }
        Wav c = wav("t_clean.wav");
        double[][] S = T2Enhance.segs();
        float[] u = c.data[0].clone(); Random R = new Random(3);
        for (double[] s : S) { double g = Math.pow(10,(R.nextDouble()*16-8)/20); for (int i=(int)(s[0]*48000);i<(int)(s[1]*48000);i++) u[i]*=g; }
        show("uneven", new float[][]{u}, 48000);
    }
    static void show(String f, float[][] x, int rate) throws Exception {
        Analyzer an = new Analyzer(x.length, rate); Node.run(x, an, 4096); Analyzer.Analysis s = an.finish();
        System.out.printf("%-16s noise %6.1f speech %6.1f snr %5.1f | hum50 %5.1f 100 %5.1f 60 %5.1f 120 %5.1f | lra %4.1f std %4.1f | sib98 %5.1f sL90-sp %5.1f (n=%d)%n",
          f, s.noiseDb, s.speechDb, s.snr, s.humRatioDb[0], s.humRatioDb[1], s.humRatioDb[2], s.humRatioDb[3], s.lra, s.speechStdDb, s.sibP98, s.sLevel90 - s.speechDb, s.sFrames);
    }
}
