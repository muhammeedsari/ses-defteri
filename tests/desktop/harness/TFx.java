package app.sesdefteri;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import app.sesdefteri.dsp.*;

/** 5.21 (run with args: runDir audioDir android/assets [ws|tl|ai|an]; cwd android/assets for the VAD model). Every tool / effect / preset / mastering style with the arguments the page sends, on real test recordings; measures the output */
public class TFx {
  static android.content.Context c = new android.content.Context();
  static AudioTools.Progress P = new AudioTools.Progress() { public void on(double f) { } };
  static File A; static int pass, warn, fail; static List<String> bad = new ArrayList<>();
  static PrintStream out = System.out;

  static float[][] read(File f) throws Exception { Workspace.Info i = Workspace.info(f); RandomAccessFile r = new RandomAccessFile(f, "r"); try { return Workspace.readRegion(r, i, 0, (int) i.frames); } finally { r.close(); } }
  static int rate(File f) throws Exception { return Workspace.info(f).rate; }
  static double rms(float[][] y) { double e = 0; long n = 0; for (float[] q : y) { for (float v : q) e += v * v; n += q.length; } return 10 * Math.log10(e / Math.max(1, n) + 1e-12); }
  static double peak(float[][] y) { double p = 0; for (float[] q : y) for (float v : q) p = Math.max(p, Math.abs(v)); return p; }
  static int clipped(float[][] y) { int k = 0; for (float[] q : y) for (float v : q) if (Math.abs(v) >= 0.999f) k++; return k; }
  static boolean finite(float[][] y) { for (float[] q : y) for (float v : q) if (Float.isNaN(v) || Float.isInfinite(v)) return false; return true; }
  static double lufs(float[][] y, int fs) { Loudness L = new Loudness(y.length, fs); L.measure(y, y[0].length); return L.integrated(); }
  static double tp(float[][] y, int fs) { return TruePeak.db(TruePeak.measure(y)); }
  static double diff(float[][] a, float[][] b) { int n = Math.min(a[0].length, b[0].length); double e = 0, s = 0; for (int c = 0; c < Math.min(a.length, b.length); c++) for (int i = 0; i < n; i++) { double d = a[c][i] - b[c][i]; e += d * d; s += a[c][i] * a[c][i]; } return 10 * Math.log10(e / (s + 1e-12) + 1e-12); }
  /** longest run of near-digital silence in the middle of the output (s) — dropouts */
  static double gap(float[][] y, int fs) { int n = y[0].length, last = n - 1; while (last > 0) { float m = 0; for (float[] q : y) m = Math.max(m, Math.abs(q[last])); if (m >= 1e-5) break; last--; } int run = 0, best = 0; for (int i = 0; i <= last; i++) { float m = 0; for (float[] q : y) m = Math.max(m, Math.abs(q[i])); if (m < 1e-5) { run++; best = Math.max(best, run); } else run = 0; } return best / (double) fs; }

  static File f(String n) { return new File(A, n + ".wav"); }
  static JSONObject op(File in, String json) throws Exception {
    JSONObject a = new JSONObject(json); a.put("path", in.getAbsolutePath());
    return Workspace.op(c, a, P, new AtomicBoolean());
  }

  interface Check { String run(float[][] x, float[][] y, int fs, JSONObject r) throws Exception; }
  static final Check ANY = (x, y, fs, r) -> "";

