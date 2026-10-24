package app.sesdefteri;
import org.json.*;
import java.io.File;
import app.sesdefteri.dsp.*;
public class TTl { public static void main(String[] a) throws Exception {
  JSONArray cl = new JSONArray("[{\"path\":\""+a[0]+"\",\"at\":0,\"in\":3,\"len\":10,\"g\":1,\"fi\":1,\"fo\":1},{\"path\":\""+a[0]+"\",\"at\":5,\"in\":20,\"len\":4,\"g\":0.5}]");
  Timeline.Engine e = new Timeline.Engine(cl);
  System.out.println("end " + e.end/48000.0 + " ch " + e.ch);
  float[][] m = new float[2][4096]; double pk=0;
  for (long p=0;p<e.end;p+=4096){ int n=(int)Math.min(4096,e.end-p); e.render(p,n,m); for(int i=0;i<n;i++) pk=Math.max(pk,Math.abs(m[0][i])); if (p==0) System.out.println("first " + m[0][100]); }
  System.out.println("peak " + pk); e.close();
}}
