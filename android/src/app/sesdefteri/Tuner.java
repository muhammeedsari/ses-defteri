package app.sesdefteri;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/**
 * 5.3 tuner and vocal range: the microphone's pitch about 20 times a second (YIN on a 24 kHz copy, 65–1000 Hz),
 * sent to the page. Never while a recording runs; nothing is stored.
 */
final class Tuner {
    private Tuner() { }

    private static volatile boolean on;
    private static Thread th;

    static synchronized boolean start(final MainActivity a) {
        if (on) return true;
        if (!"idle".equals(Recorder.stateName())) return false;
        // 5.14.3: a quick close and reopen waits for the old thread to let go of the microphone (one reader only)
        Thread old = th;
        if (old != null) { try { old.join(500); } catch (InterruptedException ignored) { } }
        final int my = ++gen;
        final int fs = 48000, min = AudioRecord.getMinBufferSize(fs, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        final AudioRecord r;
        try {
            r = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, fs, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 8192 * 2));
            if (r.getState() != AudioRecord.STATE_INITIALIZED) { r.release(); return false; }
            r.startRecording();
        } catch (Exception e) {
            return false;
        }
        on = true;
        th = new Thread(new Runnable() {
            @Override public void run() {
                final int N = 4096, H = 2048, D = 2;
                short[] in = new short[H];
                float[] x = new float[N];
                float[] d = new float[N / D];
                double[] df = new double[400];
                try {
                    while (on && my == gen) {
                        int got = r.read(in, 0, H);
                        if (got <= 0) continue;
                        System.arraycopy(x, got, x, 0, N - got);
                        for (int i = 0; i < got; i++) x[N - got + i] = in[i] / 32768f;
                        double e = 0;
                        for (float v : x) e += v * v;
                        double db = 10 * Math.log10(e / N + 1e-12);
                        for (int i = 0; i < d.length; i++) { float s = 0; for (int k = 0; k < D; k++) s += x[i * D + k]; d[i] = s / D; }
                        a.tuned(db > -55 ? yin(d, fs / (double) D, df) : 0, db);
                    }
                } catch (Exception ignored) {
                } finally {
                    try { r.stop(); } catch (Exception ignored) { }
                    r.release();
                }
            }
        }, "tuner");
        th.start();
        return true;
    }

    private static volatile int gen;

    static synchronized void stop() { on = false; }

    /** YIN (cumulative mean normalised difference), threshold 0.12, parabolic refinement; 0 = no clear pitch */
    static double yin(float[] d, double fd, double[] df) {
        int lo = (int) (fd / 1000), hi = Math.min(df.length - 2, (int) (fd / 65)), W = d.length - hi - 1;
        double run = 0;
        df[0] = 1;
        for (int tau = 1; tau <= hi; tau++) {
            double s = 0;
            for (int j = 0; j < W; j++) { double v = d[j] - d[j + tau]; s += v * v; }
            run += s;
            df[tau] = run > 0 ? s * tau / run : 1;
        }
        for (int tau = Math.max(2, lo); tau < hi; tau++) {
            if (df[tau] < 0.12) {
                while (tau + 1 < hi && df[tau + 1] < df[tau]) tau++;
                double a = df[tau - 1], b = df[tau], c = df[tau + 1], den = a - 2 * b + c;
                return fd / (tau + (den != 0 ? 0.5 * (a - c) / den : 0));
            }
        }
        return 0;
    }
}
