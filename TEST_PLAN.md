# TEST_PLAN – LifeOS

## 1. Teststrategie
| Ebene | Werkzeug | Ort | Läuft |
|---|---|---|---|
| Unit (JVM) | JUnit 4 | `core/model`, `core/domain`, `core/security` (Tresor/Sicherung), `feature/*/src/test` | lokal (`tools/verify-core.sh`) und CI (`./gradlew test`) |
| Schema (SQLite) | Python `unittest` | `tools/schema_test.py` – SQL direkt aus `SchemaSql.kt`, Room-Schemata 1–4 | lokal |
| Typprüfung | kotlinc + Compose-Plugin gegen CI-Bibliotheken | `tools/typecheck.sh` | lokal (vor jedem Push) |
| Gerät | AndroidX Test, Room, MigrationTestHelper, Keystore | `core/*/src/androidTest`, `app/src/androidTest` | CI-Emulator API 34 (Pixel 6) |
| UI Ende-zu-Ende | Compose UI Test + echte Screenshots | `app/src/androidTest/EndToEnd*` | CI-Emulator |

## 2. Ergebnisse (Stand 2026-10-21, Version 0.5.0)

### JVM – **124 Tests, 0 Fehler** (CI `./gradlew test`, Commit `c2fc86e`; identisch lokal mit `verify-core.sh`)
| Bereich | Inhalt |
|---|---|
| Geld (`core:model`) | exakte Arithmetik, Überlauf, deutsches Parsen/Formatieren ohne Rundung, Betragseingabe |
| Finanzen | Validierung aller Buchungsarten, Salden, Umbuchung erhöht keine Ausgaben, Erstattung/Korrektur, Budgets inkl. Überschreitung, Gesamtbudget, Fixkosten, wiederkehrende Zahlungen (Fälligkeiten, nie automatisch gebucht, keine Doppelbuchung), Ersatzteil-Szenario (1 Buchung, 3 Sichten) |
| Planung | Wiederholungen, Erledigen/Wiedereröffnen, Erinnerungsplanung (erledigte Aufgaben erinnern nicht), Agenda Tag/Woche/Monat, Gruppierung |
| Projekte | Fortschritt nach Aufgaben/Meilensteinen/manuell, Kosten aus verknüpften Buchungen (Erstattung mindert, keine Doppelzählung), Zuordnen ohne Kopie |
| Mein Leben | Fahrzeugvalidierung, Kilometerstand fortschreiben, Fahrzeugkosten ohne Doppelzählung, Garantie-Status, Dokument-Dedupe und Löschen erst beim letzten Verweis, nur eine laufende Lernzeit, 12-h-Kappung, Sofort-Stopp verwirft, Nachtrag ohne Überschneidung, Statistik zählt Überschneidungen einmal, Zielzeitraum ≠ bestätigter Termin |
| Querschnitt | Home-Layout/Hinweise, zentrale Suche (Titel, Händler, Notiz, Betrag, Kategorie, Beschreibung), LifeMap (nur direkte Verbindungen, Links + feste Beziehungen) |
| Kryptografie | Sicherung: Rundreise an Segmentgrenzen, falsches Passwort, abgeschnittene/veränderte/verlängerte Datei abgewiesen; Tresor: verschlüsselt auf der Platte, keine Dublette, fremder Schlüssel scheitert, Größengrenze |

### SQLite – **30 Tests, 0 Fehler** (`python3 tools/schema_test.py`)
Alle Trigger (Buchungen, Budgets, wiederkehrende Zahlungen, Aufgaben, Termine, Projekte, Fahrzeuge, Inventar,
Dokumente, Fächer, Themen, Prüfungen, Lernzeit), Kaskaden (Fahrzeug löschen → Historie + Erinnerungen + Links
weg, Dokument bleibt; Fach löschen → Themen weg, Lernzeit bleibt), Migrationen 1→2→3→4 mit Altdaten,
Room-Schemata 1–4 = Referenz-DDL.

