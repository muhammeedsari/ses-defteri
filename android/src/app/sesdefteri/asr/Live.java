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
 * sentences with the same voice detector + Whisper as the later full run. When the phone is too slow, what does not fit
 * in memory waits in a file (16-bit, in order) and is written down later, also after the recording ended; only when
 * that file cannot grow (no space, over the limit) audio is skipped, and then the times stay right.
 * Pure Java, so it runs on a desktop JVM for testing.
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

    // the overflow file: once used, every new block goes there until the worker has caught up with it
    public final java.io.File spillFile;
    private final long spillMax;
    private final Object lock = new Object();
    private java.io.RandomAccessFile spillW, spillR;
    private volatile boolean spilling, ending;
    private final AtomicLong spilled = new AtomicLong();   // samples written to the file
    private long spillRead;                                // samples read back (worker only)
    private byte[] wb = new byte[0];

    /**
     * @param rate the recorder's sample rate
     * @param start seconds already recorded when this run begins (switched on during a recording)
     * @param out shared list the sentences are added to (synchronized on itself)
     * @param maxBacklog seconds of audio that may wait before the oldest is skipped
     */
    public Live(Maker maker, int rate, double start, List<Transcriber.Segment> out, double maxBacklog) {
        this(maker, rate, start, out, maxBacklog, null, 0);
    }

    /** spill: the overflow file (null = skip instead); maxSpill: seconds it may hold */
    public Live(Maker maker, int rate, double start, List<Transcriber.Segment> out, double maxBacklog, java.io.File spill, double maxSpill) {
        this.maker = maker; this.rate = rate; this.start = start; this.out = out;
        cap = (long) (maxBacklog * rate);
        spillFile = spill;
        spillMax = (long) (maxSpill * rate);
    }

    /** from the recording thread: mono samples, copied; into the file or skipped when the memory backlog is full */
    public void feed(float[] mono, int n) {
        if (done || n <= 0) return;
        if (spilling || queued.get() + n > cap) {
            synchronized (lock) {
                if (spilling || queued.get() + n > cap) {
                    if (!spill(mono, n)) dropped.addAndGet(n);
                    return;
                }
            }
        }
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

    /** writes to the overflow file (lock held); false when it cannot */
    private boolean spill(float[] x, int n) {
        if (spillFile == null || done) return false;
        try {
            if (spillW == null) { spillW = new java.io.RandomAccessFile(spillFile, "rw"); spillW.setLength(0); }
            long have = spilled.get() - spillRead;
            if (have + n > spillMax || spillFile.getUsableSpace() < 300L << 20) return false;
            if (wb.length < n * 2) wb = new byte[n * 2];
            for (int i = 0; i < n; i++) {
                int s = Math.round(x[i] * 32768f);   // the recorder's own 16-bit steps: nothing changes on the way back
                if (s > 32767) s = 32767; else if (s < -32768) s = -32768;
                wb[2 * i] = (byte) s; wb[2 * i + 1] = (byte) (s >> 8);
            }
            spillW.seek(spilled.get() * 2);
            spillW.write(wb, 0, n * 2);
            spilling = true;
            spilled.addAndGet(n);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** worker: the next block from the overflow file, or null when it is used up (then new audio goes to memory again) */
    private float[] unspill() throws Exception {
        long n;
        synchronized (lock) {
            n = Math.min(spilled.get() - spillRead, rate / 2);
            if (n <= 0) {
                if (spilling) { spilling = false; spilled.set(0); spillRead = 0; }   // caught up: the file starts again from 0
                return null;
            }
        }
        if (spillR == null) spillR = new java.io.RandomAccessFile(spillFile, "r");
        byte[] b = new byte[(int) n * 2];
        spillR.seek(spillRead * 2);
        spillR.readFully(b);
        float[] x = new float[(int) n];
        for (int i = 0; i < n; i++) x[i] = (short) ((b[2 * i] & 0xff) | (b[2 * i + 1] << 8)) / 32768f;
        synchronized (lock) { spillRead += n; }
        return x;
    }

    /** seconds of audio still waiting (in memory and in the file) */
    public double lag() { return (queued.get() + Math.max(0, spilled.get() - spillRead)) / (double) rate; }
    /** seconds skipped because the phone could not keep up */
    public double skipped() { return dropped.get() / (double) rate; }

    /** no more audio: what is waiting is still written down (the file too), then the worker ends */
    public void finish() { ending = true; q.offer(END); }

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
            boolean end = false;
            while (!abort) {
                // memory first: everything in the file came after it
                float[] b = q.poll(end || spilling ? 0 : 200, TimeUnit.MILLISECONDS);
                if (b == END) { end = true; continue; }
                if (b != null) queued.addAndGet(-b.length);
                else {
                    b = spillFile == null ? null : unspill();
                    if (b == null) { if (end) break; continue; }
                }
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
            synchronized (lock) {
                try { if (spillW != null) spillW.close(); } catch (Exception ignored) { }
                try { if (spillR != null) spillR.close(); } catch (Exception ignored) { }
                if (spillFile != null) spillFile.delete();
                spilled.set(0); spillRead = 0; spilling = false;
            }
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
