package android.content.res;
public class AssetFileDescriptor implements java.io.Closeable {
  final long n; public AssetFileDescriptor(long n) { this.n = n; }
  public long getLength() { return n; } public void close() { }
}
