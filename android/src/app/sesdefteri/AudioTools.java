package app.sesdefteri;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/** Decoding for waveforms, and cutting/trimming/silence removal by re-encoding to AAC (.m4a). */
final class AudioTools {
    interface Progress { void on(double fraction); }

    static final int PEAK_RATE = 20; // values per second

    private AudioTools() { }

    /* ---------- metadata ---------- */

    static long durationMs(Context c, Uri u) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(c, u);
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d != null ? Long.parseLong(d) : 0;
        } catch (Exception e) {
            return 0;
        } finally {
            try { r.release(); } catch (Exception ignored) { }
        }
    }

    static long durationMs(Context c, File f) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getAbsolutePath());
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d != null ? Long.parseLong(d) : 0;
        } catch (Exception e) {
            return 0;
        } finally {
            try { r.release(); } catch (Exception ignored) { }
        }
    }

    static Bitmap albumArt(Context c, long albumId, int size) {
        if (albumId < 0) return null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                Uri u = ContentUris.withAppendedId(MediaStore.Audio.Albums.getContentUri(MediaStore.VOLUME_EXTERNAL), albumId);
                return c.getContentResolver().loadThumbnail(u, new Size(size, size), null);
            }
            Uri u = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);
            InputStream in = c.getContentResolver().openInputStream(u);
            if (in == null) return null;
            Bitmap b = BitmapFactory.decodeStream(in);
            in.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    static byte[] albumArtJpeg(Context c, long albumId) {
        Bitmap b = albumArt(c, albumId, 320);
        if (b == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 86, out);
        return out.toByteArray();
    }

    /* ---------- decoding ---------- */

    interface PcmSink {
        /** Interleaved 16-bit samples. */
        void pcm(short[] data, int count, int channels, int sampleRate) throws Exception;
    }

    private static MediaExtractor extractor(Context c, JSONObject src) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        Uri u = MediaLib.srcUri(src);
        File f = MediaLib.srcFile(src);
        if (u != null) ex.setDataSource(c, u, null);
        else if (f != null) ex.setDataSource(f.getAbsolutePath());
        else throw new IllegalArgumentException("no source");
        return ex;
    }

    private static int audioTrack(MediaExtractor ex) {
        for (int i = 0; i < ex.getTrackCount(); i++) {
            String m = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith("audio/")) return i;
        }
        return -1;
    }

    /** Streams the whole file as PCM. Returns total duration in microseconds (from the track, or decoded length). */
    static long decode(Context c, JSONObject src, PcmSink sink, Progress prog, AtomicBoolean cancel) throws Exception {
        MediaExtractor ex = extractor(c, src);
        MediaCodec dec = null;
        try {
            int t = audioTrack(ex);
            if (t < 0) throw new IllegalStateException("no audio track");
            ex.selectTrack(t);
            MediaFormat fmt = ex.getTrackFormat(t);
            long durUs = fmt.containsKey(MediaFormat.KEY_DURATION) ? fmt.getLong(MediaFormat.KEY_DURATION) : 0;
            dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            dec.configure(fmt, null, null, 0);
            dec.start();
            int channels = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int rate = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            boolean floatPcm = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inEos = false, outEos = false;
            short[] tmp = new short[0];
            long lastProg = 0;
            int idle = 0;
            while (!outEos) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                if (!inEos) {
                    int ii = dec.dequeueInputBuffer(10000);
                    if (ii >= 0) {
                        ByteBuffer ib = dec.getInputBuffer(ii);
                        int n = ib != null ? ex.readSampleData(ib, 0) : -1;
                        if (n < 0) {
                            dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inEos = true;
                        } else {
                            long ts = ex.getSampleTime();
                            dec.queueInputBuffer(ii, 0, n, Math.max(0, ts), 0);
                            ex.advance();
                            if (prog != null && durUs > 0 && ts - lastProg > 1_000_000) {
                                lastProg = ts;
                                prog.on(Math.min(0.99, ts / (double) durUs));
                            }
                        }
                    }
                }
                int oi = dec.dequeueOutputBuffer(info, 10000);
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = dec.getOutputFormat();
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (Build.VERSION.SDK_INT >= 24 && of.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        floatPcm = of.getInteger(MediaFormat.KEY_PCM_ENCODING) == 4; // AudioFormat.ENCODING_PCM_FLOAT
                    }
                } else if (oi >= 0) {
                    idle = 0;
                    ByteBuffer ob = dec.getOutputBuffer(oi);
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        ByteBuffer le = ob.slice().order(ByteOrder.LITTLE_ENDIAN);
                        int count;
                        if (floatPcm) {
                            count = info.size / 4;
                            if (tmp.length < count) tmp = new short[count];
                            for (int i = 0; i < count; i++) {
                                float v = le.getFloat(i * 4);
                                tmp[i] = (short) Math.max(-32768, Math.min(32767, Math.round(v * 32767f)));
                            }
                        } else {
                            count = info.size / 2;
                            if (tmp.length < count) tmp = new short[count];
                            le.asShortBuffer().get(tmp, 0, count);
                        }
                        sink.pcm(tmp, count, Math.max(1, channels), rate);
                    }
                    dec.releaseOutputBuffer(oi, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outEos = true;
                } else if (inEos && ++idle > 300) {
                    break; // decoder stuck after end of input: stop with what we have
                }
            }
            return durUs;
        } finally {
            if (dec != null) {
                try { dec.stop(); } catch (Exception ignored) { }
                try { dec.release(); } catch (Exception ignored) { }
            }
            ex.release();
        }
    }

    /* ---------- waveform ---------- */

    interface Chunks {
        /** Waveform bytes for indexes from..from+len-1 that are now final. */
        void on(int from, byte[] data);
    }

    /**
     * Waveform at PEAK_RATE values per second, square-root scaled to 0..255.
     * Decodes from startMs to the end first, then the beginning, and hands over finished
     * pieces while it works, so the part on screen appears within seconds.
     */
    static JSONObject peaks(Context c, JSONObject src, long startMs, Progress prog, Chunks chunks, AtomicBoolean cancel) throws Exception {
        MediaExtractor probe = extractor(c, src);
        long durUs;
        try {
            int t = audioTrack(probe);
            if (t < 0) throw new IllegalStateException("no audio track");
            MediaFormat f = probe.getTrackFormat(t);
            durUs = f.containsKey(MediaFormat.KEY_DURATION) ? f.getLong(MediaFormat.KEY_DURATION) : 0;
        } finally {
            probe.release();
        }
        if (durUs <= 0) durUs = durationMs(c, MediaLib.srcUri(src)) * 1000L;
        if (durUs <= 0 && MediaLib.srcFile(src) != null) durUs = durationMs(c, MediaLib.srcFile(src)) * 1000L;
        final int[] mx = new int[(int) Math.max(16, Math.ceil(durUs / 1e6 * PEAK_RATE) + 4)];
        final long total = Math.max(1, durUs);
        long startUs = Math.max(0, Math.min(startMs * 1000L, durUs - 1_000_000L));
        if (startUs < 5_000_000L) startUs = 0;
        final long[] done = {0};
        if (startUs > 0) {
            decodeRange(c, src, startUs, Long.MAX_VALUE, mx, prog, chunks, cancel, done, total);
            decodeRange(c, src, 0, startUs, mx, prog, chunks, cancel, done, total);
        } else {
            decodeRange(c, src, 0, Long.MAX_VALUE, mx, prog, chunks, cancel, done, total);
        }
        int last = mx.length - 1;
        while (last > 0 && mx[last] == 0) last--;
        byte[] out = new byte[last + 1];
        for (int i = 0; i <= last; i++) out[i] = toByte(mx[i]);
        JSONObject o = new JSONObject();
        o.put("rate", PEAK_RATE);
        o.put("scale", "sqrt");
        o.put("dur", durUs / 1000L);
        o.put("data", Base64.encodeToString(out, Base64.NO_WRAP));
        return o;
    }

    private static byte toByte(int peak) {
        return (byte) Math.min(255, (int) Math.round(Math.sqrt(peak / 32768.0) * 255));
    }

    /** Decodes [fromUs, toUs) into the shared maxima array, emitting finished pieces about twice a second. */
    private static void decodeRange(Context c, JSONObject src, long fromUs, long toUs, int[] mx, Progress prog, Chunks chunks,
                                    AtomicBoolean cancel, long[] done, long total) throws Exception {
        MediaExtractor ex = extractor(c, src);
        MediaCodec dec = null;
        try {
            int t = audioTrack(ex);
            ex.selectTrack(t);
            MediaFormat fmt = ex.getTrackFormat(t);
            if (fromUs > 0) ex.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            dec.configure(fmt, null, null, 0);
            dec.start();
            int channels = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int rate = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            boolean floatPcm = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inEos = false, outEos = false;
            int emitFrom = (int) (fromUs * PEAK_RATE / 1_000_000L);
            int hi = emitFrom;
            long lastEmit = System.currentTimeMillis(), lastTs = fromUs;
            int idle = 0;
            while (!outEos) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                if (!inEos) {
                    int ii = dec.dequeueInputBuffer(5000);
                    if (ii >= 0) {
                        ByteBuffer ib = dec.getInputBuffer(ii);
                        int n = ib != null ? ex.readSampleData(ib, 0) : -1;
                        long ts = ex.getSampleTime();
                        if (n < 0 || ts >= toUs) {
                            dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inEos = true;
                        } else {
                            dec.queueInputBuffer(ii, 0, n, Math.max(0, ts), 0);
                            ex.advance();
                        }
                    }
                }
                int oi = dec.dequeueOutputBuffer(info, 5000);
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = dec.getOutputFormat();
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (Build.VERSION.SDK_INT >= 24 && of.containsKey(MediaFormat.KEY_PCM_ENCODING)) floatPcm = of.getInteger(MediaFormat.KEY_PCM_ENCODING) == 4;
                } else if (oi >= 0) {
                    idle = 0;
                    ByteBuffer ob = dec.getOutputBuffer(oi);
                    if (ob != null && info.size > 0 && info.presentationTimeUs < toUs) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        ByteBuffer le = ob.slice().order(ByteOrder.LITTLE_ENDIAN);
                        int ch = Math.max(1, channels);
                        int bytesPerSample = floatPcm ? 4 : 2;
                        int frames = info.size / (bytesPerSample * ch);
                        // a waveform needs no more than ~8000 looks a second
                        int stride = Math.max(1, rate / 8000);
                        double idxPerFrame = (double) PEAK_RATE / rate;
                        double base = info.presentationTimeUs * (double) PEAK_RATE / 1e6;
                        for (int f = 0; f < frames; f += stride) {
                            int idx = (int) (base + f * idxPerFrame);
                            if (idx < 0 || idx >= mx.length) continue;
                            int v;
                            int off = f * ch * bytesPerSample;
                            if (floatPcm) {
                                float a = le.getFloat(off), b = ch > 1 ? le.getFloat(off + 4) : 0f;
                                v = (int) (Math.max(Math.abs(a), Math.abs(b)) * 32767f);
                            } else {
                                int a = le.getShort(off), b = ch > 1 ? le.getShort(off + 2) : 0;
                                v = Math.max(a < 0 ? -a : a, b < 0 ? -b : b);
                            }
                            if (v > mx[idx]) mx[idx] = v;
                            if (idx > hi) hi = idx;
                        }
                        lastTs = info.presentationTimeUs;
                    }
                    dec.releaseOutputBuffer(oi, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outEos = true;
                    long now = System.currentTimeMillis();
                    if (chunks != null && now - lastEmit > 450 && hi - 1 > emitFrom) {
                        emit(chunks, mx, emitFrom, hi - 1);
                        emitFrom = hi - 1;
                        lastEmit = now;
                        if (prog != null) prog.on(Math.min(0.99, (done[0] + (lastTs - fromUs)) / (double) total));
                    }
                } else if (inEos && ++idle > 400) {
                    break;
                }
            }
            if (chunks != null && hi + 1 > emitFrom) emit(chunks, mx, emitFrom, Math.min(mx.length, hi + 1));
            done[0] += Math.max(0, Math.min(total, toUs == Long.MAX_VALUE ? total : toUs) - fromUs);
        } finally {
            if (dec != null) {
                try { dec.stop(); } catch (Exception ignored) { }
                try { dec.release(); } catch (Exception ignored) { }
            }
            ex.release();
        }
    }

    private static void emit(Chunks chunks, int[] mx, int from, int to) {
        if (to <= from) return;
        byte[] b = new byte[to - from];
        for (int i = from; i < to; i++) b[i - from] = toByte(mx[i]);
        chunks.on(from, b);
    }

    /* ---------- editing: keep the listed ranges, re-encode to AAC ---------- */

    static JSONObject render(Context c, JSONObject src, JSONArray keepMs, String displayName, Progress prog, AtomicBoolean cancel) throws Exception {
        return render(c, src, keepMs, MediaLib.create(c, displayName, "audio/mp4"), 0.008, false, prog, cancel);
    }

    /** fadeSec: ramps at the joins; edges: also fade in at the very start and out at the very end */
    static JSONObject render(Context c, JSONObject src, JSONArray keepMs, final MediaLib.Target target, final double fadeSec, final boolean edges,
                             Progress prog, AtomicBoolean cancel) throws Exception {
        final int n = keepMs.length();
        if (n == 0) throw new IllegalArgumentException("nothing to keep");
        final long[] ks = new long[n], ke = new long[n];
        for (int i = 0; i < n; i++) {
            JSONArray r = keepMs.getJSONArray(i);
            ks[i] = r.getLong(0);
            ke[i] = r.getLong(1);
        }
        final File tmp = new File(c.getCacheDir(), "render-" + System.currentTimeMillis() + ".m4a");
        final Encoder enc = new Encoder(tmp);
        try {
            decode(c, src, new PcmSink() {
                long frame = 0;
                int seg = 0;
                short[] buf = new short[0];

                @Override
                public void pcm(short[] d, int count, int ch, int rate) throws Exception {
                    int outCh = Math.min(ch, 2);
                    if (!enc.started) enc.start(rate, outCh);
                    int frames = count / ch;
                    if (buf.length < frames * outCh) buf = new short[frames * outCh];
                    int w = 0;
                    double fadeFrames = rate * fadeSec; // short ramps hide clicks at the joins
                    for (int f = 0; f < frames; f++) {
                        long tMs = (frame + f) * 1000L / rate;
                        while (seg < n && tMs >= ke[seg]) seg++;
                        if (seg >= n) break;
                        if (tMs < ks[seg]) continue;
                        double fromStart = ((frame + f) - ks[seg] * (double) rate / 1000.0);
                        double toEnd = (ke[seg] * (double) rate / 1000.0) - (frame + f);
                        double g = 1.0;
                        if ((ks[seg] > 0 || edges) && fromStart < fadeFrames) g = Math.min(g, fromStart / fadeFrames);
                        if ((seg < n - 1 || edges) && toEnd < fadeFrames) g = Math.min(g, toEnd / fadeFrames);
                        int base = f * ch;
                        for (int k = 0; k < outCh; k++) {
                            buf[w++] = g >= 1.0 ? d[base + k] : (short) Math.round(d[base + k] * Math.max(0, g));
                        }
                    }
                    frame += frames;
                    if (w > 0) enc.feed(buf, w);
                }
            }, prog, cancel);
            enc.finish();
            JSONObject item = MediaLib.copyInTarget(c, target, tmp);
            if (item == null) throw new IllegalStateException("publish");
            return item;
        } catch (Exception e) {
            enc.abort();
            MediaLib.discard(c, target);
            throw e;
        } finally {
            tmp.delete();
        }
    }

    /** PCM → AAC-LC → MPEG-4 file. */
    static final class Encoder {
        final File out;
        MediaCodec codec;
        MediaMuxer muxer;
        int track = -1;
        boolean started, muxing;
        int rate, channels, bitrate;
        long framesIn = 0;
        final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        boolean opus;                // Opus in Ogg (Android 10+, 48 kHz) instead of AAC in MP4
        java.io.FileDescriptor fd;   // write straight into an open file (Android 8+), out is then null

        Encoder(File out) { this.out = out; }
        Encoder(java.io.FileDescriptor fd) { this.out = null; this.fd = fd; }

        void start(int rate, int channels) throws Exception {
            this.rate = rate;
            this.channels = channels;
            String mime = opus ? MediaFormat.MIMETYPE_AUDIO_OPUS : MediaFormat.MIMETYPE_AUDIO_AAC;
            MediaFormat f = MediaFormat.createAudioFormat(mime, rate, channels);
            if (!opus) f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            f.setInteger(MediaFormat.KEY_BIT_RATE, bitrate > 0 ? bitrate : channels > 1 ? 192000 : 128000);
            f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024);
            codec = MediaCodec.createEncoderByType(mime);
            codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            int of = opus ? MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG : MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
            muxer = fd != null && Build.VERSION.SDK_INT >= 26 ? new MediaMuxer(fd, of) : new MediaMuxer(out.getAbsolutePath(), of);
            started = true;
        }

        void feed(short[] s, int count) throws Exception {
            int off = 0;
            while (off < count) {
                int ii = codec.dequeueInputBuffer(10000);
                if (ii >= 0) {
                    ByteBuffer ib = codec.getInputBuffer(ii);
                    ib.clear();
                    ib.order(ByteOrder.LITTLE_ENDIAN);
                    int room = ib.remaining() / 2;
                    room -= room % channels;
                    int take = Math.min(room, count - off);
                    for (int i = 0; i < take; i++) ib.putShort(s[off + i]);
                    long pts = framesIn * 1_000_000L / rate;
                    codec.queueInputBuffer(ii, 0, take * 2, pts, 0);
                    framesIn += take / channels;
                    off += take;
                }
                drain(false);
            }
        }

        void finish() throws Exception {
            if (!started) throw new IllegalStateException("no audio");
            boolean queued = false;
            for (int tries = 0; tries < 500 && !queued; tries++) {
                int ii = codec.dequeueInputBuffer(10000);
                if (ii >= 0) {
                    codec.queueInputBuffer(ii, 0, 0, framesIn * 1_000_000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    queued = true;
                } else {
                    drain(false);
                }
            }
            drain(true);
            if (muxing) muxer.stop();
            release();
        }

        void drain(boolean untilEos) throws Exception {
            int idle = 0;
            while (true) {
                int oi = codec.dequeueOutputBuffer(info, 10000);
                if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!untilEos || ++idle > 300) return;
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                    muxing = true;
                } else if (oi >= 0) {
                    ByteBuffer ob = codec.getOutputBuffer(oi);
                    boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                    if (ob != null && info.size > 0 && muxing && !config) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        muxer.writeSampleData(track, ob, info);
                    }
                    codec.releaseOutputBuffer(oi, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
                }
            }
        }

        void release() {
            try { if (codec != null) { codec.stop(); codec.release(); } } catch (Exception ignored) { }
            try { if (muxer != null) muxer.release(); } catch (Exception ignored) { }
            codec = null;
            muxer = null;
        }

        void abort() {
            release();
            if (out != null) out.delete();
        }
    }
}
