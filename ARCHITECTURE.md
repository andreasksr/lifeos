# LifeOS – Architektur & kritische Analyse

Stand: 2026-10-20 · Phase 0/1 · verbindlich, wird fortlaufend gepflegt.

## 1. Kritische Analyse der Anforderungen

### 1.1 Inkonsistenzen und Spannungsfelder – mit Entscheidung

| # | Spannungsfeld | Entscheidung |
|---|---|---|
| I-1 | **„Maximal futuristisch" vs. „äußerst einfach bedienbar"** | Futurismus lebt ausschließlich in Licht, Linie, Typografie und Bewegung – **nicht** in Interaktionsmustern. Gesten, Navigation, Formulare und Rückwege bleiben Android-Standard (Back-Geste, große Touch-Ziele ≥ 48 dp, klare Labels). Jede Animation ist abschaltbar und respektiert die System-Einstellung „Animationen entfernen". |
| I-2 | **Android-Keystore-Schlüssel vs. verschlüsselte, wiederherstellbare Backups** | Keystore-Schlüssel sind nicht exportierbar und gehen bei Gerätewechsel/Werksreset verloren. Ein Backup, das mit dem Keystore-Schlüssel verschlüsselt ist, wäre auf einem neuen Gerät **wertlos**. Daher zwei getrennte Schlüsselwelten: (a) Datenbankschlüssel → durch Keystore geschützt, nur lokal; (b) Backup-Schlüssel → aus einer Nutzer-Passphrase abgeleitet (PBKDF2-HMAC-SHA256 bzw. später Argon2id), unabhängig vom Gerät. |
| I-3 | **Android Auto-Backup vs. Keystore-verschlüsselte DB** | Auto-Backup würde eine verschlüsselte DB ohne Schlüssel wiederherstellen → App startet nicht. Auto-Backup ist für DB und Schlüsseldatei **deaktiviert** (`allowBackup=false`, `dataExtractionRules`). Sicherung erfolgt ausschließlich über das eigene verschlüsselte Backup. |
| I-4 | **„Ein Datensatz in mehreren Bereichen" vs. referenzielle Integrität** | Polymorphe Verknüpfungstabellen (`target_type`, `target_id`) können keine Fremdschlüssel haben. Lösung: **Supertyp-Tabelle `node`** – jedes verknüpfbare Objekt besitzt eine Zeile in `node`, die Fachtabelle hängt per 1:1-Fremdschlüssel daran. Verknüpfungen (`link`) referenzieren `node` mit echten Fremdschlüsseln. |
| I-5 | **„Keine erfundenen Statistiken" vs. „Beispieldaten"** | Beispieldaten nur über das Flag `node.is_sample = 1`, sichtbar gekennzeichnet und mit einem Schritt vollständig entfernbar. Standardkategorien und das Startkonto sind **Konfiguration**, keine Beispieldaten (keine Beträge). |
| I-6 | **„Keine funktionslosen Schaltflächen" vs. sechs geplante Bereiche** | Die Navigation zeigt nur Bereiche, die tatsächlich implementiert sind. Geplante Bereiche erscheinen erst mit ihrer Phase. Die Navigationsarchitektur ist trotzdem bereits für alle Bereiche ausgelegt (`LifeDestination`). |
| I-7 | **„Exakte Finanzberechnungen" vs. Fließkomma** | Beträge ausschließlich als `Long` in kleinster Währungseinheit (Cent). Keine `Double`/`Float` in Geldpfaden. Überlauf wird mit `Math.addExact` erkannt statt still verfälscht. |
| I-8 | **„Prüfbar" vs. gespeicherte Salden** | Salden, Summen, Budgetauslastung werden **nie gespeichert**, sondern immer aus Buchungen abgeleitet. Damit gibt es keine zweite Wahrheit, die auseinanderlaufen kann. Ein Integritätsprüfer deckt Verstöße gegen nicht per Trigger erzwingbare Regeln auf. |
| I-9 | **Ersatzteilkauf mit mehreren Kategorien (z. B. Baumarkt: Werkzeug + Fahrzeug)** | Jede Einnahme/Ausgabe besteht aus ≥ 1 **Aufteilung** (`transaction_split`) mit Summe = Buchungsbetrag. Die einfache Oberfläche erzeugt genau eine Aufteilung; Mehrfachaufteilung später ohne Migration. |
| I-10 | **Umbuchungen** (Konto → Sparkonto) | Eigener Buchungstyp `TRANSFER`, zählt **nicht** als Einnahme oder Ausgabe in Statistiken, verändert aber beide Kontosalden. |

