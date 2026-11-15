package app.sesdefteri;

import android.content.Context;

import app.sesdefteri.asr.Keywords;
import app.sesdefteri.asr.Live;
import app.sesdefteri.asr.Transcriber;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 5.16: live text while recording, only when the person switches it on. Uses the Whisper model already downloaded
 * for speech-to-text (base first: small is too slow to keep up on most phones). Nothing leaves the phone.
 */
final class LiveText {
    private LiveText() { }

    private static volatile Live cur;
    private static Thread th;
    private static String model = "";
    // the sentences of the current recording, across switching off and on again
    private static final List<Transcriber.Segment> all = new ArrayList<>();

    /** a new recording starts: nothing from the last one is kept */
    static synchronized void reset() {
        stop(true);
        synchronized (all) { all.clear(); }
    }

    /** switch on (also during a recording); sec = seconds recorded so far */
    static synchronized String on(final Context c, final String lang, int rate, double sec) {
        JSONObject r = new JSONObject();
        try {
            if (cur != null && !cur.done) { r.put("ok", true); return r.toString(); }
            final String m = AsrModels.pick(c, "base");
            if (m == null) { r.put("ok", false); r.put("error", "asr-missing"); return r.toString(); }
            model = m;
            if (rate <= 0) { r.put("ok", false); r.put("error", "idle"); return r.toString(); }
            Live l = new Live(new Live.Maker() {
                @Override public Transcriber make() {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                    return new Transcriber(null, AsrModels.dir(c, m).getAbsolutePath(), m, lang == null ? "" : lang, Device.threads(2));
                }
            }, rate, sec, all, 60);
            cur = l;
            th = new Thread(l, "live-text");
            th.start();
            r.put("ok", true);
            r.put("model", m);
        } catch (Exception e) {
            try { r.put("ok", false); r.put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) { }
        }
        return r.toString();
    }

    /** switch off: what is waiting is still written down (abort: dropped) */
    static synchronized void stop(boolean abort) {
        Live l = cur;
        if (l == null) return;
        if (abort) l.abort(); else l.finish();
        cur = null;
    }

    /** from the recording thread, every block it writes */
    static void feed(float[][] ch, int chans, int n) {
        Live l = cur;
        if (l != null) l.feed(ch, chans, n);
    }

    /** state + the sentences from index from on */
    static String poll(int from) {
        JSONObject r = new JSONObject();
        try {
            Live l = cur;
            r.put("on", l != null && !l.done);
            if (l != null) {
                r.put("ready", l.ready);
                r.put("lag", Math.round(l.lag() * 10) / 10.0);
                r.put("skip", Math.round(l.skipped()));
                if (!l.error.isEmpty()) r.put("error", l.error);
            }
            r.put("model", model);
            r.put("segs", segs(Live.since(all, from)));
            r.put("n", from + r.getJSONArray("segs").length());
        } catch (Exception ignored) { }
        return r.toString();
    }

    /** the recording ended: waits until the backlog is written down, then gives the text like a full run would */
    static JSONObject end(java.util.concurrent.atomic.AtomicBoolean cancel) throws Exception {
        Thread t;
        Live l;
        synchronized (LiveText.class) { l = cur; stop(false); t = th; th = null; }
        if (t != null) {
            while (t.isAlive()) {
                if (cancel != null && cancel.get()) { if (l != null) l.abort(); break; }
                t.join(300);
            }
        }
        List<Transcriber.Segment> segs = Live.since(all, 0);
        // switched off and on again: the earlier run may have finished its last sentences after the new one began
        java.util.Collections.sort(segs, new java.util.Comparator<Transcriber.Segment>() {
            @Override public int compare(Transcriber.Segment a, Transcriber.Segment b) { return Double.compare(a.start, b.start); }
        });
        JSONObject res = new JSONObject();
        res.put("segments", segs(segs));
        String text = Transcriber.text(segs);
        res.put("text", text);
        res.put("srt", Transcriber.srt(segs));
        JSONArray tags = new JSONArray();
        for (String s : Keywords.tags(text, 5)) tags.put(s);
        res.put("tags", tags);
        res.put("title", segs.isEmpty() ? "" : Keywords.title(segs));
        res.put("lang", l != null ? l.lang : "");
        res.put("model", model);
        return res;
    }

    private static JSONArray segs(List<Transcriber.Segment> l) throws Exception {
        JSONArray a = new JSONArray();
        for (Transcriber.Segment s : l) {
            JSONArray x = new JSONArray();
            x.put(Math.round(s.start * 100) / 100.0); x.put(Math.round(s.end * 100) / 100.0); x.put(s.text);
            a.put(x);
        }
        return a;
    }
}
