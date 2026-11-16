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
        public Segment(double s, double e, String t) { start = s; end = e; text = t; }
    }

    public interface Listener { void onSegment(Segment s); }

    private final Vad vad;
    private final OfflineRecognizer rec;
    private final OnlineRecognizer seda;   // 5.17: Turkish seda model instead of Whisper (rec is null then)
    private final List<Segment> segs = new ArrayList<>();
    private final float[] win = new float[512];
    private int winFill;
    private Listener listener;
    public String detectedLang = "";
    public double offset;   // 4.1: seconds already done before the first sample given (a resumed run)

    /**
     * @param assets AssetManager (paths are asset names) or null (paths are files)
     * @param dir directory/asset folder with base-encoder.int8.onnx, base-decoder.int8.onnx, base-tokens.txt, silero_vad.onnx
     * @param language "" = detect, or "tr", "en", ...
     */
    public Transcriber(Object assets, String dir, String language, int threads) { this(assets, dir, "base", language, threads); }

    /** prefix: model file prefix ("base", "small"…) */
    public Transcriber(Object assets, String dir, String prefix, String language, int threads) {
        String p = dir.isEmpty() || dir.endsWith("/") ? dir : dir + "/";
        vad = newVad(assets, p + "silero_vad.onnx");
        seda = null;
        OfflineRecognizerConfig rc = new OfflineRecognizerConfig();
        rc.featConfig.sampleRate = RATE;
        rc.featConfig.featureDim = 80;
        rc.modelConfig.whisper.encoder = p + prefix + "-encoder.int8.onnx";
        rc.modelConfig.whisper.decoder = p + prefix + "-decoder.int8.onnx";
        rc.modelConfig.whisper.language = language == null ? "" : language;
        rc.modelConfig.whisper.task = "transcribe";
        rc.modelConfig.whisper.tailPaddings = 1000;
        rc.modelConfig.tokens = p + prefix + "-tokens.txt";
        rc.modelConfig.numThreads = Math.max(1, threads);
        rc.modelConfig.modelType = "whisper";
        rc.modelConfig.provider = "cpu";
        rc.decodingMethod = "greedy_search";
        rec = new OfflineRecognizer(assets, rc);
    }

    /** 5.17: a seda Turkish streaming model reads the whole file in one go (dir: encoder.int8.onnx, decoder.onnx, joiner.int8.onnx, tokens.txt);
     *  its own pause detection makes the sentences. Measured on the Turkish test set: 11.6 % word errors (seda-v0.2), cutting at
     *  Whisper's voice-detected pieces gave 15.3 % (the model loses the context between sentences). */
    public Transcriber(String dir, int threads) {
        String p = dir.endsWith("/") ? dir : dir + "/";
        vad = null;
        rec = null;
        OnlineRecognizerConfig c = new OnlineRecognizerConfig();
        c.modelConfig.transducer.encoder = p + "encoder.int8.onnx";
        c.modelConfig.transducer.decoder = p + "decoder.onnx";
        c.modelConfig.transducer.joiner = p + "joiner.int8.onnx";
        c.modelConfig.tokens = p + "tokens.txt";
        c.modelConfig.numThreads = Math.max(1, threads);
        c.enableEndpoint = true;
        c.endpointConfig.rule2.minTrailingSilence = 0.8f;
        c.endpointConfig.rule3.minUtteranceLength = 20f;
        seda = new OnlineRecognizer(c);
        sst = seda.createStream();
        detectedLang = "tr";
    }

    private OnlineStream sst;
    private long spos, sseg;   // samples given to seda, and where the open sentence started

    private void sedaFeed(float[] x, int n) {
        float[] y = n == x.length ? x : java.util.Arrays.copyOf(x, n);
        sst.acceptWaveform(y, RATE);
        spos += n;
        while (seda.isReady(sst)) seda.decode(sst);
        if (seda.isEndpoint(sst)) sedaClose(spos);
    }

    private void sedaClose(long end) {
        OnlineRecognizerResult r = seda.getResult(sst);
        String t = clean(r.text);
        if (!t.isEmpty()) {
            double st = offset + sseg / (double) RATE + (r.timestamps != null && r.timestamps.length > 0 ? Math.max(0, r.timestamps[0] - 0.2) : 0);
            Segment seg = new Segment(st, Math.max(st + 0.3, offset + end / (double) RATE), t);
            segs.add(seg);
            if (listener != null) listener.onSegment(seg);
        }
        seda.reset(sst);
        sseg = end;
    }

    private static Vad newVad(Object assets, String file) {
        VadModelConfig vc = new VadModelConfig();
        vc.sileroVadModelConfig.model = file;
        vc.sileroVadModelConfig.threshold = 0.5f;
        vc.sileroVadModelConfig.minSilenceDuration = 0.45f;
        vc.sileroVadModelConfig.minSpeechDuration = 0.25f;
        vc.sileroVadModelConfig.maxSpeechDuration = 20f;
        vc.sileroVadModelConfig.windowSize = 512;
        vc.sampleRate = RATE;
        vc.numThreads = 1;
        return new Vad(assets, vc);
    }

    private String sedaText(float[] x) {
        OnlineStream st = seda.createStream();
        try {
            st.acceptWaveform(x, RATE);
            st.acceptWaveform(new float[RATE / 2], RATE);
            st.inputFinished();
            while (seda.isReady(st)) seda.decode(st);
            return seda.getResult(st).text;
        } finally { st.release(); }
    }

    public void setListener(Listener l) { listener = l; }

    /** 16 kHz mono samples */
    public void accept(float[] x, int n) {
        if (seda != null) { sedaFeed(x, n); return; }
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
        if (seda != null) {
            sst.acceptWaveform(new float[RATE / 2], RATE);   // the last words need a little silence after them
            sst.inputFinished();
            while (seda.isReady(sst)) seda.decode(sst);
            sedaClose(spos + RATE / 2);
            return segs;
        }
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
            double st = offset + s.start / (double) RATE, en = st + s.samples.length / (double) RATE;
            // each sentence at a steady level: quiet or far-away speech is recognised as well as close speech
            float[] x = s.samples;
            float pk = 0;
            for (float v : x) pk = Math.max(pk, Math.abs(v));
            if (pk > 1e-4f && pk < 0.7f) { float g = Math.min(12f, 0.8f / pk); x = x.clone(); for (int i = 0; i < x.length; i++) x[i] *= g; }
            OfflineStream os = rec.createStream();
            os.acceptWaveform(x, RATE);
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
        // 5.17: Whisper writes sound labels like "(Müzik çalıyor)" or "[Alkış]" over music: they are not speech
        t = t.replaceAll("\\([^()]*\\)?|\\[[^\\[\\]]*\\]?|\\*[^*]*\\*", " ");
        t = t.trim().replaceAll("\\s+", " ");
        String low = t.toLowerCase(Locale.ROOT);
        for (String j : JUNK) if (low.contains(j)) {
            if (low.replace(j, "").replaceAll("[\\p{Punct}\\s]", "").length() < 3) return "";
        }
        if (t.matches("[\\p{Punct}\\s♪]*")) return "";
        return t;
    }

    /** 4.7: one short 16 kHz snippet straight to Whisper (no voice detection); "" when nothing was said */
    public String recognize(float[] x) {
        if (seda != null) return clean(sedaText(x));
        float pk = 0;
        for (float v : x) pk = Math.max(pk, Math.abs(v));
        if (pk > 1e-4f && pk < 0.7f) { float g = Math.min(12f, 0.8f / pk); x = x.clone(); for (int i = 0; i < x.length; i++) x[i] *= g; }
        OfflineStream os = rec.createStream();
        os.acceptWaveform(x, RATE);
        rec.decode(os);
        OfflineRecognizerResult r = rec.getResult(os);
        os.release();
        return clean(r.text);
    }

    public void release() { if (vad != null) vad.release(); if (rec != null) rec.release(); if (sst != null) sst.release(); if (seda != null) seda.release(); }

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
