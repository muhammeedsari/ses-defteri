package app.sesdefteri;
import org.json.*; import java.io.*;
public class TAll { public static void main(String[] x) throws Exception {
  String[] ops = x[1].split(",");
  for (String op : ops) {
    JSONObject a = new JSONObject().put("path", new File(x[0]).getAbsolutePath()).put("op", op);
    if (op.equals("cut")) a.put("keep", new JSONArray("[[0,2000],[3000,6000]]"));
    if (op.equals("speed")) a.put("speed", 1.25).put("semitones", 0);
    if (op.equals("speedp")) { a.put("op","speed").put("speed", 1).put("semitones", 3); }
    if (op.equals("vchange")) a.put("semitones", -4).put("preset", "robot");
    if (op.equals("gainmap")) a.put("map", new JSONArray("[[1,2,-10]]"));
    if (op.equals("chain")) a.put("steps", new JSONArray("[{\"op\":\"eq\"},{\"op\":\"comp\"}]"));
    if (op.equals("eqpreset")) a.put("preset", "voice");
    if (op.equals("insert")) a.put("at", 1.0).put("len", 0.5);
    if (op.equals("mix")) a.put("with", new File(x[0]).getAbsolutePath());
    long t0 = System.currentTimeMillis();
    try {
      JSONObject r = Workspace.op(null, a, new AudioTools.Progress(){ public void on(double v){} }, new java.util.concurrent.atomic.AtomicBoolean());
      String p = r.optString("path", "");
      String st = "";
      if (!p.isEmpty() && new File(p).isFile()) {
        Workspace.Info i = Workspace.info(new File(p)), i0 = Workspace.info(new File(x[0]));
        // peak / rms / nan check
        RandomAccessFile f = new RandomAccessFile(p, "r"); byte[] b = new byte[(int) Math.min(f.length() - 44, 48000L * 2 * i.ch * 60)]; f.seek(44); f.readFully(b); f.close();
        double pk = 0, ss = 0; int n = b.length / 2, clip = 0;
        for (int k = 0; k < n; k++) { int v = (short) ((b[2*k] & 0xff) | (b[2*k+1] << 8)); double d = v / 32768.0; pk = Math.max(pk, Math.abs(d)); ss += d*d; if (Math.abs(v) >= 32767) clip++; }
        st = String.format("dur %.2f->%.2f ch %d->%d peak %.2f rms %.1fdB clip %d", i0.frames/(double)i0.rate, i.frames/(double)i.rate, i0.ch, i.ch, pk, 10*Math.log10(ss/Math.max(1,n)+1e-12), clip);
        new File(p).delete();
      }
      System.out.println("OK   " + op + " " + (r.optBoolean("same") ? "SAME " : "") + st + " rep=" + String.valueOf(r.opt("report")).replace("\n","").substring(0, Math.min(140, String.valueOf(r.opt("report")).length())) + " " + (System.currentTimeMillis()-t0) + "ms");
    } catch (Throwable e) {
      System.out.println("FAIL " + op + " " + e);
    }
  }
}}
