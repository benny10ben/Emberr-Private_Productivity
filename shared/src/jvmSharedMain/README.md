# jvmSharedMain

This source set holds code that needs a JVM-only API (like `java.io.File`) but is otherwise
identical on Android and Desktop.

## Why this exists

Kotlin Multiplatform's `commonMain` has to compile for every target the project could ever
have, so it can't use JVM-only classes like `java.io.File` or `java.nio`. But Android and
Desktop are both actually JVM under the hood - they both have those classes, even though no
other platform does.

Without this source set, any code that touches the filesystem would have to be written twice:
once in `androidMain`, once in `desktopMain`, with the same logic copy-pasted in both places.

`jvmSharedMain` sits between `commonMain` and the two JVM targets. Both `androidMain` and
`desktopMain` depend on it, and it depends on `commonMain`. Write the code once here instead
of twice.

## What belongs here

Put code here if both of these are true:

- it needs a JVM-only API (`java.io.*`, `java.nio.*`, etc.)
- the logic is the same on Android and Desktop, not platform-specific

If the code doesn't need a JVM-only API at all, it belongs in `commonMain` instead. That makes
it visible to Compose UI code and everything else in the project, not just the two JVM
targets.

If the code needs a JVM-only API but genuinely behaves differently on Android vs Desktop, it
belongs in `androidMain` or `desktopMain`, not here.

## Tests

`jvmSharedTest` is the matching test source set for this one. Tests for `jvmSharedMain` code
go there instead of being duplicated into `androidHostTest` and `desktopTest`.
