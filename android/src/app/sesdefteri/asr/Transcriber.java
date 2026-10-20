package app.sesdefteri.asr;

import com.k2fsa.sherpa.onnx.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline speech-to-text: Silero VAD cuts the audio into utterances, Whisper (base, int8) writes each one down.
 * Feed 16 kHz mono audio with {@link #accept}; utterances are recognised as soon as the VAD closes them.
 * Pure Java (the Android AssetManager is passed as Object), so it also runs on a desktop JVM for testing.
 */
public final class Transcriber {
    public static final int RATE = 16000;

    public static final class Segment {
        public final double start, end;
        public final String text;
        Segment(double s, double e, String t) { start = s; end = e; text = t; }
    }

    public interface Listener { void onSegment(Segment s); }

    private final Vad vad;
    private final OfflineRecognizer rec;
    private final List<Segment> segs = new ArrayList<>();
    private final float[] win = new float[512];
    private int winFill;
    private Listener listener;
    public String detectedLang = "";

    /**
     * @param assets AssetManager (paths are asset names) or null (paths are files)
     * @param dir directory/asset folder with base-encoder.int8.onnx, base-decoder.int8.onnx, base-tokens.txt, silero_vad.onnx
     * @param language "" = detect, or "tr", "en", ...
     */
    public Transcriber(Object assets, String dir, String language, int threads) {
        String p = dir.isEmpty() || dir.endsWith("/") ? dir : dir + "/";
        VadModelConfig vc = new VadModelConfig();
        vc.sileroVadModelConfig.model = p + "silero_vad.onnx";
        vc.sileroVadModelConfig.threshold = 0.5f;
        vc.sileroVadModelConfig.minSilenceDuration = 0.45f;
        vc.sileroVadModelConfig.minSpeechDuration = 0.25f;
        vc.sileroVadModelConfig.maxSpeechDuration = 20f;
        vc.sileroVadModelConfig.windowSize = 512;
        vc.sampleRate = RATE;
        vc.numThreads = 1;
        vad = new Vad(assets, vc);
        OfflineRecognizerConfig rc = new OfflineRecognizerConfig();
        rc.featConfig.sampleRate = RATE;
        rc.featConfig.featureDim = 80;
        rc.modelConfig.whisper.encoder = p + "base-encoder.int8.onnx";
        rc.modelConfig.whisper.decoder = p + "base-decoder.int8.onnx";
        rc.modelConfig.whisper.language = language == null ? "" : language;
        rc.modelConfig.whisper.task = "transcribe";
        rc.modelConfig.whisper.tailPaddings = 1000;
        rc.modelConfig.tokens = p + "base-tokens.txt";
        rc.modelConfig.numThreads = Math.max(1, threads);
        rc.modelConfig.modelType = "whisper";
        rc.modelConfig.provider = "cpu";
        rc.decodingMethod = "greedy_search";
        rec = new OfflineRecognizer(assets, rc);
    }

    public void setListener(Listener l) { listener = l; }

    /** 16 kHz mono samples */
    public void accept(float[] x, int n) {
        int i = 0;
        while (i < n) {
            int take = Math.min(n - i, win.length - winFill);
            System.arraycopy(x, i, win, winFill, take);
            winFill += take;
            i += take;
            if (winFill == win.length) {
                vad.acceptWaveform(win.clone());
                winFill = 0;
                drain();
            }
        }
    }

    public List<Segment> finish() {
        if (winFill > 0) {
            float[] last = new float[win.length];
            System.arraycopy(win, 0, last, 0, winFill);
            vad.acceptWaveform(last);
            winFill = 0;
        }
        vad.flush();
        drain();
        return segs;
    }

    private void drain() {
        while (!vad.empty()) {
            SpeechSegment s = vad.front();
            vad.pop();
            double st = s.start / (double) RATE, en = st + s.samples.length / (double) RATE;
            OfflineStream os = rec.createStream();
            os.acceptWaveform(s.samples, RATE);
            rec.decode(os);
            OfflineRecognizerResult r = rec.getResult(os);
            os.release();
            if (r.lang != null && !r.lang.isEmpty()) detectedLang = r.lang;
            String t = clean(r.text);
            if (t.isEmpty()) continue;
            Segment seg = new Segment(st, en, t);
            segs.add(seg);
            if (listener != null) listener.onSegment(seg);
        }
    }

    // well-known Whisper hallucinations on silence / music (subtitle credits etc.)
    private static final String[] JUNK = {
        "altyazı m.k.", "altyazı m.k", "abone olmayı unutmayın", "izlediğiniz için teşekkürler", "thanks for watching", "thank you for watching",
        "subtitles by", "sous-titres", "untertitel", "amara.org", "продолжение следует", "субтитры", "字幕"
    };

    static String clean(String t) {
        if (t == null) return "";
        t = t.trim().replaceAll("\\s+", " ");
        String low = t.toLowerCase(Locale.ROOT);
        for (String j : JUNK) if (low.contains(j)) {
            if (low.replace(j, "").replaceAll("[\\p{Punct}\\s]", "").length() < 3) return "";
        }
        if (t.matches("[\\p{Punct}\\s♪]*")) return "";
        return t;
    }

    public void release() { vad.release(); rec.release(); }

    // ---------- outputs ----------

    /** SubRip subtitles; long utterances are split at about 7 seconds / 84 characters */
    public static String srt(List<Segment> segs) {
        StringBuilder b = new StringBuilder();
        int n = 1;
        for (Segment s : segs) {
            List<String> parts = splitText(s.text, 84);
            double dur = s.end - s.start, total = 0;
            for (String p : parts) total += p.length();
            double t = s.start;
            for (String p : parts) {
                double d = dur * p.length() / Math.max(1, total);
                b.append(n++).append('\n').append(ts(t)).append(" --> ").append(ts(t + d)).append('\n').append(wrap(p, 42)).append("\n\n");
                t += d;
            }
        }
        return b.toString();
    }

    static List<String> splitText(String t, int max) {
        List<String> out = new ArrayList<>();
        String[] w = t.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String x : w) {
            if (cur.length() > 0 && cur.length() + 1 + x.length() > max) { out.add(cur.toString()); cur.setLength(0); }
            if (cur.length() > 0) cur.append(' ');
            cur.append(x);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    static String wrap(String t, int line) {
        if (t.length() <= line) return t;
        int mid = t.length() / 2, best = -1;
        for (int d = 0; d < mid; d++) {
            if (mid + d < t.length() && t.charAt(mid + d) == ' ') { best = mid + d; break; }
            if (mid - d > 0 && t.charAt(mid - d) == ' ') { best = mid - d; break; }
        }
        return best < 0 ? t : t.substring(0, best) + "\n" + t.substring(best + 1);
    }

    static String ts(double s) {
        long ms = Math.round(Math.max(0, s) * 1000);
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, ms % 1000);
    }

    public static String text(List<Segment> segs) {
        StringBuilder b = new StringBuilder();
        for (Segment s : segs) { if (b.length() > 0) b.append(' '); b.append(s.text); }
        return b.toString();
    }
}
