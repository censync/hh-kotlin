# hh - Humanized Hash (Kotlin)

hh turns a blockchain address, a public key or any hash into a small deterministic picture that a
person can compare at a glance: a 4 x 4 matrix of solid squares, circles and triangles in four
colours. It exists to catch address poisoning and clipboard substitution, which work because
people check only the first and last characters of a long string. The colours are chosen so that
people with a colour vision deficiency can tell them apart as well.

| `0x1234567890abcdef00112233445566778899aabb` | `0x12345678f1e2d3c4b5a69788796a5b4c8899aabb` |
|---|---|
| ![picture of the first address](testdata/golden/poison-a-universal-128.png) | ![picture of the second address](testdata/golden/poison-b-universal-128.png) |

The two addresses agree in their first and last eight hex digits. Their pictures are unrelated.

This is the Kotlin implementation. It depends on the Kotlin standard library only, runs on the JVM
and on Android, and produces, byte for byte, the output of the C++ reference implementation
[hh-cpp](https://github.com/censync/hh-cpp), which owns the
[specification](https://github.com/censync/hh-cpp/blob/v1.0.0/docs/SPEC.md) and the golden
vectors. `testdata/` is a byte-identical copy of those vectors; `testdata/SOURCE` names the hh-cpp
release they came from.

## Implementations

Every implementation produces the same pictures, tags and encoded files, byte for byte, and its
tests check it against a copy of the golden vectors of hh-cpp.

| Language | Repository | Package | Install |
|---|---|---|---|
| C++17, C ABI | [hh-cpp](https://github.com/censync/hh-cpp), the reference: specification and golden vectors | CMake `hh::hh`, pkg-config `hh` ([releases](https://github.com/censync/hh-cpp/releases)) | CMake `FetchContent` or `find_package(hh)` |
| Kotlin and Java: JVM, Android | hh-kotlin (this repository) | Maven Central [`io.github.censync:hh`](https://central.sonatype.com/artifact/io.github.censync/hh) | `implementation("io.github.censync:hh:1.0.0")` |
| TypeScript and JavaScript: browsers, Node.js, Deno, Bun | [hh-ts](https://github.com/censync/hh-ts) | npm [`@censync/hh`](https://www.npmjs.com/package/@censync/hh) | `npm install @censync/hh` |
| Go | [go-hh](https://github.com/censync/go-hh) | [`github.com/censync/go-hh`](https://pkg.go.dev/github.com/censync/go-hh) | `go get github.com/censync/go-hh` |
| Python | [hh-python](https://github.com/censync/hh-python) | PyPI [`humanized-hash`](https://pypi.org/project/humanized-hash/) | `pip install humanized-hash` |

## A longer example: Sui

A Sui address has 64 hex digits, and nobody reads 64 digits. The second address below differs
from the first in one digit, the third in two; the changed digits are marked. In the text
they are easy to miss. The pictures and the tags are unrelated, because every cell depends on
every bit of the input.

| Picture | Address | Tag |
|---|---|---|
| ![picture of the first Sui address](docs/images/sui-a.png) | <code>0xeab3150efcb34ff74930d8f3d491be109070a39e4d380de7737aff5c72a0b6b2</code> | `B6P-65H` |
| ![picture of the second Sui address](docs/images/sui-b.png) | <code>0xeab3150efcb34ff74930d8f<ins><b>8</b></ins>d491be109070a39e4d380de7737aff5c72a0b6b2</code> | `Q60-QKR` |
| ![picture of the third Sui address](docs/images/sui-c.png) | <code>0xeab3150efcb34ff74930d8f3d491be1090<ins><b>1</b></ins>0a39e4d380d<ins><b>c</b></ins>7737aff5c72a0b6b2</code> | `ZSJ-7BK` |

What a forger pays, by calculation. One current GPU tries about 1.4 billion addresses per second;
a try against hh also has to compute the stretched base digest, which leaves about 680 000 tries
per second. The figures are the expected search times on one such GPU for a typical picture
([SECURITY.md](https://github.com/censync/hh-cpp/blob/v1.0.0/docs/SECURITY.md) of hh-cpp has the
reasoning).

| The forged address has to match | Tries | One GPU |
|---|---|---|
| the first 4 and the last 4 hex digits | 2^32 | 3 seconds |
| the first 6 and the last 6 hex digits | 2^48 | 2.3 days |
| the first 8 and the last 8 hex digits | 2^64 | 420 years |
| the universal picture, with two cells allowed to differ | 2^52, stretched | 210 years |
| the universal picture, in every cell | 2^68, stretched | 14 million years |
| the ends of the text and the picture | the product of the two | |
| the keyed picture | cannot be searched: without the key the picture cannot be computed | |

A lookalike of the text is cheap, which is why address poisoning works. A lookalike of the
picture is not, and the two costs multiply. A picture that looks the same is still strong
evidence rather than proof; the tag or the full address is the check that is certain.

## Properties

- **Two modes.** A *universal* picture is the same for everyone and is what two people compare. A
  *keyed* picture is computed with a 32-byte secret of the wallet: an attacker who does not hold
  the key cannot compute, and therefore cannot grind, a lookalike. Inside an application keyed
  pictures are the default.
- **Deterministic to the byte.** Integer arithmetic only. The same input gives the same pixels
  and the same PNG, BMP and JPEG bytes as hh-cpp, on every platform.
- **Frozen.** The algorithm has no version and never changes; a picture that a user has learned
  stays the same for ever. Library releases follow SemVer and never alter the output.
- **No dependencies** beyond `kotlin-stdlib`: no `java.awt`, `javax.imageio`, `java.util.zip` or
  `android.*`. SHA-256, HMAC, PBKDF2, deflate and the image encoders are part of the library.
- **Made for colour vision deficiency.** About one man in twelve does not see colours the way the
  rest do. The four colours were chosen for them: the palette was searched so that every pair stays
  apart under simulated protanopia, deuteranopia and tritanopia, and every colour keeps a contrast
  of 3:1 on white and on dark surfaces. Shape carries most of the information, so a picture still
  works in greyscale (the measurements are in
  [docs/design](https://github.com/censync/hh-cpp/tree/v1.0.0/docs/design) of hh-cpp).
- **Pixels, not pictures.** The library returns pixel arrays and encoded files; making a `Bitmap`
  or an `ImageBitmap` of them is one line in the host.

## Quick start

```kotlin
dependencies {
    implementation("io.github.censync:hh:1.0.0")
}
```

```kotlin
import io.github.censync.hh.*

val digest = BaseDigest.ofHex("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")  // slow: cache it
val fingerprint = Fingerprint.universal(digest)      // or Fingerprint.keyed(digest, key)
val image = fingerprint.render(128)                  // 128 x 128 pixels

val bitmap = Bitmap.createBitmap(image.toArgb(), image.width, image.height, Bitmap.Config.ARGB_8888)
val png: ByteArray = image.encodePng()
val tag: String = fingerprint.tag                    // "TKSPVH", shown as TKS-PVH
```

Invalid arguments throw `HhException`, an `IllegalArgumentException` that carries the
`HhErrorCode` of the specification; the functions that take outside data have `...OrNull`
companions. Text goes in as a `String` (`BaseDigest.ofText`) or, when it is UTF-8 bytes already,
verbatim (`BaseDigest.ofUtf8`). Consumers need Kotlin 1.9 or newer (the jar carries Kotlin 2.0
metadata) and a JVM 8 or Android API 24 runtime.

From Java the API reads the same: the factories are static, the constants are fields and default
arguments are overloads.

```java
BaseDigest digest = BaseDigest.ofHex("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed");
HhImage image = Fingerprint.universal(digest).render(128, RenderOptions.TRANSPARENT);
byte[] png = image.encodePng();
```

A decision (confirming a payment, verifying a pasted address) should be backed by a picture of at
least 64 dp, better 96 dp, next to the picture it is compared with. Smaller pictures are for
recognition in lists. See [docs/INTEGRATION.md](docs/INTEGRATION.md) for Android, Compose and
desktop recipes and for the product rules, and
[SECURITY.md](https://github.com/censync/hh-cpp/blob/v1.0.0/docs/SECURITY.md) of hh-cpp for what
a picture proves and what it does not.

## A complete program

A JVM command line program that writes the picture of an address to a PNG file and prints its
tag. Gradle takes hh and the Kotlin standard library from Maven Central.

`settings.gradle.kts`:

```kotlin
rootProject.name = "hh-example"
```

`build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.4.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.censync:hh:1.0.0")
}

application {
    mainClass = "MainKt"
}
```

`src/main/kotlin/Main.kt`:

```kotlin
import io.github.censync.hh.BaseDigest
import io.github.censync.hh.Fingerprint
import java.io.File

fun main() {
    val digest = BaseDigest.ofHex("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")
    val fingerprint = Fingerprint.universal(digest)
    File("address.png").writeBytes(fingerprint.render(128).encodePng())
    val tag = fingerprint.tag
    println("${tag.take(3)}-${tag.drop(3)}")
}
```

`gradle run` prints `TKS-PVH` and writes `address.png`, byte for byte the file
`testdata/golden/evm-1-universal-128.png` that every implementation reproduces. A Maven project
declares the same coordinates, and `kotlin-stdlib` comes with them:

```xml
<dependency>
    <groupId>io.github.censync</groupId>
    <artifactId>hh</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Building

JDK 11 or newer; the Gradle wrapper fetches Gradle 8.9.

```sh
./gradlew build                                  # compiles and runs every test
tools/crosscheck.sh <path to hh_cli of hh-cpp>   # differential test against hh-cpp
./gradlew :benchmark:run                         # stretching benchmark on the JVM
benchmark/run-on-device.sh                       # the same on a connected Android device
```

| Module | Purpose |
|---|---|
| `hh` | the published library, `io.github.censync:hh` |
| `cli` | command line tool for the differential test; never published |
| `benchmark` | stretching benchmark for the JVM and Android devices; never published |

The rules for patches are in [CONTRIBUTING.md](CONTRIBUTING.md), the releases in
[CHANGELOG.md](CHANGELOG.md).

## License

MIT, see [LICENSE](LICENSE). Copyright (c) 2026 Dmitry Mandrika.
[CenSync](https://censync.com)
