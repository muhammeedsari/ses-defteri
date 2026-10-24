package app.sesdefteri;
import java.io.File;
public class TDbg { public static void main(String[] a) throws Exception {
  File f = new File(a[0]); Workspace.Info inf = Workspace.info(f);
  Speech.Scan sc = Speech.scan(f, inf, null, null);
  double t0 = Double.parseDouble(a[1]), t1 = Double.parseDouble(a[2]);
  for (int k = (int)(t0/0.02); k < (int)(t1/0.02); k++) System.out.printf("%.2f L=%.1f Z=%.0f%n", k*0.02, sc.lvl[k], sc.hf[k]);
}}
