package app.sesdefteri;
import org.json.*; import java.io.File;
public class T19 { public static void main(String[] a) throws Exception {
  AudioTools.Progress p = new AudioTools.Progress() { public void on(double f) {} };
  File f = new File(a[0]); Workspace.Info inf = Workspace.info(f);
  Speech.Scan sc = Speech.scan(f, inf, null, null);
  System.out.println("noise " + sc.noise + " speech " + sc.speech);
  for (int[] r : Speech.breaths(sc)) System.out.println("breath " + r[0]*0.02 + "-" + r[1]*0.02);
  for (int[] r : Speech.pauses(sc, 0.8)) System.out.println("pause " + r[0]*0.02 + "-" + r[1]*0.02);
  for (String op : a[1].split(",")) {
    JSONObject o = new JSONObject().put("path", f.getAbsolutePath()).put("op", op);
    if (op.equals("eqpreset")) o.put("gains", new JSONArray().put(3).put(0).put(0).put(2).put(4));
    long t = System.currentTimeMillis();
    JSONObject r = Workspace.op(null, o, p, null);
    System.out.println(op + " -> " + r + " ms " + (System.currentTimeMillis() - t));
  }
}}
