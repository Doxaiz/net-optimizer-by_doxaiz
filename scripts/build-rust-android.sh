#!/usr/bin/env bash
# Build one JNI library for each ABI supported by the existing universal APK.
set -euo pipefail
if [[ $# != 1 ]]; then
  echo 'Usage: build-rust-android.sh OUTPUT_JNILIBS_DIRECTORY' >&2
  exit 2
fi
root=$(cd "$(dirname "$0")/.." && pwd)
out=$1
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
# Hosted runners may export ANDROID_NDK_HOME for a different revision. Prefer
# the pinned SDK installation; only use the override when that is absent.
if [[ -n "$sdk" && -d "$sdk/ndk/27.2.12479018" ]]; then
  ndk="$sdk/ndk/27.2.12479018"
else
  ndk=${ANDROID_NDK_HOME:-"$sdk/ndk/27.2.12479018"}
fi
case "$(uname -s)" in
  Linux) host=linux-x86_64 ;;
  Darwin) host=darwin-x86_64 ;;
  *) echo 'Rust Android build currently supports Linux and macOS hosts.' >&2; exit 1 ;;
esac
bin="$ndk/toolchains/llvm/prebuilt/$host/bin"
if [[ ! -x "$bin/clang" ]] || ! grep -Eq '^Pkg\.Revision[[:space:]]*=[[:space:]]*27\.2\.12479018[[:space:]]*$' "$ndk/source.properties"; then
  echo "Android NDK 27.2.12479018 not found or version does not match: $ndk" >&2
  exit 1
fi
export CARGO_TARGET_DIR="$root/build/rust-target"
manifest="$root/native/netoptimizer-core/Cargo.toml"
bridge="$root/native/netoptimizer-core/jni/bridge.c"
exports="$root/native/netoptimizer-core/jni/exports.map"
cd "$root"
while read -r abi target compiler; do
  echo "Building Rust core for $abi ($target)"
  cargo build --locked --release --manifest-path "$manifest" --target "$target"
  mkdir -p "$out/$abi"
  # Write atomically so a failed build cannot leave a newly-created partial library.
  tmp="$out/$abi/libnetoptimizer_core.so.tmp"
  rm -f "$tmp" "$out/$abi/libnetoptimizer_core.so"
  "$bin/$compiler" -shared -O2 -fPIC -ffunction-sections -fdata-sections \
    -Wall -Wextra -Werror "$bridge" "$CARGO_TARGET_DIR/$target/release/libnetoptimizer_core.a" \
    -Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,--no-undefined \
    -Wl,--version-script="$exports" -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 \
    -ldl -llog -lm -o "$tmp"
  "$bin/llvm-strip" --strip-unneeded "$tmp"
  symbols=$("$bin/llvm-nm" -D --defined-only "$tmp")
  for symbol in Java_com_doxaiz_netoptimizer_core_net_NativeMeasurements_tcpNative \
                Java_com_doxaiz_netoptimizer_core_net_NativeMeasurements_medianNative; do
    if [[ "$symbols" != *"$symbol"* ]]; then
      echo "Missing JNI export $symbol for $abi" >&2
      exit 1
    fi
  done
  mv "$tmp" "$out/$abi/libnetoptimizer_core.so"
done <<'ABIS'
arm64-v8a aarch64-linux-android aarch64-linux-android31-clang
armeabi-v7a armv7-linux-androideabi armv7a-linux-androideabi31-clang
x86 i686-linux-android i686-linux-android31-clang
x86_64 x86_64-linux-android x86_64-linux-android31-clang
ABIS
