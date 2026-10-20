# LifeOS · CONTINUUM

Persönliches Betriebssystem für den Alltag – native Android-App (Kotlin, Jetpack Compose, Room + SQLCipher).

**Stand:** Phase 1 implementiert, Domänenkern und Datenbank-Invarianten getestet; erster Android-Build steht aus.
Details: [CHANGELOG.md](CHANGELOG.md) · Plan: [ROADMAP.md](ROADMAP.md) · Regeln: [CLAUDE.md](CLAUDE.md)

## Schnellstart (Android Studio)
1. Ordner in Android Studio (Ladybug oder neuer) öffnen, JDK 17+.
2. Gradle-Sync; ggf. Versionen in `gradle/libs.versions.toml` aktualisieren.
3. `./gradlew build` und `./gradlew :app:installDebug`.

## Ohne Android SDK prüfbar
```bash
python3 tools/schema_test.py -v
KOTLIN_HOME=… JUNIT_JAR=… HAMCREST_JAR=… tools/verify-core.sh
```
