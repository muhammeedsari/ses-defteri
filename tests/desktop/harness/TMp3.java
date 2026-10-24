package app.sesdefteri;
import java.io.*;
public class TMp3 { public static void main(String[] a) throws Exception {
  System.load("/home/claude/vendor/mp3/jni/out/x64/libsdmp3.so");
  File in = new File(a[0]); Workspace.Info inf = Workspace.info(in);
  long h = Mp3.init(inf.rate, inf.ch, 192);
  RandomAccessFile r = new RandomAccessFile(in, "r"); r.seek(44);
  OutputStream os = new FileOutputStream(a[1]); int BL = 4096; byte[] b = new byte[BL * inf.ch * 2]; short[] s = new short[BL * inf.ch];
  long done = 0, t = System.currentTimeMillis();
  while (done < inf.frames) { int n = (int) Math.min(BL, inf.frames - done); r.readFully(b, 0, n * inf.ch * 2);
    for (int i = 0; i < n * inf.ch; i++) s[i] = (short) ((b[2*i] & 0xff) | (b[2*i+1] << 8));
    os.write(Mp3.encode(h, s, n, inf.ch)); done += n; }
  os.write(Mp3.flush(h)); Mp3.close(h); os.close();
  System.out.println("ms " + (System.currentTimeMillis() - t) + " for " + inf.frames / (double) inf.rate + " s");
}}
