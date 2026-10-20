# DATA_MODEL – LifeOS Schema v1

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

## 2. Tabellen (v1)

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
Regeln in `Migrations.kt` und CLAUDE.md §5. Room exportiert das Schema nach `core/database/schemas/`
(entsteht beim ersten echten Build – **bisher nicht erzeugt**).

## 8. Geplante Erweiterungen (je eigene Migration)
| Phase | Tabellen |
|---|---|
| 2 | `recurring_rule` (Fixkosten/Abos), `contract`, `savings_goal`, `ledger_audit` (Änderungsprotokoll) |
| 3 | `attachment` (Belege, verschlüsselte Dateien), Export-Manifest |
| 4 | `project`, `project_task`, `material_item`, `asset`, `vehicle`, `maintenance_event`, `warranty` |
| 5 | `event`, `task`, `reminder`, `note`, `document`, FTS4-Index für Suche |
| 6 | `course`, `topic`, `study_session`, `exam` |