### 1.2 Technische Risiken

| Risiko | Auswirkung | Gegenmaßnahme |
|---|---|---|
| R-1 Verlust des Keystore-Schlüssels (Werksreset, Biometrie-Änderung bei falscher Konfiguration, OEM-Fehler) | Datenbank unlesbar | Schlüssel **ohne** `setUserAuthenticationRequired` (sonst Invalidierung bei Biometrie-Änderung); regelmäßiges verschlüsseltes Backup mit Erinnerung (Phase 3). |
| R-2 Room + SQLCipher-Kompatibilität | Build-/Laufzeitfehler bei Versionssprüngen | Verschlüsselung ab Schema v1 (keine spätere Umverschlüsselung nötig), Versionen im Version Catalog gebündelt, instrumentierter Test öffnet DB mit Schlüssel. |
| R-3 Schema-Evolution über Jahre | Datenverlust bei Updates | `exportSchema = true`, Schema-JSON versioniert, **keine** `fallbackToDestructiveMigration`, jede Migration mit `MigrationTestHelper` getestet. |
| R-4 Invarianten, die Room nicht abbildet (CHECK-Constraints) | Inkonsistente Finanzdaten | SQLite-Trigger (in `onCreate` und in jeder Migration angelegt) + Domänenvalidierung + Integritätsprüfer. |
| R-5 Animationen auf schwächeren Geräten | Ruckeln, Akkuverbrauch | Linien über `Canvas`/`drawBehind`, keine Recomposition pro Frame, globale Motion-Steuerung, Endlos-Animationen nur auf sichtbaren Elementen. |
| R-6 Zeitzonen / Datumsgrenzen | Buchung im falschen Monat | Buchungsdatum als lokales Kalenderdatum (`LocalDate`, ISO-Text), technische Zeitstempel in UTC-Millisekunden. |
| R-7 Mehrwährung | Falsche Summen | v1: Summen nur innerhalb einer Währung; Konten tragen Währung; Trigger erzwingt Buchungswährung = Kontowährung. Umrechnung erst mit eigener Phase. |
| R-8 Umfang (6 Bereiche) | Halbfertige Module | Strikte Phasen mit Abnahmekriterien, vertikale Schnitte, keine Platzhalter-UI. |
| R-9 Diese Entwicklungsumgebung | Kein Android-Build möglich (Maven-Repos gesperrt) | Domänenkern rein in Kotlin/JVM → real kompiliert und getestet; Schema-Trigger real gegen SQLite getestet; Android-Teile als „nicht gebaut" gekennzeichnet. |

## 2. Gesamtarchitektur

### 2.1 Schichten

```
┌──────────────────────────────────────────────────────────────┐
│ :app                 Shell, Navigation, DI-Container, Theme │
├──────────────────────────────────────────────────────────────┤
│ :feature:ledger      Haushaltskasse (UI + ViewModels)        │
│ :feature:… (später)  today · projects · assets · learning · │
│                      archive                                 │
├──────────────────────────────────────────────────────────────┤
│ :core:designsystem   CONTINUUM-Komponenten (Compose)         │
├──────────────────────────────────────────────────────────────┤
│ :core:domain         Use-Cases, Regeln, Repository-Ports     │  rein Kotlin/JVM
│ :core:model          Geld, IDs, Entitäten                    │  rein Kotlin/JVM
├──────────────────────────────────────────────────────────────┤
│ :core:database       Room + SQLCipher, Trigger, Repos        │  Android
│ :core:security       Keystore-Schlüsselverwaltung            │  Android
├──────────────────────────────────────────────────────────────┤
│ :integration:ai      (später, optional) – darf NIE von core  │
│                      referenziert werden                     │
└──────────────────────────────────────────────────────────────┘
```

