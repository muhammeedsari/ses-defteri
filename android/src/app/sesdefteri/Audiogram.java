package app.sesdefteri;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Surface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 4.8: a captioned video from the project mix ("audiogram"): a coloured background, the spoken words as big
 * captions, moving level bars and a progress line. Frames are drawn with Canvas, uploaded to a GL texture and
 * fed to the H.264 encoder through its input surface with exact timestamps; then the AAC mix is muxed in.
 */
final class Audiogram {
    private Audiogram() { }

    static final int FPS = 15;

    /** a: {path: mix wav, segs: [[s,e,text]…], w, h, title, color, name} */
    static JSONObject run(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        File wav = new File(a.getString("path"));
        Workspace.Info inf = Workspace.info(wav);
        double dur = inf.frames / (double) inf.rate;
        int W = a.optInt("w", 720), H = a.optInt("h", 1280), N = (int) Math.ceil(dur * FPS);
        File cache = c.getCacheDir();
        File vid = new File(cache, "ag-" + System.nanoTime() + ".mp4"), m4a = new File(cache, "ag-" + System.nanoTime() + ".m4a"), out = new File(cache, "agout-" + System.nanoTime() + ".mp4");
        try {
            float[] lv = levels(wav, inf, N);
            p.on(0.05);
            encodeAudio(wav, inf, m4a, cancel);
            p.on(0.12);
            encodeVideo(a, W, H, N, dur, lv, vid, p, cancel);
            mux(vid, m4a, out);
            p.on(1);
            return VideoMux.publish(c, out, a.optString("name", "Video") + ".mp4");
        } finally {
            vid.delete(); m4a.delete(); out.delete();
        }
    }

    /** loudness per video frame, 0..1 */
    private static float[] levels(File wav, Workspace.Info inf, int n) throws Exception {
        float[] lv = new float[n];
        int per = Math.max(1, inf.rate / FPS), ch = inf.ch;
        byte[] b = new byte[per * ch * 2];
        RandomAccessFile r = new RandomAccessFile(wav, "r");
        try {
            r.seek(44);
            float mx = 1e-4f;
            for (int i = 0; i < n; i++) {
                int got = r.read(b);
                if (got <= 0) break;
                double e = 0; int cnt = got / 2;
                for (int k = 0; k < cnt; k++) { double v = (short) ((b[2 * k] & 0xff) | (b[2 * k + 1] << 8)) / 32768.0; e += v * v; }
                lv[i] = (float) Math.sqrt(e / Math.max(1, cnt));
                mx = Math.max(mx, lv[i]);
            }
            for (int i = 0; i < n; i++) lv[i] = (float) Math.min(1, Math.sqrt(lv[i] / mx));
        } finally { r.close(); }
        return lv;
    }

