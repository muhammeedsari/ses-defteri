package app.sesdefteri.asr;

import app.sesdefteri.dsp.Node;
import app.sesdefteri.dsp.Resampler;
import app.sesdefteri.dsp.Stages;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 5.16: live text while recording. The recorder hands over its samples (never waits), a worker thread turns them into
 * sentences with the same voice detector + Whisper as the later full run. When the phone is too slow the backlog is
 * capped: older audio is skipped and the times stay right. Pure Java, so it runs on a desktop JVM for testing.
 */
public final class Live implements Runnable {
    public interface Maker { Transcriber make() throws Exception; }

    private static final float[] END = new float[0];
    private final LinkedBlockingQueue<float[]> q = new LinkedBlockingQueue<>();
    private final AtomicLong queued = new AtomicLong(), dropped = new AtomicLong();
    private final Maker maker;
    private final int rate;
    private final long cap;
    private final double start;
    private final List<Transcriber.Segment> out;
    public volatile boolean ready, done;
    public volatile String error = "";
    public volatile String lang = "";

    /**
     * @param rate the recorder's sample rate
     * @param start seconds already recorded when this run begins (switched on during a recording)
     * @param out shared list the sentences are added to (synchronized on itself)
     * @param maxBacklog seconds of audio that may wait before the oldest is skipped
     */
    public Live(Maker maker, int rate, double start, List<Transcriber.Segment> out, double maxBacklog) {
        this.maker = maker; this.rate = rate; this.start = start; this.out = out;
        cap = (long) (maxBacklog * rate);
    }

    /** from the recording thread: mono samples, copied; skipped when the backlog is full */
    public void feed(float[] mono, int n) {
        if (done || n <= 0) return;
        if (queued.get() + n > cap) { dropped.addAndGet(n); return; }
        float[] c = new float[n];
        System.arraycopy(mono, 0, c, 0, n);
        queued.addAndGet(n);
        q.offer(c);
    }

    /** interleaved 16-bit frames → mono, then {@link #feed(float[], int)} */
    private float[] mono = new float[0];
    public void feed(float[][] ch, int chans, int n) {
        if (chans == 1) { feed(ch[0], n); return; }
        if (mono.length < n) mono = new float[n];
        for (int i = 0; i < n; i++) mono[i] = (ch[0][i] + ch[1][i]) * 0.5f;
        feed(mono, n);
    }

    /** seconds of audio still waiting */
    public double lag() { return queued.get() / (double) rate; }
    /** seconds skipped because the phone could not keep up */
    public double skipped() { return dropped.get() / (double) rate; }

    /** no more audio: what is waiting is still written down, then the worker ends */
    public void finish() { q.offer(END); }

    private volatile boolean abort;
    /** stop now, the waiting audio is dropped */
    public void abort() { abort = true; q.offer(END); }

    @Override public void run() {
        Transcriber tr = null;
        try {
            tr = maker.make();
            final Transcriber t = tr;
            t.offset = start;
            t.setListener(new Transcriber.Listener() {
                @Override public void onSegment(Transcriber.Segment s) { synchronized (out) { out.add(s); } lang = t.detectedLang; }
            });
            Node head = new Resampler(1, (double) rate, (double) Transcriber.RATE);
            // the same chain as the full run: rumble below 80 Hz only confuses the recogniser
            head.to(new Stages.Filters(1, Transcriber.RATE, 80, null)).to(new Node() {
                @Override public void push(float[][] b, int n) { t.accept(b[0], n); }
                @Override public void end() { }
            });
            ready = true;
            float[][] one = new float[1][];
            long skippedSeen = 0;
            while (!abort) {
                float[] b = q.poll(500, TimeUnit.MILLISECONDS);
                if (b == null) continue;
                if (b == END) break;
                queued.addAndGet(-b.length);
                long d = dropped.get();
                // audio was skipped before this block: later sentences move by that much so their times match the file
                if (d != skippedSeen) { t.offset += (d - skippedSeen) / (double) rate; skippedSeen = d; }
                one[0] = b;
                head.push(one, b.length);
            }
            if (!abort) { head.end(); t.finish(); }
        } catch (Throwable e) {
            error = String.valueOf(e.getMessage());
        } finally {
            done = true;
            q.clear();
            queued.set(0);
            if (tr != null) try { tr.release(); } catch (Throwable ignored) { }
        }
    }

    /** a copy of the sentences from index from on */
    public static List<Transcriber.Segment> since(List<Transcriber.Segment> all, int from) {
        synchronized (all) { return new ArrayList<>(all.subList(Math.max(0, Math.min(from, all.size())), all.size())); }
    }
}
