package android.content.res;
import java.io.*;
public class AssetManager {
  public InputStream open(String n) throws IOException { return new FileInputStream(new File(android.content.Context.ASSETS, n)); }
  public AssetFileDescriptor openFd(String n) throws IOException { return new AssetFileDescriptor(new File(android.content.Context.ASSETS, n).length()); }
}
