package app.sesdefteri;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import app.sesdefteri.dsp.Wav;

/** Real window/disk analysis pipelines under a small heap. The sound classifier and Android decoder are doubles. */
public class MemoryTest {
    static void check(boolean ok, String m) { if (!ok) throw new AssertionError(m); }
    static final AudioTools.Progress PROGRESS = new AudioTools.Progress() { public void on(double v) {} };
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("sesdefteri-memory-"); Context c = new Context(root.toFile());
        try {
            File tone = new File(c.getCacheDir(), "boundary.wav");
            int rate = 16000, frames = rate * 30;
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tone)))) {
                out.write(Wav.header(1, rate, false, frames));
                for (int i = 0; i < frames; i++) {
                    double sec = i / (double) rate;
                    short sample = (short) (sec >= 10.5 && sec < 12.5 ? 10000 * Math.sin(2 * Math.PI * 160 * sec) : 0);
                    out.writeByte(sample & 255); out.writeByte((sample >> 8) & 255);
                }
            }
            JSONObject detected = Workspace.fillers(c, new JSONObject().put("src", new JSONObject().put("path", tone.getAbsolutePath())), PROGRESS, new AtomicBoolean());
            JSONArray ranges = detected.getJSONArray("ranges");
            check(ranges.length() == 1, "window boundary duplicated/lost a hesitation: " + ranges);
            check(Math.abs(ranges.getJSONArray(0).getDouble(0) - 10.5) < 0.1 && Math.abs(ranges.getJSONArray(0).getDouble(1) - 12.5) < 0.1, "boundary timing: " + ranges);
            System.out.println("PASS filler spanning a window boundary appears once with correct times");
            Speakers.emb(c).createNewFile(); boolean limited = false;
            try { Speakers.rec(c, new JSONObject().put("src", new JSONObject().put("path", tone.getAbsolutePath())).put("dur", 2700), PROGRESS, new AtomicBoolean()); }
            catch (IllegalStateException e) { limited = "dia-long".equals(e.getMessage()); }
            check(limited, "long speaker analysis was not explicitly rejected");
            System.out.println("PASS speaker analysis explicitly rejects a duration over 30 minutes");
            File longAudio = new File(c.getCacheDir(), "hour.wav");
            try (RandomAccessFile out = new RandomAccessFile(longAudio, "rw")) {
                long count = 3600L * rate; out.write(Wav.header(1, rate, false, count)); out.setLength(44 + count * 2);
            }
            JSONObject result = Workspace.fillers(c, new JSONObject().put("src", new JSONObject().put("path", longAudio.getAbsolutePath())), PROGRESS, new AtomicBoolean());
            check(result.getJSONArray("ranges").length() == 0, "silence became filler");
            System.out.println("PASS 60-minute filler scan with a 40 MiB heap");
            File tag = Sounds.dir(c); tag.mkdirs(); for (String name : Sounds.FILES) new File(tag, name).createNewFile();
            JSONObject events = Sounds.scan(c, new JSONObject().put("path", longAudio.getAbsolutePath()), PROGRESS, new AtomicBoolean());
            check(events.getJSONArray("events").length() == 0, "fake classifier contract");
            for (File f : c.getCacheDir().listFiles()) check(!f.getName().startsWith("sounds-"), "temporary PCM leaked");
            System.out.println("PASS 60-minute sound scan with a 40 MiB heap (classifier mocked)");
            AtomicBoolean cancelled = new AtomicBoolean(true); boolean stopped = false;
            try { Sounds.scan(c, new JSONObject().put("path", longAudio.getAbsolutePath()), PROGRESS, cancelled); } catch (InterruptedException e) { stopped = true; }
            check(stopped, "sound conversion ignored cancellation");
            System.out.println("PASS sound scan cancellation during conversion");
        } finally { try (java.util.stream.Stream<Path> s = Files.walk(root)) { s.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (IOException e) { throw new RuntimeException(e); } }); } }
    }
}
class Device { static int threads(int n) { return 1; } }
