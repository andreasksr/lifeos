# ROADMAP – LifeOS

Status-Legende: ✅ erledigt und verifiziert · 🟡 implementiert, Verifikation offen · ⬜ offen

> **Ehrlicher Gesamtstand (2026-10-20):** Phase 0 erledigt. Phase 1 ist vollständig implementiert;
> Domänenkern, Präsentationslogik und Datenbank-Trigger sind real getestet. **Ein Android-Gradle-Build
> hat noch nie stattgefunden** – die Entwicklungsumgebung hatte keinen Zugriff auf Google Maven.
> Nächster Schritt ist daher zwingend Phase 1b.

---

## Phase 0 – Architektur & Fundament ✅
- ✅ Kritische Analyse, Risiken, Inkonsistenzen (`ARCHITECTURE.md`)
- ✅ Projektdokumente (CLAUDE, PRODUCT_VISION, DESIGN_SYSTEM, DATA_MODEL, ROADMAP, TEST_PLAN, CHANGELOG)
- ✅ Modulstruktur, Version Catalog, Gradle Wrapper 8.14.3

## Phase 1 – Grundprojekt, Designsystem, Haushaltskasse (Kern) 🟡
| # | Abnahmekriterium | Status |
|---|---|---|
| 1.1 | Geldbeträge exakt (Long, Überlaufschutz, deutsches Parsen/Formatieren ohne Rundung) | ✅ 13 Tests |
| 1.2 | Buchung erfassen/bearbeiten/löschen mit vollständiger Validierung | ✅ Domäne · 🟡 UI |
| 1.3 | Ausgabe, Einnahme, Umbuchung; Umbuchung beeinflusst keine Statistik | ✅ Domäne |
| 1.4 | Splits: Σ = Betrag, Kategorie-Richtung passend | ✅ Domäne + Trigger |
| 1.5 | Monatsübersicht: Gesamtsaldo, Einnahmen, Ausgaben, Ergebnis, Kategorien, Zeitleiste | ✅ Logik · 🟡 UI |
| 1.6 | Monatsbudgets je Kategorie mit Gültigkeit ab Monat, Auslastung, Überschreitung | ✅ Logik · 🟡 UI |
| 1.7 | Konten und Kategorien anlegen; Standardkategorien einmalig (keine Fake-Beträge) | ✅ Logik · 🟡 UI |
| 1.8 | Datenmodell `node`/`link`: Ersatzteil-Szenario – 1 Buchung, 3 Sichten | ✅ Domäne + SQLite |
| 1.9 | DB-Trigger erzwingen Invarianten auch bei Umgehung der Domäne | ✅ 17 SQLite-Tests |
| 1.10 | Integritätsprüfer (Split-Summen, verwaiste Nodes, FK-Check) mit UI | ✅ SQL · 🟡 Room/UI |
| 1.11 | SQLCipher-Verschlüsselung, Schlüssel per Android Keystore, Auto-Backup aus | 🟡 |
| 1.12 | CONTINUUM: LifeLine, LifeNode, LifePanel, LifeMetric, LifeTimeline, LifeNavigation + Controls | 🟡 |
| 1.13 | Animationen abschaltbar (Einstellung + System-Einstellung) | 🟡 |
| 1.14 | Navigation zeigt nur implementierte Bereiche | 🟡 |

## Phase 1b – Build-Verifikation 🟡
Abnahme:
- ✅ `./gradlew test :app:assembleDebug` grün (GitHub Actions, 2026-10-21)
- ⬜ `./gradlew :core:database:connectedAndroidTest :core:security:connectedAndroidTest` grün
- ✅ Schema-JSON `core/database/schemas/…/1.json` erzeugt, eingecheckt, = Referenz-DDL
- ⬜ APK auf echtem Gerät: Buchung erfassen → App beenden → neu starten → Buchung vorhanden
- Screenshot-Tests (Roborazzi) für alle CONTINUUM-Komponenten, Motion an/aus
- Versionsnummern im Version Catalog auf aktuellen Stand prüfen und gebündelt anheben

## Phase 2 – Haushaltskasse vollständig ⬜
- Fixkosten, Abonnements, Verträge (`recurring_rule`, `contract`) mit Fälligkeiten und Kündigungsfristen
- Mehrfachaufteilung im Formular (UI für Splits, `SplitAllocator` existiert bereits)
- Sparziele mit Fortschritt als LifeLine
- Suche/Filter in Buchungen; Jahres- und Langzeitstatistik (12-Monats-Linie)
- Änderungsprotokoll `ledger_audit` (Prüfbarkeit: wer/was/wann geändert)
- Kategorien/Konten bearbeiten, archivieren, sortieren
- Eigene Schriften (OFL) einbinden
- Abnahme: 10 000 Buchungen – Monatsansicht < 300 ms (Benchmark), alle Statistiken mit Tests

## Phase 3 – Sicherheit & Daten-Souveränität ⬜
- Verschlüsseltes Backup (Passphrase → PBKDF2/Argon2id → AES-256-GCM), Wiederherstellung mit Prüfung
- Export CSV/JSON (Buchungen, Kategorien) – unverschlüsselt nur nach Warnung
- Belege als verschlüsselte Anhänge (Kamera/Datei)
- App-Sperre (Biometrie) unabhängig vom DB-Schlüssel
- Backup-Erinnerung (Benachrichtigung)
- Abnahme: Backup auf Gerät A → Wiederherstellung auf Gerät B → Integritätsprüfung 0 Abweichungen

## Phase 4 – Projekte & Besitz ⬜
- Projekte mit Aufgaben, Materialliste, Meilensteinen; Kosten = verknüpfte Buchungen (ohne Kopie)
- Fahrzeuge/Werkzeuge/Geräte mit Kaufbeleg, Garantie, Wartungen, Reparaturen
- Verknüpfungs-UI: aus einer Buchung „Fahrzeug/Projekt zuordnen"; LINKED-Knoten in Zeitleisten
- Abnahme: Ersatzteil-Szenario in der echten UI, Summen in allen drei Sichten identisch

## Phase 5 – Alltag & Archiv ⬜
- Kalender, Aufgaben, Erinnerungen, Wiederholungen; Benachrichtigungen (WorkManager/AlarmManager)
- Notizen, Dokumente, Fotos; Volltextsuche (FTS) über alle Nodes
- „Heute"-Ansicht als Startbildschirm (eine LifeLine durch den Tag); erstes Widget

## Phase 6 – Weiterbildung Industriemeister Mechatronik ⬜
- Fächer/Themen, Lernzeiten, Prüfungstermine, Unterrichtsplanung, Fortschritt als LifeLine

## Phase 7+ – Optionale Intelligenz ⬜
- Belegerkennung (zuerst on-device, ML Kit), Kategorievorschläge
- Optionale externe KI in `integration:ai`, explizite Einwilligung, Datenminimierung
