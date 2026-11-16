package android.content;
import java.io.*;
import android.net.Uri;
public class ContentResolver {
    public InputStream openInputStream(Uri u) throws IOException { return new FileInputStream(u.toString()); }
    public OutputStream openOutputStream(Uri u, String mode) throws IOException { return new FileOutputStream(u.toString()); }
}
