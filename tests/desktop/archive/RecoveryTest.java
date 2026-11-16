package app.sesdefteri;
import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.*;

/** Invokes the real compiled Recorder.recover/start; Android I/O boundaries are doubled. */
public class RecoveryTest {
    static void check(boolean ok, String m) { if (!ok) throw new AssertionError(m); }
    static Field field(String name) throws Exception { Field f = Recorder.class.getDeclaredField(name); f.setAccessible(true); return f; }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("sesdefteri-recovery-"); Context c = new Context(root.toFile());
        try {
            field("state").set(null, "rec"); String format = (String) field("fmt").get(null);
            String busy = Recorder.start(c, "voice", "new.wav", "{\"fmt\":\"wav\",\"temp\":true,\"call\":false}");
            check(new JSONObject(busy).getString("error").equals("busy"), "busy rejected");
            check(format.equals(field("fmt").get(null)) && !field("temp").getBoolean(null), "busy call changed active options");
            field("state").set(null, "idle");
            System.out.println("PASS busy recorder leaves active options unchanged");
            File journal = new File(c.getFilesDir(), "rec-active.json"), raw = new File(c.getFilesDir(), "rec-active.aac");
            File wave = new File(c.getFilesDir(), "voice.wav");
            Files.write(raw.toPath(), new byte[] {1,2,3});
            JSONObject j = new JSONObject().put("file", wave.getAbsolutePath()).put("uri", "").put("fmt", "wav").put("name", "voice.wav").put("rate", 16000).put("ch", 1);
            Files.write(journal.toPath(), j.toString().getBytes("UTF-8"));
            check(Recorder.recover(c) == null, "empty file recovery succeeded");
            check(journal.exists() && raw.exists(), "failed recovery deleted sources");
            try (RandomAccessFile w = new RandomAccessFile(wave, "rw")) { w.setLength(44 + 16000 * 2); }
            MediaLib.failPublish = true;
            check(Recorder.recover(c) == null && journal.exists() && raw.exists() && wave.exists(), "publish failure deleted recovery data");
            MediaLib.failPublish = false;
            check(Recorder.recover(c) != null && !journal.exists() && !raw.exists() && wave.length() > 32000, "retry did not commit safely");
            System.out.println("PASS WAV recovery preserves journal/source on failure and commits on retry");
            j.put("fmt", "m4a"); Files.write(journal.toPath(), j.toString().getBytes("UTF-8")); Files.write(raw.toPath(), new byte[] {1,2,3});
            check(Recorder.recover(c) == null && raw.exists() && journal.exists(), "remux failure lost data");
            check(new JSONObject(Recorder.start(c, "voice", "next.m4a", "{}")).getString("error").equals("recovery-pending"), "new recording overwrote pending recovery");
            System.out.println("PASS remux failure remains retryable and cannot be overwritten by a new recording");
            j.put("fmt", "flac"); Files.write(journal.toPath(), j.toString().getBytes("UTF-8")); MediaLib.failPublish = true;
            check(Recorder.recover(c) == null && journal.exists() && wave.exists(), "FLAC publish failure lost data");
            MediaLib.failPublish = false;
            check(Recorder.recover(c) != null && !journal.exists(), "FLAC publish retry");
            System.out.println("PASS FLAC recovery publication failure preserves the file");
        } finally {
            field("state").set(null, "idle");
            try (java.util.stream.Stream<Path> s = Files.walk(root)) { s.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (IOException e) { throw new RuntimeException(e); } }); }
        }
    }
}
