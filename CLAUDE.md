# CLAUDE.md – Arbeitsregeln für LifeOS

Dieses Dokument richtet sich an jeden (Mensch oder KI), der an LifeOS weiterentwickelt.
Es ist verbindlich. Bei Widerspruch zu anderen Dokumenten gilt: CLAUDE.md > ARCHITECTURE.md > Rest.

## Projekt in einem Satz
Native Android-App (Kotlin, Jetpack Compose, Room/SQLCipher), die Finanzen, Alltag, Projekte, Besitz,
Weiterbildung und Archiv in **einem** relationalen Datenmodell verbindet – im Design **CONTINUUM**.

## Dokumente
| Datei | Inhalt |
|---|---|
| `ARCHITECTURE.md` | Kritische Analyse, Risiken, Schichten, ADRs |
| `PRODUCT_VISION.md` | Zielbild, Prinzipien, Nicht-Ziele |
| `DESIGN_SYSTEM.md` | CONTINUUM: Tokens, Komponenten, Bewegung, Barrierefreiheit |
| `DATA_MODEL.md` | Tabellen, Invarianten, Trigger, Migrationsregeln |
| `ROADMAP.md` | Phasen mit Abnahmekriterien und Status |
| `TEST_PLAN.md` | Teststrategie, was wie geprüft wird, aktueller Stand |
| `CHANGELOG.md` | Was tatsächlich implementiert/gebaut/getestet wurde |

## Modulstruktur
```
app/                 Shell, Navigation, AppContainer (manuelle DI)
core/model/          reines Kotlin – Money, NodeId, Entitäten
core/domain/         reines Kotlin – Use-Cases, Regeln, Repository-Ports
core/database/       Room + SQLCipher, Trigger (SchemaSql.kt), Repositories
core/security/       Keystore-Schlüsselverwaltung
core/designsystem/   CONTINUUM-Komponenten
feature/ledger/      Haushaltskasse; presentation/ ist bewusst Android-frei
tools/               verify-core.sh (JVM-Tests ohne Gradle), schema_test.py (SQLite)
```
Abhängigkeiten nur nach unten. `feature/*` darf **nie** `core:database` importieren.

## Unverhandelbare Regeln
1. **Geld** ist `Long` in Minor Units. Kein `Double`/`Float`/`BigDecimal`-Rundungscode in Geldpfaden. Summen mit `Math.addExact`.
2. **Keine gespeicherten Aggregate.** Salden, Summen, Budgetauslastung werden immer aus Buchungen berechnet (`LedgerMath`).
3. **Eine Buchung = eine Zeile.** Mehrfache Sichtbarkeit nur über `link`, niemals über Kopien.
4. **Nodes nie mit `REPLACE` schreiben.** `INSERT OR REPLACE` löscht per Kaskade Buchungen, Splits und Links (bewiesen in `tools/schema_test.py::test_insert_or_replace_on_node_would_destroy_data`). Nur `ABORT`-Insert oder `UPDATE`.
5. **Schemaänderung = Migration + Test.** Kein `fallbackToDestructiveMigration`. Jede Migration führt am Ende `SchemaSql.TRIGGERS` aus. Schema-JSON in `core/database/schemas/` einchecken.
6. **Trigger-SQL lebt nur in `SchemaSql.kt`.** Änderungen dort → `python3 tools/schema_test.py` muss grün sein.
7. **Keine funktionslosen Schaltflächen**, keine Platzhalter-Screens. Navigationsziele erst mit `implemented = true` in `LifeDestination`.
8. **Keine erfundenen Zahlen.** Beispieldaten nur mit `node.is_sample = 1` und sichtbarer Kennzeichnung.
9. **Erwartbare Fehler sind Werte** (`LedgerResult.Invalid`), keine Exceptions. Unerwartete Exceptions werden in ViewModels abgefangen und verständlich angezeigt; nichts wird halb gespeichert (DAO-Methoden sind `@Transaction`).
10. **KI/Netzwerk** nur in eigenem Modul `integration:*`, opt-in, nie von `core:*` referenziert.
11. **Animationen** nur über `Continuum.motion`; jede Komponente muss mit `motion.enabled = false` sofort korrekt aussehen.
12. **Ehrlichkeit im CHANGELOG:** „gebaut" nur nach echtem Gradle-Build, „getestet" nur nach echtem Testlauf, mit Angabe wie.

## Befehle
```bash
./gradlew build                          # vollständiger Build inkl. Unit-Tests
./gradlew :core:domain:test :core:model:test :feature:ledger:testDebugUnitTest
./gradlew :core:database:connectedAndroidTest   # Gerät/Emulator nötig
./gradlew :app:assembleDebug             # APK: app/build/outputs/apk/debug/
python3 tools/schema_test.py -v          # Trigger/Schema gegen SQLite
KOTLIN_HOME=… JUNIT_JAR=… HAMCREST_JAR=… tools/verify-core.sh   # JVM-Tests ohne Gradle
```

## Definition of Done (pro Funktion)
- Domänenlogik in `core:domain` mit Unit-Tests (inkl. Fehlerfälle).
- Persistenz über Repository-Port, atomar, mit Trigger-Absicherung wo nötig.
- UI nutzt ausschließlich CONTINUUM-Komponenten und Tokens.
- Funktioniert mit Animationen aus, mit TalkBack (Labels/Rollen), ohne Netzwerk.
- `ROADMAP.md`, `TEST_PLAN.md`, `CHANGELOG.md` aktualisiert – mit tatsächlichem Prüfstatus.
