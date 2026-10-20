package app.sesdefteri.dsp;

import java.io.*;

/** Runs the three passes (measure → repair/clean → loudness/limit) over any decoder. */
public final class Pipeline {
    private Pipeline() { }

    /** Decodes the whole recording into the given node and calls end(). Called once per pass. */
    public interface Source { void run(Node sink) throws Exception; }

    public interface Progress { void on(double f); }

    public static final class Result {
        public Enhancer.Plan plan;
        public Enhancer.Report report;
        public String json;
        public double[] toneAfter;      // tone of the result (for learning the user's voice)
        public Mix mix;
    }

    /** Intermediate audio between pass 2 and pass 3: 24-bit PCM on disk with 12 dB headroom. */
    public static final class FileTemp extends Node {
        static final float SCALE = 0.25f;
        final File f;
        final int ch;
        OutputStream os;
        byte[] b = new byte[0];
        long frames;

        public FileTemp(File f, int ch) throws IOException {
            this.f = f; this.ch = ch;
            os = new BufferedOutputStream(new FileOutputStream(f), 1 << 16);
        }

        @Override public void push(float[][] x, int n) throws IOException {
            int need = n * ch * 3;
            if (b.length < need) b = new byte[need];
            int q = 0;
            for (int i = 0; i < n; i++) for (int c = 0; c < ch; c++) {
                float v = x[c][i] * SCALE;
                if (v > 0.9999999f) v = 0.9999999f; else if (v < -1f) v = -1f;
                int s = Math.round(v * 8388607f);
                b[q++] = (byte) s; b[q++] = (byte) (s >> 8); b[q++] = (byte) (s >> 16);
            }
            os.write(b, 0, need);
            frames += n;
        }

        @Override public void end() throws IOException { os.close(); os = null; }

        public void replay(Node sink, Progress prog) throws Exception {
            final int BL = 4096;
            float[][] x = new float[ch][BL];
            byte[] rb = new byte[BL * ch * 3];
            long done = 0;
            try (InputStream is = new BufferedInputStream(new FileInputStream(f), 1 << 16)) {
                while (done < frames) {
                    int n = (int) Math.min(BL, frames - done);
                    int need = n * ch * 3, got = 0;
                    while (got < need) { int r = is.read(rb, got, need - got); if (r < 0) throw new EOFException(); got += r; }
                    int q = 0;
                    for (int i = 0; i < n; i++) for (int c = 0; c < ch; c++) {
                        int s = (rb[q] & 0xff) | ((rb[q + 1] & 0xff) << 8) | (rb[q + 2] << 16);
                        q += 3;
                        x[c][i] = s / 8388607f / SCALE;
                    }
                    sink.push(x, n);
                    done += n;
                    if (prog != null && (done & 0x3ffff) < BL) prog.on((double) done / frames);
                }
            }
            sink.end();
        }

        public void delete() { f.delete(); }
    }

    /** Wraps a node and reports progress by sample count. */
    static final class Counter extends Node {
        final long total; final Progress p; final double from, span; long seen;
        Counter(long total, Progress p, double from, double span) { this.total = Math.max(1, total); this.p = p; this.from = from; this.span = span; }
        @Override public void push(float[][] b, int n) throws Exception {
            seen += n;
            if (p != null && (seen & 0x3ffff) < n) p.on(from + span * Math.min(1, (double) seen / total));
            next.push(b, n);
        }
    }

    public static Result enhance(Source src, int channels, int fs, Enhancer.Options o, File tempFile, Node out, final Progress prog) throws Exception {
        // pass 1
        Analyzer an = new Analyzer(channels, fs);
        src.run(an);
        Analyzer.Analysis a = an.finish();
        if (prog != null) prog.on(0.25);
        Enhancer.Plan plan = Enhancer.plan(a, o);
        Enhancer.Report rep = new Enhancer.Report();
        // pass 2
        Loudness mid = new Loudness(plan.outCh, fs);
        Node[] c2 = Enhancer.chain2(plan, mid, rep);
        FileTemp tmp = new FileTemp(tempFile, plan.outCh);
        c2[1].to(tmp);
        Counter cnt2 = new Counter(a.samples, prog, 0.25, 0.45);
        cnt2.to(c2[0]);
        try {
            src.run(cnt2);
            rep.midLufs = mid.integrated();
            // pass 3: the limiter takes some loudness away, so first measure (no output) and correct the gain
            double gain = finalGain(plan, rep.midLufs, tmp, fs);
            Loudness outM = new Loudness(plan.outCh, fs);
            Node[] c3 = Enhancer.chain3(plan, gain, outM, rep);
            c3[1].to(out);
            tmp.replay(c3[0], prog == null ? null : new Progress() { @Override public void on(double f) { prog.on(0.7 + 0.3 * f); } });
            rep.outLufs = outM.integrated();
        } finally {
            tmp.delete();
        }
        Result r = new Result();
        r.plan = plan;
        r.report = rep;
        r.json = rep.json(plan);
        double[] after = plan.measuredBands.clone();
        if (plan.eqBandsDb != null) for (int i = 0; i < after.length; i++) after[i] += plan.eqBandsDb[i];
        r.toneAfter = Enhancer.normalise(after);
        return r;
    }

