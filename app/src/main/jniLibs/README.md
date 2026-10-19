# Native JNI Libraries Directory

The app loads `libadblock_jni.so` from these ABI folders. Build the Rust crate
with cargo-ndk and copy its outputs here:

```sh
cd native
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -t x86 \
  -o ../app/src/main/jniLibs build --release
```

Required Rust targets: `aarch64-linux-android`, `armv7-linux-androideabi`,
`x86_64-linux-android`, and `i686-linux-android`.
