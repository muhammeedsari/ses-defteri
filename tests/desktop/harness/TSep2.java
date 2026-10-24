package app.sesdefteri;
import java.io.File;
public class TSep2 { public static void main(String[] a) throws Exception {
  System.load("/home/claude/vendor/shim/out/x64/libsdsep.so");
  String D = "/home/claude/vendor/sep/sherpa-onnx-spleeter-2stems-fp16/";
  File in = new File(a[0]); Workspace.Info inf = Workspace.info(in);
  System.out.println("in ch " + inf.ch + " rate " + inf.rate + " frames " + inf.frames);
  File[] o = Separator.run(in, inf, new File(D + "vocals.fp16.onnx"), new File(D + "accompaniment.fp16.onnx"), new AudioTools.Progress() { public void on(double f) {} }, null);
  for (File f : o) { Workspace.Info i = Workspace.info(f); System.out.println(f + " ch " + i.ch + " rate " + i.rate + " frames " + i.frames + " size " + f.length()); }
}}
