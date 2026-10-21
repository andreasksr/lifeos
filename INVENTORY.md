# Bestandsaufnahme v0.1.0 (Stand 2026-10-21, vor dem Next-Generation-Update)

Sicherungsstand: Branch `backup/v0.1.0` (Commit `f48ff9e`). Paketkennung `app.lifeos.continuum` bleibt.

## Architektur & Technik
| Bereich | Befund |
|---|---|
| Module | `app`, `core:model`, `core:domain`, `core:database`, `core:security`, `core:designsystem`, `feature:ledger` – saubere Schichtung, keine Rückwärtsabhängigkeiten |
| DI | manuell (`AppContainer`) – funktioniert, übersichtlich |
| Datenbank | Room 2.7 + SQLCipher 4.9, Schema v1 (8 Tabellen), 12 Trigger, Schema-JSON eingecheckt und = Referenz-DDL |
| Verschlüsselung | DB-Passphrase per Keystore-AES-GCM geschützt, Auto-Backup aus, kein INTERNET |
| Tests | 53 JVM-Unit-Tests + 17 SQLite-Tests grün (CI); 6 Gerätetests nie ausgeführt |
| Build | GitHub Actions baut Debug-APK |

## Funktionsstand
| Funktion | Status |
|---|---|
| Buchung erfassen/bearbeiten/löschen (Ausgabe, Einnahme, Umbuchung) | funktioniert (UI noch nicht auf Gerät geprüft) |
| Monatsübersicht, Salden, Kategorien, Zeitleiste | funktioniert |
| Kategorie-Budgets | funktioniert |
| Konten/Kategorien anlegen | funktioniert; **umbenennen/archivieren fehlt** |
| Erstattungen, Korrekturen | **fehlt** |
| Gesamt-Monatsbudget, Fixkosten/variable Kosten, Prognose | **fehlt** |
| Wiederkehrende Zahlungen | **fehlt** |
| Suche/Filter in Buchungen | **fehlt** |
| Diagramme / Mehrmonatsverlauf | **fehlt** |
| Schnellerfassung | **fehlt** (Formular nutzt Systemtastatur, keine Vorschläge) |
| Startseite (Home) | **fehlt** – App startet in der Haushaltskasse |
| Planung, Projekte, Fahrzeuge, Inventar, Dokumente, Weiterbildung | **fehlen** (nur als NodeType reserviert) |
| Widget, zentrale Suche, Backup/Restore, App-Sperre, LifeMap | **fehlen** |

## Design
- Palette und Formensprache vorhanden, aber Systemschriften, nur 2 Navigationsziele, Textglyphen („←", „‹") statt eigener Icons, Panels wirken wie gewöhnliche Karten, keine Diagramme, keine Einstiegsanimation, kein Overlay-/Detailsystem. Die LifeLine ist Dekor, kein Navigationselement.

## Technische Schwachstellen (werden im Update behoben)
1. **Trigger-Aktualisierung in Migrationen:** `CREATE TRIGGER IF NOT EXISTS` ersetzt geänderte Trigger nicht → Migrationen müssen Trigger explizit löschen und neu anlegen.
2. `LedgerViewModel` übernimmt „heute" nur beim Start – nach Mitternacht bleibt der Monat stehen.
3. Löschen ohne Rückgängig-Möglichkeit (nur Zweistufen-Bestätigung).
4. Material3-`DatePickerDialog` und Systemtastatur brechen den CONTINUUM-Stil.
5. Keine Gerätetests in CI, keine echten Screenshots.
6. Saldenberechnung lädt alle Buchungen – bis ~10 000 Buchungen unkritisch, Benchmark offen.
