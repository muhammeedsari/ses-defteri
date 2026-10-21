# libsdsep.so

Tiny JNI bridge from `app.sesdefteri.Separator` to sherpa-onnx's C API (source separation has no Java binding in the shipped JNI library).

Build (zig as the cross compiler, no NDK needed), for each ABI:

    zig cc -target aarch64-linux-android -O2 -fPIC -shared -nostdlib -ffreestanding -fno-builtin -fno-stack-protector \
      -Istub -I$JAVA_HOME/include -I$JAVA_HOME/include/linux -Wl,-soname,libsdsep.so \
      -o arm64-v8a/libsdsep.so sdsep.c arm64-v8a/libsherpa-onnx-c-api.so

(`stub/stdio.h` is an empty file; `c-api.h` comes from sherpa-onnx v1.13.8; use `arm-linux-androideabi` for armeabi-v7a.)
