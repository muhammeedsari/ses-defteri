package app.sesdefteri;

import android.content.Context;

import app.sesdefteri.dsp.Node;
import app.sesdefteri.dsp.Resampler;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * "Separate voice": splits a project clip into vocals and everything else with Spleeter (2 stems)
 * running on ONNX Runtime through sherpa-onnx's C API (libsdsep.so is a tiny JNI bridge to it).
 */
final class Separator {
    private Separator() { }

    static native long create(String vocals, String accompaniment, int threads);
    static native void destroy(long h);
    static native float[][] process(long h, float[][] chans, int rate, int[] outRate);

    private static Boolean ok;

    static synchronized boolean available() {
        if (ok == null) {
            try { System.loadLibrary("sdsep"); ok = true; } catch (Throwable e) { ok = false; }
        }
        return ok;
    }

    /** the C API wants file paths: models are copied out of the APK once */
    static File model(Context c, String name) throws Exception {
        File d = new File(c.getFilesDir(), "sep");
        d.mkdirs();
        File f = new File(d, name);
        long size;
        android.content.res.AssetFileDescriptor fd = null;
        try { fd = c.getAssets().openFd("sep/" + name); size = fd.getLength(); } catch (Exception e) { size = -1; } finally { if (fd != null) fd.close(); }
        if (f.exists() && (size < 0 || f.length() == size)) return f;
        File tmp = new File(d, name + ".part");
        InputStream is = c.getAssets().open("sep/" + name);
        OutputStream os = new java.io.FileOutputStream(tmp);
        try { byte[] b = new byte[1 << 16]; int n; while ((n = is.read(b)) > 0) os.write(b, 0, n); } finally { is.close(); os.close(); }
        if (!tmp.renameTo(f)) throw new IllegalStateException("model");
        return f;
    }

    static JSONObject split(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        if (!available()) throw new IllegalStateException("sep-unavailable");
        File in = new File(a.getString("path"));
        Workspace.Info inf = Workspace.info(in);
        p.on(0.01);
        File vm = model(c, "vocals.fp16.onnx"), am = model(c, "accompaniment.fp16.onnx");
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        long h = create(vm.getAbsolutePath(), am.getAbsolutePath(), threads);
        if (h == 0) throw new IllegalStateException("sep-model");
        File dir = in.getParentFile();
        File vo = new File(dir, "v" + System.nanoTime() + ".wav"), ao = new File(dir, "a" + System.nanoTime() + ".wav");
        RandomAccessFile r = new RandomAccessFile(in, "r");
        try {
            final int rate = inf.rate, ch = inf.ch;
            final long N = inf.frames, CH = 30L * rate, CTX = rate;   // 30 s pieces, 1 s of real audio on each side
            Node[] sink = new Node[2];
            Studio.WavNode[] wn = { new Studio.WavNode(vo, rate), new Studio.WavNode(ao, rate) };
            Resampler[] rs = new Resampler[2];
            int outRate = -1;
            byte[] buf = new byte[0];
            for (long s = 0; s < N; s += CH) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                long e = Math.min(N, s + CH), A = Math.max(0, s - CTX), B = Math.min(N, e + CTX);
                int n = (int) (B - A);
                float[][] x = new float[ch][n];
                int bytes = n * ch * 2;
                if (buf.length < bytes) buf = new byte[bytes];
                r.seek(44 + A * ch * 2);
                r.readFully(buf, 0, bytes);
                for (int i = 0, q = 0; i < n; i++) for (int k = 0; k < ch; k++, q += 2) x[k][i] = (short) ((buf[q] & 0xff) | (buf[q + 1] << 8)) / 32768f;
                int[] or = new int[1];
                float[][] y = process(h, ch == 1 ? new float[][] { x[0], x[0] } : x, rate, or);
                x = null;
                if (y == null || y.length < 2) throw new IllegalStateException("sep-run");
                int sc = y.length / 2;   // channels per stem
                if (outRate < 0) {
                    outRate = or[0];
                    for (int t = 0; t < 2; t++) {
                        sink[t] = wn[t];
                        if (outRate != rate) { rs[t] = new Resampler(ch, outRate, rate); rs[t].to(wn[t]); sink[t] = rs[t]; }
                    }
                }
                // keep only [s, e) in the output's own sample clock (absolute positions, so pieces line up exactly)
                double f = (double) outRate / rate;
                long oA = Math.round(A * f);
                int from = (int) (Math.round(s * f) - oA), to = (int) Math.min(y[0].length, Math.round(e * f) - oA);
                int m = Math.max(0, to - from);
                for (int t = 0; t < 2; t++) {
                    float[][] o = new float[ch][m];
                    for (int i = 0; i < m; i++) {
                        if (ch == 1) {
                            float v = 0;
                            for (int k = 0; k < sc; k++) v += y[t * sc + k][from + i];
                            o[0][i] = v / sc;
                        } else for (int k = 0; k < 2; k++) o[k][i] = y[t * sc + Math.min(k, sc - 1)][from + i];
                    }
                    for (int k = 0; k < ch; k++) for (int i = 0; i < m; i++) { float v = o[k][i]; o[k][i] = v > 1 ? 1 : v < -1 ? -1 : v; }
                    sink[t].push(o, m);
                }
                p.on(0.05 + 0.93 * e / N);
            }
            if (sink[0] != null) { sink[0].end(); sink[1].end(); }
            JSONObject res = new JSONObject();
            res.put("voc", clip(vo));
            res.put("acc", clip(ao));
            return res;
        } catch (Exception x) {
            vo.delete();
            ao.delete();
            throw x;
        } finally {
            r.close();
            destroy(h);
        }
    }

    static JSONObject clip(File f) throws Exception {
        Workspace.Info i = Workspace.info(f);
        JSONObject o = new JSONObject();
        o.put("path", f.getAbsolutePath());
        o.put("dur", i.frames * 1000.0 / i.rate);
        o.put("ch", i.ch);
        return o;
    }
}
