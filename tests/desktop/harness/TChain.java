package app.sesdefteri;
import org.json.*; import java.io.File;
public class TChain { public static void main(String[] a) throws Exception {
  AudioTools.Progress p = new AudioTools.Progress() { public void on(double f) {} };
  String path = new File(a[0]).getAbsolutePath();
  for (String op : a[1].split(",")) {
    String[] pa = op.split("@"); op = pa[0];
    JSONObject o = new JSONObject().put("path", path).put("op", op);
    for (int i = 1; i < pa.length; i++) { String[] kv = pa[i].split("="); if (kv[1].equals("false")) o.put(kv[0], false); else if (kv[1].contains(":")) o.put(kv[0], new JSONArray().put(Double.parseDouble(kv[1].split(":")[0])).put(Double.parseDouble(kv[1].split(":")[1]))); else o.put(kv[0], Double.parseDouble(kv[1])); }
    if (op.equals("eqpreset")) o.put("gains", new JSONArray().put(6).put(0).put(0).put(-6).put(6));
    try {
      JSONObject r = Workspace.op(null, o, p, null);
      String s = r.toString(); System.out.println("R " + op + " " + r.optString("path", "same") + " " + r.optJSONObject("report"));
      if (!r.optBoolean("same") && a.length < 3) path = r.getString("path");
    } catch (Throwable e) { System.out.println(op + " FAIL"); e.printStackTrace(System.out); }
  }
}}
