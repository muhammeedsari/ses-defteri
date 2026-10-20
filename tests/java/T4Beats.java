import app.sesdefteri.dsp.*;
public class T4Beats extends TestUtil {
    public static void main(String[] a) throws Exception {
        for (int bpm : new int[] { 120, 93 }) {
            Wav w = wav("t_music" + bpm + ".wav");
            Analyzer an = new Analyzer(2, w.rate); Node.run(w.data, an, 4096);
            Analyzer.Analysis s = an.finish();
            // the analysis frames are centred half a window later than their start
            Beats b = Beats.track(s.onset, s.onsetRate);
            double shift = s.fftSize / 2.0 / w.rate;
            double[] truth = T2Enhance.readDoubles("t_music" + bpm + ".json");
            int hit = 0;
            boolean[] used = new boolean[truth.length];
            for (double t : b.times) for (int i = 0; i < truth.length; i++) if (!used[i] && Math.abs(t + shift - truth[i]) < 0.07) { used[i] = true; hit++; break; }
            double prec = (double) hit / Math.max(1, b.times.length), rec = (double) hit / truth.length, fm = 2 * prec * rec / Math.max(1e-9, prec + rec);
            check(bpm + " BPM detected within 1 BPM", Math.abs(b.bpm - bpm) < 1, f(b.bpm) + " confidence " + f(b.confidence));
            check(bpm + " BPM beat positions F-measure >= 0.9 (±70 ms)", fm >= 0.9, "F " + f(fm) + " (" + b.times.length + " beats, " + truth.length + " true)");
        }
        Wav sp = wav("t_clean.wav");
        Analyzer an = new Analyzer(1, sp.rate); Node.run(sp.data, an, 4096);
        Beats bs = Beats.track(an.finish().onset, 48000.0 / 512);
        check("speech is not reported as music with a beat", bs.confidence < 0.3, "confidence " + f(bs.confidence));
        done();
    }
}
