#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
destination=${1:-"$repo_root/iosApp/Resources/Runtime"}
rootfs_url=https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.0-aarch64.tar.gz
rootfs_sha256=f31202c4070c4ef7de9e157e1bd01cb4da3a2150035d74ea5372c5e86f1efac1

mkdir -p "$destination"

rootfs="$destination/root.tar.gz"
if [ ! -f "$rootfs" ] || [ "$(shasum -a 256 "$rootfs" | awk '{print $1}')" != "$rootfs_sha256" ]; then
    temporary_rootfs=$(mktemp "${TMPDIR:-/tmp}/aether-rootfs.XXXXXX")
    trap 'rm -f "$temporary_rootfs"' EXIT INT TERM
    curl --fail --location --retry 3 --output "$temporary_rootfs" "$rootfs_url"
    actual_sha256=$(shasum -a 256 "$temporary_rootfs" | awk '{print $1}')
    if [ "$actual_sha256" != "$rootfs_sha256" ]; then
        echo "Alpine rootfs checksum mismatch: $actual_sha256" >&2
        exit 1
    fi
    mv "$temporary_rootfs" "$rootfs"
    trap - EXIT INT TERM
fi

bridge_source="$repo_root/pi-bridge/dist/bridge.mjs"
if [ ! -f "$bridge_source" ]; then
    npm_command=${NPM:-npm}
    (
        cd "$repo_root/pi-bridge"
        "$npm_command" ci --ignore-scripts --legacy-peer-deps
        "$npm_command" run build
    )
fi
cp "$bridge_source" "$destination/bridge.mjs"

hterm_root="$repo_root/third_party/ish-arm64/deps/libapps"
hterm_dist="$hterm_root/hterm/dist/js/hterm_all.js"
if [ ! -f "$hterm_dist" ]; then
    (
        cd "$hterm_root"
        ./hterm/bin/mkdist
    )
fi
