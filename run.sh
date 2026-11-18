#!/bin/sh
set -eu
OUT="${TMPDIR:-/tmp}/infra-java-example-classes"
rm -rf "$OUT"
mkdir -p "$OUT"
find src/main/java -name '*.java' -print0 | xargs -0 javac -d "$OUT"
if [ "${1:-run}" = "test" ]; then
  find src/test/java -name '*.java' -print0 | xargs -0 javac -cp "$OUT" -d "$OUT"
  java -cp "$OUT" example.infrai.FundingPolicyTest
else
  java -cp "$OUT" example.infrai.TenantOnboardingService "$@"
fi
