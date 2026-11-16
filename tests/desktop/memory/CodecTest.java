package app.sesdefteri;
import android.media.MediaCodec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;

/** Exercise the real EOS handling with a programmable codec; this does not encode real audio. */
public class CodecTest {
    static class Codec extends MediaCodec {
        boolean input = true, output = true, queued, released;
        int waits;
        @Override public int dequeueInputBuffer(long timeout) { return input ? 0 : INFO_TRY_AGAIN_LATER; }
        @Override public void queueInputBuffer(int i, int off, int n, long pts, int flags) { queued = true; }
        @Override public int dequeueOutputBuffer(BufferInfo info, long timeout) {
            if (!output || !queued || waits-- > 0) return INFO_TRY_AGAIN_LATER;
            info.set(0, 0, 0, BUFFER_FLAG_END_OF_STREAM); return 0;
        }
        @Override public ByteBuffer getOutputBuffer(int i) { return null; }
        @Override public void releaseOutputBuffer(int i, boolean render) { }
        @Override public void release() { released = true; }
    }
    static void check(boolean ok, String m) { if (!ok) throw new AssertionError(m); }
    static void aac(boolean input, boolean output, int waits, boolean success) throws Exception {
        Codec codec = new Codec(); codec.input = input; codec.output = output; codec.waits = waits;
        AudioTools.Encoder e = new AudioTools.Encoder((File) null); e.codec = codec; e.started = true; e.rate = 16000;
        boolean ok = true; try { e.finish(); } catch (Exception failure) { ok = false; }
        check(ok == success && codec.released, "AAC EOS handling/release");
    }
    static void flac(boolean input, boolean output, int waits, boolean success) throws Exception {
        Path f = Files.createTempFile("flac-eos", ".flac");
        Codec codec = new Codec(); codec.input = input; codec.output = output; codec.waits = waits;
        MediaCodec.TEST_ENCODER = codec;
        try {
            Recorder.FlacSink e = new Recorder.FlacSink(new RandomAccessFile(f.toFile(), "rw").getChannel(), 16000, 1);
            boolean ok = true; try { e.finish(); } catch (Exception failure) { ok = false; }
            check(ok == success && codec.released && !e.fc.isOpen(), "FLAC EOS handling/release");
        } finally { MediaCodec.TEST_ENCODER = null; Files.delete(f); }
    }
    public static void main(String[] args) throws Exception {
        aac(true, false, 0, false); aac(false, true, 0, false); aac(true, true, 10, true); aac(true, true, 0, true);
        System.out.println("PASS AAC missing input/output EOS fails, delayed/normal EOS succeeds, resources close");
        flac(true, false, 0, false); flac(false, true, 0, false); flac(true, true, 10, true); flac(true, true, 0, true);
        System.out.println("PASS FLAC missing input/output EOS fails, delayed/normal EOS succeeds, resources close");
    }
}
