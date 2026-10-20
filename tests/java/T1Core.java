import app.sesdefteri.dsp.*;
public class T1Core extends TestUtil {
    public static void main(String[] a) throws Exception {
        int fs = 48000;
        // 1) loudness of a 997 Hz sine at -20 dBFS in one channel: reference value -23.0 LUFS (BS.1770 / EBU test)
        float[][] s = new float[1][fs * 20];
        for (int i = 0; i < s[0].length; i++) s[0][i] = (float) (0.1 * Math.sin(2 * Math.PI * 997 * i / fs));
        double L = Loudness.of(s, fs);
        check("sine -20 dBFS mono = -23.0 LUFS", Math.abs(L + 23.0) < 0.1, f(L));
        float[][] st = { s[0], s[0] };
        double L2 = Loudness.of(st, fs);
        check("same sine stereo = -20.0 LUFS", Math.abs(L2 + 20.0) < 0.1, f(L2));
        // 2) compare with pyloudnorm on real files (values passed in from python)
        String[] files = a.length > 0 ? a[0].split(",") : new String[0];
        for (String spec : files) {
            String[] p = spec.split("=");
            Wav w = wav(p[0]);
            double me = Loudness.of(w.data, w.rate), ref = Double.parseDouble(p[1]);
            check("loudness " + p[0] + " vs pyloudnorm", Math.abs(me - ref) < 0.2, f(me) + " vs " + f(ref));
        }
        // 3) true peak: sine at fs/4 offset 45 degrees has sample peak 0.707*A but true peak A
        float[][] tpS = new float[1][4800];
        for (int i = 0; i < tpS[0].length; i++) tpS[0][i] = (float) (0.9 * Math.sin(2 * Math.PI * 12000 * i / fs + Math.PI / 4));
        double sp = 0; for (float v : tpS[0]) sp = Math.max(sp, Math.abs(v));
        double tp = TruePeak.measure(tpS);
        check("true peak finds inter-sample peak", Math.abs(TruePeak.db(tp) - TruePeak.db(0.9)) < 0.5 && sp < 0.65, "sample " + f(TruePeak.db(sp)) + " dB, true " + f(TruePeak.db(tp)) + " dB (expect -0.92)");
        // 4) limiter: loud speech +12 dB must stay under -1 dBTP, timeline aligned, length same
        Wav w = wav("t_clean.wav");
        Limiter lim = new Limiter(1, fs, -1.0, 24.0);
        float[][] y = Node.apply(w.data, lim, lim, 1);
        double ytp = TruePeak.db(TruePeak.measure(y));
        check("limiter output <= -1 dBTP", ytp <= -0.95, f(ytp) + " dBTP, min gain " + f(TruePeak.db(lim.minGain)) + " dB");
        check("limiter keeps length", y[0].length == w.data[0].length, y[0].length + " vs " + w.data[0].length);
        // alignment: best cross-correlation lag between output and input must be 0
        int best = 999; double bv = -1;
        for (int lag = -300; lag <= 300; lag++) {
            double acc = 0;
            for (int i = 48000 * 5; i < 48000 * 15; i++) acc += y[0][i] * w.data[0][i + lag];
            if (acc > bv) { bv = acc; best = lag; }
        }
        check("limiter aligned (no delay)", best == 0, "lag " + best + " samples");
        // 5) resampler: 1 kHz sine 48k->16k, check amplitude and aliasing of 10 kHz tone
        float[][] mix = new float[1][fs * 2];
        for (int i = 0; i < mix[0].length; i++) mix[0][i] = (float) (0.5 * Math.sin(2 * Math.PI * 1000 * i / fs) + 0.3 * Math.sin(2 * Math.PI * 10000 * i / fs));
        float[][] r = Resampler.resample(mix, 48000, 16000);
        check("resampler length", r[0].length == 32000, "" + r[0].length);
        // project onto 1 kHz and onto alias of 10k at 16k rate (6 kHz)
        double c1 = 0, s1 = 0, c6 = 0, s6 = 0;
        for (int i = 4000; i < 28000; i++) { double t = i / 16000.0; c1 += r[0][i] * Math.cos(2 * Math.PI * 1000 * t); s1 += r[0][i] * Math.sin(2 * Math.PI * 1000 * t); c6 += r[0][i] * Math.cos(2 * Math.PI * 6000 * t); s6 += r[0][i] * Math.sin(2 * Math.PI * 6000 * t); }
        double a1 = 2 * Math.hypot(c1, s1) / 24000, a6 = 2 * Math.hypot(c6, s6) / 24000;
        check("resampler passband amplitude", Math.abs(a1 - 0.5) < 0.01, f(a1));
        check("resampler alias rejection > 60 dB", db(a6 / 0.3) < -60, f(db(a6 / 0.3)) + " dB");
        // phase/delay: compare with ideal sine at 16k
        double e2 = 0, r2 = 0;
        for (int i = 4000; i < 28000; i++) { double ideal = 0.5 * Math.sin(2 * Math.PI * 1000 * i / 16000.0); e2 += Math.pow(r[0][i] - ideal - 0, 2); r2 += ideal * ideal; }
        // 10k component is removed, so residual = 0 ideally
        check("resampler no delay (matches ideal 1 kHz)", 10 * Math.log10(r2 / e2) > 40, "SNR " + f(10 * Math.log10(r2 / e2)) + " dB");
        done();
    }
}
