#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

git -C "$repo_root" submodule update --init --recursive third_party/ish-arm64
"$repo_root/scripts/prepare-ios-runtime.sh" "$repo_root/iosApp/Resources/Runtime"

if ! command -v xcodegen >/dev/null 2>&1; then
    echo "xcodegen is required (brew install xcodegen)." >&2
    exit 1
fi
xcodegen generate --spec "$repo_root/iosApp/project.yml" --project "$repo_root/iosApp"
