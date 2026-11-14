package app.sesdefteri;
import org.json.*;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
/** 3.4: real download + unpack + Piper voice through the app's own classes */
public class T1Tts { public static void main(String[] a) throws Exception {
  android.content.Context.FILES = new File(a[0], "files"); android.content.Context.CACHE = new File(a[0], "cache"); android.content.Context.ASSETS = new File(a[1]);
  android.content.Context.FILES.mkdirs(); android.content.Context.CACHE.mkdirs();
  android.content.Context c = new android.content.Context();
  AudioTools.Progress p = new AudioTools.Progress() { double last = -1; public void on(double f) { if (f - last > 0.2) { last = f; System.out.printf("  %.0f%%%n", f * 100); } } };
  System.out.println("status before " + Tts.status(c));
  for (String l : a[2].split(",")) {
    long t0 = System.currentTimeMillis();
    if (!Tts.ready(c, l)) System.out.println("download " + l + " -> " + Tts.download(c, new JSONObject().put("lang", l), p, new AtomicBoolean()) + " " + (System.currentTimeMillis() - t0) + " ms");
  }
  String[][] tx = { { "tr", "Merhaba, bugün Ses Defteri uygulamasının yeni özelliklerini deniyoruz. Konuşmacı ayırma, otomatik ses düzeltme ve metinden ses." },
                    { "en", "Hello, this is a test of the natural voice. It should sound clear and calm." } };
  for (String[] s : tx) {
    if (!Tts.ready(c, s[0])) continue;
    long t0 = System.currentTimeMillis();
    JSONObject r = Tts.speak(c, new JSONObject().put("text", s[1]).put("lang", s[0]).put("engine", "ai").put("speed", 1.0), p, new AtomicBoolean());
    long ms = System.currentTimeMillis() - t0;
    Workspace.Info i = Workspace.info(new File(r.getString("path")));
    System.out.println(s[0] + " -> " + r + " rate " + i.rate + " ch " + i.ch + " in " + ms + " ms");
    JSONObject r2 = Tts.speak(c, new JSONObject().put("text", s[1]).put("lang", s[0]).put("engine", "ai").put("speed", 1.5), p, new AtomicBoolean());
    System.out.println("  speed 1.5 dur " + r2.getDouble("dur") + " vs " + r.getDouble("dur"));
  }
  try { Tts.speak(c, new JSONObject().put("text", "  ").put("lang", "tr").put("engine", "ai"), p, null); } catch (Exception e) { System.out.println("empty -> " + e.getMessage()); }
  System.out.println("split " + Tts.split("Bir. İki cümle! Üç? " + new String(new char[50]).replace('\0', 'x'), 20));
  System.out.println("delete en -> " + Tts.delete(c, new JSONObject().put("lang", "en")));
}}