Abhängigkeitsregel: Pfeile zeigen nur nach unten. `:core:domain` kennt weder Android noch Room. `:feature:*` kennen nur `:core:domain`, `:core:model`, `:core:designsystem` – nie `:core:database`. Die Verdrahtung passiert ausschließlich im `AppContainer` von `:app`.

### 2.2 Muster

- **Präsentation:** Jetpack Compose, unidirektionaler Datenfluss. ViewModel hält `StateFlow<UiState>`, Screens sind zustandslose Funktionen `(state, onEvent)`.
- **Domäne:** Use-Cases als kleine Klassen mit `operator fun invoke`. Fehler als typisierte Ergebnisse (`LedgerResult`), keine Exceptions für erwartbare Validierungsfehler.
- **Daten:** Repository-Ports in `:core:domain`, Implementierungen in `:core:database`. Room-Entities sind ein reines Persistenzdetail und verlassen das Modul nie.
- **DI:** Manuelle Konstruktor-Injektion über `AppContainer` (ADR-003). Bewusst ohne Hilt in Phase 1 – weniger Annotation-Processing, leichter prüfbar; Migration auf Hilt ist jederzeit möglich, da alle Klassen bereits konstruktor-injiziert sind.
- **Nebenläufigkeit:** Kotlin Coroutines + Flow; Room-Flows für reaktive Anzeigen.
- **Offline-First:** Die lokale Datenbank ist die einzige Wahrheit. Netzwerk wird von keiner Kernfunktion benötigt; das App-Manifest fordert in Phase 1 **keine** `INTERNET`-Berechtigung an.

### 2.3 Gemeinsame Konzepte für alle Module

| Konzept | Zweck |
|---|---|
| `NodeId` (UUID) | Stabile, gerätunabhängige ID jedes Objekts; überlebt Export/Import/späteren Sync. |
| `node` (Supertyp) | Titel, Typ, Zeitstempel, Archivierung, Beispieldaten-Flag – Grundlage für globale Suche, Archiv und Verknüpfungen. |
| `link` | Gerichtete, typisierte Verknüpfung zweier Nodes (`CONCERNS`, `PART_OF`, `DOCUMENTS`, …). Löst die Anforderung „eine Buchung – mehrere Bereiche". |
| `LifeDestination` | Zentrale Liste aller Navigationsziele; neue Module registrieren sich dort. |
| `AppSettings` | Schlüssel/Wert-Einstellungen in der verschlüsselten DB (z. B. Animationen an/aus). |

### 2.4 Das Ersatzteil-Beispiel

```
node(TRANSACTION "Bremsbeläge", 89,90 €) ── ledger_transaction + transaction_split(Kategorie Fahrzeug)
        │ link CONCERNS
        ├──────────────────────────► node(VEHICLE "Golf VII")          (Phase 4)
        │ link PART_OF
        └──────────────────────────► node(PROJECT "Bremsen erneuern")  (Phase 4)
```

Haushaltskasse, Fahrzeughistorie und Projekt zeigen dieselbe Buchung. Summiert wird ausschließlich in der Haushaltskasse über `transaction_split`; Fahrzeug und Projekt **lesen** Beträge über den Link und addieren dieselbe eine Buchung – es gibt keine Kopie.

## 3. Sicherheit

