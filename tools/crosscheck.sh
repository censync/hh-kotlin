#!/bin/sh
# Differential test of hh-kotlin against hh-cpp. Two batches go through hh_cli of hh-cpp and
# through the cli module here: pseudo-random cases, valid and invalid, and the hand-made cases
# of tools/edge-cases.txt, which pin down the batch format itself. Every printed value and
# every written file (PNG, BMP, JPEG, raw pixels) must be identical.
#
# usage: tools/crosscheck.sh <path to hh_cli> [case count] [seed]
set -eu

here=$(cd "$(dirname "$0")/.." && pwd)
hh_cli=${1:?usage: tools/crosscheck.sh <path to hh_cli> [case count] [seed]}
count=${2:-400}
seed=${3:-1}
work=$here/build/crosscheck
rm -rf "$work"
mkdir -p "$work"

"$here/gradlew" -p "$here" -q :cli:installDist
kotlin_cli=$here/cli/build/install/cli/bin/cli
JAVA_OPTS="-Djava.awt.headless=true"
export JAVA_OPTS

# Runs one batch through both tools and compares what they print and what they write.
compare() {
    name=$1
    cases=$2
    "$hh_cli" --batch "$cases" "$work/$name-cpp" > "$work/$name-cpp.txt"
    "$kotlin_cli" --batch "$cases" "$work/$name-kotlin" > "$work/$name-kotlin.txt"
    if ! diff "$work/$name-cpp.txt" "$work/$name-kotlin.txt" > "$work/$name-values.diff"; then
        echo "crosscheck: $name cases: the printed values differ, see $work/$name-values.diff" >&2
        head -n 20 "$work/$name-values.diff" >&2
        exit 1
    fi
    if ! diff -r "$work/$name-cpp" "$work/$name-kotlin" > "$work/$name-files.diff"; then
        echo "crosscheck: $name cases: the written files differ, see $work/$name-files.diff" >&2
        head -n 20 "$work/$name-files.diff" >&2
        exit 1
    fi
}

"$kotlin_cli" --generate "$count" "$seed" > "$work/generated.txt"
compare generated "$work/generated.txt"
compare edge "$here/tools/edge-cases.txt"

edge=$(wc -l < "$work/edge-cpp.txt" | tr -d ' ')
ok=$(cat "$work/generated-cpp.txt" "$work/edge-cpp.txt" | grep -c "	ok	" || true)
files=$(find "$work/generated-cpp" "$work/edge-cpp" -type f | wc -l | tr -d ' ')
echo "crosscheck: $count generated and $edge hand-made cases agree, seed $seed" \
    "($ok rendered, $files files compared byte for byte)"
