package app.sesdefteri;
import org.json.*;
public class TEq { public static void main(String[] a) throws Exception {
  for (int k = 0; k < 2; k++) {
    JSONObject c = new JSONObject().put("path", a[0]).put("at", 0).put("in", 0).put("len", 14).put("g", 1);
    if (k == 1) c.put("eq", new JSONArray().put(6).put(0).put(0).put(-6).put(6));
    Timeline.Engine e = new Timeline.Engine(new JSONArray().put(c)); System.out.println("eq " + e.clips[0].eq + " " + c);
    float[][] o = new float[2][48000 * 14];
    for (int p = 0; p < o[0].length; p += 1024) { float[][] b = new float[2][1024]; int n = Math.min(1024, o[0].length - p); e.render(p, n, b); System.arraycopy(b[0], 0, o[0], p, n); }
    java.io.DataOutputStream d = new java.io.DataOutputStream(new java.io.FileOutputStream("/tmp/claude-0/eq" + k + ".f32"));
    for (float v : o[0]) d.writeFloat(v); d.close(); e.close();
  }
}}
