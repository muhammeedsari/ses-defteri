import app.sesdefteri.dsp.*;
import java.nio.*; import java.nio.file.*;
public class TP {
  public static void main(String[] a) throws Exception {
    byte[] b = Files.readAllBytes(Paths.get(a[0])); FloatBuffer fb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
    float[] x = new float[fb.remaining()]; fb.get(x);
    int fs = Integer.parseInt(a[2]); double sp = Double.parseDouble(a[3]), st = Double.parseDouble(a[4]);
    TimePitch tp = new TimePitch(1, fs, sp, st, a[5].equals("1"));
    Node.Collect col = new Node.Collect(1); tp.to(col);
    long t0 = System.nanoTime();
    for (int i = 0; i < x.length; i += 4096) { int k = Math.min(4096, x.length - i); tp.push(new float[][] { java.util.Arrays.copyOfRange(x, i, i + k) }, k); }
    tp.end();
    float[] y = col.result()[0];
    System.err.printf("%.1f ms%n", (System.nanoTime() - t0) / 1e6);
    ByteBuffer o = ByteBuffer.allocate(y.length * 4).order(ByteOrder.LITTLE_ENDIAN); o.asFloatBuffer().put(y);
    Files.write(Paths.get(a[1]), o.array());
  }
}
