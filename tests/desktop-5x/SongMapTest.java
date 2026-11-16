import app.sesdefteri.dsp.*;
public class T {
  public static void main(String[] a) throws Exception {
    int fs = 44100; double bpm = 120, beat = 60 / bpm;
    String[][] secs = { {"C","C","C","C"}, {"Am","F","C","G","Am","F","C","G"}, {"F","G","C","Am","F","G","C","Am"}, {"Am","F","C","G","Am","F","C","G"}, {"F","G","C","Am","F","G","C","Am"}, {"Dm","Em","Dm","Em"}, {"F","G","C","Am","F","G","C","Am"}, {"C","C","C","C"} };
    boolean[] bright = {false,false,true,false,true,false,true,false};
    String[] N = {"C","C#","D","Eb","E","F","F#","G","Ab","A","Bb","B"};
    java.util.List<String> bars = new java.util.ArrayList<>(); java.util.List<Boolean> br = new java.util.ArrayList<>();
    for (int s=0;s<secs.length;s++) for (String c: secs[s]) { bars.add(c); br.add(bright[s]); }
    int nb = bars.size(); double dur = nb * 4 * beat; int n = (int)(dur*fs);
    float[] x = new float[n]; java.util.Random r = new java.util.Random(1);
    for (int b=0;b<nb;b++) { String c = bars.get(b); boolean m = c.endsWith("m"); String root = m? c.substring(0,c.length()-1):c; int ri=java.util.Arrays.asList(N).indexOf(root);
      int[] tones = { ri, ri + (m?3:4), ri+7 };
      for (int k=0;k<4;k++) { int s0=(int)((b*4+k)*beat*fs), s1=(int)((b*4+k+1)*beat*fs);
        for (int i=s0;i<s1&&i<n;i++) { double t=(i-s0)/(double)fs, env=Math.exp(-t*3), v=0;
          for (int tn: tones) { double f=261.63*Math.pow(2,tn/12.0); for(int h=1;h<=5;h++) v+=Math.sin(2*Math.PI*f*h*t)/h*0.08; }
          double fb=65.41*Math.pow(2,ri/12.0); v+=Math.sin(2*Math.PI*fb*t)*0.2+Math.sin(2*Math.PI*fb*2*t)*0.1;
          v*=env; if (br.get(b)) { v*=1.6; if (t<0.05) v+=r.nextGaussian()*0.15*(1-t/0.05); } x[i]=(float)(v + r.nextGaussian()*0.002); } } }
    SongMap sm = new SongMap(fs);
    for (int i=0;i<n;i+=4096){int k=Math.min(4096,n-i); float[] q=java.util.Arrays.copyOfRange(x,i,i+k); sm.push(new float[][]{q},k);}
    double[] bt = new double[nb*4]; for(int i=0;i<bt.length;i++) bt[i]=i*beat;
    long t0=System.nanoTime(); SongMap.Result res = sm.finish(bt, dur);
    StringBuilder sb=new StringBuilder(); for(Object[] o:res.chords) sb.append(o[0]).append(':').append(o[1]).append(' ');
    System.out.println(sb); for(Object[] o:res.sections) System.out.println(o[0]+"-"+o[1]+" "+o[2]+" "+o[3]);
    System.out.println("ms "+(System.nanoTime()-t0)/1e6);
  }
}
