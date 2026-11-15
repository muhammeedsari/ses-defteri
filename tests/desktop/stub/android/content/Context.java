package android.content;
import java.io.File;
/** desktop stand-in: only what the tested code paths call */
public class Context {
  public static File FILES, CACHE, ASSETS;
  public File getFilesDir() { return FILES; }
  public File getCacheDir() { return CACHE; }
  public Context getApplicationContext() { return this; }
  public android.content.res.AssetManager getAssets() { return new android.content.res.AssetManager(); }
}
