# DATA_MODEL – LifeOS Schema v4

Kanonische Quelle: `core/database/src/main/kotlin/app/lifeos/core/database/schema/SchemaSql.kt`
Room-Entities: `core/database/.../entity/Entities.kt` · geprüft durch `tools/schema_test.py` (SQLite)

## 1. Grundprinzip: Supertyp `node` + `link`

```
                    ┌──────────────┐
          ┌────────►│     node     │◄────────┐
          │ 1:1     │ id (UUID) PK │   1:1   │
          │         │ type, title  │         │
          │         │ created/upd. │         │
          │         │ archived_at  │         │
          │         │ is_sample    │         │
          │         └──────▲───────┘         │
     ┌────┴────┐     from  │  to        ┌────┴──────────────┐
     │ account │   ┌───────┴───────┐    │ ledger_transaction│──┐ 1:n
     └────▲────┘   │     link      │    └─────────▲─────────┘  │
          │        │ relation      │              │            ▼
          │        └───────────────┘   account_id │   ┌──────────────────┐
          └───────────────────────────────────────┘   │ transaction_split│
     ┌──────────┐                                     │ amount_minor     │
     │ category │◄────────────────────────────────────│ category_id      │
     └────▲─────┘                                     └──────────────────┘
          │ 1:n
     ┌────┴───┐      ┌─────────────┐
     │ budget │      │ app_setting │
     └────────┘      └─────────────┘
```

Jedes verknüpfbare Objekt hat genau eine Zeile in `node` (gleiche ID wie die Fachzeile).
`link` verbindet beliebige Nodes mit echten Fremdschlüsseln → referenzielle Integrität auch über
Bereichsgrenzen. Künftige Bereiche (Projekt, Fahrzeug, Dokument …) fügen nur eine Fachtabelle mit
`node_id`-PK hinzu.

## 2. Tabellen

| Tabelle | Spalten | Schlüssel / Fremdschlüssel |
|---|---|---|
| `node` | `id` TEXT, `type` TEXT, `title` TEXT, `created_at` INT, `updated_at` INT, `archived_at` INT?, `is_sample` INT=0 | PK `id`; Index `type` |
| `link` | `id`, `from_node_id`, `to_node_id`, `relation`, `created_at` | beide → `node` CASCADE; UNIQUE(from,to,relation) |
| `account` | `node_id`, `kind`, `currency`, `opening_balance_minor`, `sort_order` | `node_id` → `node` CASCADE |
| `category` | `node_id`, `parent_id`?, `direction`, `sort_order` | → `node` CASCADE; `parent_id` → `category` RESTRICT |
| `ledger_transaction` | `node_id`, `kind`, `account_id`, `counter_account_id`?, `amount_minor`, `currency`, `booking_date` (ISO), `payee`?, `note`? | → `node` CASCADE; Konten RESTRICT; Index Datum/Konten |
| `transaction_split` | `id`, `transaction_id`, `category_id`, `amount_minor`, `memo`?, `position` | → Buchung CASCADE; → Kategorie RESTRICT |
| `budget` | `id`, `category_id`, `valid_from` (`yyyy-MM`), `amount_minor`, `currency` | → Kategorie CASCADE; UNIQUE(category, valid_from) |
| `app_setting` | `key`, `value` | PK `key` |
| **v2** `recurring_payment` | `node_id`, `kind`, `account_id`, `category_id`, `amount_minor`, `currency`, `interval_months` (1/3/12 …), `anchor_date`, `end_date`?, `payee`?, `note`?, `active`, `is_fixed_cost` | → `node` CASCADE; Konto/Kategorie RESTRICT |
| **v2** `recurring_booking` | `recurring_id`, `due_date`, `status`, `transaction_id`? | PK (recurring, due_date); Buchung höchstens einmal je Fälligkeit (UNIQUE) |
| **v2** `total_budget` | `id`, `valid_from`, `amount_minor`, `currency` | UNIQUE(valid_from) |
| **v3** `project` | `node_id`, `description`?, `category`?, `status`, `start_date`, `target_date`?, `progress_mode` (TASKS/MILESTONES/MANUAL), `manual_progress`? (‰), `budget_minor`?, `notes`? | → `node` CASCADE |
| **v3** `milestone` | `node_id`, `project_id`, `due_date`?, `done_at`?, `sort_order` | → Projekt CASCADE; Knoten per Trigger mitgelöscht |
| **v3** `task` | `node_id`, `description`?, `status`, `priority`, `due_date`?, `due_time`?, `repeat_unit`?, `repeat_interval`, `repeat_anchor`?, `project_id`?, `completed_at`? | Projekt → SET NULL (Aufgabe bleibt) |
| **v3** `task_completion` | `id`, `task_id`, `completed_at`, `due_date`? | Historie wiederkehrender Aufgaben |
| **v3** `calendar_event` | `node_id`, `start_at`, `end_at` (`yyyy-MM-ddTHH:mm`), `all_day`, `location`?, `note`?, `repeat_*` | → `node` CASCADE |
| **v3** `reminder` | `id`, `node_id`, `minutes_before`, `enabled`, `last_fired_for`?, `snoozed_until`? | → Aufgabe/Termin CASCADE |
| **v4** `vehicle` | `node_id`, `make`, `model`, `build_year`?, `plate`?, `vin`?, `odometer_km`?, `purchase_date`?, `purchase_price_minor`?, `note`? | → `node` CASCADE |
| **v4** `vehicle_event` | `node_id`, `vehicle_id`, `kind`, `event_date`, `odometer_km`?, `done`, `note`? | → Fahrzeug CASCADE; Knoten per Trigger mitgelöscht |
| **v4** `asset` | `node_id`, `kind`, `manufacturer`?, `model`?, `serial_number`?, `purchase_date`?, `price_minor`?, `warranty_until`?, `location`?, `note`? | → `node` CASCADE |
| **v4** `document` | `node_id`, `file_name`, `mime_type`, `size_bytes`, `sha256`, `imported_at`, `doc_date`?, `note`?, `ocr_text`? (vorbereitet, leer) | UNIQUE(`sha256`) – dieselbe Datei nie doppelt |
| **v4** `subject` | `node_id` (Typ COURSE), `part` (BQ/HQ/OTHER), `sort_order`, `note`? | → `node` CASCADE |
| **v4** `topic` | `node_id`, `subject_id`, `status` (OPEN/IN_PROGRESS/DONE), `sort_order` | → Fach CASCADE; Knoten per Trigger mitgelöscht |
| **v4** `study_session` | `id`, `subject_id`?, `start_at`, `end_at`? (NULL = läuft), `source` (TIMER/MANUAL), `note`? | Fach → SET NULL (Lernzeit bleibt) |
| **v4** `exam` | `node_id`, `part`, `target_month`? (`yyyy-MM`), `target_label`?, `confirmed_date`?, `location`?, `note`? | Zielzeitraum ≠ bestätigter Termin |

