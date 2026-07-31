# Third-party notices

## OpenMinis/ish-arm64

Aether's iOS runtime integrates [OpenMinis/ish-arm64](https://github.com/OpenMinis/ish-arm64)
using commit `89269e6fef7ab7aa61b133deae90d78e34a09ed1` as its upstream base.

- License: GNU General Public License v3.0. See `third_party/ish-arm64/LICENSE.md`.
- iOS distribution exception and additional terms: see
  `third_party/ish-arm64/LICENSE.IOS`.
- Corresponding source: the complete pinned source, its pinned submodules, and
  Aether's changes are included in this repository under
  `third_party/ish-arm64`.
- Aether integration changes against the upstream base:
  - `asbestos/guest-arm64/gadgets-aarch64/math.S` and
    `asbestos/guest-arm64/gen.c` add missing ARM64 SIMD conversion and move
    instructions needed by the bundled Alpine and Node runtime.
  - `fs/fd.c` and `fs/poll.c` correct descriptor-table locking, duplication,
    `close_range`, and polling lifetime behavior for concurrent guest
    processes.
  - `fs/sock.c` and `fs/sock.h` add the ARM64 control-message layout and
    `SO_PASSCRED`/`SCM_CREDENTIALS` behavior used by local Unix sockets.
  - The Objective-C/Swift host adapter is maintained in `iosApp/Runtime`; the
    reproducible build integration is in `scripts/build-ios-ish-runtime.sh`.

Release archives must include this notice, both iSH license files, the upstream
base commit above, the complete modified corresponding source, and a durable
link to the corresponding Aether source tag.

## Alpine Linux

The iOS runtime starts from Alpine Linux 3.21 AArch64 minirootfs. Individual
packages retain their own licenses. The build downloads the archive from the
official Alpine CDN and verifies SHA-256
`f31202c4070c4ef7de9e157e1bd01cb4da3a2150035d74ea5372c5e86f1efac1`.