    /**
     * Voice + background music with ducking, then loudness/limiter for the target.
     * @param music decoded music already converted to the voice's sample rate, in a FileTemp
     */
    public static Result mix(Source voice, int vCh, int fs, FileTemp music, double bedDb, double duckDb, String target,
                             File tempFile, Node out, final Progress prog) throws Exception {
        Analyzer an = new Analyzer(vCh, fs);
        voice.run(an);
        Analyzer.Analysis a = an.finish();
        if (prog != null) prog.on(0.2);
        // music loudness
        Loudness ml = new Loudness(music.ch, fs);
        music.replay(ml, null);
        double mLufs = ml.integrated();
        double vLufs = a.inLufs;
        int outCh = Math.max(vCh, music.ch);
        double bedGain = (Double.isInfinite(mLufs) || Double.isInfinite(vLufs)) ? -20 : (vLufs + bedDb) - mLufs;
        Mix.FileBed bed = new Mix.FileBed(music, outCh, fs);
        Mix mx = new Mix(vCh, music.ch, fs, a.noiseDb, a.speechDb, bed, bedGain, duckDb, a.samples);
        Loudness mid = new Loudness(outCh, fs);
        FileTemp tmp = new FileTemp(tempFile, outCh);
        Counter cnt = new Counter(a.samples, prog, 0.2, 0.5);
        cnt.to(mx).to(mid).to(tmp);
        Result r = new Result();
        try {
            voice.run(cnt);
            bed.close();
            Enhancer.Options o = new Enhancer.Options();
            o.target = target;
            Enhancer.Plan plan = new Enhancer.Plan();
            plan.fs = fs; plan.inCh = outCh; plan.outCh = outCh; plan.analysis = a; plan.trimEnd = a.samples;
            plan.target = Enhancer.Target.of(target);
            plan.measuredBands = new double[Enhancer.BANDS.length];
            Enhancer.Report rep = new Enhancer.Report();
            rep.midLufs = mid.integrated();
            double gain = finalGain(plan, rep.midLufs, tmp, fs);
            Loudness outM = new Loudness(outCh, fs);
            Node[] c3 = Enhancer.chain3(plan, gain, outM, rep);
            c3[1].to(out);
            tmp.replay(c3[0], prog == null ? null : new Progress() { @Override public void on(double f) { prog.on(0.7 + 0.3 * f); } });
            rep.outLufs = outM.integrated();
            r.plan = plan; r.report = rep; r.mix = mx;
            r.json = String.format(java.util.Locale.ROOT, "{\"lufs\":%.1f,\"bed\":%.1f,\"duck\":%.1f}", rep.outLufs, bedDb, duckDb);
        } finally {
            tmp.delete();
        }
        return r;
    }

    /** gain that lands on the target loudness after the limiter (measured, up to 3 tries) */
    static double finalGain(Enhancer.Plan plan, double midLufs, FileTemp tmp, int fs) throws Exception {
        double gain = Double.isInfinite(midLufs) ? 0 : Enhancer.clamp(plan.target.lufs - midLufs, -30, 30);
        for (int it = 0; it < 3; it++) {
            Loudness probe = new Loudness(plan.outCh, fs);
            Limiter lim = new Limiter(plan.outCh, fs, plan.target.ceilingDb, gain);
            lim.to(probe);
            probe.to(new Node() { @Override public void push(float[][] b, int n) { } @Override public void end() { } });
            tmp.replay(lim, null);
            double got = probe.integrated();
            if (Double.isInfinite(got) || Math.abs(plan.target.lufs - got) < 0.2) break;
            gain = Enhancer.clamp(gain + (plan.target.lufs - got) * 1.1, -30, 36);
        }
        return gain;
    }

    /** convenience for tests and short clips */
    public static Result enhance(final float[][] x, int fs, Enhancer.Options o, Node out) throws Exception {
        File t = File.createTempFile("sd-enh", ".pcm");
        return enhance(new Source() { @Override public void run(Node sink) throws Exception { Node.run(x, sink, 4096); } }, x.length, fs, o, t, out, null);
    }
}
