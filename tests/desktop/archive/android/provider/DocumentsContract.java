package android.provider;
import android.content.ContentResolver;
import android.net.Uri;
public class DocumentsContract {
    public static boolean deleteDocument(ContentResolver r, Uri u) { return new java.io.File(u.toString()).delete(); }
}
