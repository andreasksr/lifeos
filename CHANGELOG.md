# CHANGELOG

Format: Was wurde **implementiert**, was wurde **gebaut**, was wurde **getestet** – jeweils mit Nachweis.

## [0.1.1] – 2026-10-21 · Phase 1b: erster echter Android-Build

### Gebaut
- ✅ **Debug-APK gebaut** über GitHub Actions (`andreasksr/lifeos`, Workflow `Android Build`, Commit `b525afb`):
  `./gradlew test :app:assembleDebug`, JDK 17, AGP 8.10.1, Kotlin 2.1.21. APK ≈ 30 MB, enthält
  `libsqlcipher.so` für arm64-v8a, armeabi-v7a, x86, x86_64.
- Erster Build-Lauf scheiterte an genau einem Fehler (`:app` sah `RoomDatabase` nicht) → `room-runtime`
  in `:core:database` als `api` deklariert. Alle übrigen Android-Module kompilierten beim ersten Versuch.

### Getestet
- ✅ **53 Unit-Tests über Gradle grün** (0 Fehler) – bestätigt die vorherigen kotlinc-Ergebnisse.
- ✅ 17 SQLite-Schematests in CI grün.
- ✅ **Room-Schema `1.json` erzeugt, eingecheckt und mit `SchemaSql`-Referenz verglichen:** Spalten,
  Fremdschlüssel und Indizes aller 8 Tabellen identisch.
- ⬜ Weiterhin nicht ausgeführt: instrumentierte Tests (Gerät/Emulator), App auf echtem Gerät.

### CI
- `.github/workflows/android.yml` legt `build.log`, `errors.txt`, Testergebnisse, Room-Schema und APK
  zusätzlich im Branch `ci-output` ab.

## [0.1.0] – 2026-10-20 · Phase 0 + Phase 1 (unverifizierter Android-Build)

### Umgebung
- Cloud-Arbeitsumgebung: JDK 21, Gradle 8.14.3 lokal, **kein Android SDK**, **kein Zugriff** auf
  `dl.google.com`, `maven.google.com`, Maven Central, Gradle Plugin Portal (Proxy-Policy 403).
- Kotlin-Compiler 2.1.21 von GitHub-Releases bezogen; JUnit 4.13.2 aus der lokalen Gradle-Distribution.

### Implementiert
- **Architektur & Dokumente:** ARCHITECTURE (10 Inkonsistenzen mit Entscheidung, 9 Risiken, 7 ADRs),
  CLAUDE, PRODUCT_VISION, DESIGN_SYSTEM, DATA_MODEL, ROADMAP, TEST_PLAN, CHANGELOG.
- **Projekt:** 7 Gradle-Module, Version Catalog, Gradle Wrapper 8.14.3.
- **`core:model`:** `Money`/`CurrencyCode` (Minor Units, Überlaufschutz), `MoneyText` (deterministisches
  deutsches Parsen/Formatieren), `NodeId` (UUID), `NodeType`, `LinkRelation`, Ledger-Entitäten.
- **`core:domain`:** `TransactionValidator`, `LedgerMath` (Salden, Monatsübersicht, Kategorien, Budgets,
  Promille), `SplitAllocator`, Use-Cases (RecordTransaction, DeleteTransaction, SetBudget, CreateAccount,
  CreateCategory, EnsureLedgerDefaults, ObserveLedgerMonth/LedgerSnapshotBuilder, LinkNodes),
  `AppSettings`, Repository-Ports, `IntegrityChecker`-Port.
- **`core:database`:** Room-Entities (8 Tabellen), DAOs mit atomaren `save*`-Methoden (ohne REPLACE auf
  Nodes), `SchemaSql` (Referenz-DDL, 12 Trigger, 3 Prüfabfragen), `SchemaCallback`, SQLCipher-Factory,
  Repositories, `RoomIntegrityChecker`, Migrationsgerüst.
- **`core:security`:** `DatabaseKeyManager` (AES-256-GCM-KEK im Keystore, atomar geschriebene, verschlüsselte
  32-Byte-Passphrase, erkennbarer Schlüsselverlust statt stiller Neuanlage).
- **`core:designsystem`:** CONTINUUM-Tokens, `LifeLine`, `LifeNode`, `LifePanel`, `LifeMetric`,
  `LifeTimelineRow/Header`, `LifeNavigation`, `LifeButton`, `LifeTextField`, `LifeSegmented`,
  `LifeChoiceChip`, `LifeTopBar`, Material-Brücke.
- **`feature:ledger`:** Monatsübersicht (Saldo, Einnahmen/Ausgaben, Ergebnis, Konten, Kategorien mit
  Budgets, Zeitleiste), Buchungsformular (Ausgabe/Einnahme/Umbuchung, Konto, Kategorie, Datum,
  Empfänger, Notiz, Löschen zweistufig), „Konten & Budgets" (Budgets setzen, Konten/Kategorien anlegen),
  Android-freie Präsentationslogik (`EditorFormLogic`, `LedgerFormat`).
- **`app`:** `AppContainer` (manuelle DI), Start im Hintergrund, Wiederherstellungsbildschirm bei
  Schlüsselverlust, `LifeDestination`-Registry, Navigation, System-Bildschirm (Bewegung an/aus,
  Integritätsprüfung, Sicherheitsstatus, Version), Manifest ohne INTERNET, Auto-Backup aus, Icon.

### Gebaut
- ✅ `core:model`, `core:domain`, `feature:ledger/presentation` mit `kotlinc 2.1.21 -Werror` (JVM 17) kompiliert.
- ❌ **Kein Android-Gradle-Build.** Kein APK. Versuch `gradle --offline help` scheitert reproduzierbar an
  „Plugin com.android.application:8.10.1 was not found" (Repositories gesperrt). Room-KSP, Compose-Compiler und Android-Ressourcen wurden
  nie ausgeführt. Android-Quellcode wurde nur mit `kotlinc` auf **Syntaxfehler** geprüft (0 Syntaxfehler;
  Typprüfung ohne Android-Bibliotheken nicht möglich).

### Getestet
- ✅ **53 JUnit-Tests grün** (`tools/verify-core.sh`): model 13, domain 30, feature/ledger 10.
- ✅ **17 SQLite-Tests grün** (`tools/schema_test.py`, SQLite 3.45.1) – SQL direkt aus `SchemaSql.kt` gelesen.
- ✅ Mutationsprobe: absichtlicher Rechenfehler wird von der Suite erkannt.
- ⬜ 6 instrumentierte Tests geschrieben, **nicht ausgeführt** (kein Gerät/Emulator).
- ⬜ Keine UI-, Screenshot- oder manuellen Gerätetests.

### Bekannte offene Punkte
- Erster echter Gradle-Build kann Kompilierfehler in Android-Modulen zeigen (v. a. API-Details von
  Room 2.7 / Compose BOM 2025.05 / SQLCipher 4.9). Versionen im Version Catalog vor dem Build prüfen.
- Room-Schema-JSON (`core/database/schemas/`) existiert noch nicht.
- Kein verschlüsseltes Backup → Datenverlust bei Deinstallation möglich (Hinweis im System-Bildschirm).
- Konten/Kategorien können noch nicht umbenannt/archiviert werden (Phase 2).
- Systemschriften statt eigener Schriften.
