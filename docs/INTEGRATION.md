# Integration

How to put hh-kotlin into an application. What to hash, which mode to show where and how large a
picture must be are the same for every implementation and are described once, in
[INTEGRATION.md of hh-cpp](https://github.com/censync/hh-cpp/blob/v1.1.0/docs/INTEGRATION.md)
(sections 1 to 4). This document adds the Kotlin side.

## 1. The three steps and what to cache

```kotlin
val digest = BaseDigest.ofHex(address)               // slow, public: cache digest.toByteArray()
val fingerprint = Fingerprint.keyed(digest, key)     // one HMAC; or Fingerprint.universal(digest)
val image = fingerprint.render(sizePx, options)      // fast
```

- The base digest costs about 16 000 HMAC calls: 8 to 9 ms on a desktop JVM, 20 ms on the big
  core of a mid-range phone, up to 70 ms on a little core. Compute it on a background dispatcher
  and cache the 32 bytes per address (`BaseDigest.toByteArray`, `BaseDigest.fromBytes`). It is
  public and needs no protection.
- The very first call of a process is several times slower until the runtime has compiled the
  hash; warm it up off the UI thread if the first picture matters.
- Changing the key or the mode never needs the slow step again.
- An address that is text (Bech32, Base58) goes in with `BaseDigest.ofText`. Text that is UTF-8
  bytes already, from a file, a socket or native code, goes in with `BaseDigest.ofUtf8`, which
  takes the bytes verbatim. Do not decode such bytes into a `String` first: decoding replaces
  every ill-formed sequence with U+FFFD, and the picture would be that of another text.

### Addresses that are text

The table of hh-cpp's INTEGRATION.md asks for bytes wherever one address has several spellings.
The functions below turn the usual spellings into those bytes. They check the form and the
checksum, not whether the address exists or whose it is, and they are not part of the library,
which takes any bytes and any text: copy them into the application.

- TON: every spelling of one account (bounceable `EQ...`, non-bounceable `UQ...`, base64 or
  base64url, raw `0:...`) gives the same 36 bytes and so the same picture. Hashed as text, the
  four spellings would give four unrelated pictures.
- Bitcoin: a bech32 address may be written in capitals, as QR codes do; both spellings give one
  picture. Base58 addresses are case-sensitive and pass unchanged.
- Free text (a name, an e-mail address, a label a person types) is hashed exactly as given, so
  case, spaces and the Unicode form all count: an accented letter typed as one character (U+00E9)
  and as a letter and a combining accent (U+0065 U+0301) gives two different pictures. Normalise
  text a person types to NFC first; what to do about case and spaces is the application's choice.

```kotlin
import java.text.Normalizer
import java.util.Base64

/**
 * TON: the canonical 36 bytes (the workchain as 4 bytes big-endian, then the 32-byte account hash) from a
 * user-friendly address (48 characters of base64 or base64url, any flags) or a raw one ("0:" or "-1:" and 64 hex
 * digits). Null for anything else or for a wrong checksum. `java.util.Base64` needs Android API 26.
 */
fun tonAddressBytes(text: String): ByteArray? {
    val out = ByteArray(36)
    fun putWorkchain(workchain: Int) {
        for (i in 0..3) out[i] = (workchain shr (24 - 8 * i)).toByte()
    }
    val colon = text.indexOf(':')
    if (colon >= 0) {
        val workchain = text.substring(0, colon)
        val hash = text.substring(colon + 1)
        if (workchain != "0" && workchain != "-1") return null
        if (hash.length != 64 || !hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        putWorkchain(workchain.toInt())
        for (i in 0 until 32) out[4 + i] = hash.substring(2 * i, 2 * i + 2).toInt(16).toByte()
        return out
    }
    if (text.length != 48) return null
    val raw = try {
        Base64.getDecoder().decode(text.replace('-', '+').replace('_', '/'))
    } catch (e: IllegalArgumentException) {
        return null
    }
    var crc = 0 // CRC-16/XMODEM over flags, workchain and hash
    for (i in 0 until 34) {
        crc = crc xor ((raw[i].toInt() and 0xFF) shl 8)
        repeat(8) { crc = (if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1) and 0xFFFF }
    }
    if (crc != ((raw[34].toInt() and 0xFF) shl 8 or (raw[35].toInt() and 0xFF))) return null
    putWorkchain(raw[1].toInt())
    raw.copyInto(out, destinationOffset = 4, startIndex = 2, endIndex = 34)
    return out
}

/**
 * Bitcoin: bech32 and bech32m addresses (bc1, tb1, bcrt1) are case-insensitive and are hashed in lower case; one
 * in mixed case is invalid. Base58 addresses are hashed as they are written.
 */
fun bitcoinAddressText(text: String): String? {
    val lower = text.lowercase()
    if (listOf("bc1", "tb1", "bcrt1").none { lower.startsWith(it) }) return text
    return if (text == lower || text == text.uppercase()) lower else null
}

/** Text a person typed: one spelling per string, whatever the keyboard produced. */
fun typedText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
```

```kotlin
val tonDigest = tonAddressBytes(tonAddress)?.let { BaseDigest.of(it) }
val bitcoinDigest = bitcoinAddressText(bitcoinAddress)?.let { BaseDigest.ofText(it) }
val labelDigest = BaseDigest.ofText(typedText(label))
```

## 2. Android

```kotlin
fun Fingerprint.toBitmap(sizePx: Int, options: RenderOptions = RenderOptions.TRANSPARENT): Bitmap {
    val image = render(sizePx, options)
    return Bitmap.createBitmap(image.toArgb(), image.width, image.height, Bitmap.Config.ARGB_8888)
}
```

`toArgb` gives straight (non-premultiplied) alpha, which is what `createBitmap(int[], ...)`
expects. Render at the exact pixel size (`dp * density`) instead of scaling a bitmap: the
rasteriser is anti-aliased for the size it is asked for.

### Compose

```kotlin
@Composable
fun HashImage(fingerprint: Fingerprint, size: Dp, modifier: Modifier = Modifier) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceIn(16, 1024)
    val bitmap = remember(fingerprint, sizePx) {
        fingerprint.toBitmap(sizePx).asImageBitmap()
    }
    Image(bitmap, contentDescription = null, modifier = modifier.size(size), filterQuality = FilterQuality.None)
}
```

`Fingerprint` has value equality, so it is a stable `remember` key. Give the picture a content
description that names the mode ("Private picture of this address"); the tag is the accessible
alternative to the picture itself.

## 3. Desktop JVM

```kotlin
val image = fingerprint.render(256)
val buffered = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
buffered.setRGB(0, 0, image.width, image.height, image.toArgb(), 0, image.width)
File("address.png").writeBytes(image.encodePng())      // deterministic bytes, no ImageIO needed
```

## 4. Keyed mode and the key

```kotlin
val key = try {
    SecretKey.of(keyBytes)              // exactly 32 bytes, not all zero; the bytes are copied
} finally {
    keyBytes.fill(0)                    // wipe your own copy, whether the key was accepted or not
}
val fingerprint = key.use { Fingerprint.keyed(digest, it) }   // close() wipes the library's copy
```

- Where the key comes from is the same for every implementation: the section "The key" of
  [SECURITY.md of hh-cpp](https://github.com/censync/hh-cpp/blob/v1.1.0/docs/SECURITY.md). In
  short: 32 uniformly random bytes or the output of a key derivation function, best derived from
  the wallet seed on a dedicated path, so that it needs no storage and survives a restore. There
  is no passphrase form, and the all-zero key is refused, because a zero-filled buffer is what a
  failed key load looks like.
- `SecretKey.of` copies the bytes. Wipe your own array as soon as the key exists, and whatever
  produced it. `close()` wipes the library's copy; `SecretKey.of(bytes).use { key -> ... }`
  closes on every path, also when the block throws. A key that lives as long as the wallet is
  unlocked is closed when the wallet locks.
- `key.checkValue` is the key check value: 4 bytes that identify the key and reveal nothing
  useful about it. Store them beside cached keyed pictures (files, bitmaps, an image cache that
  outlives the process). When the value differs from the stored one, the key, and with it every
  keyed picture, has changed: drop the cache, and explain the change to the user in a blocking
  notice instead of re-keying silently. The base digests stay valid; they do not depend on the
  key.
- `key.isClosed` tells whether `close()` was called. A closed key holds zeros:
  `Fingerprint.keyed` throws `HhErrorCode.INVALID_KEY` for it and `keyedOrNull` returns null.
  The check value is public, was computed when the key was created and stays readable after
  `close()`.
- `toString()` of a key is `SecretKey(***)`. Keeping the key out of logs, crash reports and
  heap dumps is the host's task; the JVM may also have copied the array while it moved objects,
  which no library can undo.

### Keeping the key out of the JVM

An application that keeps seed-derived secrets on its native side, in a secure element or in an
HSM computes the keyed fingerprint there. It is one HMAC (section 4 of the specification):
`HMAC-SHA-256(key, M2)` with `M2 = "HumanizedHash" || 00 || 02 || digest`; hh-cpp has it as
`hh_keyed_fingerprint`. Only the 32 result bytes come to Kotlin:

```kotlin
val fingerprint = Fingerprint.fromBytes(nativeKeyedFingerprint(digest.toByteArray()), Mode.KEYED)
```

The shared golden vectors guarantee that both implementations render the same picture. The key
check value is computed on that side as well: the first 4 bytes of
`HMAC-SHA-256(key, "HumanizedHash" || 00 || 03)`.

## 5. Looks and contrast

```kotlin
val options = RenderOptions(
    shape = Shape.ROUND,
    frame = FrameStyle.DOUBLE,            // any style of the shape, in either mode
    backgroundRgb = 0x121212,
    backgroundAlpha = 255,
    frameAlpha = 255,
)
val report = options.measureContrast(pageRgb = 0x121212)
if (report.figuresX100 < 300) { /* warn the user: figures may be hard to see */ }
```

`FrameStyle.AUTOMATIC` gives universal pictures no frame and keyed square pictures rounded
corners. Every style is open to both modes: `NONE`, `PLAIN`, `DOUBLE` and `THICK` fit either
shape, `ROUNDED`, `CHAMFERED` and `BRACKETS` the square, `TICKS` and `GAPS` the round shape; a
style that does not fit the shape is `HhErrorCode.INVALID_FRAME`. A host that marks its keyed
pictures with a frame uses one style everywhere in the application and on every device of a
user: a marker is only useful if it is familiar. The library does not enforce the marker, so the
caption, not the frame, is what tells the user the mode.

Rendering refuses an opaque background with less than 2:1 against any palette colour
(`HhErrorCode.LOW_CONTRAST`). For a translucent background pass the colour of the surface underneath
as `pageRgb`. On a dark theme use `RenderOptions.TRANSPARENT` over a dark surface or an opaque dark
background; avoid mid greys and saturated surfaces.

## 6. Errors

| Call | `HhErrorCode` |
|---|---|
| `BaseDigest.of`, `ofHex`, `ofText`, `ofUtf8` | `EMPTY_INPUT`, `INPUT_TOO_LARGE`, `INVALID_HEX`, `INVALID_ARGUMENT` (an unpaired surrogate in `ofText`) |
| `BaseDigest.fromBytes`, `Fingerprint.fromBytes` | `INVALID_DIGEST`, `INVALID_FINGERPRINT` |
| `SecretKey.of`, `Fingerprint.keyed` with a closed key | `INVALID_KEY` |
| `Fingerprint.render` | `INVALID_SIZE`, `INVALID_FRAME` (a frame style that does not fit the shape, in either mode), `LOW_CONTRAST` |
| `HhImage.encodeJpeg`, `HhImage.ofRgba` | `INVALID_QUALITY`, `INVALID_IMAGE` |
| `RenderOptions(...)`, `measureContrast`, `encodeBmp`, `encodeJpeg` | `INVALID_ARGUMENT` for a colour outside `0..0xFFFFFF` or an alpha outside `0..255` |

`HhException` extends `IllegalArgumentException`; its `error` is the `HhErrorCode`, whose `code`
is the number and `specName` the name the specification gives the error (`3`, `invalid_hex`).
The functions that take outside data have `...OrNull` forms that return null instead:
`BaseDigest.of*OrNull`, `fromBytesOrNull`, `SecretKey.ofOrNull`, `Fingerprint.keyedOrNull`,
`fromBytesOrNull`, `renderOrNull`, `HhImage.ofRgbaOrNull`. Nothing else is thrown apart from
`OutOfMemoryError` and, for a caller written in Java, `NullPointerException`: no parameter of the
library accepts `null`, and a `null` passed from Java to one throws it at the call.

### From Java

The factories and functions of the companion objects are static methods, the constants are
static fields, and every default argument has an overload:

```java
BaseDigest digest = BaseDigest.ofHex(address);
Fingerprint fingerprint = Fingerprint.universal(digest);
HhImage image = fingerprint.render(128);                        // RenderOptions.DEFAULT
HhImage round = fingerprint.render(128, new RenderOptions(Shape.ROUND));
byte[] png = image.encodePng();
byte[] jpeg = image.encodeJpeg();                               // HhImage.DEFAULT_JPEG_QUALITY

try (SecretKey key = SecretKey.of(keyBytes)) {
    Fingerprint keyed = Fingerprint.keyed(digest, key);
}
```

Properties are getters (`fingerprint.getTag()`, `key.getCheckValue()`, `key.isClosed()`,
`e.getError()`). The constructor of `RenderOptions` takes its arguments in the order shape,
frame, background colour, background alpha, frame alpha, and any tail of them may be left out.

## 7. Threads

The library keeps no global state; every function may be called from any thread. `Fingerprint`,
`BaseDigest`, `HhImage`, `RenderOptions` and `Layout` are immutable. A `SecretKey` must not be
closed while another thread uses it.

## 8. Conformance

`./gradlew :hh:test` reproduces every record of `testdata/vectors.tsv` and every file of
`testdata/golden/`; `tools/crosscheck.sh` compares thousands of pseudo-random cases, valid and
invalid, and the hand-made cases of `tools/edge-cases.txt` with `hh_cli` of hh-cpp byte for byte.
A mismatch is a bug in this port, never a reason to change the vectors.
