#!/bin/bash
set -euo pipefail

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ish_root="$repo_root/third_party/ish-arm64"
build_dir="${TARGET_TEMP_DIR:?}/aether-ish-meson"
mkdir -p "$build_dir"

export PATH="/opt/homebrew/opt/llvm/bin:/usr/local/opt/llvm/bin:$PATH:/opt/homebrew/bin:/usr/local/bin"

if ! command -v ld.lld >/dev/null 2>&1 && ! command -v lld >/dev/null 2>&1; then
    echo "LLVM LLD is required to build the ARM64 guest VDSO (brew install llvm lld)." >&2
    exit 1
fi
export CC_FOR_BUILD="$(DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}" xcrun --sdk macosx --find clang)"

if ! meson introspect --buildoptions "$build_dir" >/dev/null 2>&1; then
    arch=${CURRENT_ARCH:-}
    if [ -z "$arch" ] || [ "$arch" = undefined_arch ]; then
        arch=${NATIVE_ARCH_ACTUAL:-arm64}
    fi
    if [ "$arch" != arm64 ]; then
        echo "AetherIshRuntime currently supports only arm64 Apple targets (got $arch)." >&2
        exit 1
    fi

    sdk=${PLATFORM_NAME:-iphonesimulator}
    case "$sdk" in
        iphoneos)
            target_triple="arm64-apple-ios${IPHONEOS_DEPLOYMENT_TARGET:-17.0}"
            ;;
        iphonesimulator)
            target_triple="arm64-apple-ios${IPHONEOS_DEPLOYMENT_TARGET:-17.0}-simulator"
            ;;
        *)
            echo "Unsupported Apple SDK for AetherIshRuntime: $sdk" >&2
            exit 1
            ;;
    esac
    sdk_root=$(DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}" xcrun --sdk "$sdk" --show-sdk-path)
    meson_arch=$arch
    if [ "$meson_arch" = arm64 ]; then meson_arch=aarch64; fi
    cross_file="$build_dir/cross.txt"
    cat > "$cross_file" <<EOF
[binaries]
c = ['/usr/bin/xcrun', '--sdk', '$sdk', 'clang']
ar = ['/usr/bin/xcrun', '--sdk', '$sdk', 'ar']

[host_machine]
system = 'darwin'
cpu_family = '$meson_arch'
cpu = '$meson_arch'
endian = 'little'

[built-in options]
c_args = ['-arch', '$arch', '-target', '$target_triple', '-isysroot', '$sdk_root']
c_link_args = ['-arch', '$arch', '-target', '$target_triple', '-isysroot', '$sdk_root']

[properties]
needs_exe_wrapper = true
EOF
    meson setup "$build_dir" "$ish_root" --cross-file "$cross_file" -Dguest_arch=arm64
fi

build_type=debug
if [ "${CONFIGURATION:-Debug}" = Release ]; then build_type=debugoptimized; fi
meson configure "$build_dir" -Dbuildtype="$build_type" -Dguest_arch=arm64
ninja -C "$build_dir" libish.a libish_emu.a libfakefs.a
