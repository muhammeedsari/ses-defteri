package android.media;
import java.util.HashMap;
public class MediaFormat {
  public static final String KEY_MIME = "mime", KEY_DURATION = "durationUs", KEY_CHANNEL_COUNT = "channel-count", KEY_SAMPLE_RATE = "sample-rate",
    KEY_PCM_ENCODING = "pcm-encoding", KEY_MAX_INPUT_SIZE = "max-input-size", KEY_BIT_RATE = "bitrate", KEY_AAC_PROFILE = "aac-profile";
  final HashMap<String, Object> m = new HashMap<>();
  public String getString(String k) { Object v = m.get(k); return v == null ? null : v.toString(); }
  public int getInteger(String k) { return ((Number) m.get(k)).intValue(); }
  public long getLong(String k) { return ((Number) m.get(k)).longValue(); }
  public boolean containsKey(String k) { return m.containsKey(k); }
  public void setInteger(String k, int v) { m.put(k, v); }
  public void setLong(String k, long v) { m.put(k, v); }
  public void setString(String k, String v) { m.put(k, v); }
  public static MediaFormat createAudioFormat(String mime, int rate, int ch) { MediaFormat f = new MediaFormat(); f.m.put(KEY_MIME, mime); f.m.put(KEY_SAMPLE_RATE, rate); f.m.put(KEY_CHANNEL_COUNT, ch); return f; }
}
