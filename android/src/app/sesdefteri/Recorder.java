package app.sesdefteri;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * Native recorder (3.1): AudioRecord → AAC (.m4a), WAV or FLAC inside Music/Ses Defteri, kept alive by RecordService.
 * Because we see the samples, the page also gets a live spectrum + loudness, and the input can be heard on headphones.
 */
final class Recorder {
    private static AudioRecord ar;
    private static Sink sink;
    private static Thread th;
    private static MediaLib.Target target;
    private static ParcelFileDescriptor pfd;
    private static volatile String state = "idle";
    static String stateName() { return state; }
    private static volatile boolean running;
    private static volatile Exception fail;
    private static volatile float peak;
    private static volatile Timeline.Meter meter;
    private static volatile AudioTrack mon;
    // 5.4: what the headphones hear while recording can have its own reverb / echo (the file stays dry)
    private static volatile app.sesdefteri.dsp.Node monFx;
    private static volatile Timeline.RackOut monOut;
    private static boolean lowLat;
    private static int rate, chans;
    // 5.16: live text (null when off); liveModel from the start options
    private static volatile LiveText live;
    private static String liveModel = "", liveLang = "", liveW = "";

    private Recorder() { }

    private static boolean callPause = true;
    private static boolean agc = true;
    private static boolean temp;   // 2.9 latency test: a throwaway file in the cache, not in Music
    private static String fmt = "m4a";
    // 3.7: chosen quality (0 = from the old q setting), live noise suppression / echo cancelling, Bluetooth mic, folder
    private static int optRate, optCh, optKbps;
    private static boolean ns, aec, bt, btOn;
    private static String dir = "";
    private static android.media.audiofx.NoiseSuppressor nsFx;
    private static android.media.audiofx.AcousticEchoCanceler aecFx;

    /** opts: {q: std|high|stereo, src: auto|builtin|wired|usb, call: pause during calls, fmt: m4a|wav|flac} */
    static synchronized String start(Context c, String mode, String displayName, String optsJson) {
        JSONObject o;
        try { o = new JSONObject(optsJson == null ? "{}" : optsJson); } catch (Exception e) { o = new JSONObject(); }
        callPause = o.optBoolean("call", true);
        agc = o.optBoolean("agc", true);
        lowLat = o.optBoolean("lowlat", false);
        temp = o.optBoolean("temp", false);
        fmt = temp ? "m4a" : o.optString("fmt", "m4a");
        if (!"wav".equals(fmt) && !"flac".equals(fmt)) fmt = "m4a";
        optRate = o.optInt("rate", 0); optCh = o.optInt("ch", 0); optKbps = o.optInt("kbps", 0);
        ns = o.optBoolean("ns", false); aec = o.optBoolean("aec", false); bt = !temp && o.optBoolean("bt", false);
        dir = o.optString("dir", "");
        liveModel = temp ? "" : o.optString("live", "");
        liveLang = o.optString("liveLang", "");
        liveW = o.optString("liveW", "");
        int dot = displayName.lastIndexOf('.');
        displayName = (dot > 0 ? displayName.substring(0, dot) : displayName) + "." + fmt;
        return start(c, mode, displayName, o.optString("q", "std"), o.optString("src", "auto"));
    }