### Wertebereiche (als TEXT gespeichert, ADR-005)
- `node.type`: ACCOUNT, CATEGORY, TRANSACTION · reserviert: PROJECT, TASK, EVENT, ASSET, VEHICLE, CONTRACT, DOCUMENT, NOTE, COURSE, TOPIC
- `link.relation`: CONCERNS, PART_OF, DOCUMENTS, RELATES_TO
- `account.kind`: CASH, CHECKING, SAVINGS, CREDIT_CARD
- `category.direction`: EXPENSE, INCOME
- `ledger_transaction.kind`: EXPENSE, INCOME, TRANSFER

## 3. Invarianten und wo sie erzwungen werden

| # | Regel | Domäne | DB-Trigger | Integritätsprüfer |
|---|---|---|---|---|
| D-1 | Betrag > 0 (Richtung über `kind`) | ✔ | ✔ `LIFEOS_TX_AMOUNT` | |
| D-2 | Umbuchung: Gegenkonto vorhanden und ≠ Konto | ✔ | ✔ `LIFEOS_TX_TRANSFER` | |
| D-3 | Gegenkonto nur bei Umbuchung | ✔ | ✔ `LIFEOS_TX_COUNTER_ACCOUNT` | |
| D-4 | Buchungswährung = Kontowährung (beide Konten) | ✔ | ✔ `LIFEOS_TX_CURRENCY` | |
| D-5 | Datum ist echtes ISO-Datum | (Typ) | ✔ `LIFEOS_TX_DATE` | |
| D-6 | Einnahme/Ausgabe: Σ Splits = Betrag, ≥ 1 Split | ✔ | – (zeilenweise unmöglich) | ✔ `SPLIT_SUM` |
| D-7 | Umbuchung hat keine Splits | ✔ | ✔ `LIFEOS_SPLIT_ON_TRANSFER` | ✔ |
| D-8 | Split-Kategorie passt zur Richtung | ✔ | ✔ `LIFEOS_SPLIT_DIRECTION` | |
| D-9 | Art einer Buchung mit Splits unveränderlich | (Formular) | ✔ `LIFEOS_TX_KIND_LOCKED` | |
| D-10 | Node-Typ passt zur Fachtabelle, unveränderlich | (Repo) | ✔ `LIFEOS_NODE_TYPE*` | ✔ `ORPHAN_NODE` |
| D-11 | Kontowährung nach erster Buchung gesperrt | – | ✔ `LIFEOS_ACCOUNT_CURRENCY_LOCKED` | |
| D-12 | Kategorie-Richtung gesperrt, sobald verwendet | – | ✔ `LIFEOS_CATEGORY_DIRECTION_LOCKED` | |
| D-13 | Kein Selbst-Link, gültige Relation, keine Duplikate | ✔ | ✔ + UNIQUE | |
| D-14 | Benutzte Konten/Kategorien nicht löschbar | – | FK RESTRICT | ✔ `FOREIGN_KEY` |
| D-15 | Budget > 0, Monat `yyyy-MM`, nur Ausgabenkategorien, 1 je Monat | ✔ | ✔ + UNIQUE | |
| D-16 | Wiederkehrende Zahlung wird nie automatisch gebucht; je Fälligkeit höchstens eine Buchung | ✔ | ✔ UNIQUE | |
| D-17 | Aufgaben/Termine/Projekte: gültige Status, echte Daten, Ziel ≥ Start, Wiederholung nur mit Datum | ✔ | ✔ `LIFEOS_TASK_*`, `LIFEOS_PROJECT_*`, `LIFEOS_EVENT_*` | |
| D-18 | Höchstens EINE laufende Lernzeit; Ende > Start; ≤ 12 h | ✔ | ✔ `LIFEOS_SESSION_*` | |
| D-19 | Nachgetragene Lernzeit überschneidet sich nicht; Statistik zählt Überschneidungen nur einmal | ✔ (Vereinigung) | – | |
| D-20 | Dokument: SHA-256-Hash gültig und eindeutig | ✔ | ✔ `LIFEOS_DOCUMENT_HASH` + UNIQUE | |
| D-21 | Fahrzeug/Inventar: plausible Jahre, km ≥ 0, Garantie ≥ Kauf | ✔ | ✔ `LIFEOS_VEHICLE_*`, `LIFEOS_ASSET_*` | |
| D-22 | Prüfung: Zielmonat `yyyy-MM` (01–12), bestätigtes Datum echt | ✔ | ✔ `LIFEOS_EXAM_*` | |

