#!/usr/bin/env bash
# Typprüfung (Kompilierung ohne Packaging) aller Module mit kotlinc + Compose-Compiler-Plugin gegen die
# von der CI exportierten Bibliotheken (Branch `ci-deps`). Für Umgebungen ohne Google-Maven-Zugriff.
# Ersetzt NICHT den Gradle-Build (KSP/Room-SQL-Prüfung, Ressourcen, Lint laufen nur in der CI).
#
#   DEPS=/pfad/zu/ci-deps KOTLIN_HOME=/pfad/kotlinc tools/typecheck.sh [modul ...]
#   Module: model domain security database designsystem ledger home planning projects life app
#   Ohne Argumente: alle. Bereits kompilierte Abhängigkeiten werden wiederverwendet.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${DEPS:?DEPS auf den ausgecheckten Branch ci-deps setzen}"
: "${KOTLIN_HOME:?KOTLIN_HOME setzen}"
OUT="$ROOT/build/typecheck"
mkdir -p "$OUT"
KOTLINC="$KOTLIN_HOME/bin/kotlinc"
COMPOSE="-Xplugin=$KOTLIN_HOME/lib/compose-compiler-plugin.jar"

declare -A SRC=(
  [model]=core/model [domain]=core/domain [security]=core/security [database]=core/database
  [designsystem]=core/designsystem [ledger]=feature/ledger [home]=feature/home
  [planning]=feature/planning [projects]=feature/projects [life]=feature/life [app]=app [apptest]=app
)
declare -A CPLIST=(
  [model]=_core_model__compileClasspath [domain]=_core_domain__compileClasspath
  [security]=_core_security__debugCompileClasspath [database]=_core_database__debugCompileClasspath
  [designsystem]=_core_designsystem__debugCompileClasspath [ledger]=_feature_ledger__debugCompileClasspath
  [home]=_feature_home__debugCompileClasspath [planning]=_feature_home__debugCompileClasspath
  [projects]=_feature_home__debugCompileClasspath [life]=_feature_home__debugCompileClasspath
  [app]=_app__debugCompileClasspath [apptest]=_app__debugAndroidTestCompileClasspath
)
declare -A PROJDEPS=(
  [model]="" [domain]="model" [security]="" [database]="model domain"
  [designsystem]="model" [ledger]="model domain designsystem" [home]="model domain designsystem"
  [planning]="model domain designsystem" [projects]="model domain designsystem" [life]="model domain designsystem"
  [app]="model domain security database designsystem ledger home planning projects life"
  [apptest]="model domain security database designsystem ledger home planning projects life app"
)
declare -A ANDROID=([security]=1 [database]=1 [designsystem]=1 [ledger]=1 [home]=1 [planning]=1 [projects]=1 [life]=1 [app]=1 [apptest]=1)
declare -A USES_COMPOSE=([designsystem]=1 [ledger]=1 [home]=1 [planning]=1 [projects]=1 [life]=1 [app]=1 [apptest]=1)
ORDER=(model domain security database designsystem ledger home planning projects life app apptest)

classpath_for() {
  local m="$1" cp="" f
  local list="$DEPS/classpath/${CPLIST[$m]}.txt"
  if [[ -f "$list" ]]; then
    while IFS= read -r f; do [[ -n "$f" ]] && cp="$cp:$DEPS/jars/$f"; done < "$list"
  fi
  for d in ${PROJDEPS[$m]}; do cp="$cp:$OUT/$d"; done
  if [[ -n "${ANDROID[$m]:-}" ]]; then cp="$DEPS/android.jar$cp"; fi
  echo "${cp#:}"
}

compile() {
  local m="$1"
  local dir="$ROOT/${SRC[$m]}/src/main"
  [[ "$m" == "apptest" ]] && dir="$ROOT/app/src/androidTest"
  [[ -d "$dir" ]] || { echo "  (übersprungen: $dir fehlt)"; return 0; }
  local files; files=$(find "$dir" -name '*.kt' | sort)
  [[ -n "$files" ]] || return 0
  # R-Klasse aus den Ressourcenordnern erzeugen (nicht-transitiv, nur eigene Ressourcen)
  local rstub="$OUT/rstub-$m.kt"
  if [[ "$m" == "apptest" ]]; then : > "$rstub"; else python3 "$ROOT/tools/rstub.py" "$ROOT/${SRC[$m]}" > "$rstub"; fi
  [[ -s "$rstub" ]] && files="$files $rstub"
  local extra=()
  [[ -n "${USES_COMPOSE[$m]:-}" ]] && extra+=("$COMPOSE")
  [[ -n "${ANDROID[$m]:-}" ]] && extra+=(-no-jdk)
  rm -rf "$OUT/$m" && mkdir -p "$OUT/$m"
  echo "▸ $m"
  "$KOTLINC" -jvm-target 17 -nowarn "${extra[@]}" -cp "$(classpath_for "$m")" -d "$OUT/$m" $files 2>&1 \
    | grep -v "^Picked up JAVA_TOOL_OPTIONS" | grep -E "error:|^\S+\.kt:[0-9]+:[0-9]+: error" -A2 || true
  # Erfolg erkennen: mindestens eine .class erzeugt und kein Fehler
  if [[ -z "$(find "$OUT/$m" -name '*.class' -print -quit)" ]]; then echo "✗ $m: Kompilierung fehlgeschlagen"; return 1; fi
}

targets=("$@")
[[ ${#targets[@]} -eq 0 ]] && targets=("${ORDER[@]}")
for m in "${ORDER[@]}"; do
  for t in "${targets[@]}"; do [[ "$t" == "$m" ]] && compile "$m"; done
done
