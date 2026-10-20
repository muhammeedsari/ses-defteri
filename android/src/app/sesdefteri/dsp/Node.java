package app.sesdefteri.dsp;

/**
 * A processing step in a push chain. Audio is planar: buf[channel][index].
 * Every step keeps the timeline intact (same number of samples out as in, no extra delay),
 * so positions such as bookmarks stay valid after processing.
 */
public abstract class Node {
    protected Node next;

    public Node to(Node n) { next = n; return n; }

    public abstract void push(float[][] buf, int n) throws Exception;

    public void end() throws Exception { if (next != null) next.end(); }

    /** Collects everything into memory (tests, short clips). */
    public static final class Collect extends Node {
        public float[][] data;
        public int len;
        private int ch;

        public Collect(int channels) { ch = channels; data = new float[channels][1 << 16]; }

        @Override public void push(float[][] b, int n) {
            if (len == 0 && b.length != ch) { ch = b.length; data = new float[ch][1 << 16]; }
            if (len + n > data[0].length) {
                int cap = Math.max(len + n, data[0].length * 2);
                for (int c = 0; c < ch; c++) data[c] = java.util.Arrays.copyOf(data[c], cap);
            }
            for (int c = 0; c < ch; c++) System.arraycopy(b[c], 0, data[c], len, n);
            len += n;
        }

        @Override public void end() { }

        public float[][] result() {
            float[][] r = new float[ch][];
            for (int c = 0; c < ch; c++) r[c] = java.util.Arrays.copyOf(data[c], len);
            return r;
        }
    }

    /** Feeds a whole signal through a chain in blocks. */
    public static void run(float[][] x, Node head, int block) throws Exception {
        int ch = x.length, len = x[0].length;
        float[][] b = new float[ch][block];
        for (int p = 0; p < len; p += block) {
            int n = Math.min(block, len - p);
            for (int c = 0; c < ch; c++) System.arraycopy(x[c], p, b[c], 0, n);
            head.push(b, n);
        }
        head.end();
    }

    public static float[][] apply(float[][] x, Node head, Node tail, int outCh) throws Exception {
        Collect col = new Collect(outCh);
        tail.to(col);
        run(x, head, 4096);
        return col.result();
    }
}