  static void t(String name, String in, String json, double lenRatio, boolean mustChange, Check ck) { t(name, f(in), json, lenRatio, mustChange, ck); }
  static void t(String name, File in, String json, double lenRatio, boolean mustChange, Check ck) {
    long t0 = System.currentTimeMillis();
    try {
      JSONObject r = op(in, json);
      long ms = System.currentTimeMillis() - t0;
      float[][] x = read(in);
      int fs = rate(in);
      if (!r.has("path") || r.optBoolean("same")) { report(name, mustChange ? "W:no change (same) report=" + r.opt("report") : "", "same | " + ms + " ms"); return; }
      File o = new File(r.getString("path"));
      float[][] y = read(o);
      int fo = rate(o);
      List<String> w = new ArrayList<>();
      if (!finite(y)) w.add("F:NaN/Inf in output");
      double pk = peak(y); int cl = clipped(y);
      if (pk > 1.0001) w.add("F:peak " + String.format("%.2f", pk));
      if (cl > 10 && clipped(x) < cl / 2) w.add("W:" + cl + " clipped samples (input " + clipped(x) + ")");
      double ry = rms(y), rx = rms(x);
      if (ry < -65) w.add("F:output nearly silent " + String.format("%.1f dB", ry));
      double lr = y[0].length / (double) fo / (x[0].length / (double) fs);
      if (lenRatio > 0 && Math.abs(lr - lenRatio) > 0.05 * lenRatio + 0.02) w.add("F:length ratio " + String.format("%.3f want %.2f", lr, lenRatio));
      if (ry - rx > 15) w.add("W:much louder " + String.format("%+.1f dB", ry - rx));
      if (ry - rx < -15) w.add("W:much quieter " + String.format("%+.1f dB", ry - rx));
      double g = gap(y, fo); double gx = gap(x, fs);
      if (g > Math.max(0.25, gx + 0.2)) w.add("W:dropout " + String.format("%.2fs", g) + " (input " + String.format("%.2fs", gx) + ")");
      double d = fo == fs && y[0].length == x[0].length ? diff(x, y) : 0;
      if (mustChange && fo == fs && y[0].length == x[0].length && d < -60) w.add("W:barely changed (" + String.format("%.0f dB", d) + ")");
      String extra = ck.run(x, y, fs, r);
      if (extra != null && !extra.isEmpty()) w.add(extra);
      String info = String.format("%dch %.2fs pk %.2f rms %.1f→%.1f lufs %.1f tp %.1f diff %.0f | %d ms", y.length, y[0].length / (double) fo, pk, rx, ry, lufs(y, fo), tp(y, fo), d, ms) + (r.has("report") ? " | " + r.get("report") : "");
      String why = String.join("; ", w);
      report(name, why, info);
    } catch (Throwable e) { report(name, "F:exception " + e, ""); e.printStackTrace(out); }
  }
  static void report(String name, String why, String info) {
    boolean f = why.contains("F:"), wa = !f && why.contains("W:");
    if (f) { fail++; bad.add("FAIL " + name + ": " + why); } else if (wa) { warn++; bad.add("WARN " + name + ": " + why); } else pass++;
    out.println((f ? "FAIL " : wa ? "WARN " : "PASS ") + name + (why.isEmpty() ? "" : "  [" + why + "]") + "  " + info);
  }
  static String q(String s) { return s.replace('\'', '"'); }

  /** Timeline render of one clip with an effect list / mastering / clip settings */
  static void tl(String name, String in, String clipExtra, String master, Check ck) {
    try {
      File src = f(in); Workspace.Info inf = Workspace.info(src);
      JSONObject clip = new JSONObject(q("{'at':0,'in':0,'g':1,'fi':0,'fo':0,'xi':0,'xo':0}")).put("path", src.getAbsolutePath()).put("len", inf.frames / (double) inf.rate);
      if (clipExtra != null) { JSONObject e = new JSONObject(q(clipExtra)); for (Iterator<String> it = e.keys(); it.hasNext();) { String k = it.next(); clip.put(k, e.get(k)); } }
      JSONArray clips = new JSONArray().put(clip);
      if (master != null) clips.put(new JSONObject().put("master", new JSONObject(q(master))));
      long t0 = System.currentTimeMillis();
      JSONObject r = Timeline.render(c, new JSONObject().put("clips", clips).put("limit", true), P, new AtomicBoolean());
      long ms = System.currentTimeMillis() - t0;
      float[][] x = read(src), y = read(new File(r.getString("path")));
      List<String> w = new ArrayList<>();
      if (!finite(y)) w.add("F:NaN");
      double pk = peak(y); int cl = clipped(y);
      if (pk > 1.0001) w.add("F:peak " + String.format("%.2f", pk) + " after export limiter");
      if (cl > 10 && clipped(x) < cl / 2) w.add("W:" + cl + " clipped samples");
      double ry = rms(y), rx = rms(x);
      if (ry < -65) w.add("F:silent");
      if (ry - rx > 12) w.add("W:louder " + String.format("%+.1f dB", ry - rx));
      double g = gap(y, 48000), gx = gap(x, inf.rate);
      if (g > Math.max(0.25, gx + 0.2)) w.add("W:dropout " + String.format("%.2fs", g));
      String e2 = ck.run(x, y, 48000, r); if (e2 != null && !e2.isEmpty()) w.add(e2);
      report("timeline " + name, String.join("; ", w), String.format("%dch %.2fs pk %.2f rms %.1f→%.1f lufs %.1f tp %.1f | %d ms", y.length, y[0].length / 48000.0, pk, rx, ry, lufs(y, 48000), tp(y, 48000), ms));
    } catch (Throwable e) { report("timeline " + name, "F:exception " + e, ""); e.printStackTrace(out); }
  }

