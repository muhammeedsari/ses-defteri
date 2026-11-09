package app.sesdefteri;

/**
 * 4.15: how hard the AI may push this phone. Light mode (weak phones, or chosen after an AI tool closed the app):
 * at most 2 model threads and one chunk at a time, so less memory is in use at once.
 */
final class Device {
    private Device() { }

    static volatile boolean lite;

    static int cores() { return Runtime.getRuntime().availableProcessors(); }

    /** model threads: at most max, one core left for the phone */
    static int threads(int max) { return Math.max(1, Math.min(lite ? Math.min(2, max) : max, cores() - 1)); }

    /** model instances working side by side */
    static int workers() { int w = Integer.getInteger("sd.w", 0); return w > 0 ? w : lite ? 1 : Math.max(1, Math.min(4, cores() - 1)); }

    /* ---- crash note: written while a model works, removed when it is done; still there at start = the app was closed mid-way ---- */
    private static int busy;
    private static java.io.File note;

    static synchronized void begin(android.content.Context c, String what) {
        if (c == null || what == null) return;
        busy++;
        note = new java.io.File(c.getFilesDir(), "ai_running");
        try { java.io.FileOutputStream o = new java.io.FileOutputStream(note); o.write(what.getBytes("UTF-8")); o.close(); } catch (Exception ignored) { }
    }

    static synchronized void end() {
        if (busy > 0 && --busy == 0 && note != null) note.delete();
    }

    /** the tool that was running when the app was closed last time ("" = none); read once */
    static String lastCrash(android.content.Context c) {
        java.io.File f = new java.io.File(c.getFilesDir(), "ai_running");
        if (!f.isFile() || busy > 0) return "";
        try {
            byte[] b = new byte[(int) Math.min(64, f.length())];
            java.io.FileInputStream i = new java.io.FileInputStream(f);
            int n = i.read(b);
            i.close();
            return n > 0 ? new String(b, 0, n, "UTF-8") : "";
        } catch (Exception e) {
            return "";
        } finally {
            f.delete();
        }
    }

    /** the model tool a job runs, or null when it uses none */
    static String model(String kind, org.json.JSONObject a) {
        if ("wsOp".equals(kind)) {
            String op = a.optString("op", "");
            return "aidenoise".equals(op) || "sepdenoise".equals(op) || "hd".equals(op) || "aibreaths".equals(op) || "aipauses".equals(op) || "aigate".equals(op) || "ailevel".equals(op) ? op : null;
        }
        if ("clean".equals(kind)) return a.optBoolean("ai", false) ? "aidenoise" : null;
        if ("fillers".equals(kind)) return a.optBoolean("ai", false) ? "flai" : null;
        return "tlSep".equals(kind) || "sounds".equals(kind) || "fwords".equals(kind) || "transcribe".equals(kind) || "tlSpk".equals(kind) || "vad".equals(kind) || "ttsSpeak".equals(kind) ? kind : null;
    }
}
