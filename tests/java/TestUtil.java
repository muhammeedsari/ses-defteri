import app.sesdefteri.dsp.*;
import java.io.File;
public class TestUtil {
    static final String W = "/home/claude/sd-tests/wav/";
    static int fails = 0, passes = 0;
    static Wav wav(String n) throws Exception { return Wav.read(new File(W + n)); }
    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  PASS " : "  FAIL ") + name + "  " + detail);
        if (ok) passes++; else fails++;
    }
    static double rms(float[] x, int a, int b) { double s = 0; for (int i = a; i < b; i++) s += (double) x[i] * x[i]; return Math.sqrt(s / Math.max(1, b - a)); }
    static double db(double v) { return 20 * Math.log10(Math.max(v, 1e-12)); }
    static String f(double v) { return String.format("%.2f", v); }
    static void done() { System.out.println("RESULT " + passes + " passed, " + fails + " failed"); if (fails > 0) System.exit(1); }
}
