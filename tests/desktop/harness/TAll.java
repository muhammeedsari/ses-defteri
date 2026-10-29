package app.sesdefteri;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import app.sesdefteri.dsp.*;
/** every editing operation, Clean, AI and export paths on generated test audio (desktop) */
public class TAll {
  static android.content.Context c = new android.content.Context();
  static AudioTools.Progress P = new AudioTools.Progress() { public void on(double f) { } };
  static File D; static int pass, fail; static List<String> bad = new ArrayList<>();
  static float[][] read(File f) throws Exception { Workspace.Info i = Workspace.info(f); RandomAccessFile r = new RandomAccessFile(f, "r"); try { return Workspace.readRegion(r, i, 0, (int) i.frames); } finally { r.close(); } }
  static String stat(float[][] y) { double pk = 0, e = 0; boolean nan = false; for (float[] q : y) for (float v : q) { if (Float.isNaN(v) || Float.isInfinite(v)) nan = true; pk = Math.max(pk, Math.abs(v)); e += v * v; }
    return String.format("%dch %.2fs pk %.2f rms %.1f%s", y.length, y[0].length / 48000.0, pk, 10 * Math.log10(e / y[0].length / y.length + 1e-12), nan ? " NaN!" : ""); }
  static boolean finite(float[][] y) { for (float[] q : y) for (float v : q) if (Float.isNaN(v) || Float.isInfinite(v)) return false; return true; }
  static double rms(float[] x) { double e = 0; for (float v : x) e += v * v; return 10 * Math.log10(e / x.length + 1e-12); }
  static double band(float[] x, double f0, double f1) throws Exception { float[] y = x.clone(); for (int k = 0; k < 4; k++) { Biquad.highpass(1, 48000, f0, 0.707).process(0, y, 0, y.length); Biquad.lowpass(1, 48000, f1, 0.707).process(0, y, 0, y.length); } return rms(y); }
  /** scale-invariant SNR of y against reference r (dB) */
  static double sisnr(float[] y, float[] r) { int n = Math.min(y.length, r.length); double yr = 0, rr = 0; for (int i = 0; i < n; i++) { yr += y[i] * r[i]; rr += r[i] * r[i]; } double a = yr / (rr + 1e-12), s = 0, e = 0; for (int i = 0; i < n; i++) { double t = a * r[i], d = y[i] - t; s += t * t; e += d * d; } return 10 * Math.log10(s / (e + 1e-12)); }
  /** prints the shift (ms) at which y best matches r */
  static void lag(float[] y, float[] r) { int best = 0; double bv = -1e30; for (int d = -9600; d <= 9600; d += 48) { double s = 0; for (int i = 48000 * 5; i < 48000 * 15; i += 4) { int j = i + d; if (j >= 0 && j < y.length) s += y[j] * r[i]; } if (s > bv) { bv = s; best = d; } } System.out.printf("   best lag %.1f ms%n", best / 48.0); }
  static float[] REF;
  /** speech level minus level in the pauses (dB), pauses taken from the clean reference */
  static double seg(float[] y) { int F = 960; double sp = 0, si = 0; int ns = 0, nq = 0; for (int i = 0; i + F <= Math.min(y.length, REF.length); i += F) { double e = 0, ey = 0; for (int j = i; j < i + F; j++) { e += REF[j] * REF[j]; ey += y[j] * y[j]; } double d = 10 * Math.log10(e / F + 1e-12); if (d > -40) { sp += ey; ns++; } else if (d < -70) { si += ey; nq++; } } return 10 * Math.log10((sp / Math.max(1, ns)) / (si / Math.max(1, nq) + 1e-12)); }
  static File f(String n) { return new File(D, n); }
  static JSONObject op(File in, String op, String extra) throws Exception {
    JSONObject a = extra == null ? new JSONObject() : new JSONObject(extra); a.put("path", in.getAbsolutePath()).put("op", op);
    long t0 = System.currentTimeMillis(); JSONObject r = Workspace.op(c, a, P, new AtomicBoolean()); r.put("_ms", System.currentTimeMillis() - t0); return r; }
  interface Check { String run(JSONObject r, float[][] y) throws Exception; }
  /** runs one op; check returns "" when good or the reason it failed */
  static void t(String name, File in, String op, String extra, Check ck) {
    try {
      JSONObject r = op(in, op, extra);
      float[][] y = r.has("path") ? read(new File(r.getString("path"))) : null;
      String why = y != null && !finite(y) ? "NaN in output" : ck.run(r, y);
      report(name, why, (y != null ? stat(y) : "") + " | " + r.optLong("_ms") + " ms" + (r.has("report") ? " | " + r.get("report") : ""));
    } catch (Throwable e) { report(name, "exception " + e, ""); e.printStackTrace(System.out); }
  }
  static void report(String name, String why, String info) {
    if (why == null || why.isEmpty()) { pass++; System.out.println("PASS " + name + "  " + info); } else { fail++; bad.add(name + ": " + why); System.out.println("FAIL " + name + "  " + why + "  " + info); }
  }
  static String need(boolean ok, String msg) { return ok ? "" : msg; }
  public static void main(String[] args) throws Exception {
    File run = new File(args[0]); D = new File(args[1]);
    android.content.Context.FILES = new File(run, "files"); android.content.Context.CACHE = new File(run, "cache"); android.content.Context.ASSETS = new File(args[2]);
    Context_mk(run);
    final float[] ref = read(f("01-konusma-temiz.wav"))[0];
    final double refRms = rms(ref); REF = ref;
    File clean = f("01-konusma-temiz.wav"), noisy = f("03-konusma-gurultulu.wav"), muf = f("04-konusma-boguk.wav"), beep = f("05-konusma-uzerinde-zil.wav"), music = f("06-muzik.wav"),
      song = f("07-sarki-detone.wav"), clip = f("09-kirpilmis.wav"), echo = f("10-yankili.wav");
    final double noisySnr = sisnr(read(noisy)[0], ref), echoSnr = sisnr(read(echo)[0], ref);
    System.out.printf("inputs: clean rms %.1f | noisy SI-SNR %.1f dB | echo SI-SNR %.1f dB%n", refRms, noisySnr, echoSnr);
    // ---- 1.x basics
    t("cut (keep 2 parts)", clean, "cut", "{\"keep\":[[1000,5000],[10000,12000]]}", (r, y) -> need(Math.abs(y[0].length / 48000.0 - 6) < 0.05, "length " + y[0].length / 48000.0));
    t("trim silence ends", clean, "trim", null, (r, y) -> need(y[0].length < ref.length && y[0].length > ref.length * 0.8, "length " + y[0].length / 48000.0));
    t("level (even out loud/quiet parts)", f("02-diyalog-iki-kisi.wav"), "level", null, (r, y) -> "");
    for (String tg : new String[] { "youtube", "podcast", "whatsapp" }) t("loudness " + tg, clean, "loud", "{\"target\":\"" + tg + "\"}", (r, y) -> { Loudness L = new Loudness(1, 48000); L.measure(y, y[0].length); double li = L.integrated(); double want = tg.equals("podcast") ? -16 : tg.equals("youtube") ? -14 : -16; return need(Math.abs(li - want) < 2.5, String.format("LUFS %.1f want ~%.0f", li, want)); });
    t("speed 1.5 (pitch kept)", clean, "speed", "{\"speed\":1.5}", (r, y) -> need(Math.abs(y[0].length / (double) ref.length - 1 / 1.5) < 0.03, "ratio " + y[0].length / (double) ref.length));
    t("pitch +3 semitones", clean, "speed", "{\"speed\":1,\"semitones\":3}", (r, y) -> need(Math.abs(y[0].length / (double) ref.length - 1) < 0.02, "ratio " + y[0].length / (double) ref.length));
    t("channels fix", clean, "channels", null, (r, y) -> "");
    t("mono to stereo", clean, "stereo", null, (r, y) -> need(y.length == 2, "channels " + y.length));
    t("compressor", clean, "comp", "{\"thr\":-30,\"ratio\":4}", (r, y) -> "");
    t("reverb", clean, "reverb", "{\"room\":0.6,\"mix\":0.3}", (r, y) -> "");
    t("delay", clean, "delay", "{\"ms\":250,\"fb\":0.3,\"mix\":0.3}", (r, y) -> "");
    t("EQ preset (+6 dB at 1 kHz band)", clean, "eqpreset", "{\"gains\":[0,0,6,0,0]}", (r, y) -> "");
    t("auto EQ", muf, "eq", null, (r, y) -> String.format("%s", band(y[0], 3000, 8000) > band(read(muf)[0], 3000, 8000) ? "" : "no brightening"));
    // ---- noise & repair
    t("noise reduction", noisy, "denoise", null, (r, y) -> { double s = sisnr(y[0], ref); System.out.printf("   SI-SNR %.1f -> %.1f dB%n", noisySnr, s); return need(s > noisySnr + 3, "only " + s); });
    t("noise reduction (learn from 0-0.4 s)", noisy, "denoise", "{\"learn\":[0,0.4]}", (r, y) -> { double s = sisnr(y[0], ref); System.out.printf("   SI-SNR %.1f -> %.1f dB%n", noisySnr, s); return need(s > noisySnr + 3, "only " + s); });
    t("hum removal", f("11-ugultu.wav"), "hum", null, (r, y) -> { double b0 = band(read(f("11-ugultu.wav"))[0], 45, 55), b1 = band(y[0], 45, 55); System.out.printf("   SI-SNR %.1f -> %.1f dB%n", sisnr(read(f("11-ugultu.wav"))[0], ref), sisnr(y[0], ref)); System.out.printf("   45-55 Hz %.1f -> %.1f dB%n", b0, b1); return need(b1 < b0 - 10, "hum left"); });
    t("noise gate", noisy, "gate", null, (r, y) -> "");
    t("de-reverb", echo, "dereverb", null, (r, y) -> { double s = sisnr(y[0], ref); System.out.printf("   SI-SNR %.1f -> %.1f dB%n", echoSnr, s); return need(s > echoSnr, "worse " + s); });
    t("declip", clip, "declip", null, (r, y) -> { float[] cl = read(clip)[0]; double s0 = sisnr(cl, ref), s1 = sisnr(y[0], ref); System.out.printf("   SI-SNR %.1f -> %.1f dB%n", s0, s1); return need(s1 > s0, "worse"); });
    t("clicks", clean, "clicks", null, (r, y) -> "");
    t("plosives", clean, "plosive", null, (r, y) -> "");
    t("de-ess", clean, "deess", null, (r, y) -> "");
    t("mouth noises", clean, "mouth", null, (r, y) -> "");
    t("breaths", clean, "breaths", null, (r, y) -> "");
    t("shorten pauses", clean, "pauses", "{\"max\":0.5,\"keep\":0.3}", (r, y) -> need(y[0].length < ref.length, "not shorter"));
    // ---- 2.x
    t("AI noise removal (DPDFNet)", noisy, "aidenoise", null, (r, y) -> { System.out.printf("   speech-to-pause %.1f -> %.1f dB%n", seg(read(noisy)[0]), seg(y[0])); double s = sisnr(y[0], ref); System.out.printf("   SI-SNR %.1f -> %.1f dB%n", noisySnr, s); return need(s > noisySnr + 3, "only " + s); });
    t("AI noise removal (Spleeter voice)", noisy, "sepdenoise", null, (r, y) -> { double s0 = seg(read(noisy)[0]), s1 = seg(y[0]); System.out.printf("   speech-to-pause %.1f -> %.1f dB, SI-SNR %.1f%n", s0, s1, sisnr(y[0], ref)); return need(s1 > s0 + 6, "little gain"); });
    t("Spleeter voice on clean speech", clean, "sepdenoise", null, (r, y) -> { double s = sisnr(y[0], ref); System.out.printf("   SI-SNR vs original %.1f dB, level %.1f vs %.1f%n", s, rms(y[0]), refRms); return need(s > 10, "voice damaged " + s); });
    t("clarify (muffled voice)", muf, "clarify", null, (r, y) -> need(band(y[0], 2000, 4000) > band(read(muf)[0], 2000, 4000) + 2, "not clearer"));
    for (String v : new String[] { "radio", "phone", "megaphone", "robot", "high", "deep" }) t("voice " + v, clean, "voicefx", "{\"preset\":\"" + v + "\"}", (r, y) -> "");
    t("spectral erase (box on 4 s beep)", beep, "spaint", "{\"rects\":[[3.9,4.9,2200,2600]],\"mode\":\"blend\"}", (r, y) -> { float[] x = read(beep)[0]; float[] a = Arrays.copyOfRange(x, 4 * 48000, (int) (4.8 * 48000)), b = Arrays.copyOfRange(y[0], 4 * 48000, (int) (4.8 * 48000)); double b0 = band(a, 2300, 2500), b1 = band(b, 2300, 2500); System.out.printf("   beep %.1f -> %.1f dB%n", b0, b1); return need(b1 < b0 - 10, "beep left"); });
    t("autotune (auto key)", song, "autotune", "{\"style\":\"strong\"}", (r, y) -> "");
    // ---- Clean (diagnose + fix all)
    try {
      JSONObject dg = Workspace.diagnose(c, new JSONObject().put("src", new JSONObject().put("path", noisy.getAbsolutePath())), P, new AtomicBoolean());
      report("Clean: diagnose noisy speech", need(dg.has("path"), "no result"), dg.toString().replaceAll("\"(lv|levels|profile|bands)\":\\[[^\\]]*\\]", "").substring(0, Math.min(500, dg.toString().length())));
      JSONArray fx = new JSONArray(); for (String k : new String[] { "hum", "denoise", "eq", "level", "loud" }) fx.put(k);
      JSONObject cr = Workspace.clean(c, new JSONObject().put("path", dg.getString("path")).put("fixes", fx).put("strength", 1).put("target", "youtube").put("ai", true), P, new AtomicBoolean());
      float[][] y = read(new File(cr.getString("path")));
      double s0 = seg(read(noisy)[0]), s = seg(y[0]); Loudness L = new Loudness(y.length, 48000); L.measure(y, y[0].length);
      report("Clean: fix all (hum, AI noise, EQ, level, loudness)", need(finite(y) && s > s0 + 10 && Math.abs(L.integrated() + 14) < 2, "speech-to-pause " + s), String.format("speech-to-pause %.1f -> %.1f dB, %.1f LUFS, %s, reports %s", s0, s, L.integrated(), stat(y), cr.opt("reports")));
    } catch (Throwable e) { report("Clean", "exception " + e, ""); e.printStackTrace(System.out); }
    // ---- speech to text, fillers, chapters, beats
    try {
      long t0 = System.currentTimeMillis();
      JSONObject tr = Studio.transcribe(c, new JSONObject().put("path", clean.getAbsolutePath()), "tr", P, new AtomicBoolean());
      JSONArray sg = tr.getJSONArray("segments"); String txt = tr.optString("text");
      report("speech to text (Whisper base, Turkish)", need(txt.toLowerCase().contains("ses defteri") || txt.toLowerCase().contains("stanbul"), "text looks wrong"), (System.currentTimeMillis() - t0) + " ms, " + sg.length() + " segments, srt " + tr.optString("srt").length() + " chars\n   " + txt);
    } catch (Throwable e) { report("speech to text", "exception " + e, ""); e.printStackTrace(System.out); }
    try { JSONObject fl = Workspace.fillers(c, new JSONObject().put("src", new JSONObject().put("path", clean.getAbsolutePath())), P, new AtomicBoolean()); report("filler sounds scan", "", fl.toString()); } catch (Throwable e) { report("fillers", "exception " + e, ""); }
    try { JSONObject ch = Workspace.chapters(c, new JSONObject().put("src", new JSONObject().put("path", clean.getAbsolutePath())).put("min", 10).put("max", 30), P, new AtomicBoolean()); report("chapters by pauses", "", ch.toString()); } catch (Throwable e) { report("chapters", "exception " + e, ""); }
    try { JSONObject bt = Studio.beats(c, new JSONObject().put("path", music.getAbsolutePath()), P, new AtomicBoolean()); double bpm = bt.optDouble("bpm"); report("beat detection (100 BPM music)", need(Math.abs(bpm - 100) < 2 || Math.abs(bpm - 200) < 3 || Math.abs(bpm - 50) < 2, "bpm " + bpm), "bpm " + bpm + ", " + bt.optJSONArray("times").length() + " beats"); } catch (Throwable e) { report("beats", "exception " + e, ""); e.printStackTrace(System.out); }
    try { JSONObject lo = Workspace.loudness(c, new JSONObject().put("src", new JSONObject().put("path", music.getAbsolutePath())), P, new AtomicBoolean()); report("loudness measure", need(lo.has("lufs"), "no lufs"), lo.toString()); } catch (Throwable e) { report("loudness", "exception " + e, ""); }
    try { JSONObject pk = AudioTools.peaks(c, new JSONObject().put("path", music.getAbsolutePath()), 0, P, null, new AtomicBoolean()); report("waveform peaks", "", pk.toString().substring(0, Math.min(160, pk.toString().length()))); } catch (Throwable e) { report("peaks", "exception " + e, ""); e.printStackTrace(System.out); }
    try { JSONObject sp = Separator.split(c, new JSONObject().put("path", music.getAbsolutePath()), P, new AtomicBoolean()); report("separate voice / music (Spleeter)", "", sp.toString()); } catch (Throwable e) { report("separate", "exception " + e, ""); e.printStackTrace(System.out); }
    // ---- MP3
    try {
      float[][] y = read(clean); short[] s = new short[y[0].length]; for (int i = 0; i < s.length; i++) s[i] = (short) Math.round(y[0][i] * 32767);
      if (!Mp3.available()) throw new IllegalStateException("lib"); long h = Mp3.init(48000, 1, 192); ByteArrayOutputStream o = new ByteArrayOutputStream();
      for (int i = 0; i < s.length; i += 4800) { int n = Math.min(4800, s.length - i); o.write(Mp3.encode(h, Arrays.copyOfRange(s, i, i + n), n, 1)); }
      o.write(Mp3.flush(h)); Mp3.close(h);
      File m = new File(run, "t.mp3"); FileOutputStream fo = new FileOutputStream(m); o.writeTo(fo); fo.close();
      report("MP3 export (LAME 192 kbps)", need(m.length() > 100000, "tiny file"), m.length() + " bytes");
    } catch (Throwable e) { report("mp3", "exception " + e, ""); e.printStackTrace(System.out); }
    System.out.println("\nRESULT " + pass + " passed, " + fail + " failed"); for (String b : bad) System.out.println(" - " + b);
  }
  static void Context_mk(File run) { new File(run, "files").mkdirs(); new File(run, "cache").mkdirs(); }
}
