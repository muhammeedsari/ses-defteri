import app.sesdefteri.dsp.*;
public class T9Sweep extends TestUtil {
    public static void main(String[] a) throws Exception {
        double[][] S = T2Enhance.segs(); Wav clean = wav("t_clean.wav"), noisy = wav("t_noisy.wav");
        Analyzer an = new Analyzer(1, 48000); Node.run(noisy.data, an, 4096); Analyzer.Analysis s = an.finish();
        double base = T2Enhance.fidelity(clean.data[0], noisy.data[0], S, 0);
        for (double os : new double[]{1.0, 1.5, 2.0}) for (double al : new double[]{0.92, 0.96, 0.98}) for (double red : new double[]{12, 18}) {
            Spectral sp = new Spectral(1, 48000).noise(s.noisePower, red, os); sp.alpha = al;
            float[][] y = Node.apply(noisy.data, sp, sp, 1);
            double fi = T2Enhance.fidelity(clean.data[0], y[0], S, 0);
            double gap = T2Enhance.avgEnergy(y[0], S, 0) - T2Enhance.avgEnergy(y[0], T2Enhance.gaps(S), 0);
            System.out.printf("os %.1f alpha %.2f red %2.0f -> fidelity %+.2f dB, speech/gap %.1f dB%n", os, al, red, fi - base, gap);
        }
    }
}
