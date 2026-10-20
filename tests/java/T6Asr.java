import app.sesdefteri.dsp.*;
import app.sesdefteri.asr.*;
import java.util.*;

/** Whisper through the app's own Java classes and the real native library (desktop build). */
public class T6Asr extends TestUtil {
    public static void main(String[] a) throws Exception {
        Wav w = wav("t_clean.wav");
        float[] x = Resampler.resample(w.data, 48000, 16000)[0];
        long t0 = System.currentTimeMillis();
        Transcriber tr = new Transcriber(null, "/home/claude/vendor/asr", "", 2);
        long tLoad = System.currentTimeMillis() - t0;
        final int[] live = { 0 };
        tr.setListener(new Transcriber.Listener() { public void onSegment(Transcriber.Segment s) { live[0]++; } });
        t0 = System.currentTimeMillis();
        for (int i = 0; i < x.length; i += 1600) tr.accept(Arrays.copyOfRange(x, i, Math.min(x.length, i + 1600)), Math.min(1600, x.length - i));
        List<Transcriber.Segment> segs = tr.finish();
        double secs = (System.currentTimeMillis() - t0) / 1000.0;
        System.out.println("  model load " + tLoad + " ms, transcription " + f(secs) + " s for " + f(x.length / 16000.0) + " s audio (x" + f(x.length / 16000.0 / secs) + " real time), language " + tr.detectedLang);
        double[][] S = T2Enhance.segs();
        String ref = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(W + "t_segments.json")), "UTF-8");
        List<String> refText = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"\\]").matcher(ref);
        while (m.find()) refText.add(m.group(1));
        check("segments found (20 sentences)", segs.size() >= 17 && segs.size() <= 26, segs.size() + " segments, " + live[0] + " delivered live");
        int ok = 0;
        for (double[] s : S) for (Transcriber.Segment g : segs) if (Math.abs(g.start - s[0]) < 0.5) { ok++; break; }
        check("segment start times match sentences (±0.5 s)", ok >= S.length * 0.85, ok + " of " + S.length);
        String hyp = Transcriber.text(segs);
        double wer = wer(String.join(" ", refText), hyp);
        check("word error rate <= 35 % (synthetic Turkish voices)", wer <= 0.35, "WER " + f(wer * 100) + " %");
        for (int i = 0; i < 3 && i < segs.size(); i++) System.out.println("    " + f(segs.get(i).start) + "s  " + segs.get(i).text);
        String srt = Transcriber.srt(segs);
        check("SRT format", srt.matches("(?s)1\\n\\d\\d:\\d\\d:\\d\\d,\\d\\d\\d --> \\d\\d:\\d\\d:\\d\\d,\\d\\d\\d\\n.+") && srt.split("\\n\\n").length >= segs.size(), srt.substring(0, Math.min(90, srt.length())).replace("\n", " | "));
        List<String> tags = Keywords.tags(hyp, 5);
        String title = Keywords.title(segs);
        System.out.println("    tags " + tags + "  title \"" + title + "\"");
        check("tags are content words", tags.size() == 5 && Collections.disjoint(tags, Arrays.asList("ve", "bir", "bu", "için")), tags.toString());
        check("title from the first utterance", title.length() > 8 && title.length() <= 61, title);
        // forced Turkish
        Transcriber tr2 = new Transcriber(null, "/home/claude/vendor/asr", "tr", 2);
        tr2.accept(x, x.length);
        double wer2 = wer(String.join(" ", refText), Transcriber.text(tr2.finish()));
        check("with language fixed to Turkish WER <= 35 %", wer2 <= 0.35, "WER " + f(wer2 * 100) + " %");
        // silence and music must not produce text
        float[] sil = new float[16000 * 20];
        Random R = new Random(1);
        for (int i = 0; i < sil.length; i++) sil[i] = (float) (R.nextGaussian() * 0.0003);
        Transcriber tr3 = new Transcriber(null, "/home/claude/vendor/asr", "", 2);
        tr3.accept(sil, sil.length);
        check("silence gives no text", tr3.finish().isEmpty(), "");
        tr.release(); tr2.release(); tr3.release();
        done();
    }

    static List<String> norm(String s) {
        s = s.replace("I", "ı").replace("İ", "i").toLowerCase(new Locale("tr")).replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        List<String> r = new ArrayList<>();
        for (String t : s.trim().split("\\s+")) if (!t.isEmpty()) r.add(t);
        return r;
    }

    static double wer(String ref, String hyp) {
        List<String> r = norm(ref), h = norm(hyp);
        int[][] d = new int[r.size() + 1][h.size() + 1];
        for (int i = 0; i <= r.size(); i++) d[i][0] = i;
        for (int j = 0; j <= h.size(); j++) d[0][j] = j;
        for (int i = 1; i <= r.size(); i++) for (int j = 1; j <= h.size(); j++)
            d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + (r.get(i - 1).equals(h.get(j - 1)) ? 0 : 1));
        return (double) d[r.size()][h.size()] / r.size();
    }
}