## 4. Abgeleitete Werte (nie gespeichert)
| Wert | Berechnung (`LedgerMath`) |
|---|---|
| Kontosaldo | `opening_balance_minor + Σ Wirkung` (Ausgabe −, Einnahme +, Umbuchung −/+ ) |
| Gesamtsaldo | Σ Kontosalden je Währung (Umbuchungen neutral) |
| Monatseinnahmen/-ausgaben | Σ Beträge je Art im Monat, Umbuchungen ausgenommen |
| Kategoriesumme | Σ Splits je Kategorie im Monat |
| Gültiges Budget | jüngstes `valid_from ≤ Monat` je Kategorie |
| Auslastung | `spent · 1000 / limit` in Promille, abgerundet (BigInteger, kein Überlauf) |

## 5. Löschsemantik
- Buchung löschen = `DELETE FROM node` → Kaskade auf Buchung, Splits, Links. Verknüpfte Objekte bleiben.
- Konto/Kategorie mit Buchungen: Löschen wird von der DB verweigert → stattdessen archivieren (`archived_at`).
- **Nie `INSERT OR REPLACE` auf `node`** – würde per Kaskade Daten vernichten (Test belegt).

## 6. Identität & Zeit
- IDs: UUID v4, kleingeschrieben, unveränderlich, gerätunabhängig (Export/Import/Sync-fähig).
- `created_at`/`updated_at`: UTC-Epoch-Millisekunden.
- `booking_date`: lokales Kalenderdatum als ISO-Text – keine Zeitzonenverschiebung über Monatsgrenzen.

## 7. Migrationen
Regeln in `Migrations.kt` und CLAUDE.md §5. Alle Migrationen sind **rein additiv** (neue Tabellen,
keine Spaltenänderung, keine Löschung). Room-Schemata `1.json`–`4.json` sind eingecheckt.

| Migration | Inhalt | Geprüft |
|---|---|---|
| 1 → 2 | wiederkehrende Zahlungen, Gesamtbudget | SQLite (`schema_test.py`), Emulator (`MigrationTestHelper`) |
| 2 → 3 | Planung, Projekte, Meilensteine, Erinnerungen | SQLite, Emulator |
| 3 → 4 | Fahrzeuge, Inventar, Dokumente, Weiterbildung | SQLite, Emulator (Kette 1 → 4 mit Altdaten) |

## 8. Dateien außerhalb der Datenbank
- **Dokumenttresor** `noBackupFilesDir/vault/<sha256>.lv`: AES-256-GCM, Schlüssel aus dem
  Datenbankgeheimnis abgeleitet, AAD = Hash. Inhaltsadressiert → keine Doppelablage.
- **Sicherung** (`*.lifeosbackup`): ZIP mit Klartext-Abzug der DB + Dokumenten, vollständig verschlüsselt
  (PBKDF2-HMAC-SHA256 210 000 Iterationen → AES-256-GCM in 1-MiB-Segmenten, Abschneiden/Vertauschen erkennbar).

## 9. LifeMap-Beziehungen
Die LifeMap kombiniert freie Verknüpfungen (`link`) mit festen Beziehungen (`task.project_id`,
`milestone.project_id`, `vehicle_event.vehicle_id`, `topic.subject_id`) – immer nur für EIN ausgewähltes Objekt.

## 10. Geplante Erweiterungen (je eigene Migration)
| Tabelle | Zweck |
|---|---|
| `contract` | Verträge mit Kündigungsfristen (heute: wiederkehrende Zahlungen) |
| `ledger_audit` | Änderungsprotokoll für Buchungen |
| FTS-Index | Volltextsuche inkl. OCR-Text, sobald OCR existiert |
