package io.github.censync.hh.cli

import io.github.censync.hh.BaseDigest
import io.github.censync.hh.Fingerprint
import io.github.censync.hh.FrameStyle
import io.github.censync.hh.HhException
import io.github.censync.hh.RenderOptions
import io.github.censync.hh.SecretKey
import io.github.censync.hh.Shape
import java.io.File
import kotlin.random.Random
import kotlin.system.exitProcess

// hh-cli: the counterpart of hh_cli in hh-cpp for differential tests.
//
//   hh-cli --generate COUNT SEED      prints COUNT pseudo-random cases, valid and invalid ones
//   hh-cli --batch FILE DIR           runs the cases of FILE, writes case-<n>.<format> into DIR
//   hh-cli INPUT_HEX OUT.png [SIZE]   renders the universal picture of an address
//
// The batch format is the one hh_cli defines at the head of its source, followed to the letter:
//
//   - The file is bytes. Lines end with LF; one CR before it is dropped. A line that is then empty or begins
//     with '#' is skipped and not counted. Cases are numbered from 1.
//   - A case is exactly 11 fields separated by runs of ASCII spaces or tabs:
//       hex|text  input  key|-  size  shape  frame  background  frame-alpha  format  quality  matte
//   - size, frame-alpha and quality are 1 to 10 ASCII digits without a sign. A frame alpha above 255 is a bad
//     case; size and quality are clamped to 2 147 483 647, which is as invalid as any larger value.
//   - For "text" the input is the hexadecimal form of the UTF-8 bytes, which go to the library verbatim; for
//     "hex" it is passed on as written. background is 8 and matte 6 hexadecimal digits.
//   - A line that breaks these rules, or names an unknown kind, shape or frame, prints "<n>\tbad_case".
//     Everything else is the library's answer: "<n>\t<error name>" and, for ok, the base digest, the
//     fingerprint, the tag and the key check value (or "-"), and the file DIR/case-<n>.<format>. An unknown
//     format is invalid_argument, reported after every other error.
//
// Both tools must print the same lines and write the same files. SIZE of a single render follows the rule of
// the numbers; a malformed one is a usage error.

private const val USAGE = "usage: hh-cli --generate COUNT SEED | --batch FILE DIR | INPUT_HEX OUT.png [SIZE]"

private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

/** Strict hexadecimal: an even number of ASCII digits of either case, or null. */
private fun unhex(text: String): ByteArray? {
    if (text.length % 2 != 0 || !text.all { it in "0123456789abcdefABCDEF" }) {
        return null
    }
    return ByteArray(text.length / 2) { text.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
}

/** 1 to 10 ASCII digits without a sign, clamped to the largest `Int` so that nothing wraps around; or null. */
private fun parseNumber(text: String): Int? {
    if (text.isEmpty() || text.length > 10 || !text.all { it in '0'..'9' }) {
        return null
    }
    return minOf(text.toLong(), Int.MAX_VALUE.toLong()).toInt()
}

private fun rgb(bytes: ByteArray): Int =
    ((bytes[0].toInt() and 0xFF) shl 16) or ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)

private class BadCase : Exception()

private fun runCase(fields: List<String>, number: Int, dir: File): String {
    if (fields.size != 11 || fields[0] !in setOf("hex", "text")) {
        throw BadCase()
    }
    val size = parseNumber(fields[3]) ?: throw BadCase()
    val shape = Shape.entries.firstOrNull { it.name.lowercase() == fields[4] } ?: throw BadCase()
    val frame = FrameStyle.entries.firstOrNull { it.name.lowercase() == fields[5] } ?: throw BadCase()
    val background = unhex(fields[6])?.takeIf { it.size == 4 } ?: throw BadCase()
    val frameAlpha = parseNumber(fields[7])?.takeIf { it <= 255 } ?: throw BadCase()
    val format = fields[8]
    val quality = parseNumber(fields[9]) ?: throw BadCase()
    val matte = unhex(fields[10])?.takeIf { it.size == 3 } ?: throw BadCase()
    val textBytes = if (fields[0] == "text") unhex(fields[1]) ?: throw BadCase() else null

    val digest = if (textBytes != null) BaseDigest.ofUtf8(textBytes) else BaseDigest.ofHex(fields[1])
    var kcv = "-"
    val fingerprint = if (fields[2] == "-") {
        Fingerprint.universal(digest)
    } else {
        SecretKey.of(unhex(fields[2]) ?: ByteArray(0)).use { key ->
            kcv = hex(key.checkValue)
            Fingerprint.keyed(digest, key)
        }
    }
    val options = RenderOptions(
        shape = shape,
        frame = frame,
        backgroundRgb = rgb(background),
        backgroundAlpha = background[3].toInt() and 0xFF,
        frameAlpha = frameAlpha,
    )
    val image = fingerprint.render(size, options)
    val bytes = when (format) {
        "png" -> image.encodePng()
        "bmp" -> image.encodeBmp(rgb(matte))
        "jpeg" -> image.encodeJpeg(quality, rgb(matte))
        "rgba" -> image.toRgba()
        else -> return "invalid_argument"
    }
    File(dir, "case-$number.$format").writeBytes(bytes)
    return "ok\t${hex(digest.toByteArray())}\t${hex(fingerprint.toByteArray())}\t${fingerprint.tag}\t$kcv"
}

