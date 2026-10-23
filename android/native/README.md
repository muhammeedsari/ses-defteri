# libsdsep.so

Tiny JNI bridge from `app.sesdefteri.Separator` to sherpa-onnx's C API (source separation has no Java binding in the shipped JNI library).

Build (zig as the cross compiler, no NDK needed), for each ABI:

    zig cc -target aarch64-linux-android -O2 -fPIC -shared -nostdlib -ffreestanding -fno-builtin -fno-stack-protector \
      -Istub -I$JAVA_HOME/include -I$JAVA_HOME/include/linux -Wl,-soname,libsdsep.so \
      -o arm64-v8a/libsdsep.so sdsep.c arm64-v8a/libsherpa-onnx-c-api.so

(`stub/stdio.h` is an empty file; `c-api.h` comes from sherpa-onnx v1.13.8; use `arm-linux-androideabi` for armeabi-v7a.)

# libsdmp3.so (MP3 export, arm64-v8a only)

`sdmp3.c` is a JNI bridge to LAME 3.99.5 (libmp3lame, LGPL-2.0; source: Ubuntu `lame_3.99.5+repack1.orig.tar.gz`).
LAME's `libmp3lame/*.c` (configured with `--disable-frontend --disable-decoder`) and `sdmp3.c` are compiled with
`zig cc -target aarch64-linux-musl -O2 -fPIC` and linked as a shared library against stub `libc.so`/`libm.so`
(empty functions with the same names) so that the real Android libc/libm are used at run time.
As a separate shared library it can be replaced with any other build of LAME.
