package app.sesdefteri;

import android.content.Context;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

/** Real Archive.java with filesystem-backed Android boundary doubles. No codec/device claims. */
public class ArchiveTest {
    static Context c;
    static AtomicBoolean cancel = new AtomicBoolean();
    static AudioTools.Progress progress = new AudioTools.Progress() { public void on(double v) {} };
    static byte[] bytes(String s) throws Exception { return s.getBytes("UTF-8"); }
    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
    static File zip(String json, Map<String, byte[]> entries) throws Exception {
        File f = File.createTempFile("archive", ".zip", c.getCacheDir());
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) { z.putNextEntry(new ZipEntry(e.getKey())); z.write(e.getValue()); z.closeEntry(); }
            if (json != null) { z.putNextEntry(new ZipEntry(Archive.META)); z.write(bytes(json)); z.closeEntry(); }
        }
        return f;
    }
    static JSONObject meta(String entry) {
        JSONArray recs = new JSONArray();
        if (entry != null) recs.put(new JSONObject().put("e", entry).put("k", "same.wav|4").put("m", new JSONObject().put("title", "voice")));
        return new JSONObject().put("app", "sesdefteri").put("v", 1).put("recs", recs);
    }
    static JSONObject restore(File f) throws Exception {
        JSONObject result = Archive.restore(c, new JSONObject().put("uri", f.getAbsolutePath()).put("have", new JSONArray().put("same.wav|4")), progress, cancel);
        check(Archive.pendingExists(c), "replay receipt missing");
        check(!Archive.acknowledge(c, "wrong-token") && Archive.pendingExists(c), "wrong receipt acknowledged");
        check(Archive.acknowledge(c, result.getString("restoreToken")), "receipt acknowledge failed");
        return result;
    }
    static long libraryCount() throws Exception { try (java.util.stream.Stream<Path> s = Files.walk(c.getFilesDir().toPath())) { return s.filter(Files::isRegularFile).count(); } }
    static void rejects(File f, String label) throws Exception {
        long before = libraryCount(); boolean failed = false;
        try { restore(f); } catch (Exception e) { failed = true; }
        check(failed, label + " accepted"); check(before == libraryCount(), label + " modified library");
        for (File x : c.getCacheDir().listFiles()) check(!x.getName().startsWith("restore-"), "leaked staging");
        System.out.println("PASS " + label);
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("sesdefteri-archive-test-"); c = new Context(root.toFile());
        try {
            Map<String, byte[]> audio = new LinkedHashMap<String, byte[]>(); audio.put("audio/0/same.wav", bytes("abcd"));
            rejects(zip(null, audio), "metadata absent");
            rejects(zip(meta("audio/0/same.wav").put("app", "other").toString(), audio), "wrong application");
            rejects(zip("{invalid", audio), "malformed metadata");
            rejects(zip(meta("audio/0/missing.wav").toString(), audio), "missing recording");
            rejects(zip(meta(null).toString(), Collections.singletonMap("proj/../escape.wav", bytes("bad"))), "path traversal");
            JSONObject badHash = new JSONObject().put("audio/0/same.wav", new JSONObject().put("size", 4).put("sha256", "wrong"));
            rejects(zip(meta("audio/0/same.wav").put("v", 2).put("manifest", badHash).toString(), audio), "checksum mismatch");
            File valid = zip(meta("audio/0/same.wav").toString(), audio);
            File truncated = new File(c.getCacheDir(), "truncated.zip");
            byte[] full = Files.readAllBytes(valid.toPath()); Files.write(truncated.toPath(), Arrays.copyOf(full, full.length - 22));
            rejects(truncated, "truncated ZIP footer");
            JSONObject first = restore(valid), second = restore(valid);
            JSONObject receipt = Archive.restore(c, new JSONObject().put("uri", valid.getAbsolutePath()), progress, cancel);
            c = new Context(root.toFile()); // Simulate a fresh Activity/context; the result must still be replayable.
            check(new JSONObject(Archive.pending(c)).getString("restoreToken").equals(receipt.getString("restoreToken")), "receipt lost after recreation");
            rejects(valid, "new restore cannot overwrite unacknowledged result");
            check(Archive.acknowledge(c, receipt.getString("restoreToken")), "receipt replay acknowledge");
            System.out.println("PASS restore result survives context recreation until acknowledged");
            String one = first.getJSONObject("items").getJSONObject("audio/0/same.wav").getString("path");
            String two = second.getJSONObject("items").getJSONObject("audio/0/same.wav").getString("path");
            check(!one.equals(two) && Arrays.equals(Files.readAllBytes(Paths.get(one)), bytes("abcd")), "name-size dedupe lost data");
            System.out.println("PASS name/size matches preserve separate recordings");
            cancel.set(true); rejects(valid, "cancel before restore"); cancel.set(false);
            progress = new AudioTools.Progress() { public void on(double v) { cancel.set(true); } };
            rejects(valid, "cancel during commit rolls back"); cancel.set(false);
            progress = new AudioTools.Progress() { public void on(double v) {} };
            MediaLib.failPublish = true; rejects(valid, "publish failure rolls back"); MediaLib.failPublish = false;
            MediaLib.failCopy = true; rejects(valid, "copy failure rolls back"); MediaLib.failCopy = false;
            Map<String, byte[]> project = new LinkedHashMap<String, byte[]>(); project.put("proj/shared.wav", bytes("complete"));
            File existing = new File(Timeline.dir(c), "shared.wav"); Files.write(existing.toPath(), bytes("partial"));
            JSONObject pm = meta(null).put("proj", new JSONObject().put("clips", new JSONArray().put(new JSONObject().put("path", "/old/shared.wav"))));
            JSONObject result = restore(zip(pm.toString(), project));
            File restored = new File(result.getJSONObject("paths").getString("proj/shared.wav"));
            check(Arrays.equals(Files.readAllBytes(restored.toPath()), bytes("complete")), "existing partial skipped");
            check(Arrays.equals(Files.readAllBytes(existing.toPath()), bytes("partial")), "existing file overwritten");
            System.out.println("PASS partial/colliding project files are neither reused nor overwritten");
            // Exercise real backup, manifest generation and v2 restore with quoted/backslash metadata.
            File backup = new File(c.getCacheDir(), "backup.zip");
            JSONObject bm = meta("audio/0/same.wav"); bm.getJSONArray("recs").getJSONObject(0).getJSONObject("m").put("title", "quoted \"title\" \\ notes");
            Archive.backup(c, new JSONObject().put("uri", backup.getAbsolutePath()).put("json", bm.toString()).put("files", new JSONArray().put(new JSONArray().put("audio/0/same.wav").put(new JSONObject().put("path", one)))), progress, cancel);
            JSONObject roundtrip = restore(backup);
            check(new JSONObject(roundtrip.getString("json")).getInt("v") == 2, "manifest version");
            check(roundtrip.getJSONObject("items").length() == 1, "backup roundtrip");
            System.out.println("PASS v2 backup/hash/restore round trip");
            rejects(zip(meta(null).toString(), Collections.singletonMap("junk.bin", new byte[1])), "unexpected entry");
            byte[] huge = new byte[(int) Archive.MAX_META + 1]; Arrays.fill(huge, (byte) ' ');
            rejects(zip(new String(huge, "UTF-8"), Collections.<String,byte[]>emptyMap()), "metadata limit");
        } finally { try (java.util.stream.Stream<Path> s = Files.walk(root)) { s.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (IOException e) { throw new RuntimeException(e); } }); } }
    }
}
class AudioTools {
    interface Progress { void on(double v); }
    static int remux(File raw, java.io.FileDescriptor fd, File out) throws Exception { throw new IOException("injected remux failure"); }
}
class Timeline { static File dir(Context c) { File f = new File(c.getFilesDir(), "proj"); f.mkdirs(); return f; } }
class MainActivity { static File photoDir(Context c) { File f = new File(c.getFilesDir(), "photos"); f.mkdirs(); return f; } }
class MediaLib {
    static boolean failCopy, failPublish;
    static class Target { File file; Uri uri; String name; }
    static Uri srcUri(JSONObject o) { return o.has("uri") ? Uri.parse(o.getString("uri")) : null; }
    static File srcFile(JSONObject o) { return o.has("path") ? new File(o.getString("path")) : null; }
    static String mimeForName(String n) { return "audio/wav"; }
    static Target create(Context c, String n, String m, String d) throws Exception { Target t = new Target(); t.file = File.createTempFile("audio-", ".wav", c.getFilesDir()); return t; }
    static OutputStream open(Context c, Target t) throws Exception {
        if (failCopy) return new FilterOutputStream(new FileOutputStream(t.file)) { public void write(byte[] b, int o, int n) throws IOException { out.write(b, o, Math.max(1, n / 2)); throw new IOException("injected"); } };
        return new FileOutputStream(t.file);
    }
    static JSONObject publish(Context c, Target t) { return failPublish ? null : new JSONObject().put("path", t.file.getAbsolutePath()); }
    static void discard(Context c, Target t) { t.file.delete(); }
}
