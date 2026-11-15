import app.sesdefteri.asr.*;
import java.io.*;
import java.util.*;

/** 5.16 desktop check (args: modelDir model wav lang speed maxBacklog [overflowFile]; speed 4 = a phone 4x slower than this machine).
 *  Needs the sherpa-onnx linux-x64 JNI library on -Djava.library.path. Desktop check of the 5.16 live text: real-time feeding like the recorder, latency per sentence, skipping when slow */
public class TLive {
    static float[] wav(String f, int[] rate) throws Exception {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
        byte[] all = new byte[(int) new File(f).length()]; in.readFully(all); in.close();
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(all).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        rate[0] = b.getInt(24);
        int p = 12; while (!(all[p]=='d'&&all[p+1]=='a'&&all[p+2]=='t'&&all[p+3]=='a')) p += 8 + b.getInt(p + 4);
        int n = b.getInt(p + 4) / 2; float[] x = new float[n];
        for (int i = 0; i < n; i++) x[i] = b.getShort(p + 8 + 2 * i) / 32768f;
        return x;
    }
    public static void main(String[] a) throws Exception {
        final String dir = a[0], model = a[1], lang = a[3]; double speed = Double.parseDouble(a[4]); double cap = Double.parseDouble(a[5]);
        int[] r = new int[1]; float[] x = wav(a[2], r); int rate = r[0];
        final List<Transcriber.Segment> out = new ArrayList<>();
        Live l = new Live(new Live.Maker() { public Transcriber make() {
            return new Transcriber(null, dir, model, lang, 2); } }, rate, 0, out, cap, a.length > 6 ? new File(a[6]) : null, 3 * 3600);
        Thread th = new Thread(l); th.start();
        long t0 = System.nanoTime();
        int blk = rate / 50; float[][] buf = new float[2][blk]; int seen = 0;
        double maxLag = 0;
        Map<Integer, Double> shown = new HashMap<>();
        for (int i = 0; i < x.length; i += blk) {
            int n = Math.min(blk, x.length - i);
            System.arraycopy(x, i, buf[0], 0, n); System.arraycopy(x, i, buf[1], 0, n);
            l.feed(buf, 2, n);
            double audioT = (i + n) / (double) rate;
            long due = t0 + (long) (audioT / speed * 1e9);
            while (System.nanoTime() < due) Thread.sleep(1);
            maxLag = Math.max(maxLag, l.lag());
            synchronized (out) { for (; seen < out.size(); seen++) shown.put(seen, audioT); }
        }
        double lagAtStop = l.lag(); long te = System.nanoTime();
        l.finish(); th.join();
        System.out.printf("at stop %.1fs of audio waited; written down %.1fs after stop (wall)%s%n", lagAtStop, (System.nanoTime() - te) / 1e9,
            a.length > 6 ? ", overflow file removed: " + !new File(a[6]).exists() : "");
        double feedEnd = x.length / (double) rate;
        System.out.printf("rate %d  ready %b  error '%s'  sentences %d  max backlog %.1fs  skipped %.1fs  lang %s%n", rate, l.ready, l.error, out.size(), maxLag, l.skipped(), l.lang);
        double sum = 0; int k = 0;
        for (int i = 0; i < out.size(); i++) {
            Transcriber.Segment s = out.get(i);
            Double at = shown.get(i);
            double lat = at == null ? Double.NaN : at - s.end;
            if (at != null) { sum += lat; k++; }
            if (i < 4 || i == out.size() - 1) System.out.printf("  %6.2f-%6.2f  shown %s  %s%n", s.start, s.end, at == null ? "after stop" : String.format("+%.1fs", lat), s.text);
        }
        System.out.printf("average: text shown %.1fs after the sentence ends (audio time)%n", sum / Math.max(1, k));
        // words for WER
        StringBuilder b = new StringBuilder(); for (Transcriber.Segment s : out) b.append(s.text).append(' ');
        try (Writer w = new OutputStreamWriter(new FileOutputStream(a[2] + "." + model + ".hyp"), "UTF-8")) { w.write(b.toString()); }
        // times sorted and inside the file
        double last = -1; boolean ok = true;
        for (Transcriber.Segment s : out) { if (s.start < last - 0.01 || s.end > feedEnd + 0.5) ok = false; last = s.start; }
        System.out.println("times in order and inside the file: " + ok);
    }
}