- Datenbank: SQLCipher (AES-256). Passphrase = 32 Zufallsbytes, verschlüsselt (AES-256-GCM) mit einem nicht-exportierbaren Keystore-Schlüssel, abgelegt in `noBackupFilesDir`.
- Kein Logging von Fachdaten. Keine Analytics, kein Netzwerk in Phase 1.
- Backups (Phase 3): eigenes Containerformat, AES-256-GCM, Schlüssel aus Passphrase, Integritätsprüfung vor dem Import, Import nur in leere DB oder nach expliziter Bestätigung.
- KI (Phase 7+): eigenes Modul, explizite Einwilligung pro Funktion, Datenminimierung, standardmäßig aus.

## 4. Architecture Decision Records (Kurzform)

- **ADR-001** Supertyp `node` + `link` statt polymorpher Links – wegen referenzieller Integrität.
- **ADR-002** Geld als `Long` Minor Units; Summen nie persistiert.
- **ADR-003** Manuelle DI in Phase 1.
- **ADR-004** SQLCipher ab Schema v1.
- **ADR-005** Enums als TEXT gespeichert (lesbar, robust gegen Umsortierung); Mapping in `:core:database`.
- **ADR-006** Buchungsdatum als ISO-`TEXT` (`yyyy-MM-dd`) – sortierbar, zeitzonenneutral, menschenlesbar in Exporten.
- **ADR-007** Jede Einnahme/Ausgabe hat ≥ 1 Split; Umbuchungen haben keine Splits.

## 5. Stand 0.5.0 – Module und ergänzende Entscheidungen (2026-10-21)

```
:app                Shell, Navigation, DI, Benachrichtigungen, Dokumenttresor-Anbindung, Sicherung,
                    App-Sperre, Widget (Glance), Suche, LifeMap
:feature:ledger · :feature:home · :feature:planning · :feature:projects · :feature:life
:core:designsystem  CONTINUUM v2
:core:domain        + planning, projects, life (Fahrzeuge, Inventar, Dokumente, Lernen), search, map
:core:model
:core:database      Schema v4, Migrationen 1→4, DatabaseSnapshot (Sicherung)
:core:security      Keystore-Schlüssel + vault/ (FileVault, BackupCipher – reines JVM, getestet)
```

- **ADR-008** Feste Beziehungen als Spalten (Aufgabe→Projekt, Eintrag→Fahrzeug, Thema→Fach), freie
  Beziehungen als `link`. Die LifeMap liest beides, zeigt aber immer nur EIN Objekt mit direkten Nachbarn.
- **ADR-009** Dokumente inhaltsadressiert (`sha256`, UNIQUE) in einem AES-256-GCM-Tresor außerhalb der DB;
  Schlüssel aus dem DB-Geheimnis abgeleitet. Dieselbe Datei existiert nie doppelt.
- **ADR-010** Sicherung = Klartext-Abzug (`sqlcipher_export`) + Dokumente als ZIP, vollständig mit
  PBKDF2 (210 000) → AES-256-GCM in 1-MiB-Segmenten verschlüsselt (Abschneiden/Vertauschen erkennbar).
  Wiederherstellung prüft vollständig vor jeder Änderung, verschlüsselt mit dem Schlüssel des Zielgeräts neu,
  behält den alten Stand als `*.pre-restore` bis zum erfolgreichen Start (automatischer Rückbau).
- **ADR-011** Erinnerungen: genau ein exakter Alarm für die nächste fällige Erinnerung, Neuplanung bei
  Start, Neustart, Update und Zeitänderung. Erledigte Aufgaben werden nie erinnert.
- **ADR-012** App-Sperre schützt die Oberfläche (BiometricPrompt/Gerätesperre); die Datenverschlüsselung
  hängt bewusst nicht an der Biometrie (R-1).
- **ADR-013** Prüfungs-Zielzeiträume sind Daten ohne Datum (`target_month` + Bezeichnung); erst
  `confirmed_date` erzeugt einen exakten Countdown und Hinweise.
