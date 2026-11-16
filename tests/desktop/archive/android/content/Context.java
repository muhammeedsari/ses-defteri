package android.content;
import java.io.File;
public class Context {
    public static File ASSETS;
    private final File root;
    public Context(File root) { this.root = root; }
    public File getCacheDir() { File f = new File(root, "cache"); f.mkdirs(); return f; }
    public File getFilesDir() { File f = new File(root, "files"); f.mkdirs(); return f; }
    public ContentResolver getContentResolver() { return new ContentResolver(); }
    public Context getApplicationContext() { return this; }
}
