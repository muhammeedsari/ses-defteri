package app.sesdefteri;

import android.content.Context;

import com.k2fsa.sherpa.onnx.AudioEvent;
import com.k2fsa.sherpa.onnx.AudioTagging;
import com.k2fsa.sherpa.onnx.AudioTaggingConfig;
import com.k2fsa.sherpa.onnx.AudioTaggingModelConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;

import app.sesdefteri.dsp.Node;
import app.sesdefteri.dsp.Resampler;

/**
 * 4.16: coughs, laughs, sneezes and throat clearing found by a small sound-recognition model (CED tiny, Apache-2.0,
 * AudioSet's 527 sounds; the 6 MB int8 file from the sherpa-onnx release, downloaded once). 1 s windows every 0.5 s,
 * then each find is narrowed to its loud core so the words around it stay.
 */
final class Sounds {
    private Sounds() { }

    static final String URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/audio-tagging-models/sherpa-onnx-ced-tiny-audio-tagging-2024-04-19.tar.bz2";
    static final String[] FILES = { "model.int8.onnx", "class_labels_indices.csv" };
    // AudioSet indices: laughter (laughter, baby laughter, giggle, snicker, belly laugh, chuckle), cough, throat clearing, sneeze
    static final int[][] GROUPS = { { 16, 17, 18, 19, 20, 21 }, { 47 }, { 48 }, { 49 } };
    static final String[] KIND = { "laugh", "cough", "throat", "sneeze" };
    static final float THR = 0.3f;

    static File dir(Context c) { return c != null ? new File(c.getFilesDir(), "tag") : new File(System.getProperty("sd.tag", "")); }

    static boolean ready(Context c) {
        for (String f : FILES) if (!new File(dir(c), f).isFile()) return false;
        return true;
    }

    static JSONObject download(Context c, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        final File d = dir(c);
        d.mkdirs();
        File arc = new File(d, "ced.tar.bz2");
        long n = AsrModels.size(URL);
        if (!arc.isFile() || arc.length() != n) AsrModels.fetch(URL, arc, n, 0, n, p, cancel);
        final java.util.List<String> want = java.util.Arrays.asList(FILES);
        InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(arc), 1 << 16);
        try {
            Untar.extract(in, new Untar.Place() {
                @Override public File to(String name) {
                    String base = name.substring(name.lastIndexOf('/') + 1);
                    return want.contains(base) ? new File(d, base) : null;
                }
            });
        } finally { in.close(); }
        arc.delete();
        if (!ready(c)) throw new IllegalStateException("download-short");
        return new JSONObject().put("ready", true);
    }

    /** {path, in, len} (seconds of the file) → events [[start, end, kind, prob]] in file seconds */
    static JSONObject scan(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        if (!ready(c)) throw new IllegalStateException("tag-model");
        File f = new File(a.getString("path"));
        Workspace.Info inf = Workspace.info(f);
        long f0 = Math.max(0, Math.round(a.optDouble("in", 0) * inf.rate));
        long f1 = Math.min(inf.frames, f0 + Math.round(a.optDouble("len", 1e9) * inf.rate));
        // the heard part, mono, 16 kHz
        RandomAccessFile r = new RandomAccessFile(f, "r");
        float[] x;
        try {
            Resampler rs = new Resampler(1, (double) inf.rate, 16000.0);
            Node.Collect col = new Node.Collect(1);
            rs.to(col);
            final int BL = 1 << 15;
            for (long s = f0; s < f1; s += BL) {
                int k = (int) Math.min(BL, f1 - s);
                float[][] b = Workspace.readRegion(r, inf, s, k);
                float[][] m = new float[1][k];
                for (int i = 0; i < k; i++) { float v = 0; for (int ch = 0; ch < inf.ch; ch++) v += b[ch][i]; m[0][i] = v / inf.ch; }
                rs.push(m, k);
            }
            rs.end();
            x = col.result()[0];
        } finally {
            r.close();
        }
        File d = dir(c);
        AudioTagging at = new AudioTagging(AudioTaggingConfig.builder()
                .setModel(AudioTaggingModelConfig.builder().setCED(new File(d, FILES[0]).getAbsolutePath()).setNumThreads(Device.threads(2)).setDebug(false).build())
                .setLabels(new File(d, FILES[1]).getAbsolutePath()).setTopK(527).build());
        final int W = 16000, H = 8000, nw = Math.max(1, (x.length - W) / H + 1);
        float[][] sc = new float[nw][GROUPS.length];
        try {
            for (int w = 0; w < nw; w++) {
                if (cancel.get()) throw new InterruptedException("cancel");
                int s = w * H;
                float[] seg = java.util.Arrays.copyOfRange(x, s, Math.min(x.length, s + W));
                OfflineStream st = at.createStream();
                st.acceptWaveform(seg, 16000);
                AudioEvent[] ev = at.compute(st, 527);
                st.release();
                for (AudioEvent e : ev) for (int g = 0; g < GROUPS.length; g++) for (int i : GROUPS[g]) if (e.getIndex() == i) sc[w][g] = Math.max(sc[w][g], e.getProb());
                if ((w & 7) == 0) p.on(0.1 + 0.85 * w / nw);
            }
        } finally {
            at.release();
        }
        // 10 ms loudness, to narrow every find to its loud core
        int nf = x.length / 160;
        float[] db = new float[nf];
        for (int i = 0; i < nf; i++) { double e = 0; for (int j = 0; j < 160; j++) { float v = x[i * 160 + j]; e += v * v; } db[i] = (float) (10 * Math.log10(e / 160 + 1e-12)); }
        JSONArray out = new JSONArray();
        double off = f0 / (double) inf.rate;
        for (int w = 0; w < nw; ) {
            int g = best(sc[w]);
            if (g < 0) { w++; continue; }
            int e = w;
            float pk = sc[w][g];
            while (e + 1 < nw && sc[e + 1][g] >= THR) { e++; pk = Math.max(pk, sc[e][g]); }
            int a0 = w * H / 160, a1 = Math.min(nf, (e * H + W) / 160);
            int top = a0;
            for (int i = a0; i < a1; i++) if (db[i] > db[top]) top = i;
            float lim = db[top] - 15;
            int s0 = top, s1 = top;
            if (g == 0) {   // laughter comes in bursts: from its first loud moment to its last
                while (s0 > a0 && db[s0 - 1] > lim - 3) s0--;
                for (int i = a0; i < top; i++) if (db[i] > lim) { s0 = i; break; }
                for (int i = a1 - 1; i > top; i--) if (db[i] > lim) { s1 = i; break; }
            } else {        // a cough or a sneeze: one burst, the words around it stay
                while (s0 > a0 && db[s0 - 1] > lim) s0--;
                while (s1 < a1 - 1 && db[s1 + 1] > lim) s1++;
            }
            double t0 = Math.max(0, s0 * 0.01 - 0.06), t1 = Math.min(x.length / 16000.0, (s1 + 1) * 0.01 + 0.08);
            out.put(new JSONArray().put(Math.round((off + t0) * 100) / 100.0).put(Math.round((off + t1) * 100) / 100.0).put(KIND[g]).put(Math.round(pk * 100) / 100.0));
            w = e + 1;
        }
        p.on(1);
        return new JSONObject().put("events", out);
    }

    private static int best(float[] s) {
        int g = -1;
        for (int i = 0; i < s.length; i++) if (s[i] >= THR && (g < 0 || s[i] > s[g])) g = i;
        return g;
    }
}
