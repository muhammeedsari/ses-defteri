package app.sesdefteri;
import java.io.File;
public class TId3 { public static void main(String[] a) throws Exception {
  System.load("/home/claude/vendor/mp3/jni/out/x64/libsdmp3.so");
  File in = new File(a[0]); Workspace.Info inf = Workspace.info(in);
  org.json.JSONObject m = new org.json.JSONObject().put("title", "Şarkı Ğüzel").put("artist", "Ses Defteri");
  Workspace.mp3(in, inf, new File(a[1]), 192, new AudioTools.Progress() { public void on(double f) {} }, null, m);
}}
