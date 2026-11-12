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
        try {
            z.setLevel(6);
            z.putNextEntry(new ZipEntry(META));
            z.write(a.getString("json").getBytes("UTF-8"));
            z.closeEntry();
            z.setLevel(Deflater.NO_COMPRESSION);   // audio and photos are compressed already
            for (int i = 0; i < files.length(); i++) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                JSONArray f = files.getJSONArray(i);
                InputStream in;
                try { in = openSrc(c, f.getJSONObject(1)); } catch (Exception e) { missing++; continue; }
                if (in == null) { missing++; continue; }
                try {
                    z.putNextEntry(new ZipEntry(f.getString(0)));
                    int n;
                    while ((n = in.read(b)) > 0) z.write(b, 0, n);
                    z.closeEntry();
                } finally { in.close(); }
                p.on(++done / (double) Math.max(1, total));
            }
            if (photos != null) for (File f : photos) {
                if (!f.getName().endsWith(".jpg")) continue;
                z.putNextEntry(new ZipEntry("photos/" + f.getName()));
                InputStream in = new FileInputStream(f);
                try { int n; while ((n = in.read(b)) > 0) z.write(b, 0, n); } finally { in.close(); }
                z.closeEntry();
                p.on(++done / (double) Math.max(1, total));
            }
            z.finish();
            ok = true;
        } finally {
            try { z.close(); } catch (Exception e) { if (ok) throw e; }
            if (!ok) try { android.provider.DocumentsContract.deleteDocument(c.getContentResolver(), out); } catch (Exception ignored) { }
        }
        return new JSONObject().put("n", files.length() - missing).put("missing", missing);
    }

    /** a: {uri, have: ["name|size"…] files already on the phone}; result {json, items: {entry: item}, skip: {entry: true}, projDir} */
    static JSONObject restore(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        Uri in = Uri.parse(a.getString("uri"));
        HashSet<String> have = new HashSet<String>(), skipE = new HashSet<String>();
        JSONArray h = a.optJSONArray("have");
        if (h != null) for (int i = 0; i < h.length(); i++) have.add(h.getString(i));
        long size = -1;
        try {
            android.content.res.AssetFileDescriptor fd = c.getContentResolver().openAssetFileDescriptor(in, "r");
            if (fd != null) { size = fd.getLength(); fd.close(); }
        } catch (Exception ignored) { }
        final long[] read = { 0 };
        InputStream raw = c.getContentResolver().openInputStream(in);
        if (raw == null) throw new IllegalStateException("restore-open");
        final InputStream counted = new java.io.FilterInputStream(new java.io.BufferedInputStream(raw, 1 << 16)) {
            @Override public int read(byte[] x, int o, int n) throws java.io.IOException { int k = super.read(x, o, n); if (k > 0) read[0] += k; return k; }
        };
        ZipInputStream z = new ZipInputStream(counted);
        JSONObject res = new JSONObject(), items = new JSONObject(), skip = new JSONObject();
        String json = null;
        byte[] b = new byte[1 << 16];
        File photoDir = MainActivity.photoDir(c);
        try {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                String name = e.getName();
                if (e.isDirectory() || name.contains("../") || name.startsWith("/")) continue;
                if (META.equals(name)) {
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    int n;
                    while ((n = z.read(b)) > 0) bo.write(b, 0, n);
                    json = bo.toString("UTF-8");
                    // recordings already on this phone (same file name and size) are not written twice
                    try {
                        JSONArray recs = new JSONObject(json).optJSONArray("recs");
                        if (recs != null) for (int i = 0; i < recs.length(); i++) {
                            JSONObject o = recs.getJSONObject(i);
                            if (have.contains(o.optString("k"))) skipE.add(o.optString("e"));
                        }
                    } catch (Exception ignored) { }
                } else if (name.startsWith("proj/")) {
                    String pn = name.substring(5);
                    if (!pn.matches("[A-Za-z0-9_.-]{1,96}\\.wav")) continue;
                    File f = new File(Timeline.dir(c), pn);
                    if (f.isFile()) continue;
                    OutputStream o = new FileOutputStream(f);
                    try { int n; while ((n = z.read(b)) > 0) o.write(b, 0, n); } finally { o.close(); }
                } else if (name.startsWith("photos/")) {
                    String pn = name.substring(7);
                    if (!pn.matches("[A-Za-z0-9_-]{1,64}\\.jpg")) continue;
                    File f = new File(photoDir, pn);
                    if (f.isFile()) continue;
                    OutputStream o = new FileOutputStream(f);
                    try { int n; while ((n = z.read(b)) > 0) o.write(b, 0, n); } finally { o.close(); }
                } else if (name.startsWith("audio/")) {
                    // "audio/<n>/<file name>": the file keeps its own name
                    String fn = name.substring(name.lastIndexOf('/') + 1);
                    if (skipE.contains(name)) { skip.put(name, true); continue; }
                    MediaLib.Target t = MediaLib.create(c, fn, MediaLib.mimeForName(fn), "");
                    OutputStream o = MediaLib.open(c, t);
                    boolean ok = false;
                    try { int n; while ((n = z.read(b)) > 0) o.write(b, 0, n); ok = true; } finally { o.close(); if (!ok) MediaLib.discard(c, t); }
                    JSONObject it = MediaLib.publish(c, t);
                    if (it != null) items.put(name, it);
                }
                if (size > 0) p.on(Math.min(0.99, read[0] / (double) size));
            }
        } finally {
            z.close();
        }
        if (json == null) throw new IllegalStateException("restore-bad");
        return res.put("json", json).put("items", items).put("skip", skip).put("projDir", Timeline.dir(c).getAbsolutePath());
    }
}
