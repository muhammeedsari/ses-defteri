package app.sesdefteri;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import app.sesdefteri.dsp.Wav;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Native playback with a queue, shuffle/repeat, audio focus, lock-screen and notification controls. */
final class Player implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
    interface Listener { void onPlayerEvent(String json); }

    static final Player I = new Player();

    private Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaPlayer mp;
    private JSONArray queue = new JSONArray();
    private List<Integer> order = new ArrayList<>();
    private int pos = -1;              // position in order
    private boolean prepared, wantPlay, playing;
    private long pendingSeek = -1;
    private float rate = 1f, pitch = 1f;   // 5.3: pitch factor (2^(semitones/12)), the tempo stays
    private boolean shuffle;
    private int repeat;                // 0 off, 1 all, 2 one
    private String error = "";
    private MediaSession session;
    private Bitmap art;
    private Listener listener;
    private AudioFocusRequest focusReq;
    private boolean noisyRegistered;

    /* ---------- 2.1: level matching, EQ, crossfade / gapless, A-B loop, skip silence, sleep timer ---------- */
    private final java.util.HashMap<String, Double> gains = new java.util.HashMap<>();   // id → dB (level matching)
    private double[] eqDb;                       // gains at eqFc (3.8: 10 bands 31 Hz … 16 kHz) or null
    private double[] eqFc = { 100, 300, 1000, 3000, 8000 };
    private int bassStr, virtStr;                // 3.8: bass booster / virtual surround, 0..1000
    private int xfadeMs;                         // 0 = off
    private boolean gapless = true;
    private long loopA = -1, loopB = -1;
    private String skipsId = "";
    private long[] skips = new long[0];          // [s0, e0, s1, e1, …] in ms
    private long seekWait;                       // no loop / skip seek before this (uptime ms)
    private int lastSkip = -1;                   // the silence just skipped: never twice in a row
    private long sleepAt;                        // wall clock, 0 = off
    private boolean sleepEnd;                    // stop when this track ends
    private MediaPlayer nmp;                     // the next track, prepared ahead
    private int npos = -1;
    private boolean fading;
    private long fadeStart, fadeLen;
    private long lastCur = -1, stillSince;    // stall watchdog: the position must move while playing
    private int stalls;                          // restarts of the current track after a stall
    private boolean noFx;                        // effects off for this track (after a stall)
    private boolean focusPaused;                 // paused by a short focus loss: resume when focus returns
    private final java.util.IdentityHashMap<MediaPlayer, Object[]> fx = new java.util.IdentityHashMap<>();   // keeps EQ / enhancer alive

    private double gainOf(JSONObject it) {
        if (it == null) return 0;
        Double g = gains.get(it.optString("id"));
        return g != null ? g : it.optDouble("gain", 0);
    }

    private static float lin(double db) { return (float) Math.min(1, Math.pow(10, db / 20)); }

    /** EQ and quiet-track boost hang off the player's audio session */
    private void attachFx(MediaPlayer p, JSONObject it) {
        detachFx(p);
        Object[] o = new Object[4];
        if (noFx && p == mp) { fx.put(p, o); return; }
        // 4.0.1: only the phone's standard equalizer. DynamicsProcessing took over the volume on some phones (volume keys stopped working)
        tone(p, o);
        try {
            double g = gainOf(it);
            if (g > 0.5 && Build.VERSION.SDK_INT >= 19) {
                android.media.audiofx.LoudnessEnhancer le = new android.media.audiofx.LoudnessEnhancer(p.getAudioSessionId());
                le.setTargetGain((int) Math.round(Math.min(12, g) * 100));
                le.setEnabled(true);
                o[1] = le;
            }
        } catch (Exception ignored) { }
        fx.put(p, o);
    }

    private boolean toneOn() {
        if (bassStr > 0 || virtStr > 0) return true;   // the EQ then only makes room so the boost does not distort
        if (eqDb != null) for (double g : eqDb) if (Math.abs(g) > 0.05) return true;
        return false;
    }

    /** 4.4.1: EQ, bass and surround are made once per player and only retuned when a setting moves
       (rebuilding them on every slider step made the sound stutter) */
    private void tone(MediaPlayer p, Object[] o) {
        int ses = p.getAudioSessionId();
        try {
            if (toneOn()) { if (o[0] == null) o[0] = sysEq(ses); else eqLevels((android.media.audiofx.Equalizer) o[0]); }
            else if (o[0] != null) { ((android.media.audiofx.AudioEffect) o[0]).release(); o[0] = null; }
        } catch (Exception ignored) { }
        try {
            if (bassStr > 0) {
                if (o[2] == null) { android.media.audiofx.BassBoost bb = new android.media.audiofx.BassBoost(1000, ses); bb.setEnabled(true); o[2] = bb; }
                ((android.media.audiofx.BassBoost) o[2]).setStrength((short) Math.min(800, bassStr));
            } else if (o[2] != null) { ((android.media.audiofx.AudioEffect) o[2]).release(); o[2] = null; }
        } catch (Exception ignored) { }
        try {
            if (virtStr > 0) {
                if (o[3] == null) { android.media.audiofx.Virtualizer v = new android.media.audiofx.Virtualizer(1000, ses); v.setEnabled(true); o[3] = v; }
                ((android.media.audiofx.Virtualizer) o[3]).setStrength((short) Math.min(800, virtStr));
            } else if (o[3] != null) { ((android.media.audiofx.AudioEffect) o[3]).release(); o[3] = null; }
        } catch (Exception ignored) { }
    }

    /** room for the boosts: the whole curve goes down by most of the biggest boost (and a bit for bass / surround) so nothing clips */
    private double headroom() {
        double m = 0;
        if (eqDb != null) for (double g : eqDb) m = Math.max(m, g);
        return m * 0.85 + Math.min(800, bassStr) / 1000.0 * 5 + Math.min(800, virtStr) / 1000.0 * 2;
    }

    private void eqLevels(android.media.audiofx.Equalizer e) {
        short n = e.getNumberOfBands();
        short[] range = e.getBandLevelRange();
        double h = headroom();
        for (short b = 0; b < n; b++) e.setBandLevel(b, (short) Math.max(range[0], Math.min(range[1], Math.round((gainAt(e.getCenterFreq(b) / 1000.0) - h) * 100))));
    }

    /** gain at frequency f (Hz) from the drawn bands, straight lines on a log scale */
    private double gainAt(double f) {
        if (eqDb == null) return 0;
        int n = eqFc.length;
        if (f <= eqFc[0]) return eqDb[0];
        if (f >= eqFc[n - 1]) return eqDb[n - 1];
        int k = 0; while (k < n - 2 && f > eqFc[k + 1]) k++;
        double t = (Math.log(f) - Math.log(eqFc[k])) / (Math.log(eqFc[k + 1]) - Math.log(eqFc[k]));
        return eqDb[k] + (eqDb[k + 1] - eqDb[k]) * t;
    }

    private Object sysEq(int session) {
        try {
            android.media.audiofx.Equalizer e = new android.media.audiofx.Equalizer(1000, session);   // high priority: the phone's sound packs must not take it over
            eqLevels(e);
            e.setEnabled(true);
            return e;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void detachFx(MediaPlayer p) {
        Object[] o = fx.remove(p);
        if (o == null) return;
        for (Object x : o) if (x instanceof android.media.audiofx.AudioEffect) try { ((android.media.audiofx.AudioEffect) x).release(); } catch (Exception ignored) { }
    }

    private void setVol(MediaPlayer p, JSONObject it, float f) {
        if (p == null) return;
        float v = lin(Math.min(0, gainOf(it))) * f;
        try { p.setVolume(v, v); } catch (Exception ignored) { }
    }

    private int nextPos() {
        if (repeat == 2 || order.isEmpty()) return -1;
        if (pos + 1 < order.size()) return pos + 1;
        return repeat == 1 ? 0 : -1;
    }

    private MediaPlayer newPlayer(JSONObject it) throws Exception {
        MediaPlayer p = new MediaPlayer();
        p.setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        p.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK);
        p.setOnPreparedListener(this);
        p.setOnCompletionListener(this);
        p.setOnErrorListener(this);
        JSONObject src = it.optJSONObject("src");
        Uri u = src != null ? MediaLib.srcUri(src) : null;
        File f = src != null ? MediaLib.srcFile(src) : null;
        if (u != null) p.setDataSource(app, u);
        else if (f != null) p.setDataSource(f.getAbsolutePath());
        else throw new IllegalStateException("no source");
        return p;
    }

    private MediaPlayer preparing;     // the next track while it is being prepared
    private int preparingPos = -1;

    /** prepares the following track in the background (3.4.6: it was prepared on the main thread, and a slow file froze
     *  the whole app while repeat / A-B / skipping were being switched) */
    private void prepareNext() {
        if (nmp != null || preparing != null || mp == null || sleepEnd) return;
        int np = nextPos();
        if (np < 0 || np == pos) return;
        final JSONObject it = queue.optJSONObject(order.get(np));
        if (it == null) return;
        try {
            final MediaPlayer p = newPlayer(it);
            p.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer x) {
                    synchronized (Player.this) {
                        if (x != preparing) { try { x.release(); } catch (Exception ignored) { } return; }
                        preparing = null;
                        if (mp == null) { try { x.release(); } catch (Exception ignored) { } return; }
                        attachFx(x, it);
                        setVol(x, it, xfadeMs > 0 ? 0 : 1);
                        nmp = x; npos = preparingPos;
                        if (xfadeMs == 0 && gapless) { try { mp.setNextMediaPlayer(x); } catch (Exception e) { dropNext(); } }
                    }
                }
            });
            p.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer x, int what, int extra) {
                    synchronized (Player.this) {
                        if (x == preparing) preparing = null;
                        if (x == nmp) { nmp = null; npos = -1; try { if (mp != null) mp.setNextMediaPlayer(null); } catch (Exception ignored) { } }
                        if (x != mp) { try { x.release(); } catch (Exception ignored) { } return true; }
                    }
                    return Player.this.onError(x, what, extra);
                }
            });
            preparing = p; preparingPos = np;
            p.prepareAsync();
        } catch (Exception e) {
            preparing = null; nmp = null; npos = -1;
        }
    }

    private void dropNext() {
        fading = false;
        if (preparing != null) { try { preparing.release(); } catch (Exception ignored) { } preparing = null; }
        if (mp != null) try { mp.setNextMediaPlayer(null); } catch (Exception ignored) { }
        if (nmp != null) { detachFx(nmp); try { nmp.release(); } catch (Exception ignored) { } }
        nmp = null; npos = -1;
    }

    /** the next player has taken over (gapless hand-off or end of a crossfade) */
    private void promoteNext() {
        MediaPlayer old = mp;
        mp = nmp; pos = npos; nmp = null; npos = -1; fading = false;
        mp.setOnPreparedListener(this);
        mp.setOnErrorListener(this);
        prepared = true; playing = true; wantPlay = true;
        loopA = loopB = -1;
        setVol(mp, current(), 1);
        applyRate();
        if (old != null) { detachFx(old); try { old.setNextMediaPlayer(null); } catch (Exception ignored) { } try { old.release(); } catch (Exception ignored) { } }
        JSONObject it = current();
        if (it != null) { updateMeta(it); loadArt(it); }
        emit();
        if (xfadeMs == 0 && gapless) prepareNext();
        startPoll();
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            synchronized (Player.this) {
                if (sleepAt > 0 && System.currentTimeMillis() >= sleepAt) { sleepAt = 0; pause(); return; }
                if (!playing || mp == null || !prepared) return;
                long cur, dur;
                try { cur = mp.getCurrentPosition(); dur = mp.getDuration(); } catch (Exception e) { return; }
                // watchdog: "playing" but the position stands still (seen on the phone: the wave trembled, no sound).
                // Start the track again at that spot without effects; if that does not help, report it.
                long up = SystemClock.uptimeMillis();
                if (cur != lastCur || fading || up < seekWait) { lastCur = cur; stillSince = up; }
                else if (up - stillSince > 2500 && (dur <= 0 || cur < dur - 600)) {
                    lastCur = -1;
                    JSONObject it0 = current();
                    if (stalls++ < 1) {
                        MainActivity.saveNote(app, "player stalled at " + cur + "/" + dur + " ms, " + describe(it0) + " — restarted without effects");
                        noFx = true; load(cur, true); return;
                    }
                    MainActivity.saveNote(app, "player stalled again at " + cur + "/" + dur + " ms, " + describe(it0));
                    fallback(it0, cur); return;
                }
                // a seek takes a moment and the position still reads the old place meanwhile: seeking again then made the
                // same seconds repeat and stutter (A-B loop, skipping silence)
                long now = SystemClock.uptimeMillis();
                if (now >= seekWait) {
                    if (loopB > loopA && loopA >= 0 && cur >= loopB) { seekInternal(loopA); seekWait = now + 400; main.postDelayed(this, 50); return; }
                    JSONObject it = current();
                    int hit = -1;
                    if (it != null && skipsId.equals(it.optString("id"))) {
                        for (int i = 0; i + 1 < skips.length; i += 2) if (cur >= skips[i] && cur < skips[i + 1] - 100) { hit = i; break; }
                    }
                    if (hit >= 0 && hit != lastSkip) { seekInternal(skips[hit + 1]); seekWait = now + 400; }
                    lastSkip = hit;
                }
                JSONObject it = current();
                if (xfadeMs > 0 && !sleepEnd && dur > 0) {
                    long left = dur - cur;
                    if (nmp == null && left <= xfadeMs + 1500) prepareNext();
                    if (nmp != null && !fading && left <= xfadeMs) {
                        fading = true; fadeStart = System.currentTimeMillis(); fadeLen = Math.max(200, left);
                        try { nmp.start(); } catch (Exception e) { dropNext(); }
                    }
                    if (fading && nmp != null) {
                        float f = Math.min(1, (System.currentTimeMillis() - fadeStart) / (float) fadeLen);
                        setVol(mp, it, 1 - f);
                        setVol(nmp, queue.optJSONObject(order.get(npos)), f);
                        if (f >= 1) { promoteNext(); return; }
                    }
                }
                main.postDelayed(this, fading ? 30 : 60);
            }
        }
    };

    private void startPoll() { main.removeCallbacks(poll); main.postDelayed(poll, 60); }

    synchronized void setGain(String id, double db) {
        gains.put(id, db);
        JSONObject it = current();
        if (it != null && id.equals(it.optString("id")) && mp != null) { setVol(mp, it, fading ? 0 : 1); attachFx(mp, it); }
    }

    synchronized void setEq(String json) {
        eqDb = null; bassStr = virtStr = 0;
        try {
            if (json != null && !json.isEmpty()) {
                if (json.trim().startsWith("[")) { JSONArray a = new JSONArray(json); eqDb = new double[5]; for (int i = 0; i < 5; i++) eqDb[i] = a.optDouble(i, 0); eqFc = new double[] { 100, 300, 1000, 3000, 8000 }; }
                else {
                    JSONObject o = new JSONObject(json);
                    JSONArray g = o.optJSONArray("g"), f = o.optJSONArray("fc");
                    if (g != null && f != null && g.length() == f.length() && g.length() > 1) {
                        boolean any = false;
                        double[] d = new double[g.length()], c = new double[g.length()];
                        for (int i = 0; i < d.length; i++) { d[i] = Math.max(-15, Math.min(15, g.getDouble(i))); c[i] = f.getDouble(i); if (d[i] != 0) any = true; }
                        if (any) { eqDb = d; eqFc = c; }
                    }
                    bassStr = Math.max(0, Math.min(1000, o.optInt("bass", 0)));
                    virtStr = Math.max(0, Math.min(1000, o.optInt("virt", 0)));
                }
            }
        } catch (Exception ignored) { eqDb = null; }
        retune(mp, current());
        retune(nmp, npos >= 0 ? queue.optJSONObject(order.get(npos)) : null);
    }

    private void retune(MediaPlayer p, JSONObject it) {
        if (p == null) return;
        Object[] o = fx.get(p);
        if (o == null || (noFx && p == mp)) { attachFx(p, it); return; }
        tone(p, o);
    }

    synchronized void setXfade(int ms, boolean gap) {
        xfadeMs = Math.max(0, Math.min(12000, ms)); gapless = gap;
        dropNext();
        if (playing && xfadeMs == 0 && gapless) prepareNext();
    }

    synchronized void setLoop(long a, long b) { loopA = a; loopB = b; emit(); }

    synchronized void setSkips(String id, String json) {
        skipsId = id == null ? "" : id;
        try { JSONArray a = new JSONArray(json); skips = new long[a.length()]; for (int i = 0; i < skips.length; i++) skips[i] = a.optLong(i); } catch (Exception e) { skips = new long[0]; }
    }

    synchronized void setSleep(long ms, boolean end) {
        sleepAt = ms > 0 ? System.currentTimeMillis() + ms : 0;
        sleepEnd = end;
        if (end) dropNext();   // the next track must not start by itself (gapless hand-off) when this one should be the last
        else if (playing && xfadeMs == 0 && gapless) prepareNext();
        if (playing) startPoll(); else if (sleepAt > 0) main.postDelayed(poll, ms + 50);
        emit();
    }

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) pause();
        }
    };

    private final AudioManager.OnAudioFocusChangeListener focus = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int change) {
            synchronized (Player.this) {
                if (change == AudioManager.AUDIOFOCUS_LOSS) { focusPaused = false; pause(); abandonFocus(); }
                else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) { boolean was = playing; pause(); focusPaused = was; }
                else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) { if (mp != null) try { float v = lin(Math.min(0, gainOf(current()))) * 0.25f; mp.setVolume(v, v); } catch (Exception ignored) { } }
                else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                    if (mp != null) setVol(mp, current(), 1);
                    if (focusPaused) { focusPaused = false; play(); }
                }
            }
        }
    };

    private String loadedId = "";

    /** short, file-name-free description for the note */
    private static String describe(JSONObject it) {
        if (it == null) return "-";
        JSONObject src = it.optJSONObject("src");
        String u = src == null ? "" : src.optString("uri", src.has("path") ? "file:" : "");
        int c = u.indexOf(':');
        String name = it.optString("name", it.optString("title", ""));
        int dot = name.lastIndexOf('.');
        return (c > 0 ? u.substring(0, c) : "?") + ", " + it.optString("mime", "") + (dot >= 0 ? " " + name.substring(dot) : "")
            + ", " + it.optLong("dur", 0) + " ms listed, fx " + (eqOn() ? "eq " : "") + "gain " + it.optDouble("gain", 0);
    }

    private static boolean eqOn() { return I.eqDb != null; }

    private Player() { }

    synchronized void init(Context c, Listener l) {
        app = c.getApplicationContext();
        listener = l;
        if (session == null) {
            session = new MediaSession(app, "SesDefteri");
            session.setCallback(new MediaSession.Callback() {
                @Override public void onPlay() { play(); }
                @Override public void onPause() { pause(); }
                @Override public void onSkipToNext() { next(); }
                @Override public void onSkipToPrevious() { prev(); }
                @Override public void onSeekTo(long ms) { seek(ms); }
                @Override public void onStop() { stop(); }
            });
            session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        }
    }

    MediaSession.Token token() {
        return session != null ? session.getSessionToken() : null;
    }

    /* ---------- queue ---------- */

    synchronized void setQueue(String json, int index, long startMs, boolean autoplay) {
        try { queue = new JSONArray(json); } catch (Exception e) { queue = new JSONArray(); }
        buildOrder(index);
        pos = shuffle ? 0 : Math.max(0, Math.min(index, queue.length() - 1));
        load(startMs, autoplay);
    }

    /** "add to queue": after the current song (next) or at the end; starts playing when nothing is loaded */
    synchronized void enqueue(String json, boolean next) {
        JSONObject it;
        try { it = new JSONObject(json); } catch (Exception e) { return; }
        if (current() == null) { setQueue("[" + it + "]", 0, 0, true); return; }
        queue.put(it);
        int i = queue.length() - 1;
        if (next) { order.add(pos + 1, i); dropNext(); if (playing && xfadeMs == 0 && gapless) prepareNext(); }
        else order.add(i);
        emit();
    }

    private void buildOrder(int first) {
        order = new ArrayList<>();
        for (int i = 0; i < queue.length(); i++) order.add(i);
        if (shuffle && queue.length() > 1) {
            order.remove(Integer.valueOf(first));
            Collections.shuffle(order, new Random());
            order.add(0, first);
        }
    }

    private JSONObject current() {
        if (pos < 0 || pos >= order.size()) return null;
        return queue.optJSONObject(order.get(pos));
    }

    private void load(long startMs, boolean autoplay) {
        releasePlayer();
        JSONObject it = current();
        String key = it != null ? it.optString("id") : "";
        if (!key.equals(loadedId)) { loadedId = key; stalls = 0; noFx = false; }
        lastCur = -1;
        if (it == null) { emit(); return; }
        prepared = false;
        wantPlay = autoplay;
        pendingSeek = startMs > 0 ? startMs : -1;
        error = "";
        art = null;
        loopA = loopB = -1;
        try {
            mp = newPlayer(it);
            mp.prepareAsync();
        } catch (Exception e) {
            error = "load";
            releasePlayer();
        }
        updateMeta(it);
        loadArt(it);
        emit();
    }

    @Override
    public void onPrepared(MediaPlayer p) {
        synchronized (this) {
            if (p != mp) return;
            prepared = true;
            attachFx(mp, current());
            setVol(mp, current(), 1);
            if (pendingSeek > 0) seekInternal(pendingSeek);
            pendingSeek = -1;
            if (wantPlay) play();
            else emit();
            JSONObject it = current();
            if (it != null) updateMeta(it);
        }
    }

    @Override
    public void onCompletion(MediaPlayer p) {
        synchronized (this) {
            if (p != mp) return;
            if (sleepEnd) { sleepEnd = false; sleepAt = 0; dropNext(); playing = false; wantPlay = false; abandonFocus(); PlayerService.update(app, false); emit("ended"); return; }
            if (nmp != null && (fading || (xfadeMs == 0 && gapless))) { if (xfadeMs > 0 && !fading) { try { nmp.start(); } catch (Exception ignored) { } } promoteNext(); return; }
            if (repeat == 2) { seekInternal(0); startInternal(); return; }
            if (pos + 1 < order.size()) { pos++; load(0, true); return; }
            if (repeat == 1 && order.size() > 0) { pos = 0; load(0, true); return; }
            playing = false;
            wantPlay = false;
            abandonFocus();
            PlayerService.update(app, false);
            emit("ended");
        }
    }

    @Override
    public boolean onError(MediaPlayer p, int what, int extra) {
        synchronized (this) {
            if (p != mp) return true;
            JSONObject it = current();
            MainActivity.saveNote(app, "player error " + what + "/" + extra + ", " + describe(it));
            long at = 0;
            try { at = prepared ? mp.getCurrentPosition() : Math.max(0, pendingSeek); } catch (Exception ignored) { }
            fallback(it, at);
        }
        return true;
    }

    /**
     * The phone's player could not play this file (error, or it stood still twice): decode it ourselves into a plain
     * 16-bit stereo WAV in the cache and play that instead. Gives up with an error only when even that fails.
     */
    private void fallback(final JSONObject it, final long at) {
        releasePlayer();
        if (it == null || it.optBoolean("pcm") || app == null) {
            error = "play"; playing = false; wantPlay = false;
            PlayerService.update(app, false); emit("error");
            return;
        }
        playing = false; wantPlay = true; error = "";
        emit();
        final JSONObject src = it.optJSONObject("src");
        new Thread(new Runnable() {
            @Override public void run() {
                File out = new File(app.getCacheDir(), "play-" + Math.abs(it.optString("id").hashCode()) + ".wav");
                boolean ok = false;
                try { ok = toWav(src, out); } catch (Throwable e) { MainActivity.saveNote(app, "player fallback failed: " + e); }
                final boolean done = ok;
                final File f = out;
                main.post(new Runnable() {
                    @Override public void run() {
                        synchronized (Player.this) {
                            if (current() != it) return;
                            if (!done) { error = "play"; playing = false; wantPlay = false; PlayerService.update(app, false); emit("error"); return; }
                            try {
                                JSONObject s2 = new JSONObject(); s2.put("path", f.getAbsolutePath());
                                it.put("src", s2); it.put("pcm", true);
                            } catch (Exception ignored) { }
                            stalls = 0; noFx = false;
                            load(at, true);
                        }
                    }
                });
            }
        }, "sd-play-wav").start();
    }

    /** whole file → 16-bit stereo WAV at its own rate */
    private boolean toWav(JSONObject src, File out) throws Exception {
        File[] old = app.getCacheDir().listFiles();
        if (old != null) for (File o : old) if (o.getName().startsWith("play-") && o.getName().endsWith(".wav")) o.delete();
        final java.io.RandomAccessFile raf = new java.io.RandomAccessFile(out, "rw");
        final long[] frames = { 0 };
        final int[] rateOut = { 0 };
        try {
            raf.setLength(0);
            raf.write(new byte[44]);
            AudioTools.decode(app, src, new AudioTools.PcmSink() {
                byte[] b = new byte[0];
                @Override public void pcm(short[] x, int n, int ch, int rate) throws java.io.IOException {
                    if (rateOut[0] == 0) rateOut[0] = rate;
                    int fr = n / ch;
                    if (b.length < fr * 4) b = new byte[fr * 4];
                    for (int i = 0; i < fr; i++) {
                        short l = x[i * ch], r = ch > 1 ? x[i * ch + 1] : l;
                        b[i * 4] = (byte) l; b[i * 4 + 1] = (byte) (l >> 8); b[i * 4 + 2] = (byte) r; b[i * 4 + 3] = (byte) (r >> 8);
                    }
                    raf.write(b, 0, fr * 4);
                    frames[0] += fr;
                }
            }, null, null);
            if (frames[0] == 0) return false;
            raf.seek(0); raf.write(Wav.header(2, rateOut[0], false, frames[0]));
        } finally { raf.close(); }
        return true;
    }

    /* ---------- controls ---------- */

    synchronized void play() {
        if (mp == null) {
            if (current() != null) load(0, true);
            return;
        }
        if (!prepared) { wantPlay = true; return; }
        if (!requestFocus()) return;
        startInternal();
    }

    private void startInternal() {
        try {
            mp.start();
            applyRate();
            playing = true;
            wantPlay = true;
            startPoll();
            if (xfadeMs == 0 && gapless && nmp == null) prepareNext();
            registerNoisy(true);
            session.setActive(true);
            PlayerService.update(app, true);
        } catch (Exception e) {
            playing = false;
        }
        emit();
    }

    synchronized void pause() {
        wantPlay = false;
        focusPaused = false;
        if (fading) dropNext();
        if (mp != null && prepared && playing) {
            try { mp.pause(); } catch (Exception ignored) { }
        }
        playing = false;
        registerNoisy(false);
        PlayerService.update(app, false);
        emit();
    }

    synchronized void toggle() {
        if (playing) pause(); else play();
    }

    synchronized void seek(long ms) {
        if (mp == null) return;
        if (!prepared) { pendingSeek = ms; return; }
        if (fading) { dropNext(); setVol(mp, current(), 1); }
        seekInternal(ms);
        emit("seeked");
    }

    private void seekInternal(long ms) {
        try {
            long d = mp.getDuration();
            long t = Math.max(0, d > 0 ? Math.min(ms, d - 50) : ms);
            if (Build.VERSION.SDK_INT >= 26) mp.seekTo(t, MediaPlayer.SEEK_CLOSEST);
            else mp.seekTo((int) t);
        } catch (Exception ignored) { }
    }

    synchronized void next() {
        if (order.isEmpty()) return;
        if (pos + 1 < order.size()) pos++;
        else if (repeat == 1) pos = 0;
        else return;
        load(0, playing || wantPlay);
    }

    synchronized void prev() {
        if (order.isEmpty()) return;
        if (positionMs() > 3000 || pos == 0) { seek(0); return; }
        pos--;
        load(0, playing || wantPlay);
    }

    synchronized void setRate(float r) {
        rate = Math.max(0.5f, Math.min(r, 3f));
        if (playing) applyRate();
        emit();
    }

    synchronized void setPitch(float semis) {
        pitch = (float) Math.pow(2, Math.max(-12, Math.min(12, semis)) / 12.0);
        if (playing) applyRate();
    }

    private void applyRate() {
        if (mp == null || !prepared || Build.VERSION.SDK_INT < 23) return;
        try {
            PlaybackParams p = mp.getPlaybackParams();
            p.setSpeed(rate);
            p.setPitch(pitch);
            mp.setPlaybackParams(p);
        } catch (Exception ignored) { }
    }

    synchronized void setShuffle(boolean on) {
        if (on == shuffle) return;
        shuffle = on;
        dropNext();
        int curIdx = (pos >= 0 && pos < order.size()) ? order.get(pos) : 0;
        buildOrder(curIdx);
        pos = order.indexOf(curIdx);
        if (pos < 0) pos = 0;
        emit();
    }

    synchronized void setRepeat(int r) {
        repeat = Math.max(0, Math.min(2, r));
        dropNext();
        if (playing && xfadeMs == 0 && gapless) prepareNext();
        emit();
    }

    synchronized void stop() {
        wantPlay = false;
        playing = false;
        releasePlayer();
        queue = new JSONArray();
        order = new ArrayList<>();
        pos = -1;
        abandonFocus();
        registerNoisy(false);
        if (session != null) session.setActive(false);
        PlayerService.stop(app);
        emit();
    }

    /** True while this item is loaded; used before editing/deleting a file. */
    synchronized boolean isCurrent(String id) {
        JSONObject it = current();
        return it != null && id.equals(it.optString("id"));
    }

    synchronized void releaseIfCurrent(String id) {
        if (isCurrent(id)) stop();
    }

    /** Stops playback when the given file (src JSON) is the one loaded, so it can be replaced or deleted. */
    synchronized void releaseIfCurrentSrc(String srcJson) {
        JSONObject it = current();
        if (it == null) return;
        try {
            JSONObject want = new JSONObject(srcJson);
            JSONObject have = it.optJSONObject("src");
            if (have == null) return;
            String a = want.optString("uri", "") + "|" + want.optString("path", "");
            String b = have.optString("uri", "") + "|" + have.optString("path", "");
            if (a.equals(b)) stop();
        } catch (Exception ignored) { }
    }

    private void releasePlayer() {
        dropNext();
        if (mp != null) {
            detachFx(mp);
            try { mp.reset(); } catch (Exception ignored) { }
            try { mp.release(); } catch (Exception ignored) { }
        }
        mp = null;
        prepared = false;
    }

    /* ---------- state ---------- */

    synchronized long positionMs() {
        try { return mp != null && prepared ? mp.getCurrentPosition() : Math.max(0, pendingSeek); } catch (Exception e) { return 0; }
    }

    synchronized long durationMs() {
        try {
            if (mp != null && prepared) { int d = mp.getDuration(); if (d > 0) return d; }
        } catch (Exception ignored) { }
        JSONObject it = current();
        return it != null ? it.optLong("dur", 0) : 0;
    }

    synchronized String state() {
        JSONObject o = new JSONObject();
        try {
            JSONObject it = current();
            o.put("id", it != null ? it.optString("id") : "");
            o.put("index", pos >= 0 && pos < order.size() ? order.get(pos) : -1);
            o.put("count", queue.length());
            o.put("playing", playing);
            o.put("want", wantPlay);
            o.put("prepared", prepared);
            o.put("pos", positionMs());
            o.put("dur", durationMs());
            o.put("rate", rate);
            o.put("shuffle", shuffle);
            o.put("repeat", repeat);
            o.put("error", error);
            o.put("loopA", loopA);
            o.put("loopB", loopB);
            o.put("sleepAt", sleepAt);
            o.put("sleepEnd", sleepEnd);
            o.put("t", System.currentTimeMillis());
        } catch (Exception ignored) { }
        return o.toString();
    }

    private void emit() { emit("state"); }

    private void emit(final String kind) {
        updateSession();
        final Listener l = listener;
        if (l == null) return;
        final String s = state();
        main.post(new Runnable() {
            @Override public void run() { l.onPlayerEvent("{\"kind\":\"" + kind + "\",\"state\":" + s + "}"); }
        });
    }

    /* ---------- session / notification ---------- */

    synchronized String title() {
        JSONObject it = current();
        return it != null ? it.optString("title", "Ses Defteri") : "Ses Defteri";
    }

    synchronized String subtitle() {
        JSONObject it = current();
        if (it == null) return "";
        String a = it.optString("artist", "");
        return a.isEmpty() ? it.optString("album", "") : a;
    }

    synchronized boolean isPlaying() { return playing; }

    synchronized boolean hasQueue() { return order.size() > 1; }

    synchronized Bitmap art() { return art; }

    private void updateMeta(JSONObject it) {
        if (session == null) return;
        MediaMetadata.Builder b = new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, it.optString("title"))
            .putString(MediaMetadata.METADATA_KEY_ARTIST, it.optString("artist"))
            .putString(MediaMetadata.METADATA_KEY_ALBUM, it.optString("album"))
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs());
        if (art != null) b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        session.setMetadata(b.build());
    }

    private void loadArt(final JSONObject it) {
        final long albumId = it.optLong("albumId", -1);
        if (albumId < 0 || app == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bm = AudioTools.albumArt(app, albumId, 512);
                if (bm == null) return;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (Player.this) {
                            JSONObject cur = current();
                            if (cur == null || cur.optLong("albumId", -2) != albumId) return;
                            art = bm;
                            updateMeta(cur);
                            PlayerService.update(app, playing);
                        }
                    }
                });
            }
        }).start();
    }

    private void updateSession() {
        if (session == null) return;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
            | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP
            | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(actions)
            .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, positionMs(), playing ? rate : 0f)
            .build());
    }

    private boolean requestFocus() {
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return true;
        int r;
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusReq == null) {
                focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(focus).build();
            }
            r = am.requestAudioFocus(focusReq);
        } else {
            r = am.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        return r != AudioManager.AUDIOFOCUS_REQUEST_FAILED;
    }

    /* 3.4.6: the editor's and Clean's previews (Timeline) also give way when another app starts playing */
    private AudioFocusRequest tlReq;
    private final AudioManager.OnAudioFocusChangeListener tlFocus = new AudioManager.OnAudioFocusChangeListener() {
        @Override public void onAudioFocusChange(int change) {
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) Timeline.stop();
        }
    };

    void timelineFocus() {
        if (app == null) return;
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                if (tlReq == null) tlReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(tlFocus).build();
                am.requestAudioFocus(tlReq);
            } else am.requestAudioFocus(tlFocus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        } catch (Exception ignored) { }
    }

    private void abandonFocus() {
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= 26) { if (focusReq != null) am.abandonAudioFocusRequest(focusReq); }
        else am.abandonAudioFocus(focus);
    }

    private void registerNoisy(boolean on) {
        try {
            if (on && !noisyRegistered) {
                IntentFilter f = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
                if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(noisy, f, Context.RECEIVER_EXPORTED);
                else app.registerReceiver(noisy, f);
                noisyRegistered = true;
            } else if (!on && noisyRegistered) {
                app.unregisterReceiver(noisy);
                noisyRegistered = false;
            }
        } catch (Exception ignored) { }
    }
}
