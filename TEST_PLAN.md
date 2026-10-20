# TEST_PLAN – LifeOS

## 1. Teststrategie (Pyramide)
| Ebene | Werkzeug | Ziel | Ort |
|---|---|---|---|
| Unit (JVM) | JUnit 4 | Geld, Regeln, Berechnungen, Use-Cases, Formularlogik | `core/model`, `core/domain`, `feature/ledger/src/test` |
| Schema (SQLite) | Python `unittest` + `sqlite3` | Trigger, Fremdschlüssel, Kaskaden, Prüfabfragen | `tools/schema_test.py` |
| Integration (Gerät) | AndroidX Test, Room in-memory | Room ↔ Referenz-DDL, Round-Trip, Trigger durch Room, Keystore | `core/database/src/androidTest`, `core/security/src/androidTest` |
| Migration | `MigrationTestHelper` | jede Version mit echten Altdaten | ab Schema v2 |
| UI | Compose UI Test + Roborazzi | Komponenten, Motion an/aus, TalkBack-Semantik | ab Phase 1b |
| Manuell | Gerät | Kernflüsse, Persistenz nach Neustart | Checkliste §4 |

## 2. Abgedeckte Fälle (Stand 2026-10-20)

### `core:model` – 13 Tests
Exakte Addition/Subtraktion, Währungsmischung verboten, Überlauf erkannt, Währungscodes, NodeId-Format,
deutsches Parsen (`12,5`, `1.234,56`, `1.234`, `€`, schmale Leerzeichen), Vorzeichen, alle Fehlerarten
ohne Raten, JPY ohne Nachkommastellen, Formatierung inkl. `Long.MIN_VALUE`, Round-Trip.

### `core:domain` – 30 Tests
- `TransactionValidatorTest` (10): alle Invarianten D-1…D-8, Obergrenze, Split-Überlauf
- `LedgerMathTest` (6): Salden inkl. Umbuchung, Monatsübersicht ohne Umbuchungen, Kategoriesummen über Splits, gültiges Budget, Promille-Grenzfälle
- `SplitAllocatorTest` (4): Restverteilung, 2 000 Zufallsfälle mit exakter Summe
- `LedgerUseCasesTest` (9): Standardwerte genau einmal, keine Fake-Buchungen, Textbereinigung, ungültig ⇒ nichts gespeichert, Bearbeiten ersetzt statt dupliziert, Löschen, Budget ersetzen, Konto/Kategorie anlegen, reaktive Monatsansicht
- `SparePartScenarioTest` (1): **Abnahmekriterium aus dem Auftrag** – 1 Buchung, sichtbar in Kasse/Fahrzeug/Projekt, Kosten überall 89,90 €, Saldo genau einmal belastet

### `feature:ledger` (Android-freie Präsentationslogik) – 10 Tests
Formular → Entwurf, gleichzeitige Pflichtfeldfehler, Betragsfehlertexte, Umbuchung, Artwechsel,
aufgeteilte Buchungen bleiben beim Bearbeiten erhalten, jede Domänenmeldung hat deutschen Text,
deutsche Datums-/Monatsformate, Prozent abgerundet.

### Schema (SQLite 3.45) – 17 Tests
Gültige Buchung, Betrag > 0 (Insert/Update), Umbuchungsform, Währung, echtes ISO-Datum (inkl. 29.02.
im Schaltjahr, 30.02. abgelehnt), Node-Typ, Split-Regeln, Art gesperrt, FK-Fehler, Kaskade beim
Löschen (Links weg, Fahrzeug/Projekt bleiben), RESTRICT für benutzte Konten/Kategorien,
**Nachweis: `INSERT OR REPLACE` auf `node` vernichtet Daten**, Kontowährung gesperrt,
Kategorierichtung gesperrt, Links, Budgets, Prüfabfragen finden genau die Verstöße, Trigger idempotent.

### Mutationsprobe
Absichtlich eingebauter Fehler (Umbuchungen als Ausgabe gezählt) → Testsuite schlägt fehl
(`LedgerMathTest.monatsuebersicht…`). Fehler danach entfernt.

## 3. Geschrieben, aber noch NICHT ausgeführt
| Test | Grund |
|---|---|
| `DatabaseInstrumentedTest` (4): Room-Spalten = Referenz-DDL, Trigger-Anzahl, Round-Trip, Trigger greift durch Room, Link-Kaskade | benötigt Android-Gerät/Emulator |
| `DatabaseKeyManagerTest` (2): Passphrase stabil, fehlender Schlüssel wird gemeldet statt ersetzt | benötigt Android Keystore |

## 4. Manuelle Abnahme-Checkliste (Phase 1b, auf Gerät)
- [ ] Erststart: Konto „Haushaltskasse" + Standardkategorien vorhanden, keine Buchungen, keine Zahlen außer 0,00 €
- [ ] Ausgabe 12,50 € erfassen → erscheint in Zeitleiste unter „Heute", Ausgaben = 12,50 €
- [ ] App per Task-Manager beenden, neu starten → Buchung vorhanden
- [ ] Buchung bearbeiten (Betrag, Kategorie) → Summen aktualisieren sich, keine Dublette
- [ ] Buchung löschen (zweistufig) → verschwindet, Summen korrekt
- [ ] Zweites Konto anlegen, Umbuchung → Gesamtsaldo unverändert, Monatsausgaben unverändert
- [ ] Budget 50,00 € für Lebensmittel, Ausgaben 60,00 € → Linie endet in Flare, Text „überschritten um 10,00 €"
- [ ] Eingaben `1,234` / `abc` / leer → verständliche Fehlermeldung am Feld, nichts gespeichert
- [ ] System → Bewegung „Aus" → keine Pulse, keine Übergänge; Einstellung überlebt Neustart
- [ ] Android „Animationen entfernen" → Bewegung aus, Hinweis im System-Bildschirm
- [ ] System → Datenintegrität → „keine Abweichungen"
- [ ] TalkBack: Navigation, Beträge, Fortschrittslinien werden sinnvoll vorgelesen
- [ ] Flugmodus: alles funktioniert

## 5. Ausführung
```bash
python3 tools/schema_test.py -v
KOTLIN_HOME=/pfad/kotlinc JUNIT_JAR=…/junit-4.13.2.jar HAMCREST_JAR=…/hamcrest-core-1.3.jar tools/verify-core.sh
./gradlew test                         # sobald Gradle-Build verfügbar
./gradlew connectedAndroidTest         # Gerät/Emulator
```
