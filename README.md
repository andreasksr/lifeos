# LifeOS · CONTINUUM

Persönliches Betriebssystem für den Alltag – native Android-App (Kotlin, Jetpack Compose, Room + SQLCipher),
offline, ohne Internet-Berechtigung, ohne KI-Abhängigkeit.

**Stand:** Version 0.5.0 – Home, Finanzen, Planung, Projekte, Mein Leben (Fahrzeuge, Inventar, Dokumente,
Industriemeister), Suche, Datensicherung, App-Sperre, Widget, LifeMap.
Nachweise: [CHANGELOG.md](CHANGELOG.md) · Tests: [TEST_PLAN.md](TEST_PLAN.md) · Plan: [ROADMAP.md](ROADMAP.md) · Regeln: [CLAUDE.md](CLAUDE.md)

## Installation
Die CI (GitHub Actions, Workflow `Android Build`) baut bei jedem Push eine Debug-APK und legt sie im Branch
`ci-output` ab (`app-debug.apk`). Alle Builds sind mit demselben Entwicklungsschlüssel signiert und lassen sich
als Update übereinander installieren. **Einmalig:** v0.1.0 war anders signiert und muss vor dem ersten Update
deinstalliert werden.

## Module
```
app/                Shell, Navigation, DI, Benachrichtigungen, Tresor, Sicherung, App-Sperre, Widget, Suche, LifeMap
core/model          Geld, IDs, Entitäten (reines Kotlin)
core/domain         Regeln, Use-Cases, Statistik (reines Kotlin, JVM-getestet)
core/database       Room + SQLCipher, Schema v4, Trigger, Migrationen 1→4
core/security       Keystore-Schlüssel, Dokumenttresor, Sicherungsverschlüsselung
core/designsystem   CONTINUUM-Komponenten
feature/ledger · home · planning · projects · life
```

## Ohne Android SDK prüfbar
```bash
python3 tools/schema_test.py -v
KOTLIN_HOME=… JUNIT_JAR=… HAMCREST_JAR=… tools/verify-core.sh
DEPS=… KOTLIN_HOME=… tools/typecheck.sh        # Typprüfung aller Module gegen die CI-Bibliotheken
```
