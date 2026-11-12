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
        if (host == null) { fromGithub(c, m, d, p, cancel); return finish(c, d); }
        long total = 0, done = 0;
        for (long s : sizes) total += s;
        try {
            for (int i = 0; i < SMALL.length; i++) {
                File f = new File(d, SMALL[i]);
                if (f.isFile() && f.length() == sizes[i]) { done += sizes[i]; continue; }
                fetch(host + SMALL[i], f, sizes[i], done, total, p, cancel);
                done += sizes[i];
            }
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            if (cancel != null && cancel.get()) throw e;
            fromGithub(c, m, d, p, cancel);
        }
        return finish(c, d);
    }

    /** 3.4.6: Hugging Face could not be reached from the phone ("no internet"), GitHub can (the voices come from there):
     *  the sherpa-onnx release archive holds the same int8 files, only those are unpacked */
    static final String GH = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-";

    private static void fromGithub(Context c, String m, final File d, final AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        String url = GH + m + ".tar.bz2";
        File arc = new File(d, "whisper.tar.bz2");
        long n;
        try { n = size(url); } catch (Exception e) { MainActivity.saveNote(c, "whisper " + m + " download: " + e.getMessage()); throw new IllegalStateException("download-unreachable"); }
        if (!arc.isFile() || arc.length() != n)
            fetch(url, arc, n, 0, n, new AudioTools.Progress() { @Override public void on(double f) { p.on(f * 0.9); } }, cancel);
        p.on(0.91);
        final java.util.List<String> want = java.util.Arrays.asList(files(m));
        InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(arc), 1 << 16);
        boolean ok = false;
        try {
            Untar.extract(in, new Untar.Place() {
                @Override public File to(String name) {
                    String base = name.substring(name.lastIndexOf('/') + 1);
                    return want.contains(base) ? new File(d, base) : null;
                }
            });
            ok = true;
        } finally {
            in.close();
            // 5.14.2: a damaged archive is fetched again next time, and no half-unpacked model stays behind
            if (!ok) { arc.delete(); for (String f : want) new File(d, f).delete(); }
        }
        arc.delete();
        for (String f : files(m)) if (!new File(d, f).isFile()) throw new IllegalStateException("download-short");
    }

    private static JSONObject finish(Context c, File d) throws Exception {
        // the voice detector goes next to the model so both load from files
        File vad = new File(d, "silero_vad.onnx");
        if (!vad.isFile()) {
            InputStream is = c.getAssets().open("asr/silero_vad.onnx");
            OutputStream os = new java.io.FileOutputStream(vad);
            try { byte[] b = new byte[1 << 16]; int n; while ((n = is.read(b)) > 0) os.write(b, 0, n); } finally { is.close(); os.close(); }
        }
        return status(c);
    }

    /** 5.14.3: SHA-256 of the files that come from fixed GitHub releases; a download that does not match is thrown away */
    static final java.util.Map<String, String> SHA = new java.util.HashMap<String, String>();
    static {
        String lv = "https://github.com/Topping1/LavaSR-ONNX/releases/download/Alpha-v0.1/", k2 = "https://github.com/k2-fsa/sherpa-onnx/releases/download/";
        SHA.put(lv + "enhancer_backbone.onnx", "841e96d261dffdf1dc974f3d29e2cfcf1b16fd0b358749c1ace0bbfa1d4c8ddd");
        SHA.put(lv + "enhancer_backbone.onnx.data", "a125a4ede7cfdd1073d906a3cadf2171a30be6a40f296ad28772e0ba258de8c5");
        SHA.put(lv + "enhancer_spec_head.onnx", "f66fd164c55fd1b07e5cea5e687c71522b192f452691128fd7ae4e6b26dbc683");
        SHA.put(lv + "enhancer_spec_head.onnx.data", "b855e309b027af9aa75285b97b345571b6bd695a30fde434d06c979d83885fd6");
        SHA.put(k2 + "source-separation-models/UVR-MDX-NET-Inst_HQ_5.onnx", "487e35ae87ed9af9914c85c357e60800c07b8196949e274fe884c18a0c620dbb");
        SHA.put(k2 + "audio-tagging-models/sherpa-onnx-ced-tiny-audio-tagging-2024-04-19.tar.bz2", "84baf315b57d61aa69480c4fee878dab54cbc7be3e877db334e65d8b087e23c3");
        SHA.put(k2 + "speaker-recongition-models/3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx", "aa3cfc16963a10586a9393f5035d6d6b57e98d358b347f80c2a30bf4f00ceba2");
        SHA.put(k2 + "asr-models/sherpa-onnx-whisper-base.tar.bz2", "911b2083efd7c0dca2ac3b358b75222660dc09fb716d64fbfc417ba6c99ff3de");
        SHA.put(k2 + "asr-models/sherpa-onnx-whisper-small.tar.bz2", "486a46afbb7ba798507190ffe02fea2dd726049af212e774537efac6afb210a6");
    }

    static String sha256(File f) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        InputStream in = new java.io.FileInputStream(f);
        try { byte[] b = new byte[1 << 16]; int n; while ((n = in.read(b)) > 0) md.update(b, 0, n); } finally { in.close(); }
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }

    /** downloads url into f (resumable through f.part); progress counts from done of total bytes */
    static void fetch(String url, File f, long size, long done, long total, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        File part = new File(f.getParentFile(), f.getName() + ".part");
        long have = part.isFile() ? part.length() : 0;
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
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
        if (part.length() != size) throw new IllegalStateException("download-short");
        String want = SHA.get(url);
        if (want != null && !want.equals(sha256(part))) { part.delete(); throw new IllegalStateException("download-bad"); }
        if (!part.renameTo(f)) throw new IllegalStateException("download-rename");
    }

    /** file size: HEAD, or when that is refused (signed download links only allow GET) the first byte with GET */
    static long size(String url) throws Exception {
        try { return head(url); } catch (Exception e) {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(15000);
            con.setRequestProperty("Range", "bytes=0-0");
            try {
                int code = con.getResponseCode();
                String cr = con.getHeaderField("Content-Range");
                if (code == 206 && cr != null && cr.lastIndexOf('/') > 0) return Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1).trim());
                if (code == 200 && con.getContentLengthLong() > 0) return con.getContentLengthLong();
                throw new IllegalStateException("head-" + code);
            } finally { con.disconnect(); }
        }
    }

    private static long head(String url) throws Exception {
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