    private static void encodeAudio(File wav, Workspace.Info inf, File m4a, AtomicBoolean cancel) throws Exception {
        AudioTools.Encoder enc = new AudioTools.Encoder(m4a);
        enc.start(inf.rate, inf.ch);
        RandomAccessFile raf = new RandomAccessFile(wav, "r");
        try {
            raf.seek(44);
            final int BL = 4096;
            byte[] b = new byte[BL * inf.ch * 2];
            short[] s = new short[BL * inf.ch];
            for (long done = 0; done < inf.frames; ) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                int n = (int) Math.min(BL, inf.frames - done), cnt = n * inf.ch;
                raf.readFully(b, 0, cnt * 2);
                for (int i = 0; i < cnt; i++) s[i] = (short) ((b[2 * i] & 0xff) | (b[2 * i + 1] << 8));
                enc.feed(s, cnt);
                done += n;
            }
            enc.finish();
        } catch (Exception e) { enc.abort(); throw e; } finally { raf.close(); }
    }

    private static final String VS = "attribute vec4 p; attribute vec2 t; varying vec2 v; void main(){ gl_Position = p; v = t; }";
    private static final String FS = "precision mediump float; varying vec2 v; uniform sampler2D s; void main(){ gl_FragColor = texture2D(s, v); }";

    private static int shader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        return s;
    }

    private static void encodeVideo(JSONObject a, int W, int H, int N, double dur, float[] lv, File vid, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        MediaFormat f = MediaFormat.createVideoFormat("video/avc", W, H);
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        f.setInteger(MediaFormat.KEY_BIT_RATE, W * H * 3);
        f.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        MediaCodec enc = MediaCodec.createEncoderByType("video/avc");
        enc.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface in = enc.createInputSurface();
        enc.start();
        MediaMuxer mux = new MediaMuxer(vid.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        EGLDisplay dp = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        EGLContext ctx = null; EGLSurface sf = null;
        int[] trk = { -1 };
        try {
            int[] ver = new int[2];
            EGL14.eglInitialize(dp, ver, 0, ver, 1);
            EGLConfig[] cfg = new EGLConfig[1]; int[] num = new int[1];
            EGL14.eglChooseConfig(dp, new int[] { EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, 0x3142 /* EGL_RECORDABLE_ANDROID */, 1, EGL14.EGL_NONE }, 0, cfg, 0, 1, num, 0);
            ctx = EGL14.eglCreateContext(dp, cfg[0], EGL14.EGL_NO_CONTEXT, new int[] { EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE }, 0);
            sf = EGL14.eglCreateWindowSurface(dp, cfg[0], in, new int[] { EGL14.EGL_NONE }, 0);
            EGL14.eglMakeCurrent(dp, sf, sf, ctx);
            int prog = GLES20.glCreateProgram();
            GLES20.glAttachShader(prog, shader(GLES20.GL_VERTEX_SHADER, VS));
            GLES20.glAttachShader(prog, shader(GLES20.GL_FRAGMENT_SHADER, FS));
            GLES20.glLinkProgram(prog);
            GLES20.glUseProgram(prog);
            FloatBuffer q = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            q.put(new float[] { -1, -1, 0, 1, 1, -1, 1, 1, -1, 1, 0, 0, 1, 1, 1, 0 }).position(0);
            int ap = GLES20.glGetAttribLocation(prog, "p"), at = GLES20.glGetAttribLocation(prog, "t");
            q.position(0); GLES20.glVertexAttribPointer(ap, 2, GLES20.GL_FLOAT, false, 16, q); GLES20.glEnableVertexAttribArray(ap);
            q.position(2); GLES20.glVertexAttribPointer(at, 2, GLES20.GL_FLOAT, false, 16, q); GLES20.glEnableVertexAttribArray(at);
            int[] tex = new int[1];
            GLES20.glGenTextures(1, tex, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glViewport(0, 0, W, H);

            Frame fr = new Frame(a, W, H, dur, lv);
            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
            for (int i = 0; i < N; i++) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                fr.draw(i);
                if (i == 0) GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, fr.bmp, 0); else GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, fr.bmp);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                EGLExt.eglPresentationTimeANDROID(dp, sf, i * 1000000000L / FPS);
                EGL14.eglSwapBuffers(dp, sf);
                drain(enc, mux, trk, bi, false);
                if (i % 15 == 0) p.on(0.12 + 0.83 * i / N);
            }
            enc.signalEndOfInputStream();
            drain(enc, mux, trk, bi, true);
            fr.bmp.recycle();
        } finally {
            if (sf != null) EGL14.eglDestroySurface(dp, sf);
            if (ctx != null) EGL14.eglDestroyContext(dp, ctx);
            EGL14.eglMakeCurrent(dp, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglTerminate(dp);
            in.release();
            try { enc.stop(); } catch (Exception ignored) { }
            enc.release();
            try { if (trk[0] >= 0) mux.stop(); } catch (Exception ignored) { }
            mux.release();
        }
    }

    private static void drain(MediaCodec enc, MediaMuxer mux, int[] trk, MediaCodec.BufferInfo bi, boolean end) {
        while (true) {
            int ix = enc.dequeueOutputBuffer(bi, end ? 10000 : 0);
            if (ix == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!end) return; continue; }
            if (ix == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { trk[0] = mux.addTrack(enc.getOutputFormat()); mux.start(); continue; }
            if (ix < 0) continue;
            ByteBuffer ob = enc.getOutputBuffer(ix);
            if ((bi.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bi.size = 0;
            if (bi.size > 0 && trk[0] >= 0 && ob != null) { ob.position(bi.offset); ob.limit(bi.offset + bi.size); mux.writeSampleData(trk[0], ob, bi); }
            enc.releaseOutputBuffer(ix, false);
            if ((bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
        }
    }

    /** picture + sound → the final MP4 */
    private static void mux(File vid, File m4a, File out) throws Exception {
        MediaExtractor vx = new MediaExtractor(), ax = new MediaExtractor();
        MediaMuxer m = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        try {
            vx.setDataSource(vid.getAbsolutePath()); vx.selectTrack(0);
            ax.setDataSource(m4a.getAbsolutePath()); ax.selectTrack(0);
            int ov = m.addTrack(vx.getTrackFormat(0)), oa = m.addTrack(ax.getTrackFormat(0));
            m.start();
            ByteBuffer buf = ByteBuffer.allocate(4 << 20);
            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
            boolean vEnd = false, aEnd = false;
            while (!vEnd || !aEnd) {
                long vt = vEnd ? Long.MAX_VALUE : vx.getSampleTime(), atm = aEnd ? Long.MAX_VALUE : ax.getSampleTime();
                if (vt < 0) { vEnd = true; continue; }
                if (atm < 0) { aEnd = true; continue; }
                boolean video = vt <= atm;
                MediaExtractor x = video ? vx : ax;
                int size = x.readSampleData(buf, 0);
                if (size < 0) { if (video) vEnd = true; else aEnd = true; continue; }
                bi.set(0, size, video ? vt : atm, (x.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                m.writeSampleData(video ? ov : oa, buf, bi);
                x.advance();
            }
            m.stop();
        } finally {
            vx.release(); ax.release(); m.release();
        }
    }

    /** one picture: background, title, caption, level bars, progress */
    private static final class Frame {
        final Bitmap bmp; final Canvas cv;
        final int W, H; final double dur; final float[] lv;
        final double[][] t; final String[] txt;
        final Paint bg = new Paint(), bar = new Paint(Paint.ANTI_ALIAS_FLAG), dim = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint cap = new TextPaint(Paint.ANTI_ALIAS_FLAG), ttl = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final String title;
        StaticLayout lay; int layFor = -2;
        final RectF r = new RectF();

        Frame(JSONObject a, int W, int H, double dur, float[] lv) throws Exception {
            this.W = W; this.H = H; this.dur = dur; this.lv = lv;
            bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            cv = new Canvas(bmp);
            int col = Color.parseColor(a.optString("color", "#6A45B0"));
            int dark = Color.rgb(Color.red(col) / 4, Color.green(col) / 4, Color.blue(col) / 4);
            bg.setShader(new LinearGradient(0, 0, 0, H, col, dark, Shader.TileMode.CLAMP));
            bar.setColor(Color.WHITE);
            dim.setColor(0x55FFFFFF);
            int u = Math.min(W, H);
            cap.setColor(Color.WHITE); cap.setTypeface(Typeface.DEFAULT_BOLD); cap.setTextSize(u * 0.072f);
            cap.setShadowLayer(u * 0.01f, 0, u * 0.004f, 0x66000000);
            ttl.setColor(0xCCFFFFFF); ttl.setTypeface(Typeface.DEFAULT_BOLD); ttl.setTextSize(u * 0.042f);
            title = a.optString("title", "");
            JSONArray s = a.optJSONArray("segs");
            int n = s == null ? 0 : s.length();
            t = new double[n][2]; txt = new String[n];
            for (int i = 0; i < n; i++) { JSONArray x = s.getJSONArray(i); t[i][0] = x.getDouble(0); t[i][1] = x.getDouble(1); txt[i] = x.getString(2); }
        }

        int seg(double tm) { for (int i = 0; i < t.length; i++) if (tm >= t[i][0] && tm < t[i][1]) return i; return -1; }

        void draw(int i) {
            double tm = i / (double) FPS;
            int u = Math.min(W, H), pad = (int) (u * 0.07f);
            cv.drawRect(0, 0, W, H, bg);
            if (!title.isEmpty()) {
                StaticLayout tl = StaticLayout.Builder.obtain(title, 0, title.length(), ttl, W - 2 * pad).setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END).build();
                cv.save(); cv.translate(pad, pad); tl.draw(cv); cv.restore();
            }
            int k = seg(tm);
            if (k != layFor) { layFor = k; lay = k < 0 ? null : StaticLayout.Builder.obtain(txt[k], 0, txt[k].length(), cap, W - 2 * pad).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0, 1.08f).setMaxLines(4).setEllipsize(android.text.TextUtils.TruncateAt.END).build(); }
            if (lay != null) { cv.save(); cv.translate(pad, H * 0.44f - lay.getHeight() / 2f); lay.draw(cv); cv.restore(); }
            // level bars: the last moments to the left, now in the middle
            int nb = 31; float bw = (W - 2f * pad) / nb, base = H * 0.78f, maxH = u * 0.16f;
            for (int b = 0; b < nb; b++) {
                int fi = i - (nb / 2 - b) * 1 ; float v = fi >= 0 && fi < lv.length ? lv[fi] : 0;
                float hh = Math.max(u * 0.008f, v * maxH), x0 = pad + b * bw + bw * 0.2f;
                r.set(x0, base - hh / 2, x0 + bw * 0.6f, base + hh / 2);
                cv.drawRoundRect(r, bw * 0.3f, bw * 0.3f, b <= nb / 2 ? bar : dim);
            }
            float py = H - pad * 1.2f, pw = W - 2f * pad;
            r.set(pad, py - u * 0.006f, pad + pw, py + u * 0.006f); cv.drawRoundRect(r, u * 0.006f, u * 0.006f, dim);
            r.set(pad, py - u * 0.006f, pad + pw * (float) Math.min(1, tm / Math.max(0.01, dur)), py + u * 0.006f); cv.drawRoundRect(r, u * 0.006f, u * 0.006f, bar);
        }
    }
}
