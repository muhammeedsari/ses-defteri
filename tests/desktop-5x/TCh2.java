package app.sesdefteri;
import org.json.*; import java.io.*; import java.nio.file.*;
public class TCh2 { public static void main(String[] x) throws Exception {
  JSONArray cs = new JSONArray(new String(Files.readAllBytes(Paths.get("chains.json")), "UTF-8"));
  for (int i = 0; i < cs.length(); i++) {
    JSONObject c = cs.getJSONObject(i);
    try {
      JSONObject r = Workspace.op(null, new JSONObject().put("path", new File(x[0]).getAbsolutePath()).put("op", "chain").put("steps", c.getJSONArray("steps")), new AudioTools.Progress(){ public void on(double v){} }, new java.util.concurrent.atomic.AtomicBoolean());
      Workspace.Info inf = Workspace.info(new File(r.getString("path")));
      System.out.println("OK " + c.getString("id") + " dur " + inf.frames / (double) inf.rate + " ch " + inf.ch);
      new File(r.getString("path")).delete();
    } catch (Throwable e) { System.out.println("FAIL " + c.getString("id") + " " + e); }
  }
}}
