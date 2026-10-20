import app.sesdefteri.dsp.*;
public class T7Perf extends TestUtil {
    public static void main(String[] a) throws Exception {
        Wav w = wav("t_noisy.wav");
        Enhancer.Options o = new Enhancer.Options();
        for (int i = 0; i < 2; i++) {
            long t0 = System.nanoTime();
            Pipeline.enhance(w.data, 48000, o, new Node() { public void push(float[][] b, int n) {} public void end() {} });
            double s = (System.nanoTime() - t0) / 1e9;
            System.out.printf("enhance (noise reduction on): %.2f s for %.0f s audio = x%.0f real time%n", s, w.data[0].length / 48000.0, w.data[0].length / 48000.0 / s);
        }
        long t0 = System.nanoTime();
        TimePitch tp = new TimePitch(1, 48000, 1.25, 3, true);
        Node.apply(w.data, tp, tp, 1);
        double s = (System.nanoTime() - t0) / 1e9;
        System.out.printf("speed+pitch+formants: %.2f s = x%.0f real time%n", s, w.data[0].length / 48000.0 / s);
    }
}
