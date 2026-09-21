# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each release names the hh-cpp release
its golden vectors were copied from. The algorithm itself is frozen and has no version: no
release changes a fingerprint, a pixel or an encoded byte.

## [1.0.0] - 2026-09-21

The first release. Golden vectors: hh-cpp v1.0.0.

### Added

- Public API in `io.github.censync.hh` (explicit API mode, KDoc): `BaseDigest` from bytes,
  hexadecimal, text or UTF-8 bytes (`ofUtf8`, taken verbatim); `SecretKey`, wiped on `close()`,
  with `isClosed` and a key check value that stays readable afterwards; universal and keyed
  `Fingerprint` with layout and six-character tag; `RenderOptions` with square and round shapes,
  keyed-mode frame markers, any background colour and transparency, frame transparency and a
  WCAG contrast measure; `HhImage` with RGBA and ARGB pixels and PNG, BMP and JPEG encoders.
  `HhException` carries the `HhErrorCode` of the specification; `...OrNull` forms for hostile
  input.
- The same API from Java: static factories (`BaseDigest.ofHex`, `Fingerprint.universal`), constant
  fields (`RenderOptions.DEFAULT`, `RenderOptions.TRANSPARENT`) and overloads in place of default
  arguments (`fingerprint.render(128)`, `image.encodeJpeg()`).
- Internal SHA-256, HMAC-SHA-256, PBKDF2-HMAC-SHA-256, CRC-32, Adler-32, fixed-Huffman deflate
  and a baseline JPEG encoder: the library depends on `kotlin-stdlib` only, and a build check
  keeps the published POM that way.
- Tests with `kotlin.test`: known-answer tests of the primitives with cross-checks against the
  platform's `MessageDigest`, `Mac` and `SecretKeyFactory`; the golden vectors of hh-cpp;
  decoding of every encoder's output with `javax.imageio`; the JPEG tables against the JDK's
  copies of ITU-T T.81 annex K; deterministic robustness loops.
- `tools/crosscheck.sh` and the `cli` module: differential test against `hh_cli` of hh-cpp with
  pseudo-random cases and the hand-made cases of `tools/edge-cases.txt`.
  `tools/update-vectors.sh` copies the vectors and writes `testdata/SOURCE`.
- `benchmark` module: stretching benchmark for the JVM and, through
  `benchmark/run-on-device.sh`, for Android devices.
- Maven publication `io.github.censync:hh` with sources and javadoc jars, optional signing and a
  Central Portal bundle (`./gradlew :hh:centralBundle`).

[1.0.0]: https://github.com/censync/hh-kotlin/releases/tag/v1.0.0
