package app.sesdefteri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Unpacks the .tar.bz2 model archives of sherpa-onnx on the phone (Java has no bzip2). Small streaming bzip2 decoder
 * (Huffman → MTF → inverse BWT → run lengths) and a tar reader that only knows plain files, folders and long names.
 */
final class Untar {
    private Untar() { }

    interface Place { /** where an archive entry goes, or null to skip it */ File to(String name); }

    static void extract(InputStream raw, Place place) throws IOException {
        InputStream in = new Bz2(raw);
        byte[] h = new byte[512], b = new byte[1 << 16];
        String longName = null;
        while (true) {
            if (!full(in, h, 512)) return;
            boolean zero = true;
            for (byte x : h) if (x != 0) { zero = false; break; }
            if (zero) return;
            String name = longName != null ? longName : str(h, 345, 155).isEmpty() ? str(h, 0, 100) : str(h, 345, 155) + "/" + str(h, 0, 100);
            longName = null;
            long size = 0;
            for (int i = 124; i < 136; i++) { int c = h[i]; if (c >= '0' && c <= '7') size = size * 8 + (c - '0'); }
            char type = (char) h[156];
            long pad = (512 - size % 512) % 512;
            if (type == 'L') {
                byte[] nb = new byte[(int) size];
                if (!full(in, nb, nb.length)) throw new IOException("tar");
                longName = str(nb, 0, nb.length);
                skip(in, pad);
                continue;
            }
            File f = (type == '0' || type == 0) ? place.to(name) : null;
            if (f != null) {
                f.getParentFile().mkdirs();
                File part = new File(f.getPath() + ".part");
                OutputStream os = new FileOutputStream(part);
                try {
                    long left = size;
                    while (left > 0) {
                        int n = in.read(b, 0, (int) Math.min(b.length, left));
                        if (n < 0) throw new IOException("tar");
                        os.write(b, 0, n);
                        left -= n;
                    }
                } finally { os.close(); }
                if (!part.renameTo(f)) throw new IOException("rename");
                skip(in, pad);
            } else {
                skip(in, size + pad);
            }
        }
    }

    private static String str(byte[] b, int off, int len) {
        int e = off;
        while (e < off + len && b[e] != 0) e++;
        try { return new String(b, off, e - off, "UTF-8"); } catch (Exception x) { return ""; }
    }

    private static boolean full(InputStream in, byte[] b, int n) throws IOException {
        int got = 0;
        while (got < n) { int r = in.read(b, got, n - got); if (r < 0) { if (got == 0) return false; throw new IOException("tar"); } got += r; }
        return true;
    }

    private static void skip(InputStream in, long n) throws IOException {
        byte[] b = new byte[8192];
        while (n > 0) { int r = in.read(b, 0, (int) Math.min(b.length, n)); if (r < 0) throw new IOException("tar"); n -= r; }
    }

    /** bzip2 decompression as a stream (several concatenated streams are fine) */
    static final class Bz2 extends InputStream {
        final InputStream in;
        int bitBuf, bitCnt;
        int blockMax;
        int[] tt;
        // output of the current block
        int tPos, left, last = -1, run, repeat;
        byte repByte;
        boolean eof;

        Bz2(InputStream in) throws IOException { this.in = new java.io.BufferedInputStream(in, 1 << 16); header(); }

        private int bit() throws IOException {
            if (bitCnt == 0) { int c = in.read(); if (c < 0) throw new IOException("bz2 eof"); bitBuf = c; bitCnt = 8; }
            return (bitBuf >> --bitCnt) & 1;
        }

        private int bits(int n) throws IOException { int v = 0; for (int i = 0; i < n; i++) v = (v << 1) | bit(); return v; }

        private boolean header() throws IOException {
            bitCnt = 0;
            int b = in.read();
            if (b < 0) return false;
            if (b != 'B' || in.read() != 'Z' || in.read() != 'h') throw new IOException("bz2 magic");
            int lv = in.read() - '0';
            if (lv < 1 || lv > 9) throw new IOException("bz2 level");
            blockMax = lv * 100000;
            if (tt == null || tt.length < blockMax) tt = new int[blockMax];
            return true;
        }

