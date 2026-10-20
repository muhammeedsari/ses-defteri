package app.sesdefteri.dsp;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Turns the measurements of {@link Analyzer} into decisions ("plan") and builds the processing chains:
 *   pass 2: channels → trim → clicks → high-pass/hum → plosives → noise + EQ → de-esser → compressor → (loudness meter)
 *   pass 3: gain to the platform target → true-peak limiter → (loudness meter)
 * Nothing here is guessed from the file name or asked from the user: every step is switched on only when the
 * measurement says it is needed, and its strength follows the measurement.
 */
public final class Enhancer {
    private Enhancer() { }

    /** export target per platform */
    public static final class Target {
        public final String id;
        public final double lufs, ceilingDb;
        public final int bitrateVoice, bitrateMusic, maxRate;
        public final boolean forceMono;

        Target(String id, double lufs, double ceilingDb, int bv, int bm, int maxRate, boolean forceMono) {
            this.id = id; this.lufs = lufs; this.ceilingDb = ceilingDb; bitrateVoice = bv; bitrateMusic = bm; this.maxRate = maxRate; this.forceMono = forceMono;
        }

        public static Target of(String id) {
            if ("podcast".equals(id)) return new Target("podcast", -16, -1.0, 96000, 192000, 48000, false);
            if ("whatsapp".equals(id)) return new Target("whatsapp", -16, -1.5, 64000, 96000, 44100, true);
            return new Target("youtube", -14, -1.0, 128000, 256000, 48000, false);
        }
    }

    public static final class Options {
        public String target = "youtube";
        public boolean voice = true;              // speech recording (voice mode); false = music
        public double[] learnedBands;             // user's tone profile (dB per band), or null
        public boolean trim = true, denoise = true, deess = true, eq = true, clicks = true, plosives = true, compress = true;
    }

    public static final class Plan {
        public int fs, inCh, outCh, channelMode;
        public String channelReason = "";
        public long trimStart, trimEnd;
        public double hpfHz;
        public double[] notches = new double[0];
        public boolean clicks, plosives;
        public double[] noisePower;               // null = no noise reduction
        public double nrDb, overSub;
        public double[] eqBandsDb;                // null = no EQ
        public double deessThr = -1, deessMaxDb;  // thr < 0 = off
        public double compThrDb = 0, compRatio = 1;
        public Target target;
        public double[] measuredBands;            // tone of this recording before EQ (for learning)
        public Analyzer.Analysis analysis;
        public final ArrayList<String> notes = new ArrayList<>();
    }

    // 1/3-octave band centres used for tone (Auto-EQ) and the user's learned profile
    public static final double[] BANDS = { 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500 };
    // long-term average speech spectrum (Byrne et al. 1994, combined male+female), dB, same bands
    static final double[] LTASS = { 54.4, 57.7, 56.8, 58.2, 59.7, 60.0, 62.4, 62.6, 59.9, 55.4, 51.1, 51.3, 49.6, 47.9, 48.3, 47.6, 46.6, 45.4, 43.0, 40.4, 38.8, 37.1 };

    public static double[] referenceBands() { return normalise(LTASS.clone()); }

    /** dB per band, normalised so the 200 Hz – 4 kHz bands average to 0 */
    static double[] normalise(double[] b) {
        double s = 0; int n = 0;
        for (int i = 0; i < BANDS.length; i++) if (BANDS[i] >= 200 && BANDS[i] <= 4000) { s += b[i]; n++; }
        double m = s / n;
        for (int i = 0; i < b.length; i++) b[i] -= m;
        return b;
    }

