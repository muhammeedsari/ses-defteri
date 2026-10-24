package app.sesdefteri;
public class TSep { public static void main(String[] a) throws Exception {
  System.load("/home/claude/vendor/shim/out/x64/libsdsep.so");
  String D = "/home/claude/vendor/sep/sherpa-onnx-spleeter-2stems-fp16/";
  long h = Separator.create(D + "vocals.fp16.onnx", D + "accompaniment.fp16.onnx", 2);
  System.out.println("h " + h);
  java.io.File f = new java.io.File(a[0]); Workspace.Info inf = Workspace.info(f);
  java.io.RandomAccessFile r = new java.io.RandomAccessFile(f, "r"); int n = (int) Math.min(inf.frames, 48000L * 10);
  byte[] b = new byte[n * inf.ch * 2]; r.seek(44); r.readFully(b);
  float[][] x = new float[inf.ch][n];
  for (int i = 0, q = 0; i < n; i++) for (int k = 0; k < inf.ch; k++, q += 2) x[k][i] = (short) ((b[q] & 0xff) | (b[q + 1] << 8)) / 32768f;
  int[] or = new int[1]; long t = System.currentTimeMillis();
  float[][] y = Separator.process(h, x, inf.rate, or);
  System.out.println("ms " + (System.currentTimeMillis() - t) + " stems*ch " + y.length + " len " + y[0].length + " rate " + or[0]);
  double e0 = 0, e1 = 0; for (int i = 24000; i < y[0].length - 24000; i++) { e0 += y[0][i] * y[0][i]; e1 += y[2][i] * y[2][i]; }
  System.out.println("rms voc " + Math.sqrt(e0 / y[0].length) + " acc " + Math.sqrt(e1 / y[0].length));
  Separator.destroy(h);
}}