        /** reads the next block into tt; false at the end of everything */
        private boolean block() throws IOException {
            while (true) {
                long magic = ((long) bits(24) << 24) | bits(24);
                if (magic == 0x177245385090L) {
                    bits(32);   // stream CRC
                    if (!header()) return false;
                    continue;
                }
                if (magic != 0x314159265359L) throw new IOException("bz2 block");
                break;
            }
            bits(32);   // block CRC
            if (bit() != 0) throw new IOException("bz2 randomised");
            int origPtr = bits(24);
            // symbols in use
            int used16 = bits(16), nInUse = 0;
            int[] seqToUnseq = new int[256];
            for (int i = 0; i < 16; i++) if ((used16 & (0x8000 >> i)) != 0) {
                int u = bits(16);
                for (int j = 0; j < 16; j++) if ((u & (0x8000 >> j)) != 0) seqToUnseq[nInUse++] = i * 16 + j;
            }
            if (nInUse == 0) throw new IOException("bz2 symbols");
            int alpha = nInUse + 2;
            int nGroups = bits(3), nSel = bits(15);
            if (nGroups < 2 || nGroups > 6 || nSel < 1) throw new IOException("bz2 groups");
            byte[] sel = new byte[nSel];
            byte[] mtfG = { 0, 1, 2, 3, 4, 5 };
            for (int i = 0; i < nSel; i++) {
                int j = 0;
                while (bit() == 1) { j++; if (j >= nGroups) throw new IOException("bz2 selector"); }
                byte v = mtfG[j];
                System.arraycopy(mtfG, 0, mtfG, 1, j);
                mtfG[0] = v;
                sel[i] = v;
            }
            // code lengths and canonical decoding tables per group
            int[][] limit = new int[nGroups][22], base = new int[nGroups][22], perm = new int[nGroups][alpha];
            int[] minLen = new int[nGroups];
            int[] len = new int[alpha];
            for (int g = 0; g < nGroups; g++) {
                int c = bits(5);
                for (int s = 0; s < alpha; s++) {
                    while (true) {
                        if (c < 1 || c > 20) throw new IOException("bz2 length");
                        if (bit() == 0) break;
                        c += bit() == 0 ? 1 : -1;
                    }
                    len[s] = c;
                }
                int mn = 32, mx = 0;
                for (int s = 0; s < alpha; s++) { mn = Math.min(mn, len[s]); mx = Math.max(mx, len[s]); }
                minLen[g] = mn;
                int pp = 0;
                for (int l = mn; l <= mx; l++) for (int s = 0; s < alpha; s++) if (len[s] == l) perm[g][pp++] = s;
                int[] count = new int[22];
                for (int s = 0; s < alpha; s++) count[len[s]]++;
                // limit[l]: largest code of length l; base[l]: offset into perm
                int code = 0, idx = 0;
                for (int l = 1; l <= 20; l++) {
                    base[g][l] = idx - code;
                    code += count[l];
                    idx += count[l];
                    limit[g][l] = code - 1;
                    code <<= 1;
                }
            }
            // MTF / run-length symbols
            int[] mtf = new int[256];
            for (int i = 0; i < 256; i++) mtf[i] = i;
            int[] cnt = new int[256];
            int n = 0, gi = -1, gLeft = 0, g = 0;
            int runAcc = 0, runBit = 1;
            int eob = nInUse + 1;
            while (true) {
                if (gLeft == 0) { gi++; if (gi >= nSel) throw new IOException("bz2 selectors"); g = sel[gi]; gLeft = 50; }
                gLeft--;
                int l = minLen[g], v = bits(l);
                while (l <= 20 && v > limit[g][l]) { l++; v = (v << 1) | bit(); }
                if (l > 20) throw new IOException("bz2 code");
                int sym = perm[g][v + base[g][l]];
                if (sym <= 1) {   // RUNA / RUNB
                    runAcc += (sym + 1) * runBit;
                    runBit <<= 1;
                    if (runAcc > blockMax) throw new IOException("bz2 run");
                    continue;
                }
                if (runAcc > 0) {
                    int ch = seqToUnseq[mtf[0]];
                    if (n + runAcc > blockMax) throw new IOException("bz2 overflow");
                    cnt[ch] += runAcc;
                    while (runAcc-- > 0) tt[n++] = ch;
                    runAcc = 0; runBit = 1;
                }
                if (sym == eob) break;
                int k = sym - 1, m = mtf[k];
                System.arraycopy(mtf, 0, mtf, 1, k);
                mtf[0] = m;
                int ch = seqToUnseq[m];
                if (n >= blockMax) throw new IOException("bz2 overflow");
                cnt[ch]++;
                tt[n++] = ch;
            }
            if (origPtr >= n) throw new IOException("bz2 origPtr");
            // inverse BWT: links in the high 24 bits
            int[] cf = new int[256];
            for (int i = 0, s = 0; i < 256; i++) { cf[i] = s; s += cnt[i]; }
            for (int i = 0; i < n; i++) { int ch = tt[i] & 0xff; tt[cf[ch]++] |= i << 8; }
            tPos = tt[origPtr] >>> 8;
            left = n;
            last = -1; run = 0;
            return true;
        }

        /** next byte of the block after undoing the 4+count runs, -1 when the block is used up */
        private int next() {
            if (repeat > 0) { repeat--; return repByte & 0xff; }
            if (left == 0) return -1;
            int e = tt[tPos];
            int ch = e & 0xff;
            tPos = e >>> 8;
            left--;
            if (run == 4) {
                // this byte is the repeat count of the previous one
                run = 0;
                int prev = last;
                last = -1;
                if (ch == 0) return next();
                repeat = ch - 1;
                repByte = (byte) prev;
                return prev;
            }
            if (ch == last) run++; else { run = 1; last = ch; }
            return ch;
        }

        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }

        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (eof) return -1;
            int got = 0;
            while (got < len) {
                int c = next();
                if (c < 0) {
                    if (got > 0) return got;
                    if (!block()) { eof = true; return -1; }
                    continue;
                }
                b[off + got++] = (byte) c;
            }
            return got;
        }
    }
}