  public static void main(String[] args) throws Exception {
    File run = new File(args[0]); A = new File(args[1]);
    android.content.Context.FILES = new File(run, "files"); android.content.Context.CACHE = new File(run, "cache"); android.content.Context.ASSETS = new File(args[2]);
    new File(run, "files").mkdirs(); new File(run, "cache").mkdirs();
    String only = args.length > 3 ? args[3] : "";

    if (only.isEmpty() || only.contains("ws")) {
    // ---- Onar / Temizle group
    for (double s : new double[] { 0.6, 1, 1.4 }) t("denoise auto str " + s, "noisy", q("{'op':'denoise','strength':" + s + ",'direct':true}"), 1, true, ANY);
    t("denoise learn red12 sens6", "noisy", q("{'op':'denoise','strength':1,'learn':[0,1],'red':12,'sens':6,'direct':true}"), 1, true, ANY);
    t("denoise learn red40 sens24 (max)", "noisy", q("{'op':'denoise','learn':[0,1],'red':40,'sens':24}"), 1, true, ANY);
    t("denoise learn red3 sens0 (min)", "noisy", q("{'op':'denoise','learn':[0,1],'red':3,'sens':0}"), 1, true, ANY);
    t("denoise on clean speech", "speech", q("{'op':'denoise','strength':1}"), 1, false, ANY);
    t("denoise on music", "music", q("{'op':'denoise','strength':1}"), 1, false, ANY);
    t("musicdenoise", "music", q("{'op':'musicdenoise','strength':1}"), 1, false, ANY);
    t("musicdenoise str 1.4 on noisy speech", "noisy", q("{'op':'musicdenoise','strength':1.4}"), 1, true, ANY);
    for (double s : new double[] { 0.6, 1, 1.4 }) t("gate str " + s, "noisy", q("{'op':'gate','strength':" + s + "}"), 1, true, ANY);
    for (double s : new double[] { 0.6, 1, 1.4 }) t("dereverb str " + s, "echo", q("{'op':'dereverb','strength':" + s + "}"), 1, true, ANY);
    t("dereverb on music", "music", q("{'op':'dereverb','strength':1}"), 1, false, ANY);
    t("hum (noisy has 50 Hz)", "noisy", q("{'op':'hum'}"), 1, true, ANY);
    t("hum on clean speech", "speech", q("{'op':'hum'}"), 1, false, ANY);
    t("clicks", "fillers", q("{'op':'clicks'}"), 1, false, ANY);
    t("mouth", "fillers", q("{'op':'mouth'}"), 1, false, ANY);
    t("mouth on music", "music", q("{'op':'mouth'}"), 1, false, ANY);
    t("plosive", "speech", q("{'op':'plosive'}"), 1, false, ANY);
    for (double s : new double[] { 0.6, 1, 1.4 }) t("deess str " + s, "speech", q("{'op':'deess','strength':" + s + "}"), 1, false, ANY);
    t("declip", "clipped", q("{'op':'declip'}"), 1, true, (x, y, fs, r) -> clipped(y) >= clipped(x) ? "W:still " + clipped(y) + " clipped (input " + clipped(x) + ")" : "");
    t("declip on clean", "speech", q("{'op':'declip'}"), 1, false, ANY);
    t("breaths", "fillers", q("{'op':'breaths'}"), 1, false, ANY);
    for (double s : new double[] { 0.6, 1, 1.4 }) t("clarify str " + s + " muffled", "muffled", q("{'op':'clarify','strength':" + s + "}"), 1, true, ANY);
    t("clarify phone 8 kHz", "phone", q("{'op':'clarify','strength':1}"), 1, true, ANY);
    t("clarify music", "music", q("{'op':'clarify','strength':1}"), 1, false, ANY);
    t("channels fix (left only)", "leftonly", q("{'op':'channels'}"), 1, true, (x, y, fs, r) -> { if (y.length < 2) return ""; double l = 0, rr = 0; for (int i = 0; i < y[0].length; i++) { l += y[0][i] * y[0][i]; rr += y[1][i] * y[1][i]; } return rr < l * 0.25 ? "W:right channel still empty" : ""; });
    t("channels on normal stereo", "music", q("{'op':'channels'}"), 1, false, ANY);
    t("mono to stereo", "speech", q("{'op':'stereo'}"), 1, false, (x, y, fs, r) -> y.length == 2 ? "" : "F:not stereo");
    t("auto eq", "muffled", q("{'op':'eq'}"), 1, true, ANY);
    t("auto eq music", "music", q("{'op':'eq'}"), 1, false, ANY);
    t("level", "uneven", q("{'op':'level'}"), 1, true, ANY);
    t("level dialog", "dialog", q("{'op':'level'}"), 1, false, ANY);
    for (String tg : new String[] { "youtube", "podcast", "whatsapp" }) for (String in : new String[] { "speech", "music", "clipped", "phone" }) {
      final double want = tg.equals("youtube") ? -14 : -16;
      t("loud " + tg + " " + in, in, q("{'op':'loud','target':'" + tg + "'}"), 1, false, (x, y, fs, r) -> { double l = lufs(y, fs), p = tp(y, fs); String s = ""; if (Math.abs(l - want) > 2) s += "W:LUFS " + String.format("%.1f want %.0f", l, want); if (p > -0.5) s += (s.isEmpty() ? "" : "; ") + "W:true peak " + String.format("%.1f dBTP", p); return s; });
    }
    // ---- Ses-Ton / Efekt group (wsOp ones)
    for (String v : new String[] { "radio", "phone", "megaphone", "robot", "high", "deep" }) t("voicefx " + v, "speech", q("{'op':'voicefx','preset':'" + v + "'}"), 1, true, ANY);
    t("voicefx robot on music", "music", q("{'op':'voicefx','preset':'robot'}"), 1, true, ANY);
    for (String s : new String[] { "natural", "strong", "robot" }) t("autotune " + s + " (song)", "song07", q("{'op':'autotune','style':'" + s + "','strength':1}"), 1, true, ANY);
    t("autotune natural on speech", "speech", q("{'op':'autotune','style':'natural'}"), 1, false, ANY);
    for (double sp : new double[] { 0.5, 0.75, 1.5, 2 }) t("speed " + sp, "speech", q("{'op':'speed','speed':" + sp + ",'semitones':0,'formant':true}"), 1 / sp, true, ANY);
    for (int st : new int[] { -12, -4, 3, 12 }) t("pitch " + st + " formant", "speech", q("{'op':'speed','speed':1,'semitones':" + st + ",'formant':true}"), 1, true, ANY);
    t("speed 2 + pitch -12 no formant", "speech", q("{'op':'speed','speed':2,'semitones':-12,'formant':false}"), 0.5, true, ANY);
    for (int st : new int[] { -36, -12, 7, 36 }) t("vchange pitch " + st, "speech", q("{'op':'vchange','semitones':" + st + ",'formant':true}"), 1, true, ANY);
    t("vchange music +5", "music", q("{'op':'vchange','semitones':5,'formant':true}"), 1, true, ANY);
    for (String[] vc : new String[][] { { "female", "4", "1" }, { "male", "-4", "1" }, { "cartoon", "7", "1.1" }, { "girl", "6", "1.05" }, { "oldman", "-3", "0.9" } })
      t("voice character " + vc[0], "speech", q("{'op':'vchange','semitones':" + vc[1] + ",'speed':" + vc[2] + ",'formant':false}"), 1 / Double.parseDouble(vc[2]), true, ANY);
    t("voice character robot", "speech", q("{'op':'vchange','semitones':0,'speed':1,'formant':false,'preset':'robot'}"), 1, true, ANY);
    t("changer extremes vst12 tempo2 speed2", "speech", q("{'op':'vchange','semitones':24,'speed':4,'formant':false}"), 0.25, true, ANY);
    t("changer extremes vst-12 tempo0.5 speed0.5", "speech", q("{'op':'vchange','semitones':-24,'speed':0.25,'formant':false}"), 4, true, ANY);
    for (String[] rp : new String[][] { { "warm", "30", "70", "35", "1" }, { "wide", "45", "40", "80", "0" }, { "compact", "20", "50", "15", "1" }, { "fantasy", "70", "20", "100", "-1" }, { "ambient", "55", "60", "65", "0" }, { "max", "100", "100", "100", "12" }, { "min", "0", "0", "0", "-12" } })
      t("room preset " + rp[0], "speech", q("{'op':'vchange','rev':{'room':" + Integer.parseInt(rp[3]) / 100.0 + ",'mix':" + Integer.parseInt(rp[1]) / 100.0 + ",'soft':" + Integer.parseInt(rp[2]) / 100.0 + "},'gain':" + rp[4] + "}"), -1, !rp[0].equals("min"), ANY);
    t("harmony +4 (duet)", "speech", q("{'op':'vchange','semitones':4,'formant':true}"), 1, true, ANY);
    t("reverb", "speech", q("{'op':'reverb','room':0.6,'mix':0.3}"), -1, true, ANY);
    t("reverb room1 mix1", "speech", q("{'op':'reverb','room':1,'mix':1}"), -1, true, ANY);
    t("delay", "speech", q("{'op':'delay','ms':250,'fb':0.3,'mix':0.3}"), -1, true, ANY);
    t("delay 1000ms fb0.9", "speech", q("{'op':'delay','ms':1000,'fb':0.9,'mix':1}"), -1, true, ANY);
    t("comp", "speech", q("{'op':'comp','thr':-30,'ratio':4}"), 1, true, ANY);
    t("widen", "speech", q("{'op':'widen'}"), 1, true, ANY);
    t("double", "speech", q("{'op':'double'}"), -1, true, ANY);
    t("lofi", "music", q("{'op':'lofi'}"), -1, true, ANY);
    for (int p : new int[] { 4, 8, 12 }) t("8D period " + p, "music", q("{'op':'eightd','period':" + p + "}"), -1, true, ANY);
    t("reverse", "speech", q("{'op':'reverse'}"), 1, true, ANY);
    // EQ presets: the dialog's own presets plus extremes
    for (String g : new String[] { "[3,-1,0,2,1]", "[12,12,12,12,12]", "[-12,-12,-12,-12,-12]", "[12,0,0,0,12]", "[-12,0,4,4,-12]" }) t("eqpreset " + g, "music", q("{'op':'eqpreset','gains':" + g + "}"), 1, true, ANY);
    // ---- Kırp group
    t("trim", "fillers", q("{'op':'trim'}"), -1, false, ANY);
    for (String[] pp : new String[][] { { "0.5", "0.3" }, { "0.8", "0.45" }, { "1.2", "0.7" }, { "2", "0.6" } }) t("pauses max " + pp[0], "fillers", q("{'op':'pauses','max':" + pp[0] + ",'keep':" + pp[1] + "}"), -1, false, (x, y, fs, r) -> y[0].length > x[0].length ? "F:longer than input" : "");
    t("cut keep 2 parts", "speech", q("{'op':'cut','keep':[[1000,5000],[10000,12000]]}"), 0.3, true, ANY);
    t("cut keep reversed/overlapping ranges", "speech", q("{'op':'cut','keep':[[10000,12000],[1000,5000],[4000,6000]]}"), -1, true, (x, y, fs, r) -> "");
    // ---- Studio voice and ready-made chains
    for (String in : new String[] { "noisy", "speech", "echo", "phone", "music" }) t("studio voice " + in, in, q("{'op':'studio','strength':1,'amb':5,'target':'podcast','ai':false}"), 1, false, (x, y, fs, r) -> { double l = lufs(y, fs); return Math.abs(l + 16) > 2.5 ? "W:LUFS " + String.format("%.1f", l) : ""; });
    t("studio voice str 1.4 amb 40", "noisy", q("{'op':'studio','strength':1.4,'amb':40,'target':'youtube','ai':false}"), 1, true, ANY);
    t("studio voice str 0.6 amb 0", "noisy", q("{'op':'studio','strength':0.6,'amb':0,'target':'podcast','ai':false}"), 1, true, ANY);
    String presets = new String(java.nio.file.Files.readAllBytes(new File(args[2], "../../web/fx-presets.js").toPath()), "UTF-8");
    String ch = presets.substring(presets.indexOf("chains:") + 7, presets.lastIndexOf("]") + 1);
    ch = ch.replaceAll("(\\w+):", "\"$1\":").replace('\'', '"');
    JSONArray chains = new JSONArray(ch);
    for (int i = 0; i < chains.length(); i++) {
      JSONObject cc = chains.getJSONObject(i);
      for (String in : new String[] { "speech", "music" }) t("preset " + cc.getString("id") + " " + in, in, new JSONObject().put("op", "chain").put("steps", cc.getJSONArray("steps")).toString(), -1, true, ANY);
    }
    }

    if (only.isEmpty() || only.contains("tl")) {
    // ---- live effect list (every slider at default, min and max)
    String[][] rack = { { "reverb", "{'mix':30,'room':50,'soft':30}", "{'mix':100,'room':100,'soft':100}", "{'mix':0,'room':0,'soft':0}" },
      { "delay", "{'mix':35,'ms':300,'fb':35}", "{'mix':100,'ms':1000,'fb':90}", "{'mix':0,'ms':50,'fb':0}" },
      { "comp", "{'amt':50}", "{'amt':100}", "{'amt':0}" }, { "widen", "{'amt':60}", "{'amt':100}", "{'amt':0}" },
      { "eightd", "{'period':8}", "{'period':20}", "{'period':2}" }, { "double", "{'mix':70}", "{'mix':100}", "{'mix':0}" }, { "lofi", "{'mix':80}", "{'mix':100}", "{'mix':0}" } };
    String[] lbl = { "default", "max", "min" };
    for (String[] rk : rack) for (int k = 1; k <= 3; k++) for (String in : new String[] { "speech", "music" })
      tl(rk[0] + " " + lbl[k - 1] + " " + in, in, "{'fx':[{'k':'" + rk[0] + "','on':true,'v':" + rk[k] + "}]}", null, ANY);
    tl("all 7 effects stacked max", "music", "{'fx':[" + Arrays.stream(rack).map(r -> "{'k':'" + r[0] + "','on':true,'v':" + r[2] + "}").reduce((a, b) -> a + "," + b).get() + "]}", null, ANY);
    tl("effect switched off", "speech", "{'fx':[{'k':'reverb','on':false,'v':{'mix':100,'room':100,'soft':100}}]}", null, (x, y, fs, r) -> Math.abs(rms(y) - rms(x)) > 0.5 ? "W:off effect still changes level" : "");
    // mastering
    for (String st : new String[] { "natural", "warm", "tape", "loud" }) for (int amt : new int[] { 0, 50, 100 }) for (String in : new String[] { "speech", "music", "clipped" })
      tl("master " + st + " " + amt + " " + in, in, null, "{'on':true,'style':'" + st + "','amt':" + amt + "}", (x, y, fs, r) -> peak(y) > 1.0001 ? "W:master lets peaks over 0 dBFS" : "");
    // clip settings
    tl("gain x4 (+12 dB)", "speech", "{'g':4}", null, ANY);
    tl("pan -100", "speech", "{'pan':-100}", null, (x, y, fs, r) -> { if (y.length < 2) return "F:mono"; return rms(new float[][] { y[1] }) > -60 ? "W:right not silent" : ""; });
    tl("pan +100", "music", "{'pan':100}", null, ANY);
    tl("fade in 3 out 3", "speech", "{'fi':3,'fo':3}", null, (x, y, fs, r) -> Math.abs(y[0][10]) > 0.01 ? "W:start not faded" : "");
    tl("volume line", "speech", "{'env':[[0,0],[5,-30],[10,6],[15,0]]}", null, ANY);
    tl("clip eq +12 all", "music", "{'eq':[12,12,12,12,12]}", null, ANY);
    tl("bass +18 treble +18", "music", "{'bass':18,'treble':18}", null, ANY);
    tl("bass -12 treble -12", "music", "{'bass':-12,'treble':-12}", null, ANY);
    tl("bass +18 on speech", "speech", "{'bass':18}", null, ANY);
    }
    if (only.isEmpty() || only.contains("ai")) {
    // ---- AI tools (native sherpa-onnx / ONNX Runtime built for this computer; same C code as the phone)
    for (double s : new double[] { 0.6, 1, 1.4 }) t("AI denoise (DPDFNet) str " + s, "noisy", q("{'op':'aidenoise','strength':" + s + ",'direct':true}"), 1, true, ANY);
    t("AI denoise on clean speech", "speech", q("{'op':'aidenoise','strength':1}"), 1, false, ANY);
    t("AI denoise on music", "music", q("{'op':'aidenoise','strength':1}"), 1, false, ANY);
    t("AI denoise amb 40", "noisy", q("{'op':'aidenoise','strength':1,'amb':0.4}"), 1, true, ANY);
    t("Spleeter voice only (sepdenoise)", "noisy", q("{'op':'sepdenoise'}"), 1, true, ANY);
    t("Spleeter voice only on song", "song", q("{'op':'sepdenoise'}"), 1, true, ANY);
    t("Spleeter voice only on clean speech", "speech", q("{'op':'sepdenoise'}"), 1, false, ANY);
    for (String in : new String[] { "noisy", "speech", "echo" }) t("studio voice AI " + in, in, q("{'op':'studio','strength':1,'amb':5,'target':'podcast','ai':true}"), 1, false, (x, y, fs, r) -> { double l = lufs(y, fs); return Math.abs(l + 16) > 2.5 ? "W:LUFS " + String.format("%.1f", l) : ""; });
    for (String in : new String[] { "speech", "noisy", "music", "fillers", "dialog" }) {
      try {
        JSONObject v = Speakers.vad(c, new JSONObject().put("path", f(in).getAbsolutePath()).put("minSil", 0.3), P, new AtomicBoolean());
        JSONArray sg = v.getJSONArray("segs"); double tot = 0; for (int i = 0; i < sg.length(); i++) tot += sg.getJSONArray(i).getDouble(1) - sg.getJSONArray(i).getDouble(0);
        report("speech detector " + in, in.equals("music") && tot > 6 ? "W:music heard as speech " + String.format("%.1fs", tot) : !in.equals("music") && tot < 3 ? "W:little speech found " + tot : "", sg.length() + " stretches, " + String.format("%.1f s speech", tot));
        if (in.equals("music")) continue;
        for (int rest : new int[] { -12, -20, -35 }) {
          JSONArray map = new JSONArray(); for (int i = 0; i < sg.length(); i++) { JSONArray z = sg.getJSONArray(i); map.put(new JSONArray().put(Math.max(0, z.getDouble(0) - 0.15)).put(z.getDouble(1) + 0.1).put(0)); }
          t("smart silence " + rest + " " + in, in, new JSONObject().put("op", "aigate").put("map", map).put("rest", rest).put("ramp", 0.025).toString(), 1, false, ANY);
        }
        JSONObject v2 = Speakers.vad(c, new JSONObject().put("path", f(in).getAbsolutePath()).put("minSil", 0.25), P, new AtomicBoolean());
        t("smart pauses " + in, in, new JSONObject().put("op", "aipauses").put("talk", v2.getJSONArray("segs")).put("max", 0.8).put("keep", 0.45).toString(), -1, false, (x, y, fs, r) -> y[0].length > x[0].length + 10 ? "F:longer" : "");
      } catch (Throwable e) { report("speech detector " + in, "F:exception " + e, ""); e.printStackTrace(out); }
    }
    for (String[] sp : new String[][] { { "2 parts", "{}" }, { "4 parts", "{'four':true}" } }) try {
      long t0 = System.currentTimeMillis();
      JSONObject r = Separator.split(c, new JSONObject(q(sp[1])).put("path", f("song").getAbsolutePath()), P, new AtomicBoolean());
      StringBuilder b = new StringBuilder(); String w = "";
      for (Iterator<String> it = r.keys(); it.hasNext();) { String k = it.next(); JSONObject o = r.optJSONObject(k); if (o == null || !o.has("path")) continue; float[][] y = read(new File(o.getString("path"))); b.append(k).append(String.format(" rms %.1f pk %.2f; ", rms(y), peak(y))); if (!finite(y)) w = "F:NaN in " + k; if (rms(y) < -70) w += " W:" + k + " empty"; }
      report("separate " + sp[0], w, b + " | " + (System.currentTimeMillis() - t0) + " ms");
    } catch (Throwable e) { report("separate " + sp[0], "F:exception " + e, ""); e.printStackTrace(out); }
    try { Hd.download(c, P, new AtomicBoolean()); report("download HD voice model", "", ""); } catch (Throwable e) { report("download HD voice model", "F:exception " + e, ""); e.printStackTrace(out); }
    t("HD voice on phone 8 kHz", "phone", q("{'op':'hd'}"), 1, true, ANY);
    t("HD voice on full-band speech", "speech", q("{'op':'hd'}"), 1, false, ANY);
    try { Speakers.download(c, P, new AtomicBoolean()); report("download speaker model", "", ""); } catch (Throwable e) { report("download speaker model", "F:exception " + e, ""); e.printStackTrace(out); }
    for (String in : new String[] { "dialog", "speech" }) try {
      JSONObject r = Speakers.run(c, new JSONObject().put("path", f(in).getAbsolutePath()).put("in", 0).put("out", 20).put("n", 0), P, new AtomicBoolean());
      report("speakers " + in, in.equals("dialog") && r.optInt("n") != 2 ? "W:found " + r.optInt("n") + " speakers (want 2)" : "", r.toString().substring(0, Math.min(200, r.toString().length())));
    } catch (Throwable e) { report("speakers " + in, "F:exception " + e, ""); e.printStackTrace(out); }
    try { Separator.uvrDownload(c, P, new AtomicBoolean()); JSONObject r = Separator.split(c, new JSONObject().put("hq", true).put("path", f("song").getAbsolutePath()), P, new AtomicBoolean());
      float[][] v = read(new File(r.getJSONObject("voc").getString("path"))); report("separate high quality (UVR)", finite(v) ? "" : "F:NaN", String.format("voc rms %.1f", rms(v))); } catch (Throwable e) { report("separate high quality (UVR)", "F:exception " + e, ""); e.printStackTrace(out); }
    }
    if (only.isEmpty() || only.contains("an")) {
    try { JSONObject k = Studio.musicKey(c, new JSONObject().put("path", f("song07").getAbsolutePath()), P, new AtomicBoolean()); report("key and tempo (song in A minor)", "", k.toString().substring(0, Math.min(200, k.toString().length()))); } catch (Throwable e) { report("key and tempo", "F:exception " + e, ""); e.printStackTrace(out); }
    try { JSONObject k = Studio.beats(c, new JSONObject().put("path", f("music").getAbsolutePath()), P, new AtomicBoolean()); double bpm = k.optDouble("bpm"); report("beats (100 BPM)", Math.abs(bpm - 100) < 3 || Math.abs(bpm - 50) < 2 || Math.abs(bpm - 200) < 4 ? "" : "W:bpm " + bpm, "bpm " + bpm); } catch (Throwable e) { report("beats", "F:exception " + e, ""); e.printStackTrace(out); }
    try { JSONObject k = Studio.songMap(c, new JSONObject().put("path", f("music").getAbsolutePath()), P, new AtomicBoolean()); report("chords and sections", "", k.toString().substring(0, Math.min(200, k.toString().length()))); } catch (Throwable e) { report("chords and sections", "F:exception " + e, ""); e.printStackTrace(out); }
    try { JSONObject k = Workspace.fillers(c, new JSONObject().put("src", new JSONObject().put("path", f("fillers").getAbsolutePath())), P, new AtomicBoolean()); report("um/uh finder (plain)", "", k.toString().substring(0, Math.min(200, k.toString().length()))); } catch (Throwable e) { report("fillers", "F:exception " + e, ""); e.printStackTrace(out); }
    for (String in : new String[] { "noisy", "clipped", "echo", "phone", "leftonly", "music", "speech" }) try { JSONObject k = Workspace.diagnose(c, new JSONObject().put("src", new JSONObject().put("path", f(in).getAbsolutePath())), P, new AtomicBoolean()); String ks = k.toString().replaceAll("\\[[^\\]]{60,}\\]", "[..]"); report("diagnose " + in, "", ks.substring(0, Math.min(400, ks.length()))); } catch (Throwable e) { report("diagnose " + in, "F:exception " + e, ""); e.printStackTrace(out); }
    for (String fm : new String[] { "wav", "mp3", "flac" }) for (String chs : new String[] { "", "mono", "left" }) try {
      JSONObject r = Workspace.export(c, new JSONObject().put("path", f("music").getAbsolutePath()).put("format", fm).put("chans", chs).put("name", "t" + fm + chs), P, new AtomicBoolean());
      String path = r.getJSONObject("src").getString("path");
      Process pr = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "stream=codec_name,channels,sample_rate:format=duration", "-of", "compact", path).redirectErrorStream(true).start();
      String probe = new String(pr.getInputStream().readAllBytes()).replace("\n", " "); pr.waitFor();
      String want = chs.isEmpty() ? "channels=2" : "channels=1";
      report("export " + fm + " " + (chs.isEmpty() ? "stereo" : chs), !probe.contains("duration=") ? "F:file not readable " + probe : !probe.contains(want) ? "W:channels " + probe : "", new File(path).length() + " bytes | " + probe);
    } catch (Throwable e) { report("export " + fm + " " + chs, "F:exception " + e, ""); e.printStackTrace(out); }
    }
    out.println("\nRESULT " + pass + " pass, " + warn + " warn, " + fail + " fail");
    for (String b : bad) out.println(" - " + b);
  }
}