private fun batch(file: File, dir: File) {
    dir.mkdirs()
    var number = 0
    // ISO-8859-1 keeps every byte as one character, so a line that is not UTF-8 is a case like any other.
    for (raw in String(file.readBytes(), Charsets.ISO_8859_1).split('\n')) {
        val line = raw.removeSuffix("\r")
        if (line.isEmpty() || line.startsWith("#")) {
            continue
        }
        number++
        val result = try {
            runCase(line.split(' ', '\t').filter { it.isNotEmpty() }, number, dir)
        } catch (e: HhException) {
            e.error.specName
        } catch (e: BadCase) {
            "bad_case"
        }
        print("$number\t$result\n")
    }
}

private fun generate(count: Int, seed: Int) {
    val random = Random(seed)
    val frames = FrameStyle.entries.map { it.name.lowercase() }
    val backgrounds =
        listOf("ffffffff", "ffffffff", "00000000", "00000000", "121212ff", "000000ff", "f2f2f2ff", "9e9e9eff")
    val texts = listOf("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4", "caf\u00E9 \u20AC \uD800\uDF48", "T", "  spaced  ")
    println("# $count cases, seed $seed")
    repeat(count) {
        val text = random.nextInt(6) == 0
        val input = when {
            text -> hex((texts[random.nextInt(texts.size)] + random.nextInt(1000)).encodeToByteArray())
            random.nextInt(25) == 0 -> listOf("zz", "abc", "0x", "12 34")[random.nextInt(4)].replace(" ", "_")
            else -> (if (random.nextBoolean()) "0x" else "") + hex(random.nextBytes(1 + random.nextInt(40)))
        }
        val key = when (random.nextInt(8)) {
            0, 1, 2 -> "-"
            3 -> if (random.nextInt(4) == 0) "00".repeat(32) else hex(random.nextBytes(31))
            else -> hex(random.nextBytes(32))
        }
        val size = when (random.nextInt(30)) {
            0 -> 15
            1 -> 1025
            2 -> 1024
            3 -> 16
            else -> 17 + random.nextInt(240)
        }
        val background =
            if (random.nextInt(3) == 0) hex(random.nextBytes(4)) else backgrounds[random.nextInt(backgrounds.size)]
        val round = random.nextBoolean()
        // Mostly a frame that fits the mode and the shape, so that most cases render.
        val fitting = when {
            key == "-" -> listOf("automatic", "none", "plain")
            round -> listOf("automatic", "none", "plain", "double", "thick", "ticks", "gaps")
            else -> listOf("automatic", "none", "plain", "rounded", "chamfered", "double", "thick", "brackets")
        }
        val frame =
            if (random.nextInt(8) == 0) frames[random.nextInt(frames.size)] else fitting[random.nextInt(fitting.size)]
        val format = listOf("png", "png", "bmp", "jpeg", "rgba")[random.nextInt(5)]
        val quality = if (random.nextInt(12) == 0) 40 + random.nextInt(70) else 50 + random.nextInt(51)
        println(
            listOf(
                if (text) "text" else "hex", input, key, size, if (round) "round" else "square", frame, background,
                random.nextInt(256), format, quality, hex(random.nextBytes(3)),
            ).joinToString(" "),
        )
    }
}

private fun usage(): Nothing {
    System.err.println(USAGE)
    exitProcess(2)
}

fun main(args: Array<String>) {
    when {
        args.size == 3 && args[0] == "--generate" ->
            generate(parseNumber(args[1]) ?: usage(), args[2].toIntOrNull() ?: usage())
        args.size == 3 && args[0] == "--batch" -> batch(File(args[1]), File(args[2]))
        args.size in 2..3 && !args[0].startsWith("--") -> {
            val size = if (args.size == 3) parseNumber(args[2]) ?: usage() else 128
            try {
                File(args[1]).writeBytes(Fingerprint.universal(BaseDigest.ofHex(args[0])).render(size).encodePng())
            } catch (e: HhException) {
                System.err.println("error: ${e.error.specName}: ${e.message}")
                exitProcess(1)
            }
        }
        else -> usage()
    }
}
