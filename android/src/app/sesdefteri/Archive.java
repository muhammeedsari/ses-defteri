package app.sesdefteri;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 5.18: cloud-free backup. One .zip the user saves where they like (Downloads, a memory card, a USB stick, their own cloud app):
 * sesdefteri.json (titles, notes, marks, texts, categories), the recordings' audio, the photos. Restore puts the audio back into
 * Music/Ses Defteri and hands the notes back to the page, which joins them to the new files.
 */
final class Archive {
    private Archive() { }

    static final String META = "sesdefteri.json";
    // Metadata is bounded independently of audio. Audio is streamed and also bounded by free disk space.
    static final long MAX_META = 16L << 20, MAX_ENTRY = 16L << 30, MAX_TOTAL = 64L << 30;
    static final int MAX_ENTRIES = 100000;
    private static File receipt(Context c) { return new File(c.getFilesDir(), "restore-result.json"); }
    static boolean pendingExists(Context c) { return receipt(c).isFile(); }
    static synchronized String pending(Context c) throws Exception {
        File f = receipt(c);
        if (!f.isFile()) return "";
        if (f.length() > (64L << 20)) throw new java.io.IOException("archive-limit");
        byte[] b = new byte[(int) f.length()];
        try (java.io.DataInputStream in = new java.io.DataInputStream(new FileInputStream(f))) { in.readFully(b); }
        return new String(b, "UTF-8");
    }
    static synchronized boolean acknowledge(Context c, String token) throws Exception {
        String data = pending(c);
        return data.isEmpty() || (new JSONObject(data).getString("restoreToken").equals(token) && receipt(c).delete());
    }

