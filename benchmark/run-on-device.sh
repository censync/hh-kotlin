#!/bin/sh
# Runs the stretching benchmark on a connected Android device: the benchmark and its runtime classpath are
# converted with d8, compiled ahead of time with the device's dex2oat and started with app_process from a
# temporary directory under /data/local/tmp, which is removed afterwards. Nothing is installed on the device.
#
# app_process runs without the JIT, so without dex2oat the code would stay interpreted. The compiler filter
# "speed" gives the steady state of an installed app's compiled code; HH_BENCH_COMPILER_FILTER=none measures
# the interpreter instead (a pessimistic bound for code that has not been compiled yet).
#
# usage: benchmark/run-on-device.sh [CPU_MASK ...]
#   Each hexadecimal CPU mask (for taskset) runs one pass pinned to those cores; without masks, one unpinned
#   pass. Extra benchmark arguments can be given in HH_BENCH_ARGS.
# Needs ANDROID_HOME or ANDROID_SDK_ROOT (default ~/Android/Sdk) with build-tools and platform-tools.
set -eu

here=$(cd "$(dirname "$0")/.." && pwd)
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
adb=$sdk/platform-tools/adb
d8=${HH_D8:-$(ls -d "$sdk"/build-tools/*/d8 2> /dev/null | sort -V | tail -n 1)}
[ -x "$adb" ] || { echo "adb not found under $sdk/platform-tools" >&2; exit 1; }
[ -n "$d8" ] && [ -x "$d8" ] || { echo "d8 not found under $sdk/build-tools (set HH_D8)" >&2; exit 1; }
out=$here/benchmark/build/device
remote=/data/local/tmp/hh-bench
main=io.github.censync.hh.benchmark.StretchBenchmarkKt

"$here/gradlew" -p "$here" -q :benchmark:deviceJars
rm -f "$out/bench.zip"
"$d8" --release --min-api 28 --output "$out/bench.zip" "$out"/*.jar

filter=${HH_BENCH_COMPILER_FILTER:-speed}
model=$("$adb" shell getprop ro.product.model | tr -d '\r')
release=$("$adb" shell getprop ro.build.version.release | tr -d '\r')
soc=$("$adb" shell getprop ro.soc.model | tr -d '\r')

"$adb" shell mkdir -p "$remote"
"$adb" push "$out/bench.zip" "$remote/bench.jar" > /dev/null
trap '"$adb" shell rm -rf "$remote"' EXIT

if [ "$filter" != none ]; then
    abi=$("$adb" shell getprop ro.product.cpu.abi | tr -d '\r')
    case "$abi" in
        arm64-v8a) isa=arm64 ;;
        armeabi-v7a) isa=arm ;;
        x86_64) isa=x86_64 ;;
        x86) isa=x86 ;;
        *) echo "unknown ABI $abi" >&2; exit 1 ;;
    esac
    # Android 11 and later keep dex2oat in the ART module; older releases have /system/bin/dex2oat.
    dex2oat=/apex/com.android.art/bin/dex2oat64
    if [ "$isa" = arm ] || [ "$isa" = x86 ]; then
        dex2oat=/apex/com.android.art/bin/dex2oat32
    fi
    if ! "$adb" shell "[ -x $dex2oat ]"; then
        dex2oat=/system/bin/dex2oat
    fi
    "$adb" shell "mkdir -p $remote/oat/$isa && $dex2oat --dex-file=$remote/bench.jar \
        --oat-file=$remote/oat/$isa/bench.odex --instruction-set=$isa --compiler-filter=$filter"
fi

run() {
    label="$model, Android $release, $soc, ART app_process, dex2oat filter $filter, $1"
    "$adb" shell "CLASSPATH=$remote/bench.jar $2 app_process /system/bin $main --label '$label' ${HH_BENCH_ARGS:-}"
}

if [ "$#" -eq 0 ]; then
    run "unpinned" ""
else
    for mask in "$@"; do
        run "cpu mask $mask" "taskset $mask"
    done
fi
