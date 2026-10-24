package app.sesdefteri;
import java.io.*;
public class TFill { public static void main(String[] a) throws Exception {
  File f = new File(a[0]); Workspace.Info inf = Workspace.info(f);
  RandomAccessFile r = new RandomAccessFile(f, "r"); r.seek(44); byte[] b = new byte[(int) inf.frames * inf.ch * 2]; r.readFully(b);
  int n = (int) (inf.frames / 3); float[] x = new float[n];
  for (int i = 0; i < n; i++) { double s = 0; for (int k = 0; k < 3; k++) { int q = ((i * 3 + k) * inf.ch) * 2; s += (short) ((b[q] & 0xff) | (b[q + 1] << 8)) / 32768.0; } x[i] = (float) (s / 3); }
  long t = System.currentTimeMillis();
  for (double[] q : Speech.fillers(x, 16000)) System.out.printf("filler %.2f-%.2f%n", q[0], q[1]);
  System.out.println("ms " + (System.currentTimeMillis() - t) + " for " + n / 16000.0 + " s");
}}
