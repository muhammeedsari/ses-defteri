package app.sesdefteri;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Speech-to-text is optional: Whisper base (≈95 MB) or small (≈375 MB, more accurate), int8, downloaded once
 * from Hugging Face on request; afterwards everything runs offline. Nothing of it is in the APK but the tiny voice detector.
 */
final class AsrModels {
    private AsrModels() { }

    static String[] files(String m) { return new String[] { m + "-encoder.int8.onnx", m + "-decoder.int8.onnx", m + "-tokens.txt" }; }
    static String[] hosts(String m) {
        return new String[] { "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-" + m + "/resolve/main/",
                              "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-" + m + "/resolve/main/" };
    }
    static String name(String m) { return "small".equals(m) ? "small" : "base"; }

    static File dir(Context c, String name) {
        File d = new File(c.getFilesDir(), "asr-" + name);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static boolean ready(Context c, String name) {
        File d = new File(c.getFilesDir(), "asr-" + name);
        for (String f : files(name)) if (!new File(d, f).isFile()) return false;
        return new File(d, "silero_vad.onnx").isFile();
    }

    /** the model to use: the requested one if downloaded, else the other one, else null (nothing downloaded) */
    static String pick(Context c, String want) {
        want = name(want);
        if (ready(c, want)) return want;
        String other = "small".equals(want) ? "base" : "small";
        return ready(c, other) ? other : null;
    }

    static JSONObject status(Context c) throws Exception {
        JSONObject o = new JSONObject();
        o.put("base", ready(c, "base"));
        o.put("small", ready(c, "small"));
        return o;
    }

    static JSONObject download(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        String m = name(a.optString("model", "base"));
        String[] SMALL = files(m);
        File d = dir(c, m);
        long[] sizes = new long[SMALL.length];
        String host = null;
        for (String h : hosts(m)) {
            try { for (int i = 0; i < SMALL.length; i++) sizes[i] = size(h + SMALL[i]); host = h; break; } catch (Exception ignored) { }
        }
        if (host == null) throw new IllegalStateException("download-unreachable");
        long total = 0, done = 0;
        for (long s : sizes) total += s;
        for (int i = 0; i < SMALL.length; i++) {
            File f = new File(d, SMALL[i]);
            if (f.isFile() && f.length() == sizes[i]) { done += sizes[i]; continue; }
            File part = new File(d, SMALL[i] + ".part");
            long have = part.isFile() ? part.length() : 0;
            HttpURLConnection con = (HttpURLConnection) new URL(host + SMALL[i]).openConnection();
            con.setConnectTimeout(20000);
            con.setReadTimeout(30000);
            if (have > 0) con.setRequestProperty("Range", "bytes=" + have + "-");
            int code = con.getResponseCode();
            if (code != 200 && code != 206) throw new IllegalStateException("download-" + code);
            if (code == 200) have = 0;   // the server ignored the range: start over
            InputStream is = con.getInputStream();
            RandomAccessFile out = new RandomAccessFile(part, "rw");
            try {
                out.setLength(have);
                out.seek(have);
                byte[] b = new byte[1 << 16];
                int n;
                long last = 0;
                while ((n = is.read(b)) > 0) {
                    if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                    out.write(b, 0, n);
                    have += n;
                    if (have - last > (1 << 20)) { last = have; p.on((double) (done + have) / total); }
                }
            } finally {
                out.close();
                is.close();
                con.disconnect();
            }
            if (part.length() != sizes[i]) throw new IllegalStateException("download-short");
            if (!part.renameTo(f)) throw new IllegalStateException("download-rename");
            done += sizes[i];
        }
        // the voice detector goes next to the model so both load from files
        File vad = new File(d, "silero_vad.onnx");
        if (!vad.isFile()) {
            InputStream is = c.getAssets().open("asr/silero_vad.onnx");
            OutputStream os = new java.io.FileOutputStream(vad);
            try { byte[] b = new byte[1 << 16]; int n; while ((n = is.read(b)) > 0) os.write(b, 0, n); } finally { is.close(); os.close(); }
        }
        return status(c);
    }

    private static long size(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("HEAD");
        con.setConnectTimeout(15000);
        con.setReadTimeout(15000);
        con.setInstanceFollowRedirects(true);
        try {
            int code = con.getResponseCode();
            if (code != 200) throw new IllegalStateException("head-" + code);
            long n = con.getContentLengthLong();
            if (n <= 0) throw new IllegalStateException("size");
            return n;
        } finally {
            con.disconnect();
        }
    }

    static JSONObject delete(Context c, JSONObject a) throws Exception {
        File[] fs = dir(c, name(a.optString("model", "small"))).listFiles();
        if (fs != null) for (File f : fs) f.delete();
        return status(c);
    }
}
