package app.sesdefteri;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import app.sesdefteri.dsp.Limiter;
import app.sesdefteri.dsp.Node;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Multitrack project: every clip is a 16-bit 48 kHz WAV in files/proj. The page owns the layout;
 * here we only decode new clips, mix them live through an AudioTrack, and render the final mix.
 */
final class Timeline {
    private Timeline() { }

    static final int RATE = 48000;

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "proj");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** any audio → project WAV (48 kHz, mono or stereo) */
    static JSONObject add(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        JSONObject src = a.getJSONObject("src");
        Studio.Fmt f = Studio.probe(c, src);
        File out = new File(dir(c), "c" + System.nanoTime() + ".wav");
        try {
            Workspace.decodeTo(c, src, Math.min(2, f.ch), RATE, p, new double[] { 0, 1 }, cancel, out);
            Workspace.Info i = Workspace.info(out);
            JSONObject o = new JSONObject();
            o.put("path", out.getAbsolutePath());
            o.put("dur", i.frames * 1000.0 / i.rate);
            o.put("ch", i.ch);
            return o;
        } catch (Exception e) {
            out.delete();
            throw e;
        }
    }

    static void clean(Context c, JSONArray keep) {
        java.util.HashSet<String> k = new java.util.HashSet<>();
        if (keep != null) for (int i = 0; i < keep.length(); i++) k.add(keep.optString(i));
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) if (!k.contains(f.getAbsolutePath())) f.delete();
    }

    /* ---------- mixing ---------- */

    static final class Clip {
        RandomAccessFile f;
        int ch, rate;
        long frames, at, in, len;
        float g;
        int fi, fo;
        byte[] buf = new byte[0];
    }

    /** clips: [{path, at, in, len (seconds), g (linear), fi, fo (seconds)}] */
    static final class Engine {
        final Clip[] clips;
        final long end;
        final int ch;

        Engine(JSONArray a) throws Exception {
            java.util.ArrayList<Clip> l = new java.util.ArrayList<>();
            long e = 0;
            int c2 = 1;
            try {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    File file = new File(o.getString("path"));
                    if (!file.exists()) continue;
                    Workspace.Info inf = Workspace.info(file);
                    Clip k = new Clip();
                    k.ch = inf.ch; k.rate = inf.rate; k.frames = inf.frames;
                    k.at = Math.max(0, Math.round(o.getDouble("at") * RATE));
                    k.in = Math.max(0, Math.min(inf.frames, Math.round(o.getDouble("in") * RATE)));
                    k.len = Math.max(0, Math.min(inf.frames - k.in, Math.round(o.getDouble("len") * RATE)));
                    k.g = (float) o.optDouble("g", 1);
                    k.fi = (int) Math.round(o.optDouble("fi", 0) * RATE);
                    k.fo = (int) Math.round(o.optDouble("fo", 0) * RATE);
                    if (k.len <= 0 || k.g <= 0) continue;
                    k.f = new RandomAccessFile(file, "r");
                    l.add(k);
                    e = Math.max(e, k.at + k.len);
                    if (k.ch > 1) c2 = 2;
                }
            } catch (Exception x) {
                for (Clip k : l) try { k.f.close(); } catch (Exception ignored) { }
                throw x;
            }
            clips = l.toArray(new Clip[0]);
            end = e;
            ch = c2;
        }

        /** adds frames [pos, pos+n) of every clip into out (2 channels, cleared here) */
        void render(long pos, int n, float[][] out) throws Exception {
            for (float[] o : out) java.util.Arrays.fill(o, 0, n, 0f);
            for (Clip k : clips) {
                long a = Math.max(pos, k.at), z = Math.min(pos + n, k.at + k.len);
                if (z <= a) continue;
                int cnt = (int) (z - a), off = (int) (a - pos), bytes = cnt * k.ch * 2;
                if (k.buf.length < bytes) k.buf = new byte[bytes];
                k.f.seek(44 + (k.in + a - k.at) * k.ch * 2);
                k.f.readFully(k.buf, 0, bytes);
                byte[] b = k.buf;
                int q = 0;
                for (int i = 0; i < cnt; i++) {
                    long r = a - k.at + i;
                    float g = k.g;
                    if (k.fi > 0 && r < k.fi) g *= (float) r / k.fi;
                    if (k.fo > 0 && k.len - r < k.fo) g *= (float) (k.len - r) / k.fo;
                    float l = (short) ((b[q] & 0xff) | (b[q + 1] << 8)) / 32768f;
                    q += 2;
                    float rr = l;
                    if (k.ch > 1) { rr = (short) ((b[q] & 0xff) | (b[q + 1] << 8)) / 32768f; q += 2; }
                    out[0][off + i] += l * g;
                    out[1][off + i] += rr * g;
                }
            }
        }

        void close() { for (Clip k : clips) try { k.f.close(); } catch (Exception ignored) { } }
    }

    /* ---------- live playback ---------- */

    static final class Play implements Runnable {
        static Play cur;
        final long start;
        volatile Engine pending;
        volatile boolean stop;
        Engine eng;
        AudioTrack track;

        Play(Engine e, long start) { eng = e; this.start = start; }

        @Override public void run() {
            final int BL = 1024;
            float[][] mix = new float[2][BL];
            short[] pcm = new short[BL * 2];
            long pos = start;
            try {
                track.play();
                while (!stop) {
                    Engine p = pending;
                    if (p != null) { pending = null; eng.close(); eng = p; }
                    if (pos >= eng.end) break;
                    int n = (int) Math.min(BL, eng.end - pos);
                    eng.render(pos, n, mix);
                    for (int i = 0, j = 0; i < n; i++) {
                        for (int c = 0; c < 2; c++, j++) {
                            float v = mix[c][i];
                            if (v > 1) v = 1; else if (v < -1) v = -1;
                            pcm[j] = (short) (v * 32767);
                        }
                    }
                    track.write(pcm, 0, n * 2);
                    pos += n;
                }
                if (!stop) {
                    // let the buffer drain so the end isn't cut off
                    long want = pos - start;
                    for (int i = 0; i < 100 && !stop && (track.getPlaybackHeadPosition() & 0xffffffffL) < want; i++) Thread.sleep(20);
                }
            } catch (Exception ignored) {
            } finally {
                try { track.stop(); } catch (Exception ignored) { }
                track.release();
                eng.close();
                Engine p = pending;
                if (p != null) p.close();
                synchronized (Play.class) { if (cur == this) cur = null; }
            }
        }
    }

    static synchronized void play(String json, double sec) throws Exception {
        stop();
        Engine e = new Engine(new JSONArray(json));
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        Play p = new Play(e, Math.max(0, Math.round(sec * RATE)));
        p.track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
            Math.max(min * 2, RATE / 10 * 4), AudioTrack.MODE_STREAM);
        synchronized (Play.class) { Play.cur = p; }
        Thread th = new Thread(p, "timeline");
        th.setPriority(Thread.MAX_PRIORITY);
        th.start();
    }

    /** new layout while playing (volume, fades, moved clips) without a restart */
    static void update(String json) throws Exception {
        Play p;
        synchronized (Play.class) { p = Play.cur; }
        if (p == null) return;
        Engine e = new Engine(new JSONArray(json)), old = p.pending;
        p.pending = e;
        if (old != null) old.close();
    }

    static void stop() {
        Play p;
        synchronized (Play.class) { p = Play.cur; Play.cur = null; }
        if (p != null) p.stop = true;
    }

    /** seconds played, or -1 when stopped */
    static double pos() {
        Play p;
        synchronized (Play.class) { p = Play.cur; }
        if (p == null) return -1;
        try { return (p.start + (p.track.getPlaybackHeadPosition() & 0xffffffffL)) / (double) RATE; } catch (Exception e) { return p.start / (double) RATE; }
    }

    /* ---------- final mix ---------- */

    static JSONObject render(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        Engine e = new Engine(a.getJSONArray("clips"));
        File out = Workspace.newFile(c);
        try {
            if (e.end <= 0) throw new IllegalStateException("empty");
            final int BL = 4096, ch = e.ch;
            float[][] mix = new float[2][BL], o = new float[ch][BL];
            Limiter lim = new Limiter(ch, RATE, -1.0, 0);
            lim.to(new Studio.WavNode(out, RATE));
            Node head = lim;
            for (long pos = 0; pos < e.end; pos += BL) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                int n = (int) Math.min(BL, e.end - pos);
                e.render(pos, n, mix);
                if (ch == 1) System.arraycopy(mix[0], 0, o[0], 0, n);
                else { System.arraycopy(mix[0], 0, o[0], 0, n); System.arraycopy(mix[1], 0, o[1], 0, n); }
                head.push(o, n);
                if (p != null && (pos / BL) % 32 == 0) p.on((double) pos / e.end);
            }
            head.end();
            JSONObject r = new JSONObject();
            r.put("path", out.getAbsolutePath());
            r.put("dur", e.end * 1000.0 / RATE);
            return r;
        } catch (Exception x) {
            out.delete();
            throw x;
        } finally {
            e.close();
        }
    }
}
