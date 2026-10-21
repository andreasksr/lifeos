#!/usr/bin/env bash
# Kompiliert und testet die reinen Kotlin-Module :core:model, :core:domain und die Android-freie
# Präsentationslogik von :feature:ledger OHNE Gradle.
# Zweck: Verifikation in Umgebungen ohne Zugriff auf Maven-Repositories.
# Im normalen Alltag gilt: ./gradlew :core:model:test :core:domain:test
#
# Benötigt: KOTLIN_HOME (Kotlin-Compiler 2.1.x), JUNIT_JAR, HAMCREST_JAR
# Coroutines werden aus KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar genommen.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${KOTLIN_HOME:?KOTLIN_HOME setzen (Verzeichnis des Kotlin-Compilers)}"
: "${JUNIT_JAR:?JUNIT_JAR setzen (junit-4.13.2.jar)}"
: "${HAMCREST_JAR:?HAMCREST_JAR setzen (hamcrest-core-1.3.jar)}"

KOTLINC="$KOTLIN_HOME/bin/kotlinc"
STDLIB="$KOTLIN_HOME/lib/kotlin-stdlib.jar"
COROUTINES="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
OUT="$ROOT/build/verify-core"
rm -rf "$OUT" && mkdir -p "$OUT"

echo "▸ kompiliere :core:model"
"$KOTLINC" -jvm-target 17 -Werror -d "$OUT/model.jar" $(find "$ROOT/core/model/src/main" -name '*.kt')

echo "▸ kompiliere :core:domain"
"$KOTLINC" -jvm-target 17 -Werror -cp "$OUT/model.jar:$COROUTINES" -d "$OUT/domain.jar" \
  $(find "$ROOT/core/domain/src/main" -name '*.kt')

# Präsentationslogik der Haushaltskasse ist bewusst Android-frei und daher hier mitprüfbar.
echo "▸ kompiliere :feature:ledger (presentation, Android-frei)"
"$KOTLINC" -jvm-target 17 -Werror -cp "$OUT/model.jar:$OUT/domain.jar:$COROUTINES" -d "$OUT/feature-ledger.jar" \
  $(find "$ROOT/feature/ledger/src/main/kotlin/app/lifeos/feature/ledger/presentation" "$ROOT/feature/planning/src/main/kotlin/app/lifeos/feature/planning/presentation" -name '*.kt')

TEST_SOURCES=$(find "$ROOT/core/model/src/test" "$ROOT/core/domain/src/test" "$ROOT/feature/ledger/src/test" "$ROOT/feature/planning/src/test" -name '*.kt')
echo "▸ kompiliere Tests"
"$KOTLINC" -jvm-target 17 -cp "$OUT/model.jar:$OUT/domain.jar:$OUT/feature-ledger.jar:$COROUTINES:$JUNIT_JAR" \
  -d "$OUT/tests.jar" $TEST_SOURCES

CLASSES=$(for f in $(echo "$TEST_SOURCES" | grep 'Test\.kt$'); do
  pkg=$(grep -m1 '^package ' "$f" | sed 's/package //')
  echo "$pkg.$(basename "$f" .kt)"
done)

echo "▸ führe JUnit aus"
java -cp "$OUT/model.jar:$OUT/domain.jar:$OUT/feature-ledger.jar:$OUT/tests.jar:$STDLIB:$COROUTINES:$JUNIT_JAR:$HAMCREST_JAR" \
  org.junit.runner.JUnitCore $CLASSES