    private static String start(Context c, String mode, String displayName, String q, String src) {
        JSONObject res = new JSONObject();
        try {
            if (!"idle".equals(state)) { res.put("ok", false); res.put("error", "busy"); return res.toString(); }
            boolean music = mode.startsWith("music");
            // automatic gain off: VOICE_RECOGNITION is the source Android requires to be free of AGC
            int source = agc ? MediaRecorder.AudioSource.MIC : MediaRecorder.AudioSource.VOICE_RECOGNITION;
            if ("music".equals(mode)) {
                AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                String unp = am != null ? am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) : null;
                source = "true".equals(unp) ? MediaRecorder.AudioSource.UNPROCESSED : MediaRecorder.AudioSource.CAMCORDER;
            }
            boolean stereo = music || "stereo".equals(q) || optCh == 2, hi = stereo || "high".equals(q) || !"m4a".equals(fmt);
            rate = optRate > 0 ? optRate : hi ? 48000 : 44100;
            chans = stereo ? 2 : 1;
            android.media.AudioDeviceInfo sco = bt ? btRoute(c) : null;
            if (sco != null) { source = MediaRecorder.AudioSource.VOICE_COMMUNICATION; chans = 1; }
            // 4.2: most phones run their noise suppressor / echo canceller only on the "call" source: on MIC the switch did nothing
            // 5.4 low latency: the "live performance" input path (API 29+), meant for singing along on headphones
            if (lowLat && !music && sco == null && !ns && !aec && Build.VERSION.SDK_INT >= 29) source = MediaRecorder.AudioSource.VOICE_PERFORMANCE;
            int plain = source;
            if (!music && (ns || aec) && sco == null) { source = MediaRecorder.AudioSource.VOICE_COMMUNICATION; chans = 1; }
            int cfg = chans > 1 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
            ar = newRecord(source, cfg);
            if (ar == null && rate != 44100) { rate = 44100; ar = newRecord(source, cfg); }   // a rate this phone does not take
            if (ar == null && source != plain) { source = plain; ar = newRecord(source, cfg); }
            if (ar == null) throw new IllegalStateException("mic");
            if (sco != null && Build.VERSION.SDK_INT >= 23) ar.setPreferredDevice(sco);
            fx(ns, aec);
            if (Build.VERSION.SDK_INT >= 28 && !"auto".equals(src) && sco == null) {
                AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) for (android.media.AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                    int t = d.getType();
                    boolean m = "builtin".equals(src) ? t == android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC
                        : "wired".equals(src) ? t == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
                        : "usb".equals(src) && (t == android.media.AudioDeviceInfo.TYPE_USB_DEVICE || t == android.media.AudioDeviceInfo.TYPE_USB_HEADSET);
                    if (m) { ar.setPreferredDevice(d); break; }
                }
            }
            String mime = "wav".equals(fmt) ? "audio/x-wav" : "flac".equals(fmt) ? "audio/flac" : "audio/mp4";
            if (temp) { target = new MediaLib.Target(); target.file = new File(c.getCacheDir(), "latency.m4a"); target.name = target.file.getName(); }
            else target = MediaLib.create(c, displayName, mime, dir);
            sink = openSink(c);
            if (!temp) journal(c, true);
            meter = new Timeline.Meter(rate);
            fail = null;
            peak = 0;
            ar.startRecording();
            if (ar.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("mic busy");
            state = "rec";
            running = true;
            th = new Thread(new Runnable() { @Override public void run() { loop(); } }, "recorder");
            th.setPriority(Thread.MAX_PRIORITY);
            th.start();
            startService(c, true);
            if (!liveModel.isEmpty() && ("w".equals(liveModel) ? AsrModels.pick(c, liveW) != null : LiveText.ready(c, liveModel))) try { live = new LiveText(c, liveModel, rate, liveLang, liveW); } catch (Throwable e) { live = null; }
            res.put("ok", true);
            res.put("name", target.name);
        } catch (Exception e) {
            cleanup(c, true);
            // some phones refuse the unprocessed/camcorder source: retry with the plain microphone
            if ("music".equals(mode)) return start(c, "music-mic", displayName, q, src);
            try { res.put("ok", false); res.put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) { }
        }
        return res.toString();
    }

    private static AudioRecord newRecord(int source, int cfg) {
        try {
            int min = AudioRecord.getMinBufferSize(rate, cfg, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) return null;
            AudioRecord a = new AudioRecord(source, rate, cfg, AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 4, rate / 5 * chans * 2));
            if (a.getState() == AudioRecord.STATE_INITIALIZED) return a;
            a.release();
        } catch (Exception ignored) { }
        return null;
    }

    /** the phone's own noise suppressor / echo canceller on the microphone; can be switched while recording */
    static synchronized JSONObject fx(boolean n, boolean e) {
        ns = n; aec = e;
        JSONObject o = new JSONObject();
        try {
            o.put("ns", android.media.audiofx.NoiseSuppressor.isAvailable());
            o.put("aec", android.media.audiofx.AcousticEchoCanceler.isAvailable());
            if (ar == null) return o;
            int id = ar.getAudioSessionId();
            if (n && nsFx == null && android.media.audiofx.NoiseSuppressor.isAvailable()) nsFx = android.media.audiofx.NoiseSuppressor.create(id);
            if (nsFx != null) nsFx.setEnabled(n);
            if (e && aecFx == null && android.media.audiofx.AcousticEchoCanceler.isAvailable()) aecFx = android.media.audiofx.AcousticEchoCanceler.create(id);
            if (aecFx != null) aecFx.setEnabled(e);
        } catch (Exception ignored) { }
        return o;
    }

    private static void fxRelease() {
        try { if (nsFx != null) nsFx.release(); } catch (Exception ignored) { }
        try { if (aecFx != null) aecFx.release(); } catch (Exception ignored) { }
        nsFx = null; aecFx = null;
    }

    private static Context btCtx;

    /** a connected Bluetooth headset microphone, routed for recording; null when there is none */
    private static android.media.AudioDeviceInfo btRoute(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null || Build.VERSION.SDK_INT < 23) return null;
            android.media.AudioDeviceInfo in = null;
            for (android.media.AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS))
                if (d.getType() == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO) { in = d; break; }
            if (in == null) return null;
            if (Build.VERSION.SDK_INT >= 31) {
                for (android.media.AudioDeviceInfo d : am.getAvailableCommunicationDevices())
                    if (d.getType() == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO) { am.setCommunicationDevice(d); break; }
            } else {
                am.startBluetoothSco();
                am.setBluetoothScoOn(true);
                Thread.sleep(600);   // the headset needs a moment to open its microphone
            }
            btOn = true; btCtx = c.getApplicationContext();
            return in;
        } catch (Exception e) {
            return null;
        }
    }

    private static void btOff() {
        if (!btOn) return;
        btOn = false;
        try {
            AudioManager am = (AudioManager) btCtx.getSystemService(Context.AUDIO_SERVICE);
            if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice();
            else { am.setBluetoothScoOn(false); am.stopBluetoothSco(); }
        } catch (Exception ignored) { }
    }

    private static void loop() {
        short[] buf = new short[(rate / (lowLat ? 200 : 50)) * chans];   // 20 ms (5 ms in low-latency mode: what the headphones hear comes sooner)
        short[] mb = new short[buf.length];
        float[][] mf = new float[2][buf.length / chans];
        float[][] fl = new float[2][buf.length / chans];
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            while (running) {
                int n = ar.read(buf, 0, buf.length);
                if (n <= 0) { if (n < 0) throw new IllegalStateException("read " + n); continue; }
                int fr = n / chans;
                float pk = 0;
                for (int i = 0; i < fr; i++) {
                    float l = buf[i * chans] / 32768f, r = chans > 1 ? buf[i * chans + 1] / 32768f : l;
                    fl[0][i] = l; fl[1][i] = r;
                    float a = Math.max(Math.abs(l), Math.abs(r));
                    if (a > pk) pk = a;
                }
                AudioTrack m = mon;
                app.sesdefteri.dsp.Node fx = monFx;
                if (m != null && fx != null) try {
                    for (int c = 0; c < chans; c++) System.arraycopy(fl[c], 0, mf[c], 0, fr);
                    fx.push(chans > 1 ? mf : new float[][] { mf[0] }, fr);
                    float[][] y = monOut.o;
                    for (int i = 0; i < fr; i++) for (int c = 0; c < chans; c++) {
                        float v = y[Math.min(c, y.length - 1)][i];
                        mb[i * chans + c] = (short) (Math.max(-1f, Math.min(1f, v)) * 32767);
                    }
                    m.write(mb, 0, n, AudioTrack.WRITE_NON_BLOCKING);
                } catch (Exception ignored) { }
                else if (m != null) try { m.write(buf, 0, n, AudioTrack.WRITE_NON_BLOCKING); } catch (Exception ignored) { }
                if (!"rec".equals(state)) continue;
                if (pk > peak) peak = pk;
                meter.feed(fl, fr);
                sink.write(buf, n);
                LiveText lt = live;
                if (lt != null) lt.feed(fl[0], fl[1], fr, chans);
            }
        } catch (Exception e) {
            fail = e;
        }
    }

    static synchronized boolean pause() {
        if (!"rec".equals(state)) return false;
        state = "paused";
        return true;
    }

    static synchronized boolean resume() {
        if (!"paused".equals(state)) return false;
        state = "rec";
        return true;
    }

    /** Peak since the previous call, 0..1. */
    static float level() {
        if (!"rec".equals(state)) return 0f;
        float p = peak;
        peak = 0;
        return Math.min(1f, p);
    }

    /** live spectrum + loudness of what is being recorded, "" when idle */
    /** 5.16: live text for the recording screen */
    static String liveText(int from) {
        LiveText lt = live;
        return lt == null ? "" : lt.poll(from);
    }

    static String meter() {
        Timeline.Meter m = meter;
        return m == null || "idle".equals(state) ? "" : m.json(Long.MAX_VALUE);
    }

    /** 5.4: reverb and echo (0–100 each) on what the headphones hear; both 0 = the plain microphone */
    static synchronized void monitorFx(double rev, double echo) {
        int ch = chans > 0 ? chans : 1, fs = rate > 0 ? rate : 48000;
        if (rev <= 0 && echo <= 0) { monFx = null; return; }
        Timeline.RackOut out = new Timeline.RackOut();
        app.sesdefteri.dsp.Node head = null, tail = null;
        if (rev > 0) { head = tail = new app.sesdefteri.dsp.Stages.Reverb(ch, fs, 0.65, rev / 100.0); }
        if (echo > 0) { app.sesdefteri.dsp.Node d = new app.sesdefteri.dsp.Stages.Delay(ch, fs, 280, 0.3, echo / 100.0); if (head == null) head = tail = d; else tail = tail.to(d); }
        tail.to(out);
        monOut = out;
        monFx = head;
    }

    /** hear the microphone on headphones while recording */
    static synchronized boolean monitor(boolean on) {
        AudioTrack old = mon;
        mon = null;
        if (old != null) { try { old.stop(); } catch (Exception ignored) { } old.release(); }
        if (!on || ar == null) return false;
        try {
            int cfg = chans > 1 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
            int min = AudioTrack.getMinBufferSize(rate, cfg, AudioFormat.ENCODING_PCM_16BIT);
            AudioTrack t;
            if (Build.VERSION.SDK_INT >= 26) {
                t = new AudioTrack.Builder()
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(cfg).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(min).setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
            } else {
                t = new AudioTrack(AudioManager.STREAM_MUSIC, rate, cfg, AudioFormat.ENCODING_PCM_16BIT, min, AudioTrack.MODE_STREAM);
            }
            t.play();
            mon = t;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static synchronized String stop(Context c) {
        JSONObject res = new JSONObject();
        try {
            if (ar == null) { res.put("ok", false); res.put("error", "idle"); return res.toString(); }
            boolean cut = fail != null;
            halt();
            LiveText lt = live;
            live = null;
            Sink s = sink;
            sink = null;
            // 5.14.2: a recording cut short by an error (full storage, mic taken) keeps what was written
            boolean ok = s != null && s.frames() > rate / 10;
            try { if (ok) s.finish(); else if (s != null) s.abort(); } catch (Exception e) { ok = false; }
            try { if (pfd != null) pfd.close(); } catch (Exception ignored) { }
            pfd = null;
            journal(c, false);
            startService(c, false);
            state = "idle";
            meter = null;
            if (!ok) {
                MediaLib.discard(c, target);
                target = null;
                if (lt != null) lt.end(0);
                res.put("ok", false);
                res.put("error", "empty");
                return res.toString();
            }
            JSONObject item = MediaLib.publish(c, target);
            target = null;
            if (item == null) { if (lt != null) lt.end(0); res.put("ok", false); res.put("error", "publish"); return res.toString(); }
            res.put("ok", true);
            res.put("item", item);
            if (cut) res.put("cut", true);
            // the recording is safe first; then the text gets up to 5 s to catch up
            if (lt != null) { JSONObject lr = lt.end(5000); if (lr != null) res.put("live", lr); }
        } catch (Exception e) {
            try { res.put("ok", false); res.put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) { }
        }
        return res.toString();
    }

    /** stops the thread and the microphone; true when something was recorded without errors */
    private static boolean halt() {
        running = false;
        if (th != null) { try { th.join(2000); } catch (InterruptedException ignored) { } }
        th = null;
        monitor(false);
        fxRelease();
        btOff();
        if (ar != null) {
            try { ar.stop(); } catch (Exception ignored) { }
            ar.release();
        }
        ar = null;
        return fail == null && sink != null && sink.frames() > rate / 10;
    }

    static synchronized void cancel(Context c) {
        cleanup(c, true);
    }

    static synchronized boolean active() {
        return !"idle".equals(state);
    }

    private static void cleanup(Context c, boolean discard) {
        halt();
        LiveText lt = live;
        live = null;
        if (lt != null) lt.end(0);
        if (sink != null) sink.abort();
        sink = null;
        try { if (pfd != null) pfd.close(); } catch (Exception ignored) { }
        pfd = null;
        if (discard && target != null) MediaLib.discard(c, target);
        if (discard) journal(c, false);
        target = null;
        meter = null;
        state = "idle";
        startService(c, false);
    }

    /* ---------- 5.14.3: a recording cut off by a crash or a kill ---------- */

    private static File rawFile(Context c) { return new File(c.getFilesDir(), "rec-active.aac"); }

    private static void journal(Context c, boolean on) {
        File j = new File(c.getFilesDir(), "rec-active.json");
        if (!on) { j.delete(); return; }
        try {
            JSONObject o = new JSONObject();
            o.put("uri", target.uri != null ? target.uri.toString() : "");
            o.put("file", target.file != null ? target.file.getAbsolutePath() : "");
            o.put("name", target.name); o.put("fmt", fmt); o.put("rate", rate); o.put("ch", chans);
            FileOutputStream w = new FileOutputStream(j);
            w.write(o.toString().getBytes("UTF-8"));
            w.close();
        } catch (Exception ignored) { }
    }

    /** at start-up: finishes the recording a crash left half written (still hidden on Android 10+); the saved item or null */
    static synchronized JSONObject recover(Context c) {
        File j = new File(c.getFilesDir(), "rec-active.json");
        if (!"idle".equals(state) || !j.exists()) return null;
        File rf = rawFile(c);
        MediaLib.Target t = new MediaLib.Target();
        boolean ok = false;
        try {
            byte[] b = new byte[(int) Math.min(j.length(), 1 << 16)];
            java.io.DataInputStream in = new java.io.DataInputStream(new java.io.FileInputStream(j));
            in.readFully(b);
            in.close();
            JSONObject o = new JSONObject(new String(b, "UTF-8"));
            String u = o.optString("uri", ""), f = o.optString("file", ""), fm = o.optString("fmt", "m4a");
            if (!u.isEmpty()) t.uri = android.net.Uri.parse(u);
            if (!f.isEmpty()) t.file = new File(f);
            t.name = o.optString("name", "rec");
            int ch = Math.max(1, o.optInt("ch", 1));
            android.os.ParcelFileDescriptor p = t.uri != null ? c.getContentResolver().openFileDescriptor(t.uri, "m4a".equals(fm) ? "rwt" : "rw") : null;
            try {
                java.io.FileDescriptor fd = p != null ? p.getFileDescriptor() : null;
                if ("m4a".equals(fm)) ok = rf.exists() && AudioTools.remux(rf, fd, t.file) > 0;
                else {
                    FileChannel fc = fd != null ? new FileOutputStream(fd).getChannel() : new RandomAccessFile(t.file, "rw").getChannel();
                    long sz = fc.size();
                    if ("wav".equals(fm)) {
                        long fr = Math.max(0, (sz - 44) / (2L * ch));
                        if (fr > 0) fc.write(ByteBuffer.wrap(app.sesdefteri.dsp.Wav.header(ch, o.optInt("rate", 48000), false, fr)), 0);
                        ok = fr > o.optInt("rate", 48000) / 10;
                    } else ok = sz > 4096;
                    fc.close();
                }
            } finally { if (p != null) p.close(); }
        } catch (Exception e) { ok = false; }
        rf.delete();
        j.delete();
        if (!ok) { if (t.uri != null || t.file != null) MediaLib.discard(c, t); return null; }
        return MediaLib.publish(c, t);
    }

    /* ---------- where the samples go ---------- */

    interface Sink {
        void write(short[] s, int n) throws Exception;
        long frames();
        void finish() throws Exception;
        void abort();
    }

    private static Sink openSink(Context c) throws Exception {
        FileChannel fc = null;
        java.io.FileDescriptor fd = null;
        if (target.uri != null) {
            pfd = c.getContentResolver().openFileDescriptor(target.uri, "rw");
            if (pfd == null) throw new IllegalStateException("fd");
            fd = pfd.getFileDescriptor();
        }
        if ("m4a".equals(fmt)) {
            final AudioTools.Encoder enc = fd != null ? new AudioTools.Encoder(fd) : new AudioTools.Encoder(target.file);
            if (optKbps > 0) enc.bitrate = optKbps * 1000;
            // 5.14.3: an .m4a is unreadable until it is closed; the frames first go to a side file that survives a crash
            final File rf = rawFile(c);
            final java.io.FileDescriptor ofd = fd;
            final File of = target.file;
            enc.raw = new java.io.DataOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(rf), 1 << 16));
            enc.start(rate, chans);
            return new Sink() {
                long fr;
                @Override public void write(short[] s, int n) throws Exception { enc.feed(s, n); fr += n / chans; }
                @Override public long frames() { return fr; }
                @Override public void finish() throws Exception {
                    enc.finish();
                    int n = AudioTools.remux(rf, ofd, of);
                    rf.delete();
                    if (n == 0) throw new IllegalStateException("empty");
                }
                @Override public void abort() { enc.release(); rf.delete(); }
            };
        }
        fc = fd != null ? new FileOutputStream(fd).getChannel() : new RandomAccessFile(target.file, "rw").getChannel();
        fc.truncate(0);
        return "wav".equals(fmt) ? new WavSink(fc) : new FlacSink(fc, rate, chans);
    }

    /** 16-bit WAV; the header gets the real length at the end */
    static final class WavSink implements Sink {
        final FileChannel fc;
        ByteBuffer bb = ByteBuffer.allocate(0);
        long fr;

        WavSink(FileChannel fc) throws Exception { this.fc = fc; fc.write(ByteBuffer.wrap(app.sesdefteri.dsp.Wav.header(chans, rate, false, 0))); }

        @Override public void write(short[] s, int n) throws Exception {
            if (bb.capacity() < n * 2) bb = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN);
            bb.clear();
            for (int i = 0; i < n; i++) bb.putShort(s[i]);
            bb.flip();
            while (bb.hasRemaining()) fc.write(bb);
            fr += n / chans;
            // 5.14.2: the header follows every ~5 s, so a file cut off by a crash still plays
            if (fr - hdr >= rate * 5L) { hdr = fr; fc.write(ByteBuffer.wrap(app.sesdefteri.dsp.Wav.header(chans, rate, false, fr)), 0); }
        }
        long hdr;
        @Override public long frames() { return fr; }
        @Override public void finish() throws Exception {
            fc.write(ByteBuffer.wrap(app.sesdefteri.dsp.Wav.header(chans, rate, false, fr)), 0);
            fc.close();
        }
        @Override public void abort() { try { fc.close(); } catch (Exception ignored) { } }
    }

    /** lossless FLAC through the system encoder; the stream header gets the real length at the end */
    static final class FlacSink implements Sink {
        final FileChannel fc;
        final MediaCodec codec;
        final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        final int rate, chans;
        long fr, in;
        final byte[] head = new byte[42];
        int hn;

        FlacSink(FileChannel fc, int rate, int chans) throws Exception {
            this.fc = fc; this.rate = rate; this.chans = chans;
            MediaFormat f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_FLAC, rate, chans);
            f.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5);
            f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024);
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC);
            codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
        }

        @Override public void write(short[] s, int n) throws Exception {
            int off = 0;
            while (off < n) {
                int ii = codec.dequeueInputBuffer(10000);
                if (ii >= 0) {
                    ByteBuffer ib = codec.getInputBuffer(ii);
                    ib.clear();
                    ib.order(ByteOrder.LITTLE_ENDIAN);
                    int take = Math.min(ib.remaining() / 2 / chans * chans, n - off);
                    for (int i = 0; i < take; i++) ib.putShort(s[off + i]);
                    codec.queueInputBuffer(ii, 0, take * 2, in * 1000000L / rate, 0);
                    in += take / chans;
                    off += take;
                }
                drain(false);
            }
            fr += n / chans;
        }

        private void drain(boolean eos) throws Exception {
            int idle = 0;
            while (true) {
                int oi = codec.dequeueOutputBuffer(info, 10000);
                if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!eos || ++idle > 300) return; continue; }
                if (oi < 0) continue;
                ByteBuffer ob = codec.getOutputBuffer(oi);
                if (ob != null && info.size > 0) {
                    ob.position(info.offset); ob.limit(info.offset + info.size);
                    for (int i = ob.position(); i < ob.limit() && hn < 42; i++) head[hn++] = ob.get(i);
                    while (ob.hasRemaining()) fc.write(ob);
                }
                codec.releaseOutputBuffer(oi, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
            }
        }

        @Override public long frames() { return fr; }

        @Override public void finish() throws Exception {
            for (int tries = 0; tries < 500; tries++) {
                int ii = codec.dequeueInputBuffer(10000);
                if (ii >= 0) { codec.queueInputBuffer(ii, 0, 0, in * 1000000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM); break; }
                drain(false);
            }
            drain(true);
            try { codec.stop(); } catch (Exception ignored) { }
            codec.release();
            // STREAMINFO: the 36-bit total samples fill the low nibble of byte 21 and bytes 22–25
            if (hn == 42 && head[0] == 'f' && head[1] == 'L' && head[2] == 'a' && head[3] == 'C') {
                long t = fr & 0xFFFFFFFFFL;
                byte[] b = new byte[5];
                b[0] = (byte) ((head[21] & 0xF0) | (int) ((t >> 32) & 0x0F));
                b[1] = (byte) (t >> 24); b[2] = (byte) (t >> 16); b[3] = (byte) (t >> 8); b[4] = (byte) t;
                fc.write(ByteBuffer.wrap(b), 21);
            }
            fc.close();
        }

        @Override public void abort() {
            try { codec.stop(); } catch (Exception ignored) { }
            try { codec.release(); } catch (Exception ignored) { }
            try { fc.close(); } catch (Exception ignored) { }
        }
    }

    /* ---------- pause during phone calls (also VoIP calls), resume afterwards ---------- */
    private static boolean auto;
    private static android.os.Handler calls;
    private static Runnable poll;

    private static void watchCalls(final Context c, boolean on) {
        if (calls == null) calls = new android.os.Handler(android.os.Looper.getMainLooper());
        if (poll != null) calls.removeCallbacks(poll);
        poll = null;
        auto = false;
        if (!on || !callPause) return;
        final AudioManager am = (AudioManager) c.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        poll = new Runnable() {
            @Override public void run() {
                int m = am.getMode();
                boolean call = m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_IN_COMMUNICATION || m == AudioManager.MODE_RINGTONE;
                synchronized (Recorder.class) {
                    if (ar == null) return;
                    if (call && "rec".equals(state)) { state = "paused"; auto = true; }
                    else if (!call && auto && "paused".equals(state)) { state = "rec"; auto = false; }
                }
                if (poll == this) calls.postDelayed(this, 700);
            }
        };
        calls.postDelayed(poll, 700);
    }

    /** {"state": rec|paused|idle, "call": paused because of a call} */
    static synchronized String info() {
        return "{\"state\":\"" + state + "\",\"call\":" + auto + ",\"fail\":" + (fail != null) + "}";
    }

    static synchronized boolean resumeManual() { auto = false; return resume(); }

    private static void startService(Context c, boolean on) {
        watchCalls(c, on);
        Intent svc = new Intent(c, RecordService.class);
        try {
            if (on) {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(svc);
                else c.startService(svc);
            } else {
                c.stopService(svc);
            }
        } catch (Exception ignored) { }
    }
}
