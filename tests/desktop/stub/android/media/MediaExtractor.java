package android.media;
import java.io.*; import java.nio.*; import java.util.Map;
/** desktop stand-in: reads 16-bit PCM WAV files and hands them out in 4096-frame "samples" */
public class MediaExtractor {
  public static final int SEEK_TO_PREVIOUS_SYNC = 0, SEEK_TO_CLOSEST_SYNC = 2, SAMPLE_FLAG_SYNC = 1;
  RandomAccessFile f; long dataOff, dataLen, pos; int ch, rate, bps;
  public void setDataSource(String path) throws IOException {
    f = new RandomAccessFile(path, "r");
    byte[] h = new byte[12]; f.readFully(h);
    if (h[0] != 'R' || h[8] != 'W') throw new IOException("not a wav (desktop stub)");
    long p = 12;
    while (true) {
      f.seek(p); byte[] c = new byte[8]; f.readFully(c);
      ByteBuffer b = ByteBuffer.wrap(c).order(ByteOrder.LITTLE_ENDIAN); long len = b.getInt(4) & 0xffffffffL; String id = new String(c, 0, 4);
      if (id.equals("fmt ")) { byte[] fm = new byte[16]; f.readFully(fm); ByteBuffer q = ByteBuffer.wrap(fm).order(ByteOrder.LITTLE_ENDIAN); ch = q.getShort(2); rate = q.getInt(4); bps = q.getShort(14); }
      if (id.equals("data")) { dataOff = p + 8; dataLen = Math.min(len, f.length() - dataOff); break; }
      p += 8 + len + (len & 1);
    }
    if (bps != 16) throw new IOException("only 16-bit wav (desktop stub)");
  }
  public void setDataSource(android.content.Context c, android.net.Uri u, Map<String, String> h) throws IOException { setDataSource(u.toString().replace("file://", "")); }
  public int getTrackCount() { return 1; }
  public MediaFormat getTrackFormat(int i) { MediaFormat m = MediaFormat.createAudioFormat("audio/raw", rate, ch); m.setLong(MediaFormat.KEY_DURATION, dataLen / (2L * ch) * 1000000L / rate); return m; }
  public void selectTrack(int i) { }
  public void unselectTrack(int i) { }
  public int readSampleData(ByteBuffer b, int off) {
    if (pos >= dataLen) return -1;
    int n = (int) Math.min(Math.min(4096L * 2 * ch, dataLen - pos), b.capacity() - off);
    try { byte[] t = new byte[n]; f.seek(dataOff + pos); f.readFully(t); b.position(off); b.put(t); b.position(off); b.limit(off + n); } catch (IOException e) { return -1; }
    last = n; return n;
  }
  int last;
  public long getSampleTime() { return pos >= dataLen ? -1 : pos / (2L * ch) * 1000000L / rate; }
  public int getSampleFlags() { return SAMPLE_FLAG_SYNC; }
  public boolean advance() { pos += Math.min(4096L * 2 * ch, dataLen - pos); return pos < dataLen; }
  public void seekTo(long us, int mode) { pos = Math.min(dataLen, us * rate / 1000000L * 2 * ch); }
  public void release() { try { f.close(); } catch (Exception e) { } }
}
