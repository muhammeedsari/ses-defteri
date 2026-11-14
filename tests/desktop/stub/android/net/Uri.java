package android.net;
public class Uri {
  final String s; Uri(String s) { this.s = s; }
  public static Uri parse(String s) { return new Uri(s); }
  public static Uri fromFile(java.io.File f) { return new Uri("file://" + f.getAbsolutePath()); }
  public String getScheme() { int i = s.indexOf(':'); return i < 0 ? null : s.substring(0, i); }
  public String getPath() { return s.replaceFirst("^[a-z]+://[^/]*", ""); }
  public String getHost() { return null; }
  public String getLastPathSegment() { return s.substring(s.lastIndexOf('/') + 1); }
  public String toString() { return s; }
}
