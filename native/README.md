# Building the JNI library

The Android app loads `libadblock_jni.so` from `app/src/main/jniLibs/<abi>/`.
Install Rust, the Android NDK, and cargo-ndk, then from the repository root run:

```sh
cargo install cargo-ndk
cd native
cargo ndk -t arm64-v8a \
  -o ../app/src/main/jniLibs build --release
```

`adblock_jni` runs its engine on a dedicated Rust thread. On startup,
`BraveEngine.initializeBraveShields(context)` reads 24-hour cached list data or
fetches EasyList, uBlock filters, Brave-specific/unbreak rules, and Brave's
`adblock-resources` JSON package. The app caches those files under
`filesDir/adblock-lists`; matching files can also be placed in `assets/` as an
offline fallback. It passes filter sets as JSON objects with `filter_rules` and
`permission_mask` fields. Remote lists receive no privileged scriptlet
permissions because they are not authenticated Brave component payloads.