    /** average power spectrum (N/2+1 bins) → normalised band levels */
    public static double[] bandsOf(double[] power, int fs, int N) {
        double[] out = new double[BANDS.length];
        for (int i = 0; i < BANDS.length; i++) {
            double lo = BANDS[i] / Math.pow(2, 1 / 6.0), hi = BANDS[i] * Math.pow(2, 1 / 6.0);
            double s = 0; int n = 0;
            for (int k = 0; k < power.length; k++) {
                double f = (double) k * fs / N;
                if (f >= lo && f < hi) { s += power[k]; n++; }
            }
            if (n == 0) {               // band narrower than a bin: interpolate the nearest bin
                int k = (int) Math.round(BANDS[i] * N / fs);
                s = power[Math.min(power.length - 1, k)]; n = 1;
            }
            // band power = sum of bins (proportional to bandwidth like a 1/3-octave filter)
            out[i] = 10 * Math.log10(s / n * (hi - lo) + 1e-30);
        }
        return normalise(out);
    }

    public static Plan plan(Analyzer.Analysis a, Options o) {
        Plan p = new Plan();
        p.analysis = a;
        p.fs = a.fs;
        p.inCh = a.inCh;
        p.channelMode = a.channelMode;
        p.channelReason = a.channelReason;
        p.target = Target.of(o.target);
        if (p.target.forceMono && a.inCh > 1 && p.channelMode == Stages.ChannelMap.KEEP) { p.channelMode = Stages.ChannelMap.MONO; p.channelReason = "target-mono"; }
        if (o.voice && a.inCh > 1 && p.channelMode == Stages.ChannelMap.KEEP && a.corr > 0.9) { p.channelMode = Stages.ChannelMap.MONO; p.channelReason = "speech-mono"; }
        p.outCh = p.channelMode == Stages.ChannelMap.KEEP ? a.inCh : 1;
        if (p.channelMode != Stages.ChannelMap.KEEP) p.notes.add("channels:" + p.channelReason);

        // trim leading / trailing silence (keep a short natural pad)
        p.trimStart = 0; p.trimEnd = a.samples;
        if (o.trim && a.speechFrames > 0) {
            long padIn = Math.round(0.30 * a.fs), padOut = Math.round(0.50 * a.fs);
            long s = Math.max(0, a.activeStart - padIn), e = Math.min(a.samples, a.activeEnd + padOut);
            if (s > Math.round(0.15 * a.fs)) p.trimStart = s;
            if (a.samples - e > Math.round(0.25 * a.fs)) p.trimEnd = e;
            if (p.trimEnd - p.trimStart < a.fs) { p.trimStart = 0; p.trimEnd = a.samples; }
            if (p.trimStart > 0 || p.trimEnd < a.samples) p.notes.add(String.format(Locale.ROOT, "trim:%.2f:%.2f", (double) p.trimStart / a.fs, (double) (a.samples - p.trimEnd) / a.fs));
        }

        p.hpfHz = o.voice ? 75 : 25;
        // hum: a clear narrow peak at 50/60 Hz or its 2nd harmonic in the quietest blocks
        ArrayList<Double> notch = new ArrayList<>();
        double[] hr = a.humRatioDb;
        double need = o.voice ? 12 : 18;
        double h50 = Math.max(hr[0], hr[1]), h60 = Math.max(hr[2], hr[3]), base = 0;
        if (h50 > need && h50 >= h60) base = 50;
        else if (h60 > need) base = 60;
        if (base > 0) {
            for (int h = 1; h <= 5; h++) notch.add(base * h);
            p.notes.add("hum:" + (int) base);
        }
        p.notches = new double[notch.size()];
        for (int i = 0; i < notch.size(); i++) p.notches[i] = notch.get(i);

        p.clicks = o.clicks;
        p.plosives = o.voice && o.plosives;

        // noise reduction: only when the noise is audible relative to the speech
        // (music has no reliable pauses to learn the noise from, so it is left alone)
        if (o.voice && o.denoise && a.noiseDb > -70 && a.snr < 42 && a.speechFrames > 0) {
            p.nrDb = clamp((42 - a.snr) * 0.55 + 6, 6, 18);
            p.overSub = 1.0;
            p.noisePower = a.noisePower;
            p.notes.add(String.format(Locale.ROOT, "denoise:%.0f:%.0f", p.nrDb, a.noiseDb));
        }

        // tone (Auto-EQ), speech only
        double[] measured = bandsOf(a.ltasPower, a.fs, a.fftSize);
        p.measuredBands = measured;
        if (o.voice && o.eq && a.speechFrames > a.fs / a.frameHop * 3) {
            double[] target = o.learnedBands != null ? o.learnedBands.clone() : referenceBands();
            double[] corr = new double[BANDS.length];
            for (int i = 0; i < BANDS.length; i++) corr[i] = target[i] - measured[i];
            double[] sm = new double[BANDS.length];
            for (int i = 0; i < BANDS.length; i++) {
                double s = 0, w = 0;
                for (int j = Math.max(0, i - 1); j <= Math.min(BANDS.length - 1, i + 1); j++) { double ww = j == i ? 2 : 1; s += corr[j] * ww; w += ww; }
                sm[i] = s / w;
            }
            double meanAbs = 0;
            for (int i = 0; i < BANDS.length; i++) {
                double g = 0.8 * sm[i];
                double maxBoost = BANDS[i] < 160 ? 0 : BANDS[i] > 8000 ? 6 : 8;
                g = clamp(g, -8, maxBoost);
                sm[i] = g;
                meanAbs += Math.abs(g);
            }
            meanAbs /= BANDS.length;
            if (meanAbs >= 1.0) {
                p.eqBandsDb = sm;
                p.notes.add(String.format(Locale.ROOT, "eq:%.1f", meanAbs));
            }
        }

        // de-esser: "s" sounds that come within 5 dB of the loudest speech are harsh (natural ones sit ~10 dB lower)
        if (o.voice && o.deess && a.sFrames >= 200 && a.sLevel90 - a.speechDb > -5) {
            p.deessThr = Math.pow(10, (a.speechDb - 12) / 20);
            p.deessMaxDb = clamp(6 + (a.sLevel90 - a.speechDb + 5) * 1.5, 6, 12);
            p.notes.add(String.format(Locale.ROOT, "deess:%.1f", a.sLevel90 - a.speechDb));
        }

        // levelling: only when the loudness wanders a lot (loudness range)
        if (o.voice && o.compress && a.speechFrames > 0 && a.lra > 12) {
            p.compRatio = a.lra > 15 ? 3 : 2;
            p.compThrDb = a.speechMeanDb + 2;
            p.notes.add(String.format(Locale.ROOT, "level:%.1f", a.lra));
        }
        return p;
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /** per-bin linear EQ gains from band gains (log-frequency interpolation) */
    public static double[] eqCurve(double[] bandDb, int fs, int N) {
        int bins = N / 2 + 1;
        double[] g = new double[bins];
        for (int k = 0; k < bins; k++) {
            double f = Math.max(1, (double) k * fs / N);
            double db;
            if (f <= BANDS[0]) db = 0;                                   // never touch the deep lows
            else if (f >= BANDS[BANDS.length - 1]) db = bandDb[BANDS.length - 1];
            else {
                int i = 0;
                while (i < BANDS.length - 2 && f > BANDS[i + 1]) i++;
                double t = Math.log(f / BANDS[i]) / Math.log(BANDS[i + 1] / BANDS[i]);
                db = bandDb[i] + t * (bandDb[i + 1] - bandDb[i]);
            }
            g[k] = Math.pow(10, db / 20);
        }
        return g;
    }

    /** pass 2 chain; returns {head, tail}; meter collects loudness of the result */
    public static Node[] chain2(Plan p, Loudness meter, Report r) {
        ArrayList<Node> n = new ArrayList<>();
        Stages.ChannelMap cm = new Stages.ChannelMap(p.inCh, p.channelMode);
        n.add(cm);
        if (p.trimStart > 0 || p.trimEnd < p.analysis.samples) n.add(new Stages.Trim(p.trimStart, p.trimEnd));
        int ch = p.outCh;
        if (p.clicks) { r.clicks = new Stages.ClickRepair(ch, p.fs, 10); n.add(r.clicks); }
        n.add(new Stages.Filters(ch, p.fs, p.hpfHz, p.notches));
        if (p.plosives) { r.plosive = new Stages.PlosiveTamer(ch, p.fs, 1.8); n.add(r.plosive); }
        if (p.noisePower != null || p.eqBandsDb != null) {
            Spectral s = new Spectral(ch, p.fs);
            if (p.noisePower != null) s.noise(p.noisePower, p.nrDb, p.overSub);
            if (p.eqBandsDb != null) s.eq(eqCurve(p.eqBandsDb, p.fs, s.fftSize()));
            n.add(s);
        }
        if (p.deessThr > 0) { r.deesser = new Stages.DeEsser(ch, p.fs, p.deessThr, p.deessMaxDb); n.add(r.deesser); }
        if (p.compRatio > 1) n.add(new Stages.Compressor(ch, p.fs, p.compThrDb, p.compRatio));
        n.add(meter);
        for (int i = 0; i + 1 < n.size(); i++) n.get(i).to(n.get(i + 1));
        return new Node[] { n.get(0), n.get(n.size() - 1) };
    }

    /** pass 3: gain to target + true-peak limiter (+ output meter) */
    public static Node[] chain3(Plan p, double gain, Loudness outMeter, Report r) {
        r.gainDb = gain;
        Limiter lim = new Limiter(p.outCh, p.fs, p.target.ceilingDb, gain);
        r.limiter = lim;
        lim.to(outMeter);
        return new Node[] { lim, outMeter };
    }

    /** what was done, for the app to show */
    public static final class Report {
        public Stages.ClickRepair clicks;
        public Stages.PlosiveTamer plosive;
        public Stages.DeEsser deesser;
        public Limiter limiter;
        public double gainDb, inLufs, midLufs, outLufs, outTruePeakDb;

        public String json(Plan p) {
            StringBuilder b = new StringBuilder("{");
            b.append("\"target\":\"").append(p.target.id).append("\"");
            b.append(String.format(Locale.ROOT, ",\"lufs\":%.1f,\"targetLufs\":%.1f,\"gain\":%.1f", outLufs, p.target.lufs, gainDb));
            b.append(",\"channels\":\"").append(p.channelMode == Stages.ChannelMap.KEEP ? "" : p.channelReason).append("\"");
            b.append(String.format(Locale.ROOT, ",\"trimStart\":%.3f,\"trimEnd\":%.3f", (double) p.trimStart / p.fs, (double) (p.analysis.samples - p.trimEnd) / p.fs));
            b.append(String.format(Locale.ROOT, ",\"denoise\":%.0f,\"noiseDb\":%.0f", p.noisePower == null ? 0 : p.nrDb, p.analysis.noiseDb));
            b.append(",\"hum\":").append(p.notches.length > 0 ? (int) p.notches[0] : 0);
            b.append(",\"clicks\":").append(clicks == null ? 0 : clicks.repaired);
            b.append(",\"plosives\":").append(plosive == null ? 0 : plosive.active > 0 ? 1 : 0);
            b.append(",\"deess\":").append(p.deessThr > 0 ? 1 : 0);
            b.append(",\"eq\":").append(p.eqBandsDb == null ? 0 : 1);
            b.append(",\"level\":").append(p.compRatio > 1 ? 1 : 0);
            b.append(String.format(Locale.ROOT, ",\"limited\":%.1f", limiter == null ? 0 : 20 * Math.log10(limiter.minGain)));
            b.append(",\"tone\":[");
            for (int i = 0; i < p.measuredBands.length; i++) b.append(i > 0 ? "," : "").append(String.format(Locale.ROOT, "%.2f", p.measuredBands[i]));
            b.append("]}");
            return b.toString();
        }
    }
}
