package app.sesdefteri;

import android.content.Context;

import app.sesdefteri.asr.Keywords;
import app.sesdefteri.asr.Live;
import app.sesdefteri.asr.Transcriber;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 5.16: live text while recording, only when the person switches it on. Uses the Whisper model already downloaded
 * for speech-to-text (base first: small is too slow to keep up on most phones). Nothing leaves the phone.
 * When the phone cannot keep up, the audio waits in a file in the cache and is written down after the recording.
 */
final class LiveText {
    private LiveText() { }

    /** one recording: its runs (switched off and on again = several) and their sentences */
    static final class Rec {
        final List<Transcriber.Segment> segs = new ArrayList<>();
        final List<Live> runs = new ArrayList<>();
        final List<Thread> threads = new ArrayList<>();
        String model = "";
        double lag() { double s = 0; for (Live l : runs) if (!l.done) s += l.lag(); return s; }
        double skipped() { double s = 0; for (Live l : runs) s += l.skipped(); return s; }
        boolean busy() { for (Thread t : threads) if (t.isAlive()) return true; return false; }
    }

    private static volatile Live cur;
    private static Rec rec = new Rec();      // the recording going on
    private static Rec ended;                // the one that just stopped, until the page collects its text

    /** a new recording starts: nothing from the last one is kept (a stopped one still finishing goes on) */
    static synchronized void reset(Context c) {
        stop(true);
        rec = new Rec();
        // overflow files of a run the app was closed in the middle of
        List<File> keep = new ArrayList<>();
        if (ended != null) for (Live l : ended.runs) if (!l.done) keep.add(l.spillFile);
        File[] old = c.getCacheDir().listFiles();
        if (old != null) for (File f : old) if (f.getName().startsWith("live-") && f.getName().endsWith(".pcm") && !keep.contains(f)) f.delete();
    }

    /** switch on (also during a recording); sec = seconds recorded so far */
    static synchronized String on(final Context c, final String lang, int rate, double sec) {
        JSONObject r = new JSONObject();
        try {
            if (cur != null && !cur.done) { r.put("ok", true); return r.toString(); }
            final String m = AsrModels.pick(c, "base");
            if (m == null) { r.put("ok", false); r.put("error", "asr-missing"); return r.toString(); }
            if (rate <= 0) { r.put("ok", false); r.put("error", "idle"); return r.toString(); }
            rec.model = m;
            Live l = new Live(new Live.Maker() {
                @Override public Transcriber make() {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                    return new Transcriber(null, AsrModels.dir(c, m).getAbsolutePath(), m, lang == null ? "" : lang, Device.threads(2));
                }
            }, rate, sec, rec.segs, 20, new File(c.getCacheDir(), "live-" + System.nanoTime() + ".pcm"), 3 * 3600);
            Thread t = new Thread(l, "live-text");
            rec.runs.add(l);
            rec.threads.add(t);
            cur = l;
            t.start();
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

    /** the recording stopped: its runs finish what waits; "liveEnd" collects the text */
    static synchronized void close() {
        stop(false);
        ended = rec.runs.isEmpty() ? null : rec;
        rec = new Rec();
    }

    /** from the recording thread, every block it writes */
    static void feed(float[][] ch, int chans, int n) {
        Live l = cur;
        if (l != null) l.feed(ch, chans, n);
    }

    /** state + the sentences from index from on (of the recording going on) */
    static String poll(int from) {
        JSONObject r = new JSONObject();
        try {
            Live l = cur;
            Rec x = rec;
            r.put("on", l != null && !l.done);
            if (l != null) {
                r.put("ready", l.ready);
                if (!l.error.isEmpty()) r.put("error", l.error);
            }
            r.put("lag", Math.round(x.lag() * 10) / 10.0);
            r.put("skip", Math.round(x.skipped()));
            r.put("model", x.model);
            r.put("segs", segs(Live.since(x.segs, from)));
        } catch (Exception ignored) { }
        return r.toString();
    }

    /** the recording ended: waits until what waited is written down, then gives the text like a full run would */
    static JSONObject end(AudioTools.Progress p, java.util.concurrent.atomic.AtomicBoolean cancel) throws Exception {
        Rec x;
        synchronized (LiveText.class) { x = ended; ended = null; }
        JSONObject res = new JSONObject();
        if (x == null) { res.put("segments", new JSONArray()); return res; }
        double lag0 = Math.max(1, x.lag());
        res.put("waited", Math.round(x.lag()));
        while (x.busy()) {
            if (cancel != null && cancel.get()) { for (Live l : x.runs) l.abort(); break; }
            if (p != null) p.on(Math.max(0, Math.min(0.99, 1 - x.lag() / lag0)));
            Thread.sleep(300);
        }
        List<Transcriber.Segment> segs = Live.since(x.segs, 0);
        // switched off and on again: the earlier run may have finished its last sentences after the new one began
        java.util.Collections.sort(segs, new java.util.Comparator<Transcriber.Segment>() {
            @Override public int compare(Transcriber.Segment a, Transcriber.Segment b) { return Double.compare(a.start, b.start); }
        });
        res.put("segments", segs(segs));
        String text = Transcriber.text(segs);
        res.put("text", text);
        res.put("srt", Transcriber.srt(segs));
        JSONArray tags = new JSONArray();
        for (String s : Keywords.tags(text, 5)) tags.put(s);
        res.put("tags", tags);
        res.put("title", segs.isEmpty() ? "" : Keywords.title(segs));
        String lang = "";
        for (Live l : x.runs) if (!l.lang.isEmpty()) lang = l.lang;
        res.put("lang", lang);
        res.put("model", x.model);
        res.put("skip", Math.round(x.skipped()));
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