    private static void check(AtomicBoolean cancel) throws InterruptedException {
        if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte v : b) s.append(String.format(java.util.Locale.ROOT, "%02x", v & 255));
        return s.toString();
    }

    private static JSONObject write(InputStream in, OutputStream out, long limit, AtomicBoolean cancel) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] b = new byte[65536]; long size = 0; int n;
        while ((n = in.read(b)) != -1) {
            check(cancel); size += n;
            if (size > limit) throw new java.io.IOException("archive-limit");
            out.write(b, 0, n); md.update(b, 0, n);
        }
        check(cancel);
        return new JSONObject().put("size", size).put("sha256", hex(md.digest()));
    }

    private static void removeTree(File f) {
        File[] files = f.listFiles();
        if (files != null) for (File child : files) removeTree(child);
        f.delete();
    }

    private static InputStream openSrc(Context c, JSONObject src) throws Exception {
        Uri u = MediaLib.srcUri(src);
        if (u != null) return c.getContentResolver().openInputStream(u);
        File f = MediaLib.srcFile(src);
        if (f == null) throw new IllegalStateException("no source");
        return new FileInputStream(f);
    }

    /** a: {uri: where to write, json: the notes, files: [[entry, src]…]}; result {n: files written, missing: n} */
    static JSONObject backup(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        Uri out = Uri.parse(a.getString("uri"));
        JSONArray files = a.optJSONArray("files");
        if (files == null) files = new JSONArray();
        File[] photos = MainActivity.photoDir(c).listFiles();
        int total = files.length() + (photos == null ? 0 : photos.length), done = 0, missing = 0;
        OutputStream os = c.getContentResolver().openOutputStream(out, "w");
        if (os == null) throw new IllegalStateException("backup-open");
        ZipOutputStream z = new ZipOutputStream(new java.io.BufferedOutputStream(os, 1 << 16));
        byte[] b = new byte[1 << 16];
        boolean ok = false;
        JSONObject manifest = new JSONObject();
        long totalBytes = 0;
        try {
            if (total >= MAX_ENTRIES) throw new java.io.IOException("archive-limit");
            z.setLevel(6);
            z.setLevel(Deflater.NO_COMPRESSION);   // audio and photos are compressed already
            for (int i = 0; i < files.length(); i++) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                JSONArray f = files.getJSONArray(i);
                InputStream in;
                in = openSrc(c, f.getJSONObject(1));
                if (in == null) throw new java.io.IOException("backup-missing");
                try {
                    z.putNextEntry(new ZipEntry(f.getString(0)));
                    JSONObject sum = write(in, z, Math.min(MAX_ENTRY, MAX_TOTAL - totalBytes), cancel);
                    manifest.put(f.getString(0), sum); totalBytes += sum.getLong("size");
                    z.closeEntry();
                } finally { in.close(); }
                p.on(++done / (double) Math.max(1, total));
            }
            if (photos != null) for (File f : photos) {
                check(cancel);
                if (!f.getName().endsWith(".jpg")) continue;
                z.putNextEntry(new ZipEntry("photos/" + f.getName()));
                InputStream in = new FileInputStream(f);
                try {
                    JSONObject sum = write(in, z, Math.min(MAX_ENTRY, MAX_TOTAL - totalBytes), cancel);
                    manifest.put("photos/" + f.getName(), sum); totalBytes += sum.getLong("size");
                } finally { in.close(); }
                z.closeEntry();
                p.on(++done / (double) Math.max(1, total));
            }
            JSONObject meta = new JSONObject(a.getString("json"));
            meta.put("v", 2); meta.put("manifest", manifest);
            byte[] metadata = meta.toString().getBytes("UTF-8");
            if (metadata.length > MAX_META) throw new java.io.IOException("archive-limit");
            check(cancel);
            z.putNextEntry(new ZipEntry(META)); z.write(metadata); z.closeEntry();
            z.finish();
            ok = true;
        } finally {
            try { z.close(); } catch (Exception e) { if (ok) throw e; }
            if (!ok) try { android.provider.DocumentsContract.deleteDocument(c.getContentResolver(), out); } catch (Exception ignored) { }
        }
        return new JSONObject().put("n", files.length() - missing).put("missing", missing);
    }

    /** Validate the entire archive in staging before creating any library/project files. Never dedupe on name/size. */
    static synchronized JSONObject restore(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        if (pendingExists(c)) throw new java.io.IOException("restore-pending");
        Uri in = Uri.parse(a.getString("uri"));
        File stage = new File(c.getCacheDir(), "restore-" + java.util.UUID.randomUUID());
        if (!stage.mkdirs()) throw new java.io.IOException("restore-open");
        java.util.LinkedHashMap<String, File> files = new java.util.LinkedHashMap<String, File>();
        java.util.HashMap<String, JSONObject> checks = new java.util.HashMap<String, JSONObject>();
        java.util.ArrayList<File> created = new java.util.ArrayList<File>();
        java.util.ArrayList<MediaLib.Target> targets = new java.util.ArrayList<MediaLib.Target>();
        boolean committed = false;
        try {
            long total = 0;
            HashSet<String> names = new HashSet<String>();
            String json = null;
            InputStream raw = c.getContentResolver().openInputStream(in);
            if (raw == null) throw new java.io.IOException("restore-open");
            // A forward-only ZIP reader accepts a missing central directory. Keep a bounded local copy so
            // ZipFile can reject a truncated archive before the commit phase, including footer-only truncation.
            File archive = new File(stage, "input.zip");
            try (InputStream source = raw; OutputStream out = new FileOutputStream(archive)) {
                write(source, out, Math.min(MAX_TOTAL, Math.max(0, stage.getUsableSpace() - (32L << 20))), cancel);
            }
            int entries;
            try (java.util.zip.ZipFile central = new java.util.zip.ZipFile(archive)) {
                entries = central.size();
                if (entries > MAX_ENTRIES) throw new java.io.IOException("archive-limit");
            }
            long budget = Math.min(MAX_TOTAL, Math.max(0, stage.getUsableSpace() - (32L << 20)));
            try (ZipInputStream z = new ZipInputStream(new java.io.BufferedInputStream(new FileInputStream(archive), 65536))) {
                ZipEntry e;
                while ((e = z.getNextEntry()) != null) {
                    check(cancel);
                    if (names.size() >= MAX_ENTRIES) throw new java.io.IOException("archive-limit");
                    String name = e.getName();
                    if (!names.add(name)) throw new java.io.IOException("restore-bad");
                    if (name.startsWith("/") || name.contains("\\") || name.contains("../")) throw new java.io.IOException("restore-bad");
                    if (e.isDirectory()) continue;
                    if (META.equals(name)) {
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        write(z, bo, Math.min(MAX_META, budget - total), cancel);
                        json = bo.toString("UTF-8"); total += bo.size();
                    } else {
                        if (!name.matches("proj/[A-Za-z0-9_.-]{1,96}\\.wav") && !name.matches("photos/[A-Za-z0-9_-]{1,64}\\.jpg")
                                && !name.matches("audio/[0-9]+/[^/\\x00-\\x1f]+")) throw new java.io.IOException("restore-bad");
                        File part = new File(stage, "f" + files.size() + ".part");
                        JSONObject sum;
                        try (FileOutputStream out = new FileOutputStream(part)) { sum = write(z, out, Math.min(MAX_ENTRY, budget - total), cancel); out.getFD().sync(); }
                        files.put(name, part); checks.put(name, sum); total += sum.getLong("size");
                    }
                }
            }
            if (names.size() != entries) throw new java.io.IOException("restore-bad");
            if (json == null) throw new java.io.IOException("restore-bad");
            JSONObject meta = new JSONObject(json);
            int v = meta.optInt("v", 0);
            if (!"sesdefteri".equals(meta.optString("app")) || (v != 1 && v != 2) || meta.optJSONArray("recs") == null) throw new java.io.IOException("restore-bad");
            if (v == 2) {
                JSONObject manifest = meta.getJSONObject("manifest");
                if (manifest.length() != files.size()) throw new java.io.IOException("restore-bad");
                for (String name : files.keySet()) {
                    JSONObject expected = manifest.getJSONObject(name), actual = checks.get(name);
                    if (expected.getLong("size") != actual.getLong("size") || !expected.getString("sha256").equals(actual.getString("sha256"))) throw new java.io.IOException("restore-bad");
                }
            }
            JSONArray recs = meta.getJSONArray("recs");
            HashSet<String> audio = new HashSet<String>();
            for (int i = 0; i < recs.length(); i++) {
                JSONObject rec = recs.getJSONObject(i); String entry = rec.getString("e"); rec.getJSONObject("m");
                if (!entry.startsWith("audio/") || !files.containsKey(entry) || !audio.add(entry)) throw new java.io.IOException("restore-bad");
            }
            JSONObject project = meta.optJSONObject("proj");
            if (v == 1 && project != null) {
                JSONArray clips = project.getJSONArray("clips");
                for (int n = 0; n < clips.length(); n++) {
                    String path = clips.getJSONObject(n).getString("path");
                    if (!files.containsKey("proj/" + path.substring(path.lastIndexOf('/') + 1))) throw new java.io.IOException("backup-missing");
                }
            } else validateProject(project, files, v);
            validateProject(meta.optJSONObject("templates"), files, v);
            if (stage.getUsableSpace() < total + (16L << 20)) throw new java.io.IOException("archive-space");
            JSONObject items = new JSONObject(), paths = new JSONObject(), photos = new JSONObject();
            int done = 0;
            for (java.util.Map.Entry<String, File> entry : files.entrySet()) {
                check(cancel); String name = entry.getKey(); File f = entry.getValue();
                if (name.startsWith("audio/")) {
                    if (!audio.contains(name)) throw new java.io.IOException("restore-bad");
                    String fn = name.substring(name.lastIndexOf('/') + 1);
                    MediaLib.Target t = MediaLib.create(c, fn, MediaLib.mimeForName(fn), ""); targets.add(t);
                    try (InputStream source = new FileInputStream(f); OutputStream out = MediaLib.open(c, t)) { write(source, out, MAX_ENTRY, cancel); }
                } else {
                    boolean photo = name.startsWith("photos/");
                    File dest = new File(photo ? MainActivity.photoDir(c) : Timeline.dir(c), java.util.UUID.randomUUID().toString() + (photo ? ".jpg" : ".wav"));
                    if (dest.exists()) throw new java.io.IOException("restore-collision");
                    if (!f.renameTo(dest)) throw new java.io.IOException("restore-move");
                    created.add(dest);
                    if (photo) photos.put(name.substring(7), dest.getName()); else paths.put(name, dest.getAbsolutePath());
                }
                p.on(0.5 * ++done / Math.max(1, files.size()));
            }
            int i = 0;
            for (String name : files.keySet()) if (name.startsWith("audio/")) {
                check(cancel); JSONObject item = MediaLib.publish(c, targets.get(i++));
                if (item == null) throw new java.io.IOException("publish");
                items.put(name, item);
            }
            check(cancel);
            JSONObject res = new JSONObject().put("json", json).put("items", items).put("skip", new JSONObject()).put("paths", paths).put("photos", photos).put("projDir", Timeline.dir(c).getAbsolutePath()).put("restoreToken", java.util.UUID.randomUUID().toString());
            File part = new File(c.getFilesDir(), "restore-result.json.part");
            byte[] result = res.toString().getBytes("UTF-8");
            if (result.length > (64L << 20)) throw new java.io.IOException("archive-limit");
            try (FileOutputStream out = new FileOutputStream(part)) { out.write(result); out.getFD().sync(); }
            if (!part.renameTo(receipt(c))) throw new java.io.IOException("restore-move");
            committed = true; p.on(1); return res;
        } finally {
            if (!committed) { for (MediaLib.Target t : targets) MediaLib.discard(c, t); for (File f : created) f.delete(); }
            if (!committed) new File(c.getFilesDir(), "restore-result.json.part").delete();
            removeTree(stage);
        }
    }

    private static void validateProject(Object value, java.util.Map<String, File> files, int v) throws Exception {
        if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            for (int i = 0; i < a.length(); i++) validateProject(a.get(i), files, v);
        } else if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            java.util.Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next(); Object x = o.get(k);
                if (("path".equals(k) || "src".equals(k) || "prev".equals(k) || "hcOut".equals(k)) && x instanceof String && !((String) x).isEmpty()) {
                    String path = (String) x;
                    // Old archives did not include all history. Their current clip still must be present.
                    if (v == 2 || "path".equals(k)) {
                        String entry = v == 2 ? path : "proj/" + path.substring(path.lastIndexOf('/') + 1);
                        if (!files.containsKey(entry)) throw new java.io.IOException("backup-missing");
                    }
                } else validateProject(x, files, v);
            }
        }
    }
}
