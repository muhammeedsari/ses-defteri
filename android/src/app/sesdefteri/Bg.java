package app.sesdefteri;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 4.1: work that outlives the screen: model downloads and speech-to-text. It runs in the app process (kept alive by
 * WorkService), not in the activity, so it goes on when the app is left or its window is swiped away. Each task can be
 * paused and resumed where it stopped (downloads keep their .part file, speech-to-text keeps the sentences done so far).
 * The page asks for the list (no callbacks), so a page opened later still sees the work and its results.
 */
final class Bg {
    private Bg() { }

    static final class Task {
        String key, kind, label;
        JSONObject args;
        volatile String state = "run";   // run | pause | done | fail
        volatile double p;
        volatile String err = "";
        volatile boolean wantPause;
        AtomicBoolean cancel = new AtomicBoolean(false);
    }

    private static final Map<String, Task> tasks = new LinkedHashMap<>();
    // downloads go side by side with speech-to-text, never behind the studio's work
    private static final ExecutorService net = Executors.newSingleThreadExecutor(daemon("bg-net"));
    private static final ExecutorService cpu = Executors.newSingleThreadExecutor(daemon("bg-cpu"));
    private static Context app;

    private static java.util.concurrent.ThreadFactory daemon(final String name) {
        return new java.util.concurrent.ThreadFactory() {
            @Override public Thread newThread(Runnable r) { Thread t = new Thread(r, name); t.setPriority(Thread.NORM_PRIORITY - 1); return t; }
        };
    }

