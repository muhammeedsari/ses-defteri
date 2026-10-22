package app.sesdefteri;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** "Room test": listens for 3 seconds before recording and reports the background noise level. */
final class RoomTest {
    private RoomTest() { }

    static JSONObject run(AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        final int rate = 48000, F = rate / 20;   // 50 ms frames
        int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, F * 8));
        if (ar.getState() != AudioRecord.STATE_INITIALIZED) { ar.release(); throw new IllegalStateException("mic"); }
        short[] b = new short[F];
        int frames = 66, skip = 6;   // 0.3 s settle + 3 s
        double[] db = new double[frames - skip];
        double peak = 0;
        try {
            ar.startRecording();
            for (int k = 0; k < frames; k++) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                int got = 0;
                while (got < F) { int r = ar.read(b, got, F - got); if (r <= 0) throw new IllegalStateException("mic"); got += r; }
                if (k < skip) continue;
                double e = 0;
                for (short v : b) { double x = v / 32768.0; e += x * x; if (Math.abs(x) > peak) peak = Math.abs(x); }
                db[k - skip] = 10 * Math.log10(e / F + 1e-12);
                p.on((double) k / frames);
            }
        } finally {
            try { ar.stop(); } catch (Exception ignored) { }
            ar.release();
        }
        Arrays.sort(db);
        JSONObject o = new JSONObject();
        o.put("noise", Math.round(db[(int) (db.length * 0.2)]));
        o.put("loud", Math.round(db[(int) (db.length * 0.95)]));
        o.put("peak", Math.round(20 * Math.log10(Math.max(1e-9, peak))));
        return o;
    }
}
