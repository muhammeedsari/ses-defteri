package app.sesdefteri;
import org.json.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** 3.2: a two-voice Turkish dialogue (two Piper voices), then speaker separation through the app's classes */
public class T2Spk { public static void main(String[] a) throws Exception {
  android.content.Context.FILES = new File(a[0], "files"); android.content.Context.CACHE = new File(a[0], "cache"); android.content.Context.ASSETS = new File(a[1]);
  android.content.Context c = new android.content.Context();
  final File arc = new File(a[2]), d2 = new File(Tts.root(c), "tr2");
  if (!Tts.ready(c, "tr2")) Untar.extract(new FileInputStream(arc), new Untar.Place() { public File to(String n) {
    String b = n.substring(n.lastIndexOf('/') + 1); return n.contains("espeak") ? null : b.endsWith(".onnx") ? new File(d2, "model.onnx") : b.equals("tokens.txt") ? new File(d2, "tokens.txt") : null; } });
  AudioTools.Progress p = new AudioTools.Progress() { public void on(double f) { } };
  String[] lines = { "Merhaba, bugün hava çok güzel, dışarı çıkalım mı?", "Bence iyi fikir, ama önce işlerimi bitirmem gerekiyor.",
    "Ne kadar sürer sence, bir saat yeter mi?", "Yarım saat yeter, sonra parka gideriz.", "Tamam, ben de bu arada kahve yaparım.", "Harika, şekersiz olsun lütfen.",
    "Peki, parkta ne yapmak istersin?", "Biraz yürüyüş yaparız, sonra da bir bankta otururuz." };
  java.util.ArrayList<float[]> parts = new java.util.ArrayList<>(); java.util.ArrayList<double[]> truth = new java.util.ArrayList<>();
  int R = 48000; double t = 0.5; long total = (long) (0.5 * R);
  for (int i = 0; i < lines.length; i++) {
    JSONObject r = Tts.speak(c, new JSONObject().put("text", lines[i]).put("lang", i % 2 == 0 ? "tr" : "tr2").put("engine", "ai"), p, new AtomicBoolean());
    File f = new File(r.getString("path")); Workspace.Info inf = Workspace.info(f);
    RandomAccessFile rf = new RandomAccessFile(f, "r"); float[] x = Workspace.readRegion(rf, inf, 0, (int) inf.frames)[0]; rf.close();
    parts.add(x); truth.add(new double[] { t, t + x.length / (double) R, i % 2 }); t += x.length / (double) R + 0.45; total += x.length + (long) (0.45 * R);
  }
  float[][] y = new float[1][(int) total + R];
  int pos = R / 2; for (float[] x : parts) { System.arraycopy(x, 0, y[0], pos, x.length); pos += x.length + (int) (0.45 * R); }
  File dlg = new File(a[0], "dialog.wav"); Studio.WavNode w = new Studio.WavNode(dlg, R); w.push(y, y[0].length); w.end();
  System.out.println("dialogue " + y[0].length / (double) R + " s");
  long t0 = System.currentTimeMillis();
  if (!Speakers.ready(c)) { Speakers.download(c, p, new AtomicBoolean()); System.out.println("model downloaded in " + (System.currentTimeMillis() - t0) + " ms, " + Speakers.emb(c).length() + " bytes"); }
  for (int n : new int[] { 0, 2 }) {
    t0 = System.currentTimeMillis();
    JSONObject r = Speakers.run(c, new JSONObject().put("path", dlg.getAbsolutePath()).put("in", 0).put("out", y[0].length / (double) R).put("n", n), p, new AtomicBoolean());
    System.out.println("n=" + n + " -> speakers " + r.getInt("n") + " db " + r.get("db") + " in " + (System.currentTimeMillis() - t0) + " ms");
    JSONArray s = r.getJSONArray("segs");
    // each true turn: which found speaker covers most of it
    int ok = 0; int[] map = { -1, -1 };
    for (double[] tr : truth) {
      double[] cov = new double[8];
      for (int i = 0; i < s.length(); i++) { JSONArray g = s.getJSONArray(i); double o = Math.min(tr[1], g.getDouble(1)) - Math.max(tr[0], g.getDouble(0)); if (o > 0) cov[g.getInt(2)] += o; }
      int best = 0; for (int k = 1; k < 8; k++) if (cov[k] > cov[best]) best = k;
      int who = (int) tr[2]; if (map[who] < 0) map[who] = best; if (map[who] == best && cov[best] > 0.5 * (tr[1] - tr[0])) ok++;
      System.out.printf("  turn %.1f–%.1f voice %d -> speaker %d (%.0f%% covered)%n", tr[0], tr[1], who, best, 100 * cov[best] / (tr[1] - tr[0]));
    }
    System.out.println("  correct turns " + ok + "/" + truth.size() + (map[0] == map[1] ? "  (BOTH VOICES SAME SPEAKER)" : ""));
  }
  try { Speakers.run(c, new JSONObject().put("path", dlg.getAbsolutePath()).put("in", 0).put("out", 0.5), p, null); } catch (Exception e) { System.out.println("short -> " + e.getMessage()); }
}}
