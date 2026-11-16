package android.media;
import java.nio.*; import java.util.ArrayDeque;
/** desktop stand-in: a "decoder" for audio/raw that passes PCM through */
public class MediaCodec {
  public static final int BUFFER_FLAG_END_OF_STREAM = 4, BUFFER_FLAG_KEY_FRAME = 1, INFO_TRY_AGAIN_LATER = -1, INFO_OUTPUT_FORMAT_CHANGED = -2, INFO_OUTPUT_BUFFERS_CHANGED = -3, CONFIGURE_FLAG_ENCODE = 1;
  public static final class BufferInfo { public int offset, size, flags; public long presentationTimeUs; public void set(int o, int s, long t, int f) { offset = o; size = s; presentationTimeUs = t; flags = f; } }
  final ByteBuffer[] in = new ByteBuffer[4]; final ArrayDeque<int[]> ready = new ArrayDeque<>(); final ArrayDeque<Integer> freeIn = new ArrayDeque<>();
  final long[] ts = new long[4]; MediaFormat fmt; boolean sentFormat;
  public static MediaCodec createDecoderByType(String mime) { if (!"audio/raw".equals(mime)) throw new IllegalArgumentException("desktop stub decodes only wav"); return new MediaCodec(); }
  public static MediaCodec createEncoderByType(String mime) { throw new IllegalStateException("no encoders on desktop"); }
  public void configure(MediaFormat f, android.view.Surface s, MediaCrypto c, int flags) { fmt = f; for (int i = 0; i < 4; i++) { in[i] = ByteBuffer.allocate(1 << 16); freeIn.add(i); } }
  public void start() { } public void stop() { } public void release() { } public void flush() { freeIn.clear(); ready.clear(); for (int i = 0; i < 4; i++) freeIn.add(i); }
  public int dequeueInputBuffer(long t) { Integer i = freeIn.poll(); return i == null ? -1 : i; }
  public ByteBuffer getInputBuffer(int i) { in[i].clear(); return in[i]; }
  public void queueInputBuffer(int i, int off, int size, long t, int flags) { ready.add(new int[] { i, off, size, flags }); ts[i] = t; }
  public int dequeueOutputBuffer(BufferInfo bi, long t) {
    if (!sentFormat) { sentFormat = true; return INFO_OUTPUT_FORMAT_CHANGED; }
    int[] r = ready.poll(); if (r == null) return INFO_TRY_AGAIN_LATER;
    bi.set(r[1], r[2], ts[r[0]], r[3]); return r[0];
  }
  public ByteBuffer getOutputBuffer(int i) { ByteBuffer b = in[i].duplicate(); b.clear(); return b; }
  public MediaFormat getOutputFormat() { MediaFormat f = MediaFormat.createAudioFormat("audio/raw", fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE), fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)); f.setInteger(MediaFormat.KEY_PCM_ENCODING, 2); return f; }
  public void releaseOutputBuffer(int i, boolean r) { freeIn.add(i); }
}