### Emulator (API 34) – CI-Läufe `a5afadf` und `c2fc86e`
| Test | Ergebnis |
|---|---|
| `DatabaseInstrumentedTest` (6): Room = Referenz-DDL, Round-Trip, Trigger durch Room, Kaskaden | ✅ |
| `MigrationInstrumentedTest`: 1→2, 1→3, **1→4 mit Altdaten** (Auftrag: „Datensatz bleibt nach Update erhalten") | ✅ |
| `DatabaseKeyManagerTest` (2): Schlüssel stabil, Verlust wird gemeldet | ✅ (`a5afadf`) |
| `ReminderInstrumentedTest`: Benachrichtigung erscheint, nach Erledigen deaktiviert | ✅ (`a5afadf`) |
| `BackupRestoreTest`: Sicherung auf „Gerät A", Wiederherstellung mit völlig anderem DB- und Dokumentschlüssel, falsches Passwort ändert nichts | ✅ (`a5afadf`, `c2fc86e`) |
| `EndToEndPlanningTest`: Aufgabe erfassen, abhaken, Termin planen, Tag/Woche/Monat | ✅ (`a5afadf`) |
| `EndToEndFinanceTest`: Ausgabe anlegen → bearbeiten → löschen → rückgängig | siehe §3 |
| `EndToEndProjectTest`: Buchung einem Projekt zuordnen, korrigieren, Kosten folgen | siehe §3 |
| `EndToEndLifeTest`: Fahrzeug + HU planen, Prüfungsziele, Lernzeit, Überschneidung abgewiesen | siehe §3 |

## 3. Offene bzw. instabile Prüfungen (ehrlich)
- Die drei UI-Ende-zu-Ende-Tests Finanzen/Projekte/Mein Leben scheiterten in den Läufen bis `a5afadf` an
  **Testfehlern** (Groß-/Kleinschreibung von Überschriften, mehrdeutige Klickziele, ein Hinweis, der auf dem
  langsamen Emulator vor dem Tipp verschwand, eine falsche Annahme zur Überschneidung). Alle sind korrigiert;
  die Funktionen selbst arbeiteten laut Bildschirmfotos/Semantikbaum korrekt (Fahrzeug angelegt, HU geplant,
  Prüfungsziele „noch nicht bestätigt", Lernzeit gemessen). Im letzten Lauf (`c2fc86e`) ist der **Emulator
  während des Finanztests abgestürzt** („device not found") – die korrigierten Tests müssen noch einmal
  vollständig durchlaufen. Aktueller Stand: siehe CHANGELOG / letzter Lauf im Branch `ci-device`.
- App-Sperre (BiometricPrompt) und Widget: kein automatisierter Test (Emulator ohne Biometrie, kein Launcher-Test).
- Layout nur auf Pixel 6 (1080×2400) und mit Standard-Schriftgröße per Screenshot geprüft.

## 4. Manuelle Abnahme auf dem eigenen Gerät
- [ ] Update von 0.4.x/0.5.0 installieren → alle Daten vorhanden
- [ ] Ausgabe in der Schnellerfassung in < 5 s erfassen; Home-Kennzahl ändert sich
- [ ] Aufgabe mit Erinnerung in 2 Minuten → Benachrichtigung; „Erledigt" in der Benachrichtigung → keine weitere
- [ ] Gerät neu starten → zukünftige Erinnerung kommt trotzdem
- [ ] PDF importieren, demselben Fahrzeug und einem Projekt zuordnen; dieselbe Datei erneut importieren → „bereits im Archiv"
- [ ] Sicherung erstellen, App deinstallieren, neu installieren, Sicherung wiederherstellen → alles da, Dokumente öffnen
- [ ] App-Sperre einschalten, App 30 s in den Hintergrund → Entsperren nötig; Widget zeigt dann keine Inhalte
- [ ] Widget auf den Startbildschirm legen → nächster Termin / fällige Aufgaben, Tipp öffnet den Eintrag
- [ ] System → Bewegung aus → keine Animationen; TalkBack liest Beträge, Knoten, Fortschritt vor

## 5. Ausführung
```bash
python3 tools/schema_test.py -v
KOTLIN_HOME=… JUNIT_JAR=… HAMCREST_JAR=… tools/verify-core.sh
DEPS=… KOTLIN_HOME=… tools/typecheck.sh
./gradlew test :app:assembleDebug
./gradlew connectedDebugAndroidTest      # Emulator/Gerät
```