    /** once per process: work that was running when the process ended starts again */
    static synchronized void init(Context c) {
        if (app != null) return;
        app = c.getApplicationContext();
        try {
            JSONArray a = new JSONArray(prefs().getString("tasks", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Task t = new Task();
                t.key = o.getString("key"); t.kind = o.getString("kind"); t.label = o.optString("label", "");
                t.args = o.optJSONObject("args"); if (t.args == null) t.args = new JSONObject();
                t.state = o.optString("state", "pause"); t.p = o.optDouble("p", 0); t.err = o.optString("err", "");
                tasks.put(t.key, t);
            }
        } catch (Exception ignored) { }
        for (Task t : new ArrayList<>(tasks.values())) if ("run".equals(t.state)) submit(t);
    }

    private static SharedPreferences prefs() { return app.getSharedPreferences("bg", Context.MODE_PRIVATE); }

    private static synchronized void save() {
        try {
            JSONArray a = new JSONArray();
            for (Task t : tasks.values()) {
                JSONObject o = new JSONObject();
                o.put("key", t.key); o.put("kind", t.kind); o.put("label", t.label); o.put("args", t.args);
                o.put("state", t.state); o.put("p", t.p); o.put("err", t.err);
                a.put(o);
            }
            prefs().edit().putString("tasks", a.toString()).apply();
        } catch (Exception ignored) { }
    }

    static File resultFile(String key) {
        File d = new File(app.getFilesDir(), "bg");
        if (!d.exists()) d.mkdirs();
        return new File(d, key.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
    }
    static File partFile(String key) { return new File(resultFile(key).getPath() + ".part"); }

    /** starts (or resumes) the task; a running one is left alone */
    static synchronized String start(String kind, String key, String label, JSONObject args) {
        Task t = tasks.get(key);
        if (t != null && "run".equals(t.state)) return key;
        if (t == null) { t = new Task(); t.key = key; tasks.put(key, t); }
        t.kind = kind; t.label = label == null ? "" : label; t.args = args == null ? new JSONObject() : args;
        t.state = "run"; t.err = ""; t.wantPause = false; t.cancel = new AtomicBoolean(false);
        save();
        submit(t);
        return key;
    }

    static synchronized void resume(String key) {
        Task t = tasks.get(key);
        if (t != null && !"run".equals(t.state) && !"done".equals(t.state)) start(t.kind, t.key, t.label, t.args);
    }

    static synchronized void pause(String key) {
        Task t = tasks.get(key);
        if (t == null || !"run".equals(t.state)) return;
        t.wantPause = true;
        t.cancel.set(true);
    }

    /** stops and forgets the task with what it had done */
    static synchronized void cancel(String key) {
        Task t = tasks.remove(key);
        if (t != null) { t.wantPause = false; t.cancel.set(true); }
        partFile(key).delete();
        resultFile(key).delete();
        save();
    }

    /** the page took the result: forget the task */
    static synchronized void forget(String key) {
        Task t = tasks.get(key);
        if (t != null && "run".equals(t.state)) return;
        tasks.remove(key);
        resultFile(key).delete();
        save();
    }

    static synchronized String list() {
        JSONArray a = new JSONArray();
        try {
            for (Task t : tasks.values()) {
                JSONObject o = new JSONObject();
                o.put("key", t.key); o.put("kind", t.kind); o.put("label", t.label);
                o.put("state", t.state); o.put("p", Math.round(t.p * 1000) / 1000.0); o.put("err", t.err);
                a.put(o);
            }
        } catch (Exception ignored) { }
        return a.toString();
    }

    static String result(String key) {
        try { return readText(resultFile(key)); } catch (Exception e) { return ""; }
    }

    static synchronized boolean busy() {
        for (Task t : tasks.values()) if ("run".equals(t.state)) return true;
        return false;
    }

    private static void submit(final Task t) {
        boolean dl = t.kind.endsWith("Download") || "ttsGet".equals(t.kind) || "spkGet".equals(t.kind) || "hdGet".equals(t.kind);
        (dl ? net : cpu).execute(new Runnable() {
            @Override public void run() { work(t); }
        });
    }

    private static void work(final Task t) {
        // 4.2: the run's own cancel flag: a quick pause + resume gives the task a new one, and the old run ending late
        // must not mark the new run as failed or paused
        final AtomicBoolean cancel = t.cancel;
        if (cancel.get()) { finishCancelled(t, cancel); return; }
        WorkService.begin(app, true);
        WorkService.label(t.label);
        final long[] last = { 0 };
        AudioTools.Progress p = new AudioTools.Progress() {
            @Override public void on(double f) {
                if (t.cancel != cancel) return;
                t.p = Math.max(0, Math.min(1, f));
                WorkService.label(t.label);
                WorkService.progress(app, t.p);
                long now = android.os.SystemClock.uptimeMillis();
                if (now - last[0] > 3000) { last[0] = now; save(); }   // the percentage survives a restart
            }
        };
        try {
            JSONObject res;
            JSONObject a = new JSONObject(t.args.toString());
            if ("asrDownload".equals(t.kind)) res = AsrModels.download(app, a, p, cancel);
            else if ("ttsGet".equals(t.kind)) res = Tts.download(app, a, p, cancel);
            else if ("spkGet".equals(t.kind)) res = Speakers.download(app, p, cancel);
            else if ("hdGet".equals(t.kind)) res = Hd.download(app, p, cancel);
            else if ("transcribe".equals(t.kind)) {
                JSONObject src = a.getJSONObject("src");
                src.put("_model", a.optString("model", ""));
                res = Studio.transcribe(app, src, a.optString("lang", ""), p, cancel, partFile(t.key));
            } else throw new IllegalArgumentException("unknown");
            if (cancel.get()) throw new InterruptedException("cancel");
            synchronized (Bg.class) {
                if (t.cancel != cancel || tasks.get(t.key) != t) return;
                writeText(resultFile(t.key), res.toString());
                partFile(t.key).delete();
                t.p = 1; t.state = "done"; save();
            }
        } catch (Throwable e) {
            if (cancel.get()) finishCancelled(t, cancel);
            else synchronized (Bg.class) {
                if (t.cancel != cancel || tasks.get(t.key) != t) return;
                t.state = "fail";
                String m = String.valueOf(e.getMessage());
                t.err = m.length() > 160 ? m.substring(0, 160) : m;
                save();
                MainActivity.saveNote(app, "bg " + t.kind + ": " + t.err);
            }
        } finally {
            WorkService.end(app);
        }
    }

    private static synchronized void finishCancelled(Task t, AtomicBoolean cancel) {
        if (tasks.get(t.key) != t || t.cancel != cancel) return;   // cancelled for good, or started again meanwhile
        t.state = t.wantPause ? "pause" : "fail";
        if (!t.wantPause) t.err = "cancel";
        save();
    }

    static String readText(File f) throws Exception {
        byte[] b = new byte[(int) f.length()];
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try { int o = 0, n; while (o < b.length && (n = in.read(b, o, b.length - o)) > 0) o += n; } finally { in.close(); }
        return new String(b, "UTF-8");
    }
    static void writeText(File f, String s) throws Exception {
        File tmp = new File(f.getPath() + ".tmp");
        java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
        try { out.write(s.getBytes("UTF-8")); } finally { out.close(); }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f); }
    }

    static List<String> keys() { synchronized (Bg.class) { return new ArrayList<>(tasks.keySet()); } }
}
