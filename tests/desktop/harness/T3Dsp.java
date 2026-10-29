package app.sesdefteri;
import org.json.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import app.sesdefteri.dsp.*;
/** 2.7 / 3.0 / 3.3 edits through Workspace.op on real speech and a sung melody */
public class T3Dsp {
  static android.content.Context c = new android.content.Context();
  static AudioTools.Progress P = new AudioTools.Progress() { public void on(double f) { } };
  static float[][] read(File f) throws Exception { Workspace.Info i = Workspace.info(f); RandomAccessFile r = new RandomAccessFile(f, "r"); try { return Workspace.readRegion(r, i, 0, (int) i.frames); } finally { r.close(); } }
  static File write(File f, float[][] y) throws Exception { Studio.WavNode w = new Studio.WavNode(f, 48000); w.push(y, y[0].length); w.end(); return f; }
  static String stat(float[][] y) { double pk = 0, e = 0; boolean nan = false; for (float[] c : y) for (float v : c) { if (Float.isNaN(v) || Float.isInfinite(v)) nan = true; pk = Math.max(pk, Math.abs(v)); e += v * v; }
    return String.format("len %.2fs pk %.2f rms %.1f dB%s", y[0].length / 48000.0, pk, 10 * Math.log10(e / y[0].length / y.length + 1e-12), nan ? " NaN!" : ""); }
  /** energy (dB) of the band f0–f1 Hz over the whole signal (channel 0) */
  static double band(float[] x, double f0, double f1) throws Exception {
    Biquad hp = Biquad.highpass(1, 48000, f0, 0.707), lp = Biquad.lowpass(1, 48000, f1, 0.707); float[] y = x.clone();
    for (int k = 0; k < 4; k++) { hp = Biquad.highpass(1, 48000, f0, 0.707); lp = Biquad.lowpass(1, 48000, f1, 0.707); hp.process(0, y, 0, y.length); lp.process(0, y, 0, y.length); } double e = 0; for (float v : y) e += v * v; return 10 * Math.log10(e / y.length + 1e-15); }
  static double medF0(float[] x) throws Exception {
    java.lang.reflect.Method m = AutoTune.class.getDeclaredMethod("track", float[].class, int.class, int.class, int.class); m.setAccessible(true);
    double[] f = (double[]) m.invoke(null, x, x.length, 48000, 240); java.util.ArrayList<Double> v = new java.util.ArrayList<>(); for (double q : f) if (q > 0) v.add(q); java.util.Collections.sort(v); return v.isEmpty() ? 0 : v.get(v.size() / 2); }
  static JSONObject op(File in, String op, JSONObject extra) throws Exception {
    JSONObject a = extra == null ? new JSONObject() : extra; a.put("path", in.getAbsolutePath()).put("op", op);
    long t0 = System.currentTimeMillis(); JSONObject r = Workspace.op(c, a, P, new AtomicBoolean()); r.put("_ms", System.currentTimeMillis() - t0); return r; }
  public static void main(String[] a) throws Exception {
    android.content.Context.FILES = new File(a[0], "files"); android.content.Context.CACHE = new File(a[0], "cache"); android.content.Context.ASSETS = new File(a[1]);
    File dir = new File(a[0], "dsp"); dir.mkdirs();
    float[][] sp = read(new File(a[0], "dialog.wav"));
    File speech = write(new File(dir, "speech.wav"), sp);
    float[][] st = { sp[0].clone(), sp[0].clone() }; for (int i = 0; i < st[1].length; i++) st[1][i] *= 0.7f;
    File stereo = write(new File(dir, "stereo.wav"), st);
    System.out.println("input " + stat(sp) + " | f0 " + Math.round(medF0(sp[0])) + " Hz");
    // 2.7 clarify
    for (double s : new double[] { 0.6, 1, 1.4 }) {
      JSONObject r = op(speech, "clarify", new JSONObject().put("strength", s)); float[][] y = read(new File(r.getString("path")));
      System.out.printf("clarify %.1f: report %s | %s | 200-400 Hz %+.1f dB, 2-4 kHz %+.1f dB, 6-10 kHz %+.1f dB | %d ms%n", s, r.optJSONObject("report"), stat(y),
        band(y[0], 200, 400) - band(sp[0], 200, 400), band(y[0], 2000, 4000) - band(sp[0], 2000, 4000), band(y[0], 6300, 10000) - band(sp[0], 6300, 10000), r.getLong("_ms"));
    }
    // 2.7 voice effects
    for (String v : new String[] { "radio", "phone", "megaphone", "robot", "high", "deep" }) {
      JSONObject r = op(stereo, "voicefx", new JSONObject().put("preset", v)); float[][] y = read(new File(r.getString("path")));
      System.out.printf("voicefx %-9s %s | <250 Hz %+.1f dB, >6 kHz %+.1f dB, f0 %d Hz | %d ms%n", v, stat(y), band(y[0], 20, 250) - band(st[0], 20, 250), band(y[0], 6000, 20000) - band(st[0], 6000, 20000), Math.round(medF0(y[0])), r.getLong("_ms"));
    }
    // 3.0 spectrum + painted removal: a 2.4 kHz whistle for 1 s in the speech
    float[][] wh = { sp[0].clone() }; for (int i = 48000 * 5; i < 48000 * 6; i++) wh[0][i] += 0.15f * Math.sin(2 * Math.PI * 2400 * i / 48000.0);
    File whf = write(new File(dir, "whistle.wav"), wh);
    JSONObject sv = Workspace.spectro(new JSONObject().put("path", whf.getAbsolutePath()).put("t0", 0).put("t1", 20).put("rows", 160).put("cols", 600));
    System.out.println("spectro rows " + sv.getInt("rows") + " cols " + sv.getInt("cols") + " bytes " + java.util.Base64.getDecoder().decode(sv.getString("img")).length + " fmax " + sv.get("fmax"));
    for (String mode : new String[] { "blend", "mute" }) {
      JSONObject r = op(whf, "spaint", new JSONObject().put("mode", mode).put("rects", new JSONArray().put(new JSONArray().put(4.95).put(6.05).put(2200).put(2600))));
      float[][] y = read(new File(r.getString("path")));
      float[] seg0 = java.util.Arrays.copyOfRange(sp[0], 48000 * 5, 48000 * 6), segW = java.util.Arrays.copyOfRange(wh[0], 48000 * 5, 48000 * 6), segY = java.util.Arrays.copyOfRange(y[0], 48000 * 5, 48000 * 6);
      double outside = 0; for (int i = 0; i < 48000 * 4; i++) outside = Math.max(outside, Math.abs(y[0][i] - wh[0][i]));
      System.out.printf("spaint %-5s 2.3-2.5 kHz: clean %.1f, whistle %.1f, after %.1f dB | 300-2000 Hz before %.1f after %.1f | max change before 4 s %.5f | %d ms%n", mode,
        band(seg0, 2300, 2500), band(segW, 2300, 2500), band(segY, 2300, 2500), band(segW, 300, 2000), band(segY, 300, 2000), outside, r.getLong("_ms"));
    }
    // 3.3 autotune on a "sung" melody slightly off pitch (A minor: A C D E), with vibrato and a breathy noise floor
    int n = 48000 * 12; float[][] mel = new float[1][n]; double[] notes = { 57, 60, 62, 64, 62, 60 }; double ph = 0; java.util.Random rnd = new java.util.Random(1);
    for (int i = 0; i < n; i++) { int k = i / (2 * 48000); double off = (k % 2 == 0 ? 0.3 : -0.35); double f = 440 * Math.pow(2, (notes[k] + off - 69 + 0.08 * Math.sin(2 * Math.PI * 5 * i / 48000.0)) / 12);
      ph += 2 * Math.PI * f / 48000; double s = 0; for (int h = 1; h < 15; h++) s += Math.sin(h * ph) / h * Math.exp(-h * 0.2); double env = Math.min(1, (i % 96000) / 2000.0) * Math.min(1, (96000 - i % 96000) / 4000.0);
      mel[0][i] = (float) (0.25 * s * env + 0.003 * rnd.nextGaussian()); }
    File melf = write(new File(dir, "melody.wav"), mel);
    for (String[] cfg : new String[][] { { "natural", null }, { "strong", null }, { "robot", null }, { "strong", "9:minor" }, { "strong", "0:chromatic" } }) {
      JSONObject x = new JSONObject().put("style", cfg[0]); if (cfg[1] != null) { x.put("key", Integer.parseInt(cfg[1].split(":")[0])); x.put("scale", cfg[1].split(":")[1]); }
      JSONObject r = op(melf, "autotune", x); float[][] y = read(new File(r.getString("path")));
      StringBuilder sb = new StringBuilder();
      double err = 0; for (int k = 0; k < 6; k++) { float[] seg = java.util.Arrays.copyOfRange(y[0], k * 96000 + 24000, k * 96000 + 72000); double f = medF0(seg); double cents = 1200 * Math.log(f / (440 * Math.pow(2, (notes[k] - 69) / 12))) / Math.log(2); err += Math.abs(cents); sb.append(String.format(" %+.0f", cents)); }
      System.out.printf("autotune %-8s key %s -> report %s | %s | cents off per note:%s (mean %.0f; input ±30-35) | %d ms%n", cfg[0], cfg[1] == null ? "auto" : cfg[1], r.optJSONObject("report"), stat(y), sb, err / 6, r.getLong("_ms"));
    }
    JSONObject r = op(speech, "autotune", new JSONObject().put("style", "natural")); System.out.println("autotune on speech -> " + r.optJSONObject("report") + " | " + stat(read(new File(r.getString("path")))) + " | " + r.getLong("_ms") + " ms");
  }
}
