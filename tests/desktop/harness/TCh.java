package app.sesdefteri;
import java.io.File;
import app.sesdefteri.dsp.*;
public class TCh {
  public static void main(String[] a) throws Exception {
    File in = new File(a[0]);
    for (int k = 0; k < 2; k++) {
      Workspace.Info inf = Workspace.info(in);
      System.out.println("ch=" + inf.ch + " rate=" + inf.rate + " frames=" + inf.frames);
      if (inf.ch < 2) { System.out.println("same"); break; }
      Analyzer.Analysis an = Workspace.analyze(in, inf, null, null);
      int mode = an.channelMode == Stages.ChannelMap.KEEP ? Stages.ChannelMap.MONO : an.channelMode;
      System.out.println("mode " + mode + " " + an.channelReason + " corr " + an.corr);
      File out = new File("/tmp/claude-0/ch" + k + ".wav");
      Workspace.Meter m = new Workspace.Meter(1, inf.rate);
      Node head = new Stages.ChannelMap(inf.ch, mode);
      head.to(m);
      m.to(new Studio.WavNode(out, inf.rate));
      Workspace.wav(in, inf, null, new double[]{0.3,1}, null).run(head);
      System.out.println("lufs " + m.l.integrated());
      in = out;
    }
  }
}
