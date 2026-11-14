package app.sesdefteri;
import java.io.*; import org.json.*; import app.sesdefteri.dsp.*;
public class T4Proj {
  static File wav(File f, int ch, float[][] y) throws Exception { Studio.WavNode w = new Studio.WavNode(f, 48000); w.push(y, y[0].length); w.end(); return f; }
  static float[][] tone(double hz, double amp, double sec) { int n = (int) (sec * 48000); float[][] y = new float[2][n]; for (int i = 0; i < n; i++) y[0][i] = y[1][i] = (float) (amp * Math.sin(2 * Math.PI * hz * i / 48000)); return y; }
  static double rms(float[] x, int a, int b) { double e = 0; for (int i = a; i < b; i++) e += x[i] * x[i]; return 10 * Math.log10(e / (b - a) + 1e-15) + 3.01; }
  static float[] mix(JSONArray clips, long from, int n) throws Exception { Timeline.Engine e = new Timeline.Engine(clips); float[][] m = new float[2][n]; e.render(from, n, m); e.close(); return m[0]; }
  static JSONArray arr(String s) throws Exception { return new JSONObject("{\"a\":" + s + "}").getJSONArray("a"); }
  public static void main(String[] a) throws Exception {
    android.content.Context.FILES = new File(a[0], "files"); android.content.Context.CACHE = new File(a[0], "cache");
    File d = new File(a[0], "proj"); d.mkdirs();
    File t1 = wav(new File(d, "t1.wav"), 2, tone(440, 0.5, 4)), t2 = wav(new File(d, "t2.wav"), 2, tone(660, 0.5, 4));
    // volume line: 0 dB at 0 s, -12 dB at 2 s, then flat
    JSONArray c = arr("[{\"path\":\"" + t1 + "\",\"at\":0,\"in\":0,\"len\":4,\"env\":[[0,0],[2,-12]]}]");
    float[] m = mix(c, 0, 4 * 48000);
    System.out.printf("env: 0-0.1s %.1f dB, 0.95-1.05s %.1f dB (want -12), 2.5-3s %.1f dB (want -12-6=-18)%n", rms(m, 0, 4800), rms(m, 45600, 50400), rms(m, 120000, 144000));
    // crossfade: clip1 0..3 with xo 1 s, clip2 2..6 with xi 1 s
    c = arr("[{\"path\":\"" + t1 + "\",\"at\":0,\"in\":0,\"len\":3,\"xo\":1},{\"path\":\"" + t2 + "\",\"at\":2,\"in\":0,\"len\":4,\"xi\":1}]");
    m = mix(c, 0, 6 * 48000);
    StringBuilder s = new StringBuilder("crossfade rms per 0.25 s around 1.75-3.25 s:");
    for (double t = 1.75; t < 3.25; t += 0.25) s.append(String.format(" %.1f", rms(m, (int) (t * 48000), (int) ((t + 0.25) * 48000))));
    System.out.println(s + " (want about -6 throughout)");
    // fades + gain + ducking
    c = arr("[{\"path\":\"" + t1 + "\",\"at\":0,\"in\":0,\"len\":4,\"g\":0.5,\"fi\":0.5,\"fo\":0.5}]");
    m = mix(c, 0, 4 * 48000);
    System.out.printf("gain 0.5: mid %.1f dB (want -12), first 10 ms %.1f, last 10 ms %.1f%n", rms(m, 96000, 100000), rms(m, 0, 480), rms(m, 4 * 48000 - 480, 4 * 48000));
    c = arr("[{\"path\":\"" + t1 + "\",\"at\":0,\"in\":0,\"len\":4,\"duck\":-12},{\"path\":\"" + t2 + "\",\"at\":2,\"in\":0,\"len\":1}]");
    Timeline.Engine e = new Timeline.Engine(c); float[][] mm = new float[2][4 * 48000];
    for (int p = 0; p < mm[0].length; p += 1024) { int n = Math.min(1024, mm[0].length - p); float[][] b = new float[2][n]; e.render(p, n, b); System.arraycopy(b[0], 0, mm[0], p, n); }
    // music during the voice: filter out the 660 Hz with a crude measure: total rms before (only music) vs inside
    System.out.printf("ducking: music alone %.1f dB, music+voice at 2.5 s %.1f dB (voice alone -6, so ducked music shows as near -5.7)%n", rms(mm[0], 48000, 96000), rms(mm[0], 2 * 48000 + 24000, 2 * 48000 + 36000));
    e.close();
    // metronome
    Timeline.click("{\"on\":true,\"bpm\":120,\"bar\":4,\"vol\":0.5}");
    float[][] k = new float[2][48000 * 2]; for (int p = 0; p < k[0].length; p += 1000) { int n = Math.min(1000, k[0].length - p); float[][] b = new float[2][n]; Timeline.clicks(p, n, b); System.arraycopy(b[0], 0, k[0], p, n); }
    StringBuilder on = new StringBuilder("metronome onsets (ms):"); boolean was = false;
    for (int i = 0; i < k[0].length; i++) { boolean nz = Math.abs(k[0][i]) > 1e-4; if (nz && !was) on.append(' ').append(i / 48); if (nz) was = true; if (i % 480 == 0 && rms(k[0], Math.max(0, i - 480), Math.max(1, i)) < -80) was = false; }
    System.out.println(on + " (want 0 500 1000 1500)");
    Timeline.click("{\"on\":false}");
    // latency match on a synthetic test recording: clicks + noise, delayed by 137 ms
    java.util.Random r = new java.util.Random(1); int n = 6000; float[] env = new float[n];
    for (int i = 0; i < n; i++) env[i] = (float) (0.001 * r.nextDouble());
    for (double t : Timeline.CALIB) { int at = (int) Math.round(t * 1000) + 137; for (int j = 0; j < 6; j++) env[at + j] += (float) (0.3 * Math.exp(-j / 2.0)); }
    System.out.println("latency match: " + Timeline.match(env, n) + " ms (want 137)");
    try { for (int i = 0; i < n; i++) env[i] = (float) (0.001 * r.nextDouble()); Timeline.match(env, n); System.out.println("latency noise: no error (BAD)"); } catch (IllegalStateException x) { System.out.println("latency on noise -> " + x.getMessage() + " (good)"); }
    // meter: -6 dBFS 1 kHz sine
    Timeline.Meter mt = new Timeline.Meter(48000); float[][] sn = tone(1000, 0.5, 3); mt.feed(sn, sn[0].length);
    System.out.println("meter: " + mt.json(sn[0].length));
    // ready-made sounds
    for (String id : Sfx.IDS) {
      long t0 = System.nanoTime(); float[][] y = Sfx.finish(id, Sfx.make(id, 48000), 48000); double pk = 0; boolean bad = false;
      for (float[] q : y) for (float v : q) { if (Float.isNaN(v) || Float.isInfinite(v)) bad = true; pk = Math.max(pk, Math.abs(v)); }
      System.out.printf("sfx %-10s %.2fs pk %.2f rms %.1f %s %d ms%n", id, y[0].length / 48000.0, pk, rms(y[0], 0, y[0].length), bad ? "NaN!" : "", (System.nanoTime() - t0) / 1000000);
      wav(new File(d, "sfx_" + id + ".wav"), 2, y);
    }
    JSONObject sx = Timeline.sfx(new android.content.Context(), new JSONObject().put("id", "ding"));
    System.out.println("Timeline.sfx: " + sx);
    // render with limiter: two clips overlapping loudly
    c = arr("[{\"path\":\"" + t1 + "\",\"at\":0,\"in\":0,\"len\":4,\"g\":1.6},{\"path\":\"" + t2 + "\",\"at\":1,\"in\":0,\"len\":2,\"g\":1.6}]");
    JSONObject ro = Timeline.render(new android.content.Context(), new JSONObject().put("clips", c).put("dir", "proj"), null, null);
    float[][] rr = T3Dsp.read(new File(ro.getString("path"))); double pk = 0; for (float v : rr[0]) pk = Math.max(pk, Math.abs(v));
    System.out.printf("render: %s | %.2fs pk %.3f (limit 0.891)%n", ro, rr[0].length / 48000.0, pk);
  }
}
