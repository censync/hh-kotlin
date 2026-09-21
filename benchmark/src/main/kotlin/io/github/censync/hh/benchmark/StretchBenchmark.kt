package io.github.censync.hh.benchmark

import io.github.censync.hh.Derive
import io.github.censync.hh.Sha256
import java.util.Locale

// Stretching benchmark: the cost of the base digest (section 4 of the specification) for C = 2^12 .. 2^16 on
// one thread. The algorithm uses C = 2^14; the other counts show what the choice costs. Budgets: 10 ms on a
// desktop, 60 ms on a phone. The check value must equal the one the hh-cpp lab prints, and the base digest of
// the golden vector "evm-zero".

private fun baseDigest(data: ByteArray, iterations: Int): ByteArray =
    Derive.stretch(Derive.d0(Derive.KIND_BINARY, data), iterations)

private fun hex(bytes: ByteArray): String =
    bytes.joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xFF) }

// Deterministic 20-byte addresses: SHA-256 of "hh-lab/bench/<i>", as in the C++ benchmark.
private fun address(i: Int): ByteArray = Sha256.digest("hh-lab/bench/$i".toByteArray(Charsets.US_ASCII)).copyOf(20)

private fun millis(nanos: Long): Double = nanos / 1e6

fun main(args: Array<String>) {
    var addresses = 200
    var warmup = 30
    var label = System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version")
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--addresses" -> addresses = args[++i].toInt()
            "--warmup" -> warmup = args[++i].toInt()
            "--label" -> label = args[++i]
            else -> {
                System.err.println("usage: StretchBenchmark [--addresses N] [--warmup N] [--label TEXT]")
                return
            }
        }
        i++
    }

    // The first call runs before the JIT has compiled anything: what a host sees for its first image.
    val coldStart = System.nanoTime()
    val check = hex(baseDigest(ByteArray(20), 1 shl 14))
    val cold = System.nanoTime() - coldStart
    var sink = 0
    repeat(warmup) { sink = sink xor baseDigest(address(it), 1 shl 14)[0].toInt() }

    println("# Kotlin stretching benchmark, one thread; $label")
    println("# check value, 20 zero bytes, C = 2^14: $check")
    println("# first call (cold, C = 2^14): %.3f ms; warm-up: %d calls".format(Locale.ROOT, millis(cold), warmup))
    println("log2_c\tc\taddresses\tmean_ms\tmedian_ms\tmin_ms\tmax_ms\tns_per_compression")
    for (log2c in 12..16) {
        val c = 1 shl log2c
        val times = DoubleArray(addresses)
        for (a in 0 until addresses) {
            val data = address(a)
            val t0 = System.nanoTime()
            val s = baseDigest(data, c)
            val t1 = System.nanoTime()
            sink = sink xor s[0].toInt()
            times[a] = millis(t1 - t0)
        }
        times.sort()
        val mean = times.average()
        val median = times[times.size / 2]
        val compressions = 2.0 * c + 6.0
        println(
            "%d\t%d\t%d\t%.3f\t%.3f\t%.3f\t%.3f\t%.1f".format(
                Locale.ROOT,
                log2c, c, addresses, mean, median, times.first(), times.last(), median * 1e6 / compressions,
            ),
        )
    }
    println("# sink $sink")
}
