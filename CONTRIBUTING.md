# Contributing to hh-kotlin

hh-kotlin is the Kotlin implementation of Humanized Hash (`hh`). The C++17 repository hh-cpp is
the reference: it owns `docs/SPEC.md`, `docs/SECURITY.md` and the canonical golden vectors. This
repository carries a byte-identical copy of the vectors in `testdata/` and records the hh-cpp
release and the file hashes in `testdata/SOURCE`. The port follows `SPEC.md`, not the C++ source.

Bug reports and patches are welcome: open an issue or a pull request. Security problems are
reported privately, as `docs/SECURITY.md` of hh-cpp describes.

## The algorithm is frozen

Output is byte-identical with hh-cpp. A mismatch against the vectors is a bug here, never a reason
to change the vectors. The algorithm has no version and never changes; what the specification
leaves open (API shape, error texts, performance) may evolve under SemVer.

hh is a standalone library. Nothing here names a particular host application.

## Dependencies and license

- The published `hh` artefact depends on `kotlin-stdlib` only. No `java.*`, `javax.*` or
  `android.*` in the main source set of `hh`: SHA-256, HMAC, PBKDF2, CRC-32, Adler-32, deflate and
  the PNG/BMP/JPEG encoders are written here. `./gradlew check` verifies the POM and scans the
  sources for forbidden imports and floating point.
- No third-party libraries anywhere, including the `cli` and `benchmark` modules (they may use
  the JDK).
- Tests use the `kotlin.test` API only (test scope). On the JVM it runs on the JUnit Platform that
  `kotlin-test-junit5` brings in; test code never imports JUnit directly. Tests may cross-check
  against `java.security.MessageDigest`, `javax.crypto` and `javax.imageio`.
- License: MIT (`LICENSE`); contributions are accepted under it.

## Style

- Everything is English: code, comments, KDoc, documentation, commit messages. No emoji.
- Official Kotlin code style (`kotlin.code.style=official`), 120 columns, explicit API mode, KDoc
  on every public declaration.
- Package `io.github.censync.hh`; internals are `internal`.
- The API serves callers written in Java as well: public companion functions are `@JvmStatic`,
  companion constants that are objects are `@JvmField`, and public functions and constructors with
  default arguments are `@JvmOverloads`. `JavaApiTest` keeps it that way.
- Warnings are errors (`allWarningsAsErrors`). JVM target 1.8.

## Library rules (hh module)

- Integer arithmetic only: no `Float`, `Double`, `kotlin.math`. The `benchmark` module may use
  floating point for reporting; tests may use it to measure error.
- Invalid arguments throw `HhException` (an `IllegalArgumentException`); `...OrNull` variants
  return null instead.
- Buffers that held key material are wiped before release.

## Build and test

- `./gradlew build` builds and tests everything; `./gradlew :hh:test` runs the library tests.
- `tools/crosscheck.sh <hh_cli>` runs the differential test against hh-cpp: generated cases and
  the hand-made cases of `tools/edge-cases.txt`. That file is bytes, the same copy in every
  implementation; the batch format is defined at the head of `examples/hh_cli.cpp` of hh-cpp.
- `tools/update-vectors.sh <hh-cpp checkout>` refreshes `testdata/` and `testdata/SOURCE`; never
  edit those files by hand.
- Gradle 8.9 through the wrapper, Kotlin 2.0.21, JDK 11. The `cli` and `benchmark` modules are
  never published.

## Commits

Atomic, imperative, lower case, for example "add sha-256 with fips 180-4 vectors". Every commit
builds and passes the tests.
