package app.sesdefteri.dsp;

import java.io.*;

/** Minimal WAV reader/writer (PCM 16/24/32-bit, IEEE float 32). Used for lossless export and for tests. */
public final class Wav {
    public float[][] data;
    public int rate;

    public static Wav read(File f) throws IOException {
        byte[] all = new byte[(int) f.length()];
        try (InputStream is = new FileInputStream(f)) { int o = 0; while (o < all.length) { int r = is.read(all, o, all.length - o); if (r < 0) break; o += r; } }
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(all).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        if (bb.getInt(0) != 0x46464952 || bb.getInt(8) != 0x45564157) throw new IOException("not wav");
        int p = 12, fmt = 1, ch = 1, bits = 16, rate = 44100;
        Wav w = new Wav();
        while (p + 8 <= all.length) {
            int id = bb.getInt(p), size = bb.getInt(p + 4);
            if (id == 0x20746d66) { // fmt
                fmt = bb.getShort(p + 8) & 0xffff; ch = bb.getShort(p + 10); rate = bb.getInt(p + 12); bits = bb.getShort(p + 22);
                if (fmt == 0xFFFE) fmt = bb.getShort(p + 32) & 0xffff;
            } else if (id == 0x61746164) { // data
                int bps = bits / 8, frames = Math.min(size, all.length - p - 8) / (bps * ch);
                w.data = new float[ch][frames];
                int q = p + 8;
                for (int i = 0; i < frames; i++)
                    for (int c = 0; c < ch; c++, q += bps) {
                        float v;
                        if (fmt == 3) v = bb.getFloat(q);
                        else if (bits == 16) v = bb.getShort(q) / 32768f;
                        else if (bits == 24) v = (((all[q] & 0xff) | ((all[q + 1] & 0xff) << 8) | (all[q + 2] << 16))) / 8388608f;
                        else v = bb.getInt(q) / 2147483648f;
                        w.data[c][i] = v;
                    }
                break;
            }
            p += 8 + size + (size & 1);
        }
        w.rate = rate;
        if (w.data == null) throw new IOException("no data");
        return w;
    }

    public static void write(File f, float[][] x, int rate, boolean floatFmt) throws IOException {
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(f))) {
            Writer w = new Writer(os, x.length, rate, floatFmt, x[0].length);
            w.write(x, x[0].length);
        }
    }

    /** Streaming writer; the header needs the final length, so it is written up front from a known frame count
     *  or patched later with {@link #patch(RandomAccessFile, long, int, boolean)}. */
    public static final class Writer {
        private final OutputStream os;
        private final int ch;
        private final boolean fl;
        private byte[] buf = new byte[0];

        public Writer(OutputStream os, int ch, int rate, boolean floatFmt, long frames) throws IOException {
            this.os = os; this.ch = ch; this.fl = floatFmt;
            os.write(header(ch, rate, floatFmt, frames));
        }

        public void write(float[][] x, int n) throws IOException {
            int bps = fl ? 4 : 2, need = n * ch * bps;
            if (buf.length < need) buf = new byte[need];
            int q = 0;
            for (int i = 0; i < n; i++)
                for (int c = 0; c < ch; c++) {
                    if (fl) {
                        int b = Float.floatToIntBits(x[c][i]);
                        buf[q++] = (byte) b; buf[q++] = (byte) (b >> 8); buf[q++] = (byte) (b >> 16); buf[q++] = (byte) (b >> 24);
                    } else {
                        float v = Math.max(-1f, Math.min(1f, x[c][i]));
                        int s = Math.round(v * 32767f);
                        buf[q++] = (byte) s; buf[q++] = (byte) (s >> 8);
                    }
                }
            os.write(buf, 0, need);
        }
    }

    public static byte[] header(int ch, int rate, boolean fl, long frames) {
        int bps = fl ? 4 : 2;
        long data = frames * ch * bps;
        java.nio.ByteBuffer h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        h.putInt(0x46464952).putInt((int) Math.min(0xFFFFFFFFL, 36 + data)).putInt(0x45564157)
         .putInt(0x20746d66).putInt(16).putShort((short) (fl ? 3 : 1)).putShort((short) ch).putInt(rate)
         .putInt(rate * ch * bps).putShort((short) (ch * bps)).putShort((short) (bps * 8))
         .putInt(0x61746164).putInt((int) Math.min(0xFFFFFFFFL, data));
        return h.array();
    }

    public static void patch(RandomAccessFile raf, long frames, int ch, int rate, boolean fl) throws IOException {
        raf.seek(0);
        raf.write(header(ch, rate, fl, frames));
    }
}
