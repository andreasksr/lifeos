package app.lifeos.core.database.schema

/**
 * Kanonisches SQL des LifeOS-Schemas (aktuell v4).
 *
 * - `TABLE_*` / `INDEX_*`: Referenz-DDL. Room erzeugt die Tabellen selbst aus den Entities; diese
 *   Texte dokumentieren das erwartete Ergebnis und werden von `tools/schema_test.py` (SQLite) sowie
 *   vom instrumentierten `SchemaReferenceTest` gegen Room geprüft.
 * - `TRIGGER_*`: Invarianten, die Room nicht abbilden kann. Werden bei jedem Öffnen der DB und in
 *   jeder Migration **gelöscht und neu angelegt** ([TriggerInstaller]), damit geänderte Definitionen
 *   garantiert wirksam werden (`CREATE TRIGGER IF NOT EXISTS` allein würde alte Fassungen behalten).
 * - `QUERY_*`: Prüfabfragen für den Integritätsprüfer.
 *
 * Datumsprüfung: `date(x, '+0 days') IS NOT x`. Der Modifikator erzwingt die Normalisierung – ältere
 * SQLite-Versionen (Android 14: 3.39) geben `date('2026-02-30')` sonst unverändert zurück.
 *
 * Fehlermeldungen beginnen mit `LIFEOS_`, damit die Datenschicht sie eindeutig zuordnen kann.
 * Änderungen hier erfordern eine Schema-Migration und neue Tests.
 */
object SchemaSql {

    // ───────────────────────────── Tabellen ─────────────────────────────

    const val TABLE_NODE = """CREATE TABLE IF NOT EXISTS `node` (
        `id` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL,
        `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `archived_at` INTEGER,
        `is_sample` INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY(`id`))"""

    const val TABLE_LINK = """CREATE TABLE IF NOT EXISTS `link` (
        `id` TEXT NOT NULL, `from_node_id` TEXT NOT NULL, `to_node_id` TEXT NOT NULL,
        `relation` TEXT NOT NULL, `created_at` INTEGER NOT NULL,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`from_node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(`to_node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""

    const val TABLE_ACCOUNT = """CREATE TABLE IF NOT EXISTS `account` (
        `node_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `currency` TEXT NOT NULL,
        `opening_balance_minor` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL,
        PRIMARY KEY(`node_id`),
        FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""

    const val TABLE_CATEGORY = """CREATE TABLE IF NOT EXISTS `category` (
        `node_id` TEXT NOT NULL, `parent_id` TEXT, `direction` TEXT NOT NULL, `sort_order` INTEGER NOT NULL,
        PRIMARY KEY(`node_id`),
        FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(`parent_id`) REFERENCES `category`(`node_id`) ON UPDATE NO ACTION ON DELETE RESTRICT)"""

    const val TABLE_LEDGER_TRANSACTION = """CREATE TABLE IF NOT EXISTS `ledger_transaction` (
        `node_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `account_id` TEXT NOT NULL,
        `counter_account_id` TEXT, `amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL,
        `booking_date` TEXT NOT NULL, `payee` TEXT, `note` TEXT,
        PRIMARY KEY(`node_id`),
        FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(`account_id`) REFERENCES `account`(`node_id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
        FOREIGN KEY(`counter_account_id`) REFERENCES `account`(`node_id`) ON UPDATE NO ACTION ON DELETE RESTRICT)"""

    const val TABLE_TRANSACTION_SPLIT = """CREATE TABLE IF NOT EXISTS `transaction_split` (
        `id` TEXT NOT NULL, `transaction_id` TEXT NOT NULL, `category_id` TEXT NOT NULL,
        `amount_minor` INTEGER NOT NULL, `memo` TEXT, `position` INTEGER NOT NULL,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`transaction_id`) REFERENCES `ledger_transaction`(`node_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(`category_id`) REFERENCES `category`(`node_id`) ON UPDATE NO ACTION ON DELETE RESTRICT)"""

    const val TABLE_BUDGET = """CREATE TABLE IF NOT EXISTS `budget` (
        `id` TEXT NOT NULL, `category_id` TEXT NOT NULL, `valid_from` TEXT NOT NULL,
        `amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`category_id`) REFERENCES `category`(`node_id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""

    const val TABLE_APP_SETTING = """CREATE TABLE IF NOT EXISTS `app_setting` (
        `key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))"""

    // ── ab v2 ──
    const val TABLE_RECURRING_PAYMENT = """CREATE TABLE IF NOT EXISTS `recurring_payment` (`node_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `account_id` TEXT NOT NULL, `category_id` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `interval_months` INTEGER NOT NULL, `anchor_date` TEXT NOT NULL, `end_date` TEXT, `payee` TEXT, `note` TEXT, `active` INTEGER NOT NULL, `is_fixed_cost` INTEGER NOT NULL, PRIMARY KEY(`node_id`), FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`account_id`) REFERENCES `account`(`node_id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`category_id`) REFERENCES `category`(`node_id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"""

    const val TABLE_RECURRING_BOOKING = """CREATE TABLE IF NOT EXISTS `recurring_booking` (`recurring_id` TEXT NOT NULL, `due_date` TEXT NOT NULL, `status` TEXT NOT NULL, `transaction_id` TEXT, PRIMARY KEY(`recurring_id`, `due_date`), FOREIGN KEY(`recurring_id`) REFERENCES `recurring_payment`(`node_id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`transaction_id`) REFERENCES `ledger_transaction`(`node_id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""

    // ── ab v3 ──
    const val TABLE_PROJECT = """CREATE TABLE IF NOT EXISTS `project` (`node_id` TEXT NOT NULL, `description` TEXT, `category` TEXT, `status` TEXT NOT NULL, `start_date` TEXT NOT NULL, `target_date` TEXT, `progress_mode` TEXT NOT NULL, `manual_progress` INTEGER, `budget_minor` INTEGER, `notes` TEXT, PRIMARY KEY(`node_id`), FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""

    const val TABLE_MILESTONE = """CREATE TABLE IF NOT EXISTS `milestone` (`node_id` TEXT NOT NULL, `project_id` TEXT NOT NULL, `due_date` TEXT, `done_at` INTEGER, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`node_id`), FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`project_id`) REFERENCES `project`(`node_id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""

    const val TABLE_TASK = """CREATE TABLE IF NOT EXISTS `task` (`node_id` TEXT NOT NULL, `description` TEXT, `status` TEXT NOT NULL, `priority` TEXT NOT NULL, `due_date` TEXT, `due_time` TEXT, `repeat_unit` TEXT, `repeat_interval` INTEGER NOT NULL, `repeat_anchor` TEXT, `project_id` TEXT, `completed_at` INTEGER, PRIMARY KEY(`node_id`), FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`project_id`) REFERENCES `project`(`node_id`) ON UPDATE NO ACTION ON DELETE SET NULL )"""

    const val TABLE_TASK_COMPLETION = """CREATE TABLE IF NOT EXISTS `task_completion` (`id` TEXT NOT NULL, `task_id` TEXT NOT NULL, `completed_at` INTEGER NOT NULL, `due_date` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`task_id`) REFERENCES `task`(`node_id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""

    const val TABLE_CALENDAR_EVENT = """CREATE TABLE IF NOT EXISTS `calendar_event` (`node_id` TEXT NOT NULL, `start_at` TEXT NOT NULL, `end_at` TEXT NOT NULL, `all_day` INTEGER NOT NULL, `location` TEXT, `note` TEXT, `repeat_unit` TEXT, `repeat_interval` INTEGER NOT NULL, PRIMARY KEY(`node_id`), FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""

    const val TABLE_REMINDER = """CREATE TABLE IF NOT EXISTS `reminder` (`id` TEXT NOT NULL, `node_id` TEXT NOT NULL, `minutes_before` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `last_fired_for` INTEGER, `snoozed_until` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""

    const val TABLE_TOTAL_BUDGET = """CREATE TABLE IF NOT EXISTS `total_budget` (`id` TEXT NOT NULL, `valid_from` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, PRIMARY KEY(`id`))"""

    // ── ab v4 ──
    const val TABLE_VEHICLE = """CREATE TABLE IF NOT EXISTS `vehicle` (`node_id` TEXT NOT NULL, `make` TEXT NOT NULL, `model` TEXT NOT NULL, `build_year` INTEGER, `plate` TEXT, `vin` TEXT, `odometer_km` INTEGER, `purchase_date` TEXT, `purchase_price_minor` INTEGER, `note` TEXT, PRIMARY KEY(`node_id`), FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""

    const val TABLE_VEHICLE_EVENT = """CREATE TABLE IF NOT EXISTS `vehicle_event` (`node_id` TEXT NOT NULL, `vehicle_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `event_date` TEXT NOT NULL, `odometer_km` INTEGER, `done` INTEGER NOT NULL, `note` TEXT, PRIMARY KEY(`node_id`), FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`vehicle_id`) REFERENCES `vehicle`(`node_id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""

    const val TABLE_ASSET = """CREATE TABLE IF NOT EXISTS `asset` (`node_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `manufacturer` TEXT, `model` TEXT, `serial_number` TEXT, `purchase_date` TEXT, `price_minor` INTEGER, `warranty_until` TEXT, `location` TEXT, `note` TEXT, PRIMARY KEY(`node_id`), FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""

    const val TABLE_DOCUMENT = """CREATE TABLE IF NOT EXISTS `document` (`node_id` TEXT NOT NULL, `file_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, `sha256` TEXT NOT NULL, `imported_at` INTEGER NOT NULL, `doc_date` TEXT, `note` TEXT, `ocr_text` TEXT, PRIMARY KEY(`node_id`), FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""

    const val TABLE_SUBJECT = """CREATE TABLE IF NOT EXISTS `subject` (`node_id` TEXT NOT NULL, `part` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, `note` TEXT, PRIMARY KEY(`node_id`), FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""

    const val TABLE_TOPIC = """CREATE TABLE IF NOT EXISTS `topic` (`node_id` TEXT NOT NULL, `subject_id` TEXT NOT NULL, `status` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`node_id`), FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`subject_id`) REFERENCES `subject`(`node_id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""

    const val TABLE_STUDY_SESSION = """CREATE TABLE IF NOT EXISTS `study_session` (`id` TEXT NOT NULL, `subject_id` TEXT, `start_at` INTEGER NOT NULL, `end_at` INTEGER, `source` TEXT NOT NULL, `note` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`subject_id`) REFERENCES `subject`(`node_id`) ON UPDATE NO ACTION ON DELETE SET NULL )"""

    const val TABLE_EXAM = """CREATE TABLE IF NOT EXISTS `exam` (`node_id` TEXT NOT NULL, `part` TEXT NOT NULL, `target_month` TEXT, `target_label` TEXT, `confirmed_date` TEXT, `location` TEXT, `note` TEXT, PRIMARY KEY(`node_id`), FOREIGN KEY(`node_id`) REFERENCES `node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""

    const val INDEX_NODE_TYPE = """CREATE INDEX IF NOT EXISTS `index_node_type` ON `node` (`type`)"""
    const val INDEX_LINK_UNIQUE = """CREATE UNIQUE INDEX IF NOT EXISTS `index_link_from_node_id_to_node_id_relation` ON `link` (`from_node_id`, `to_node_id`, `relation`)"""
    const val INDEX_LINK_TO = """CREATE INDEX IF NOT EXISTS `index_link_to_node_id` ON `link` (`to_node_id`)"""
    const val INDEX_CATEGORY_PARENT = """CREATE INDEX IF NOT EXISTS `index_category_parent_id` ON `category` (`parent_id`)"""
    const val INDEX_TX_DATE = """CREATE INDEX IF NOT EXISTS `index_ledger_transaction_booking_date` ON `ledger_transaction` (`booking_date`)"""
    const val INDEX_TX_ACCOUNT = """CREATE INDEX IF NOT EXISTS `index_ledger_transaction_account_id` ON `ledger_transaction` (`account_id`)"""
    const val INDEX_TX_COUNTER = """CREATE INDEX IF NOT EXISTS `index_ledger_transaction_counter_account_id` ON `ledger_transaction` (`counter_account_id`)"""
    const val INDEX_SPLIT_TX = """CREATE INDEX IF NOT EXISTS `index_transaction_split_transaction_id` ON `transaction_split` (`transaction_id`)"""
    const val INDEX_SPLIT_CATEGORY = """CREATE INDEX IF NOT EXISTS `index_transaction_split_category_id` ON `transaction_split` (`category_id`)"""
    const val INDEX_BUDGET_UNIQUE = """CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_category_id_valid_from` ON `budget` (`category_id`, `valid_from`)"""
    const val INDEX_RECURRING_ACCOUNT = """CREATE INDEX IF NOT EXISTS `index_recurring_payment_account_id` ON `recurring_payment` (`account_id`)"""
    const val INDEX_RECURRING_CATEGORY = """CREATE INDEX IF NOT EXISTS `index_recurring_payment_category_id` ON `recurring_payment` (`category_id`)"""
    const val INDEX_RECURRING_BOOKING_TX = """CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_booking_transaction_id` ON `recurring_booking` (`transaction_id`)"""
    const val INDEX_MILESTONE_PROJECT = """CREATE INDEX IF NOT EXISTS `index_milestone_project_id` ON `milestone` (`project_id`)"""
    const val INDEX_TASK_PROJECT = """CREATE INDEX IF NOT EXISTS `index_task_project_id` ON `task` (`project_id`)"""
    const val INDEX_TASK_DUE = """CREATE INDEX IF NOT EXISTS `index_task_due_date` ON `task` (`due_date`)"""
    const val INDEX_COMPLETION_TASK = """CREATE INDEX IF NOT EXISTS `index_task_completion_task_id` ON `task_completion` (`task_id`)"""
    const val INDEX_EVENT_START = """CREATE INDEX IF NOT EXISTS `index_calendar_event_start_at` ON `calendar_event` (`start_at`)"""
    const val INDEX_REMINDER_NODE = """CREATE INDEX IF NOT EXISTS `index_reminder_node_id` ON `reminder` (`node_id`)"""
    const val INDEX_TOTAL_BUDGET_MONTH = """CREATE UNIQUE INDEX IF NOT EXISTS `index_total_budget_valid_from` ON `total_budget` (`valid_from`)"""
    const val INDEX_VEHICLE_EVENT_VEHICLE = """CREATE INDEX IF NOT EXISTS `index_vehicle_event_vehicle_id` ON `vehicle_event` (`vehicle_id`)"""
    const val INDEX_DOCUMENT_SHA = """CREATE UNIQUE INDEX IF NOT EXISTS `index_document_sha256` ON `document` (`sha256`)"""
    const val INDEX_TOPIC_SUBJECT = """CREATE INDEX IF NOT EXISTS `index_topic_subject_id` ON `topic` (`subject_id`)"""
    const val INDEX_SESSION_SUBJECT = """CREATE INDEX IF NOT EXISTS `index_study_session_subject_id` ON `study_session` (`subject_id`)"""
    const val INDEX_SESSION_START = """CREATE INDEX IF NOT EXISTS `index_study_session_start_at` ON `study_session` (`start_at`)"""

    // ───────────────────────────── Trigger ─────────────────────────────

    const val TRIGGER_NODE_TYPE_IMMUTABLE = """CREATE TRIGGER IF NOT EXISTS `trg_node_type_immutable`
        BEFORE UPDATE OF `type` ON `node` WHEN NEW.`type` <> OLD.`type`
        BEGIN SELECT RAISE(ABORT, 'LIFEOS_NODE_TYPE_IMMUTABLE'); END"""

    const val TRIGGER_LINK_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_link_check_insert`
        BEFORE INSERT ON `link`
        BEGIN SELECT CASE
            WHEN NEW.`from_node_id` = NEW.`to_node_id` THEN RAISE(ABORT, 'LIFEOS_LINK_SELF')
            WHEN NEW.`relation` NOT IN ('CONCERNS', 'PART_OF', 'DOCUMENTS', 'RELATES_TO') THEN RAISE(ABORT, 'LIFEOS_LINK_RELATION')
        END; END"""

    const val TRIGGER_ACCOUNT_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_account_check_insert`
        BEFORE INSERT ON `account`
        BEGIN SELECT CASE
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) <> 'ACCOUNT' THEN RAISE(ABORT, 'LIFEOS_NODE_TYPE')
            WHEN NEW.`kind` NOT IN ('CASH', 'CHECKING', 'SAVINGS', 'CREDIT_CARD') THEN RAISE(ABORT, 'LIFEOS_ACCOUNT_KIND')
            WHEN NEW.`currency` NOT GLOB '[A-Z][A-Z][A-Z]' THEN RAISE(ABORT, 'LIFEOS_CURRENCY')
        END; END"""

    /** Kontowährung ist nach der ersten Buchung unveränderlich – sonst würden Salden still umgedeutet. */
    const val TRIGGER_ACCOUNT_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_account_check_update`
        BEFORE UPDATE ON `account`
        BEGIN SELECT CASE
            WHEN NEW.`kind` NOT IN ('CASH', 'CHECKING', 'SAVINGS', 'CREDIT_CARD') THEN RAISE(ABORT, 'LIFEOS_ACCOUNT_KIND')
            WHEN NEW.`currency` <> OLD.`currency` AND EXISTS (
                SELECT 1 FROM `ledger_transaction`
                WHERE `account_id` = OLD.`node_id` OR `counter_account_id` = OLD.`node_id`
            ) THEN RAISE(ABORT, 'LIFEOS_ACCOUNT_CURRENCY_LOCKED')
        END; END"""

    const val TRIGGER_CATEGORY_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_category_check_insert`
        BEFORE INSERT ON `category`
        BEGIN SELECT CASE
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) <> 'CATEGORY' THEN RAISE(ABORT, 'LIFEOS_NODE_TYPE')
            WHEN NEW.`direction` NOT IN ('EXPENSE', 'INCOME') THEN RAISE(ABORT, 'LIFEOS_CATEGORY_DIRECTION')
        END; END"""

    /** Richtung darf sich nicht ändern, solange Aufteilungen auf die Kategorie verweisen. */
    const val TRIGGER_CATEGORY_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_category_check_update`
        BEFORE UPDATE OF `direction` ON `category`
        WHEN NEW.`direction` <> OLD.`direction`
            AND EXISTS (SELECT 1 FROM `transaction_split` WHERE `category_id` = OLD.`node_id`)
        BEGIN SELECT RAISE(ABORT, 'LIFEOS_CATEGORY_DIRECTION_LOCKED'); END"""

    const val TRIGGER_TX_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_ledger_transaction_check_insert`
        BEFORE INSERT ON `ledger_transaction`
        BEGIN SELECT CASE
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) <> 'TRANSACTION' THEN RAISE(ABORT, 'LIFEOS_NODE_TYPE')
            WHEN NEW.`amount_minor` <= 0 THEN RAISE(ABORT, 'LIFEOS_TX_AMOUNT')
            WHEN NEW.`kind` NOT IN ('EXPENSE', 'INCOME', 'TRANSFER', 'REFUND', 'CORRECTION_IN', 'CORRECTION_OUT') THEN RAISE(ABORT, 'LIFEOS_TX_KIND')
            WHEN NEW.`kind` = 'TRANSFER' AND (NEW.`counter_account_id` IS NULL OR NEW.`counter_account_id` = NEW.`account_id`) THEN RAISE(ABORT, 'LIFEOS_TX_TRANSFER')
            WHEN NEW.`kind` <> 'TRANSFER' AND NEW.`counter_account_id` IS NOT NULL THEN RAISE(ABORT, 'LIFEOS_TX_COUNTER_ACCOUNT')
            WHEN NEW.`currency` <> (SELECT `currency` FROM `account` WHERE `node_id` = NEW.`account_id`) THEN RAISE(ABORT, 'LIFEOS_TX_CURRENCY')
            WHEN NEW.`counter_account_id` IS NOT NULL AND NEW.`currency` <> (SELECT `currency` FROM `account` WHERE `node_id` = NEW.`counter_account_id`) THEN RAISE(ABORT, 'LIFEOS_TX_CURRENCY')
            WHEN NEW.`booking_date` NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]' OR date(NEW.`booking_date`, '+0 days') IS NOT NEW.`booking_date` THEN RAISE(ABORT, 'LIFEOS_TX_DATE')
        END; END"""

    const val TRIGGER_TX_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_ledger_transaction_check_update`
        BEFORE UPDATE ON `ledger_transaction`
        BEGIN SELECT CASE
            WHEN NEW.`node_id` <> OLD.`node_id` THEN RAISE(ABORT, 'LIFEOS_TX_ID_IMMUTABLE')
            WHEN NEW.`amount_minor` <= 0 THEN RAISE(ABORT, 'LIFEOS_TX_AMOUNT')
            WHEN NEW.`kind` NOT IN ('EXPENSE', 'INCOME', 'TRANSFER', 'REFUND', 'CORRECTION_IN', 'CORRECTION_OUT') THEN RAISE(ABORT, 'LIFEOS_TX_KIND')
            WHEN NEW.`kind` = 'TRANSFER' AND (NEW.`counter_account_id` IS NULL OR NEW.`counter_account_id` = NEW.`account_id`) THEN RAISE(ABORT, 'LIFEOS_TX_TRANSFER')
            WHEN NEW.`kind` <> 'TRANSFER' AND NEW.`counter_account_id` IS NOT NULL THEN RAISE(ABORT, 'LIFEOS_TX_COUNTER_ACCOUNT')
            WHEN NEW.`kind` IN ('TRANSFER', 'CORRECTION_IN', 'CORRECTION_OUT') AND EXISTS (SELECT 1 FROM `transaction_split` WHERE `transaction_id` = NEW.`node_id`) THEN RAISE(ABORT, 'LIFEOS_SPLIT_ON_TRANSFER')
            WHEN NEW.`kind` <> OLD.`kind` AND EXISTS (SELECT 1 FROM `transaction_split` WHERE `transaction_id` = NEW.`node_id`) THEN RAISE(ABORT, 'LIFEOS_TX_KIND_LOCKED')
            WHEN NEW.`currency` <> (SELECT `currency` FROM `account` WHERE `node_id` = NEW.`account_id`) THEN RAISE(ABORT, 'LIFEOS_TX_CURRENCY')
            WHEN NEW.`counter_account_id` IS NOT NULL AND NEW.`currency` <> (SELECT `currency` FROM `account` WHERE `node_id` = NEW.`counter_account_id`) THEN RAISE(ABORT, 'LIFEOS_TX_CURRENCY')
            WHEN NEW.`booking_date` NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]' OR date(NEW.`booking_date`, '+0 days') IS NOT NEW.`booking_date` THEN RAISE(ABORT, 'LIFEOS_TX_DATE')
        END; END"""

    const val TRIGGER_SPLIT_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_transaction_split_check_insert`
        BEFORE INSERT ON `transaction_split`
        BEGIN SELECT CASE
            WHEN NEW.`amount_minor` <= 0 THEN RAISE(ABORT, 'LIFEOS_SPLIT_AMOUNT')
            WHEN (SELECT `kind` FROM `ledger_transaction` WHERE `node_id` = NEW.`transaction_id`) IN ('TRANSFER', 'CORRECTION_IN', 'CORRECTION_OUT') THEN RAISE(ABORT, 'LIFEOS_SPLIT_ON_TRANSFER')
            WHEN (SELECT `direction` FROM `category` WHERE `node_id` = NEW.`category_id`)
                 <> (SELECT CASE `kind` WHEN 'REFUND' THEN 'EXPENSE' ELSE `kind` END FROM `ledger_transaction` WHERE `node_id` = NEW.`transaction_id`) THEN RAISE(ABORT, 'LIFEOS_SPLIT_DIRECTION')
        END; END"""

    const val TRIGGER_SPLIT_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_transaction_split_check_update`
        BEFORE UPDATE ON `transaction_split`
        BEGIN SELECT CASE
            WHEN NEW.`amount_minor` <= 0 THEN RAISE(ABORT, 'LIFEOS_SPLIT_AMOUNT')
            WHEN (SELECT `kind` FROM `ledger_transaction` WHERE `node_id` = NEW.`transaction_id`) IN ('TRANSFER', 'CORRECTION_IN', 'CORRECTION_OUT') THEN RAISE(ABORT, 'LIFEOS_SPLIT_ON_TRANSFER')
            WHEN (SELECT `direction` FROM `category` WHERE `node_id` = NEW.`category_id`)
                 <> (SELECT CASE `kind` WHEN 'REFUND' THEN 'EXPENSE' ELSE `kind` END FROM `ledger_transaction` WHERE `node_id` = NEW.`transaction_id`) THEN RAISE(ABORT, 'LIFEOS_SPLIT_DIRECTION')
        END; END"""

    const val TRIGGER_BUDGET_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_budget_check_insert`
        BEFORE INSERT ON `budget`
        BEGIN SELECT CASE
            WHEN NEW.`amount_minor` <= 0 THEN RAISE(ABORT, 'LIFEOS_BUDGET_AMOUNT')
            WHEN NEW.`valid_from` NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]' THEN RAISE(ABORT, 'LIFEOS_BUDGET_MONTH')
            WHEN (SELECT `direction` FROM `category` WHERE `node_id` = NEW.`category_id`) <> 'EXPENSE' THEN RAISE(ABORT, 'LIFEOS_BUDGET_DIRECTION')
        END; END"""

    const val TRIGGER_BUDGET_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_budget_check_update`
        BEFORE UPDATE ON `budget`
        BEGIN SELECT CASE
            WHEN NEW.`amount_minor` <= 0 THEN RAISE(ABORT, 'LIFEOS_BUDGET_AMOUNT')
            WHEN NEW.`valid_from` NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]' THEN RAISE(ABORT, 'LIFEOS_BUDGET_MONTH')
            WHEN (SELECT `direction` FROM `category` WHERE `node_id` = NEW.`category_id`) <> 'EXPENSE' THEN RAISE(ABORT, 'LIFEOS_BUDGET_DIRECTION')
        END; END"""

    // ── ab v2 ──
    const val TRIGGER_RECURRING_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_recurring_payment_check_insert`
        BEFORE INSERT ON `recurring_payment`
        BEGIN SELECT CASE
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) <> 'RECURRING' THEN RAISE(ABORT, 'LIFEOS_NODE_TYPE')
            WHEN NEW.`amount_minor` <= 0 THEN RAISE(ABORT, 'LIFEOS_RECURRING_AMOUNT')
            WHEN NEW.`kind` NOT IN ('EXPENSE', 'INCOME') THEN RAISE(ABORT, 'LIFEOS_RECURRING_KIND')
            WHEN NEW.`interval_months` NOT IN (1, 2, 3, 6, 12) THEN RAISE(ABORT, 'LIFEOS_RECURRING_INTERVAL')
            WHEN date(NEW.`anchor_date`, '+0 days') IS NOT NEW.`anchor_date` THEN RAISE(ABORT, 'LIFEOS_RECURRING_DATE')
            WHEN NEW.`end_date` IS NOT NULL AND (date(NEW.`end_date`, '+0 days') IS NOT NEW.`end_date` OR NEW.`end_date` < NEW.`anchor_date`) THEN RAISE(ABORT, 'LIFEOS_RECURRING_DATE')
            WHEN (SELECT `direction` FROM `category` WHERE `node_id` = NEW.`category_id`) <> NEW.`kind` THEN RAISE(ABORT, 'LIFEOS_RECURRING_DIRECTION')
            WHEN NEW.`currency` <> (SELECT `currency` FROM `account` WHERE `node_id` = NEW.`account_id`) THEN RAISE(ABORT, 'LIFEOS_RECURRING_CURRENCY')
        END; END"""

    const val TRIGGER_RECURRING_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_recurring_payment_check_update`
        BEFORE UPDATE ON `recurring_payment`
        BEGIN SELECT CASE
            WHEN NEW.`amount_minor` <= 0 THEN RAISE(ABORT, 'LIFEOS_RECURRING_AMOUNT')
            WHEN NEW.`kind` NOT IN ('EXPENSE', 'INCOME') THEN RAISE(ABORT, 'LIFEOS_RECURRING_KIND')
            WHEN NEW.`interval_months` NOT IN (1, 2, 3, 6, 12) THEN RAISE(ABORT, 'LIFEOS_RECURRING_INTERVAL')
            WHEN date(NEW.`anchor_date`, '+0 days') IS NOT NEW.`anchor_date` THEN RAISE(ABORT, 'LIFEOS_RECURRING_DATE')
            WHEN NEW.`end_date` IS NOT NULL AND (date(NEW.`end_date`, '+0 days') IS NOT NEW.`end_date` OR NEW.`end_date` < NEW.`anchor_date`) THEN RAISE(ABORT, 'LIFEOS_RECURRING_DATE')
            WHEN (SELECT `direction` FROM `category` WHERE `node_id` = NEW.`category_id`) <> NEW.`kind` THEN RAISE(ABORT, 'LIFEOS_RECURRING_DIRECTION')
            WHEN NEW.`currency` <> (SELECT `currency` FROM `account` WHERE `node_id` = NEW.`account_id`) THEN RAISE(ABORT, 'LIFEOS_RECURRING_CURRENCY')
        END; END"""

    const val TRIGGER_RECURRING_BOOKING_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_recurring_booking_check_insert`
        BEFORE INSERT ON `recurring_booking`
        BEGIN SELECT CASE
            WHEN NEW.`status` NOT IN ('BOOKED', 'SKIPPED') THEN RAISE(ABORT, 'LIFEOS_BOOKING_STATUS')
            WHEN NEW.`status` = 'BOOKED' AND NEW.`transaction_id` IS NULL THEN RAISE(ABORT, 'LIFEOS_BOOKING_TX')
            WHEN NEW.`status` = 'SKIPPED' AND NEW.`transaction_id` IS NOT NULL THEN RAISE(ABORT, 'LIFEOS_BOOKING_TX')
            WHEN date(NEW.`due_date`, '+0 days') IS NOT NEW.`due_date` THEN RAISE(ABORT, 'LIFEOS_BOOKING_DATE')
        END; END"""

    const val TRIGGER_RECURRING_BOOKING_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_recurring_booking_check_update`
        BEFORE UPDATE ON `recurring_booking`
        BEGIN SELECT RAISE(ABORT, 'LIFEOS_BOOKING_IMMUTABLE'); END"""

    const val TRIGGER_TOTAL_BUDGET_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_total_budget_check_insert`
        BEFORE INSERT ON `total_budget`
        BEGIN SELECT CASE
            WHEN NEW.`amount_minor` <= 0 THEN RAISE(ABORT, 'LIFEOS_BUDGET_AMOUNT')
            WHEN NEW.`valid_from` NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]' THEN RAISE(ABORT, 'LIFEOS_BUDGET_MONTH')
        END; END"""

    const val TRIGGER_TOTAL_BUDGET_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_total_budget_check_update`
        BEFORE UPDATE ON `total_budget`
        BEGIN SELECT CASE
            WHEN NEW.`amount_minor` <= 0 THEN RAISE(ABORT, 'LIFEOS_BUDGET_AMOUNT')
            WHEN NEW.`valid_from` NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]' THEN RAISE(ABORT, 'LIFEOS_BUDGET_MONTH')
        END; END"""

    // ── ab v3 ──
    const val TRIGGER_TASK_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_task_check_insert`
        BEFORE INSERT ON `task`
        BEGIN SELECT CASE
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) <> 'TASK' THEN RAISE(ABORT, 'LIFEOS_NODE_TYPE')
            WHEN NEW.`status` NOT IN ('OPEN', 'DONE') THEN RAISE(ABORT, 'LIFEOS_TASK_STATUS')
            WHEN NEW.`priority` NOT IN ('LOW', 'NORMAL', 'HIGH') THEN RAISE(ABORT, 'LIFEOS_TASK_PRIORITY')
            WHEN NEW.`repeat_unit` IS NOT NULL AND NEW.`repeat_unit` NOT IN ('DAY', 'WEEK', 'MONTH', 'YEAR') THEN RAISE(ABORT, 'LIFEOS_REPEAT')
            WHEN NEW.`repeat_interval` < 1 OR NEW.`repeat_interval` > 365 THEN RAISE(ABORT, 'LIFEOS_REPEAT')
            WHEN NEW.`repeat_unit` IS NOT NULL AND NEW.`due_date` IS NULL THEN RAISE(ABORT, 'LIFEOS_TASK_REPEAT_DUE')
            WHEN NEW.`due_date` IS NOT NULL AND date(NEW.`due_date`, '+0 days') IS NOT NEW.`due_date` THEN RAISE(ABORT, 'LIFEOS_TASK_DATE')
            WHEN NEW.`due_time` IS NOT NULL AND NEW.`due_time` NOT GLOB '[0-2][0-9]:[0-5][0-9]' THEN RAISE(ABORT, 'LIFEOS_TASK_TIME')
        END; END"""

    const val TRIGGER_TASK_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_task_check_update`
        BEFORE UPDATE ON `task`
        BEGIN SELECT CASE
            WHEN NEW.`status` NOT IN ('OPEN', 'DONE') THEN RAISE(ABORT, 'LIFEOS_TASK_STATUS')
            WHEN NEW.`priority` NOT IN ('LOW', 'NORMAL', 'HIGH') THEN RAISE(ABORT, 'LIFEOS_TASK_PRIORITY')
            WHEN NEW.`repeat_unit` IS NOT NULL AND NEW.`repeat_unit` NOT IN ('DAY', 'WEEK', 'MONTH', 'YEAR') THEN RAISE(ABORT, 'LIFEOS_REPEAT')
            WHEN NEW.`repeat_interval` < 1 OR NEW.`repeat_interval` > 365 THEN RAISE(ABORT, 'LIFEOS_REPEAT')
            WHEN NEW.`repeat_unit` IS NOT NULL AND NEW.`due_date` IS NULL THEN RAISE(ABORT, 'LIFEOS_TASK_REPEAT_DUE')
            WHEN NEW.`due_date` IS NOT NULL AND date(NEW.`due_date`, '+0 days') IS NOT NEW.`due_date` THEN RAISE(ABORT, 'LIFEOS_TASK_DATE')
            WHEN NEW.`due_time` IS NOT NULL AND NEW.`due_time` NOT GLOB '[0-2][0-9]:[0-5][0-9]' THEN RAISE(ABORT, 'LIFEOS_TASK_TIME')
        END; END"""

    const val TRIGGER_PROJECT_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_project_check_insert`
        BEFORE INSERT ON `project`
        BEGIN SELECT CASE
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) <> 'PROJECT' THEN RAISE(ABORT, 'LIFEOS_NODE_TYPE')
            WHEN NEW.`status` NOT IN ('PLANNED', 'ACTIVE', 'PAUSED', 'DONE', 'CANCELLED') THEN RAISE(ABORT, 'LIFEOS_PROJECT_STATUS')
            WHEN NEW.`progress_mode` NOT IN ('TASKS', 'MILESTONES', 'MANUAL') THEN RAISE(ABORT, 'LIFEOS_PROJECT_PROGRESS')
            WHEN NEW.`progress_mode` = 'MANUAL' AND (NEW.`manual_progress` IS NULL OR NEW.`manual_progress` < 0 OR NEW.`manual_progress` > 1000) THEN RAISE(ABORT, 'LIFEOS_PROJECT_PROGRESS')
            WHEN date(NEW.`start_date`, '+0 days') IS NOT NEW.`start_date` THEN RAISE(ABORT, 'LIFEOS_PROJECT_DATE')
            WHEN NEW.`target_date` IS NOT NULL AND (date(NEW.`target_date`, '+0 days') IS NOT NEW.`target_date` OR NEW.`target_date` < NEW.`start_date`) THEN RAISE(ABORT, 'LIFEOS_PROJECT_DATE')
            WHEN NEW.`budget_minor` IS NOT NULL AND NEW.`budget_minor` <= 0 THEN RAISE(ABORT, 'LIFEOS_PROJECT_BUDGET')
        END; END"""

    const val TRIGGER_PROJECT_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_project_check_update`
        BEFORE UPDATE ON `project`
        BEGIN SELECT CASE
            WHEN NEW.`status` NOT IN ('PLANNED', 'ACTIVE', 'PAUSED', 'DONE', 'CANCELLED') THEN RAISE(ABORT, 'LIFEOS_PROJECT_STATUS')
            WHEN NEW.`progress_mode` NOT IN ('TASKS', 'MILESTONES', 'MANUAL') THEN RAISE(ABORT, 'LIFEOS_PROJECT_PROGRESS')
            WHEN NEW.`progress_mode` = 'MANUAL' AND (NEW.`manual_progress` IS NULL OR NEW.`manual_progress` < 0 OR NEW.`manual_progress` > 1000) THEN RAISE(ABORT, 'LIFEOS_PROJECT_PROGRESS')
            WHEN date(NEW.`start_date`, '+0 days') IS NOT NEW.`start_date` THEN RAISE(ABORT, 'LIFEOS_PROJECT_DATE')
            WHEN NEW.`target_date` IS NOT NULL AND (date(NEW.`target_date`, '+0 days') IS NOT NEW.`target_date` OR NEW.`target_date` < NEW.`start_date`) THEN RAISE(ABORT, 'LIFEOS_PROJECT_DATE')
            WHEN NEW.`budget_minor` IS NOT NULL AND NEW.`budget_minor` <= 0 THEN RAISE(ABORT, 'LIFEOS_PROJECT_BUDGET')
        END; END"""

    const val TRIGGER_MILESTONE_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_milestone_check_insert`
        BEFORE INSERT ON `milestone`
        BEGIN SELECT CASE
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) <> 'MILESTONE' THEN RAISE(ABORT, 'LIFEOS_NODE_TYPE')
            WHEN NEW.`due_date` IS NOT NULL AND date(NEW.`due_date`, '+0 days') IS NOT NEW.`due_date` THEN RAISE(ABORT, 'LIFEOS_MILESTONE_DATE')
        END; END"""

    /** Meilensteine sind eigene Knoten: fällt die Meilenstein-Zeile weg (z. B. per Projekt-Kaskade), geht ihr Knoten mit. */
    const val TRIGGER_MILESTONE_CLEANUP = """CREATE TRIGGER IF NOT EXISTS `trg_milestone_cleanup_node`
        AFTER DELETE ON `milestone`
        BEGIN DELETE FROM `node` WHERE `id` = OLD.`node_id`; END"""

    const val TRIGGER_EVENT_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_calendar_event_check_insert`
        BEFORE INSERT ON `calendar_event`
        BEGIN SELECT CASE
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) <> 'EVENT' THEN RAISE(ABORT, 'LIFEOS_NODE_TYPE')
            WHEN NEW.`start_at` NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]T[0-2][0-9]:[0-5][0-9]' THEN RAISE(ABORT, 'LIFEOS_EVENT_TIME')
            WHEN NEW.`end_at` NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]T[0-2][0-9]:[0-5][0-9]' THEN RAISE(ABORT, 'LIFEOS_EVENT_TIME')
            WHEN NEW.`end_at` < NEW.`start_at` THEN RAISE(ABORT, 'LIFEOS_EVENT_END')
            WHEN NEW.`repeat_unit` IS NOT NULL AND NEW.`repeat_unit` NOT IN ('DAY', 'WEEK', 'MONTH', 'YEAR') THEN RAISE(ABORT, 'LIFEOS_REPEAT')
            WHEN NEW.`repeat_interval` < 1 OR NEW.`repeat_interval` > 365 THEN RAISE(ABORT, 'LIFEOS_REPEAT')
        END; END"""

    const val TRIGGER_EVENT_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_calendar_event_check_update`
        BEFORE UPDATE ON `calendar_event`
        BEGIN SELECT CASE
            WHEN NEW.`start_at` NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]T[0-2][0-9]:[0-5][0-9]' THEN RAISE(ABORT, 'LIFEOS_EVENT_TIME')
            WHEN NEW.`end_at` NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]T[0-2][0-9]:[0-5][0-9]' THEN RAISE(ABORT, 'LIFEOS_EVENT_TIME')
            WHEN NEW.`end_at` < NEW.`start_at` THEN RAISE(ABORT, 'LIFEOS_EVENT_END')
            WHEN NEW.`repeat_unit` IS NOT NULL AND NEW.`repeat_unit` NOT IN ('DAY', 'WEEK', 'MONTH', 'YEAR') THEN RAISE(ABORT, 'LIFEOS_REPEAT')
            WHEN NEW.`repeat_interval` < 1 OR NEW.`repeat_interval` > 365 THEN RAISE(ABORT, 'LIFEOS_REPEAT')
        END; END"""

    const val TRIGGER_REMINDER_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_reminder_check_insert`
        BEFORE INSERT ON `reminder`
        BEGIN SELECT CASE
            WHEN NEW.`minutes_before` < 0 OR NEW.`minutes_before` > 20160 THEN RAISE(ABORT, 'LIFEOS_REMINDER_OFFSET')
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) NOT IN ('TASK', 'EVENT', 'EXAM', 'VEHICLE_EVENT') THEN RAISE(ABORT, 'LIFEOS_REMINDER_TARGET')
        END; END"""

    // ── ab v4 ──
    const val TRIGGER_VEHICLE_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_vehicle_check_insert`
        BEFORE INSERT ON `vehicle`
        BEGIN SELECT CASE
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) <> 'VEHICLE' THEN RAISE(ABORT, 'LIFEOS_NODE_TYPE')
            WHEN NEW.`build_year` IS NOT NULL AND (NEW.`build_year` < 1886 OR NEW.`build_year` > 2100) THEN RAISE(ABORT, 'LIFEOS_VEHICLE_YEAR')
            WHEN NEW.`odometer_km` IS NOT NULL AND (NEW.`odometer_km` < 0 OR NEW.`odometer_km` > 5000000) THEN RAISE(ABORT, 'LIFEOS_VEHICLE_ODOMETER')
            WHEN NEW.`purchase_date` IS NOT NULL AND date(NEW.`purchase_date`, '+0 days') IS NOT NEW.`purchase_date` THEN RAISE(ABORT, 'LIFEOS_VEHICLE_DATE')
            WHEN NEW.`purchase_price_minor` IS NOT NULL AND NEW.`purchase_price_minor` < 0 THEN RAISE(ABORT, 'LIFEOS_VEHICLE_PRICE')
        END; END"""

    const val TRIGGER_VEHICLE_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_vehicle_check_update`
        BEFORE UPDATE ON `vehicle`
        BEGIN SELECT CASE
            WHEN NEW.`build_year` IS NOT NULL AND (NEW.`build_year` < 1886 OR NEW.`build_year` > 2100) THEN RAISE(ABORT, 'LIFEOS_VEHICLE_YEAR')
            WHEN NEW.`odometer_km` IS NOT NULL AND (NEW.`odometer_km` < 0 OR NEW.`odometer_km` > 5000000) THEN RAISE(ABORT, 'LIFEOS_VEHICLE_ODOMETER')
            WHEN NEW.`purchase_date` IS NOT NULL AND date(NEW.`purchase_date`, '+0 days') IS NOT NEW.`purchase_date` THEN RAISE(ABORT, 'LIFEOS_VEHICLE_DATE')
            WHEN NEW.`purchase_price_minor` IS NOT NULL AND NEW.`purchase_price_minor` < 0 THEN RAISE(ABORT, 'LIFEOS_VEHICLE_PRICE')
        END; END"""

    const val TRIGGER_VEHICLE_EVENT_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_vehicle_event_check_insert`
        BEFORE INSERT ON `vehicle_event`
        BEGIN SELECT CASE
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) <> 'VEHICLE_EVENT' THEN RAISE(ABORT, 'LIFEOS_NODE_TYPE')
            WHEN NEW.`kind` NOT IN ('PURCHASE', 'MAINTENANCE', 'REPAIR', 'PARTS', 'INSPECTION', 'TIRES', 'OTHER') THEN RAISE(ABORT, 'LIFEOS_VEHICLE_EVENT_KIND')
            WHEN date(NEW.`event_date`, '+0 days') IS NOT NEW.`event_date` THEN RAISE(ABORT, 'LIFEOS_VEHICLE_EVENT_DATE')
            WHEN NEW.`done` NOT IN (0, 1) THEN RAISE(ABORT, 'LIFEOS_VEHICLE_EVENT_DONE')
            WHEN NEW.`odometer_km` IS NOT NULL AND (NEW.`odometer_km` < 0 OR NEW.`odometer_km` > 5000000) THEN RAISE(ABORT, 'LIFEOS_VEHICLE_ODOMETER')
        END; END"""

    const val TRIGGER_VEHICLE_EVENT_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_vehicle_event_check_update`
        BEFORE UPDATE ON `vehicle_event`
        BEGIN SELECT CASE
            WHEN NEW.`kind` NOT IN ('PURCHASE', 'MAINTENANCE', 'REPAIR', 'PARTS', 'INSPECTION', 'TIRES', 'OTHER') THEN RAISE(ABORT, 'LIFEOS_VEHICLE_EVENT_KIND')
            WHEN date(NEW.`event_date`, '+0 days') IS NOT NEW.`event_date` THEN RAISE(ABORT, 'LIFEOS_VEHICLE_EVENT_DATE')
            WHEN NEW.`done` NOT IN (0, 1) THEN RAISE(ABORT, 'LIFEOS_VEHICLE_EVENT_DONE')
            WHEN NEW.`odometer_km` IS NOT NULL AND (NEW.`odometer_km` < 0 OR NEW.`odometer_km` > 5000000) THEN RAISE(ABORT, 'LIFEOS_VEHICLE_ODOMETER')
        END; END"""

    const val TRIGGER_ASSET_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_asset_check_insert`
        BEFORE INSERT ON `asset`
        BEGIN SELECT CASE
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) <> 'ASSET' THEN RAISE(ABORT, 'LIFEOS_NODE_TYPE')
            WHEN NEW.`kind` NOT IN ('TOOL', 'ELECTRONICS', 'MACHINE', 'HOUSEHOLD', 'OTHER') THEN RAISE(ABORT, 'LIFEOS_ASSET_KIND')
            WHEN NEW.`purchase_date` IS NOT NULL AND date(NEW.`purchase_date`, '+0 days') IS NOT NEW.`purchase_date` THEN RAISE(ABORT, 'LIFEOS_ASSET_DATE')
            WHEN NEW.`warranty_until` IS NOT NULL AND date(NEW.`warranty_until`, '+0 days') IS NOT NEW.`warranty_until` THEN RAISE(ABORT, 'LIFEOS_ASSET_DATE')
            WHEN NEW.`purchase_date` IS NOT NULL AND NEW.`warranty_until` IS NOT NULL AND NEW.`warranty_until` < NEW.`purchase_date` THEN RAISE(ABORT, 'LIFEOS_ASSET_WARRANTY')
            WHEN NEW.`price_minor` IS NOT NULL AND NEW.`price_minor` < 0 THEN RAISE(ABORT, 'LIFEOS_ASSET_PRICE')
        END; END"""

    const val TRIGGER_ASSET_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_asset_check_update`
        BEFORE UPDATE ON `asset`
        BEGIN SELECT CASE
            WHEN NEW.`kind` NOT IN ('TOOL', 'ELECTRONICS', 'MACHINE', 'HOUSEHOLD', 'OTHER') THEN RAISE(ABORT, 'LIFEOS_ASSET_KIND')
            WHEN NEW.`purchase_date` IS NOT NULL AND date(NEW.`purchase_date`, '+0 days') IS NOT NEW.`purchase_date` THEN RAISE(ABORT, 'LIFEOS_ASSET_DATE')
            WHEN NEW.`warranty_until` IS NOT NULL AND date(NEW.`warranty_until`, '+0 days') IS NOT NEW.`warranty_until` THEN RAISE(ABORT, 'LIFEOS_ASSET_DATE')
            WHEN NEW.`purchase_date` IS NOT NULL AND NEW.`warranty_until` IS NOT NULL AND NEW.`warranty_until` < NEW.`purchase_date` THEN RAISE(ABORT, 'LIFEOS_ASSET_WARRANTY')
            WHEN NEW.`price_minor` IS NOT NULL AND NEW.`price_minor` < 0 THEN RAISE(ABORT, 'LIFEOS_ASSET_PRICE')
        END; END"""

    const val TRIGGER_DOCUMENT_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_document_check_insert`
        BEFORE INSERT ON `document`
        BEGIN SELECT CASE
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) <> 'DOCUMENT' THEN RAISE(ABORT, 'LIFEOS_NODE_TYPE')
            WHEN length(NEW.`sha256`) <> 64 OR NEW.`sha256` GLOB '*[^0-9a-f]*' THEN RAISE(ABORT, 'LIFEOS_DOCUMENT_HASH')
            WHEN NEW.`size_bytes` < 0 THEN RAISE(ABORT, 'LIFEOS_DOCUMENT_SIZE')
            WHEN trim(NEW.`mime_type`) = '' OR trim(NEW.`file_name`) = '' THEN RAISE(ABORT, 'LIFEOS_DOCUMENT_META')
            WHEN NEW.`doc_date` IS NOT NULL AND date(NEW.`doc_date`, '+0 days') IS NOT NEW.`doc_date` THEN RAISE(ABORT, 'LIFEOS_DOCUMENT_DATE')
        END; END"""

    const val TRIGGER_DOCUMENT_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_document_check_update`
        BEFORE UPDATE ON `document`
        BEGIN SELECT CASE
            WHEN length(NEW.`sha256`) <> 64 OR NEW.`sha256` GLOB '*[^0-9a-f]*' THEN RAISE(ABORT, 'LIFEOS_DOCUMENT_HASH')
            WHEN NEW.`size_bytes` < 0 THEN RAISE(ABORT, 'LIFEOS_DOCUMENT_SIZE')
            WHEN trim(NEW.`mime_type`) = '' OR trim(NEW.`file_name`) = '' THEN RAISE(ABORT, 'LIFEOS_DOCUMENT_META')
            WHEN NEW.`doc_date` IS NOT NULL AND date(NEW.`doc_date`, '+0 days') IS NOT NEW.`doc_date` THEN RAISE(ABORT, 'LIFEOS_DOCUMENT_DATE')
        END; END"""

    const val TRIGGER_SUBJECT_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_subject_check_insert`
        BEFORE INSERT ON `subject`
        BEGIN SELECT CASE
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) <> 'COURSE' THEN RAISE(ABORT, 'LIFEOS_NODE_TYPE')
            WHEN NEW.`part` NOT IN ('BQ', 'HQ', 'OTHER') THEN RAISE(ABORT, 'LIFEOS_EXAM_PART')
        END; END"""

    const val TRIGGER_SUBJECT_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_subject_check_update`
        BEFORE UPDATE ON `subject`
        BEGIN SELECT CASE
            WHEN NEW.`part` NOT IN ('BQ', 'HQ', 'OTHER') THEN RAISE(ABORT, 'LIFEOS_EXAM_PART')
        END; END"""

    const val TRIGGER_TOPIC_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_topic_check_insert`
        BEFORE INSERT ON `topic`
        BEGIN SELECT CASE
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) <> 'TOPIC' THEN RAISE(ABORT, 'LIFEOS_NODE_TYPE')
            WHEN NEW.`status` NOT IN ('OPEN', 'IN_PROGRESS', 'DONE') THEN RAISE(ABORT, 'LIFEOS_TOPIC_STATUS')
        END; END"""

    const val TRIGGER_TOPIC_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_topic_check_update`
        BEFORE UPDATE ON `topic`
        BEGIN SELECT CASE
            WHEN NEW.`status` NOT IN ('OPEN', 'IN_PROGRESS', 'DONE') THEN RAISE(ABORT, 'LIFEOS_TOPIC_STATUS')
        END; END"""

    const val TRIGGER_EXAM_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_exam_check_insert`
        BEFORE INSERT ON `exam`
        BEGIN SELECT CASE
            WHEN (SELECT `type` FROM `node` WHERE `id` = NEW.`node_id`) <> 'EXAM' THEN RAISE(ABORT, 'LIFEOS_NODE_TYPE')
            WHEN NEW.`part` NOT IN ('BQ', 'HQ', 'OTHER') THEN RAISE(ABORT, 'LIFEOS_EXAM_PART')
            WHEN NEW.`target_month` IS NOT NULL AND (NEW.`target_month` NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]' OR substr(NEW.`target_month`, 6, 2) NOT BETWEEN '01' AND '12') THEN RAISE(ABORT, 'LIFEOS_EXAM_TARGET')
            WHEN NEW.`confirmed_date` IS NOT NULL AND date(NEW.`confirmed_date`, '+0 days') IS NOT NEW.`confirmed_date` THEN RAISE(ABORT, 'LIFEOS_EXAM_DATE')
        END; END"""

    const val TRIGGER_EXAM_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_exam_check_update`
        BEFORE UPDATE ON `exam`
        BEGIN SELECT CASE
            WHEN NEW.`part` NOT IN ('BQ', 'HQ', 'OTHER') THEN RAISE(ABORT, 'LIFEOS_EXAM_PART')
            WHEN NEW.`target_month` IS NOT NULL AND (NEW.`target_month` NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]' OR substr(NEW.`target_month`, 6, 2) NOT BETWEEN '01' AND '12') THEN RAISE(ABORT, 'LIFEOS_EXAM_TARGET')
            WHEN NEW.`confirmed_date` IS NOT NULL AND date(NEW.`confirmed_date`, '+0 days') IS NOT NEW.`confirmed_date` THEN RAISE(ABORT, 'LIFEOS_EXAM_DATE')
        END; END"""

    /** Höchstens EINE laufende Lernsitzung; Ende nach Start; max. 12 h – verhindert doppelt gezählte Zeit. */
    const val TRIGGER_SESSION_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_study_session_check_insert`
        BEFORE INSERT ON `study_session`
        BEGIN SELECT CASE
            WHEN NEW.`source` NOT IN ('TIMER', 'MANUAL') THEN RAISE(ABORT, 'LIFEOS_SESSION_SOURCE')
            WHEN NEW.`end_at` IS NULL AND EXISTS (SELECT 1 FROM `study_session` WHERE `end_at` IS NULL) THEN RAISE(ABORT, 'LIFEOS_SESSION_RUNNING')
            WHEN NEW.`end_at` IS NOT NULL AND NEW.`end_at` <= NEW.`start_at` THEN RAISE(ABORT, 'LIFEOS_SESSION_END')
            WHEN NEW.`end_at` IS NOT NULL AND NEW.`end_at` - NEW.`start_at` > 43200000 THEN RAISE(ABORT, 'LIFEOS_SESSION_LENGTH')
        END; END"""

    const val TRIGGER_SESSION_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_study_session_check_update`
        BEFORE UPDATE ON `study_session`
        BEGIN SELECT CASE
            WHEN NEW.`source` NOT IN ('TIMER', 'MANUAL') THEN RAISE(ABORT, 'LIFEOS_SESSION_SOURCE')
            WHEN NEW.`end_at` IS NULL AND EXISTS (SELECT 1 FROM `study_session` WHERE `end_at` IS NULL AND `id` <> NEW.`id`) THEN RAISE(ABORT, 'LIFEOS_SESSION_RUNNING')
            WHEN NEW.`end_at` IS NOT NULL AND NEW.`end_at` <= NEW.`start_at` THEN RAISE(ABORT, 'LIFEOS_SESSION_END')
            WHEN NEW.`end_at` IS NOT NULL AND NEW.`end_at` - NEW.`start_at` > 43200000 THEN RAISE(ABORT, 'LIFEOS_SESSION_LENGTH')
        END; END"""

    /** Kind-Knoten (Fahrzeugereignis, Lernthema) gehen mit, wenn ihre Zeile per Kaskade wegfällt. */
    const val TRIGGER_VEHICLE_EVENT_CLEANUP = """CREATE TRIGGER IF NOT EXISTS `trg_vehicle_event_cleanup_node`
        AFTER DELETE ON `vehicle_event`
        BEGIN DELETE FROM `node` WHERE `id` = OLD.`node_id`; END"""

    const val TRIGGER_TOPIC_CLEANUP = """CREATE TRIGGER IF NOT EXISTS `trg_topic_cleanup_node`
        AFTER DELETE ON `topic`
        BEGIN DELETE FROM `node` WHERE `id` = OLD.`node_id`; END"""

    // ───────────────────────────── Prüfabfragen ─────────────────────────────

    /** Buchungen, deren Aufteilungen nicht exakt zum Betrag passen (oder Umbuchungen mit Splits). */
    const val QUERY_SPLIT_VIOLATIONS = """SELECT t.`node_id` AS node_id, t.`kind` AS kind,
        t.`amount_minor` AS amount_minor, COALESCE(SUM(s.`amount_minor`), 0) AS split_sum,
        COUNT(s.`id`) AS split_count
        FROM `ledger_transaction` t LEFT JOIN `transaction_split` s ON s.`transaction_id` = t.`node_id`
        GROUP BY t.`node_id`
        HAVING (t.`kind` IN ('TRANSFER', 'CORRECTION_IN', 'CORRECTION_OUT') AND COUNT(s.`id`) > 0)
            OR (t.`kind` IN ('EXPENSE', 'INCOME', 'REFUND') AND COALESCE(SUM(s.`amount_minor`), 0) <> t.`amount_minor`)
        ORDER BY t.`node_id`"""

    /** Nodes ohne zugehörige Fachzeile (z. B. abgebrochene Schreibvorgänge). */
    const val QUERY_ORPHAN_NODES = """SELECT n.`id` AS node_id, n.`type` AS type FROM `node` n
        WHERE (n.`type` = 'TRANSACTION' AND NOT EXISTS (SELECT 1 FROM `ledger_transaction` x WHERE x.`node_id` = n.`id`))
           OR (n.`type` = 'ACCOUNT' AND NOT EXISTS (SELECT 1 FROM `account` x WHERE x.`node_id` = n.`id`))
           OR (n.`type` = 'CATEGORY' AND NOT EXISTS (SELECT 1 FROM `category` x WHERE x.`node_id` = n.`id`))
           OR (n.`type` = 'RECURRING' AND NOT EXISTS (SELECT 1 FROM `recurring_payment` x WHERE x.`node_id` = n.`id`))
           OR (n.`type` = 'TASK' AND NOT EXISTS (SELECT 1 FROM `task` x WHERE x.`node_id` = n.`id`))
           OR (n.`type` = 'EVENT' AND NOT EXISTS (SELECT 1 FROM `calendar_event` x WHERE x.`node_id` = n.`id`))
           OR (n.`type` = 'PROJECT' AND NOT EXISTS (SELECT 1 FROM `project` x WHERE x.`node_id` = n.`id`))
           OR (n.`type` = 'MILESTONE' AND NOT EXISTS (SELECT 1 FROM `milestone` x WHERE x.`node_id` = n.`id`))
           OR (n.`type` = 'VEHICLE' AND NOT EXISTS (SELECT 1 FROM `vehicle` x WHERE x.`node_id` = n.`id`))
           OR (n.`type` = 'VEHICLE_EVENT' AND NOT EXISTS (SELECT 1 FROM `vehicle_event` x WHERE x.`node_id` = n.`id`))
           OR (n.`type` = 'ASSET' AND NOT EXISTS (SELECT 1 FROM `asset` x WHERE x.`node_id` = n.`id`))
           OR (n.`type` = 'DOCUMENT' AND NOT EXISTS (SELECT 1 FROM `document` x WHERE x.`node_id` = n.`id`))
           OR (n.`type` = 'COURSE' AND NOT EXISTS (SELECT 1 FROM `subject` x WHERE x.`node_id` = n.`id`))
           OR (n.`type` = 'TOPIC' AND NOT EXISTS (SELECT 1 FROM `topic` x WHERE x.`node_id` = n.`id`))
           OR (n.`type` = 'EXAM' AND NOT EXISTS (SELECT 1 FROM `exam` x WHERE x.`node_id` = n.`id`))
        ORDER BY n.`id`"""

    const val QUERY_TRANSACTION_COUNT = """SELECT COUNT(*) FROM `ledger_transaction`"""

    // ───────────────────────────── Listen ─────────────────────────────

    val REFERENCE_TABLES: List<String> = listOf(
        TABLE_NODE, TABLE_LINK, TABLE_ACCOUNT, TABLE_CATEGORY, TABLE_LEDGER_TRANSACTION,
        TABLE_TRANSACTION_SPLIT, TABLE_BUDGET, TABLE_APP_SETTING,
        TABLE_RECURRING_PAYMENT, TABLE_RECURRING_BOOKING, TABLE_TOTAL_BUDGET,
        TABLE_PROJECT, TABLE_MILESTONE, TABLE_TASK, TABLE_TASK_COMPLETION, TABLE_CALENDAR_EVENT, TABLE_REMINDER,
        TABLE_VEHICLE, TABLE_VEHICLE_EVENT, TABLE_ASSET, TABLE_DOCUMENT, TABLE_SUBJECT, TABLE_TOPIC, TABLE_STUDY_SESSION, TABLE_EXAM,
    )

    val REFERENCE_INDICES: List<String> = listOf(
        INDEX_NODE_TYPE, INDEX_LINK_UNIQUE, INDEX_LINK_TO, INDEX_CATEGORY_PARENT, INDEX_TX_DATE,
        INDEX_TX_ACCOUNT, INDEX_TX_COUNTER, INDEX_SPLIT_TX, INDEX_SPLIT_CATEGORY, INDEX_BUDGET_UNIQUE,
        INDEX_RECURRING_ACCOUNT, INDEX_RECURRING_CATEGORY, INDEX_RECURRING_BOOKING_TX, INDEX_TOTAL_BUDGET_MONTH,
        INDEX_MILESTONE_PROJECT, INDEX_TASK_PROJECT, INDEX_TASK_DUE, INDEX_COMPLETION_TASK, INDEX_EVENT_START, INDEX_REMINDER_NODE,
        INDEX_VEHICLE_EVENT_VEHICLE, INDEX_DOCUMENT_SHA, INDEX_TOPIC_SUBJECT, INDEX_SESSION_SUBJECT, INDEX_SESSION_START,
    )

    /** Werden in `onCreate` und in jeder Migration ausgeführt. Reihenfolge egal (idempotent). */
    val TRIGGERS: List<String> = listOf(
        TRIGGER_NODE_TYPE_IMMUTABLE, TRIGGER_LINK_INSERT, TRIGGER_ACCOUNT_INSERT, TRIGGER_ACCOUNT_UPDATE,
        TRIGGER_CATEGORY_INSERT, TRIGGER_CATEGORY_UPDATE, TRIGGER_TX_INSERT, TRIGGER_TX_UPDATE,
        TRIGGER_SPLIT_INSERT, TRIGGER_SPLIT_UPDATE, TRIGGER_BUDGET_INSERT, TRIGGER_BUDGET_UPDATE,
        TRIGGER_RECURRING_INSERT, TRIGGER_RECURRING_UPDATE, TRIGGER_RECURRING_BOOKING_INSERT,
        TRIGGER_RECURRING_BOOKING_UPDATE, TRIGGER_TOTAL_BUDGET_INSERT, TRIGGER_TOTAL_BUDGET_UPDATE,
        TRIGGER_TASK_INSERT, TRIGGER_TASK_UPDATE, TRIGGER_PROJECT_INSERT, TRIGGER_PROJECT_UPDATE,
        TRIGGER_MILESTONE_INSERT, TRIGGER_MILESTONE_CLEANUP, TRIGGER_EVENT_INSERT, TRIGGER_EVENT_UPDATE, TRIGGER_REMINDER_INSERT,
        TRIGGER_VEHICLE_INSERT, TRIGGER_VEHICLE_UPDATE, TRIGGER_VEHICLE_EVENT_INSERT, TRIGGER_VEHICLE_EVENT_UPDATE,
        TRIGGER_ASSET_INSERT, TRIGGER_ASSET_UPDATE, TRIGGER_DOCUMENT_INSERT, TRIGGER_DOCUMENT_UPDATE,
        TRIGGER_SUBJECT_INSERT, TRIGGER_SUBJECT_UPDATE, TRIGGER_TOPIC_INSERT, TRIGGER_TOPIC_UPDATE,
        TRIGGER_EXAM_INSERT, TRIGGER_EXAM_UPDATE, TRIGGER_SESSION_INSERT, TRIGGER_SESSION_UPDATE,
        TRIGGER_VEHICLE_EVENT_CLEANUP, TRIGGER_TOPIC_CLEANUP,
    )

    /** Namen von Triggern früherer Versionen, die nicht mehr existieren sollen (werden gelöscht). */
    val RETIRED_TRIGGER_NAMES: List<String> = emptyList()

    /** v1 → v2: Wiederkehrende Zahlungen, Gesamtbudget. Trigger erneuert der [TriggerInstaller]. */
    val MIGRATION_1_2: List<String> = listOf(
        TABLE_RECURRING_PAYMENT, INDEX_RECURRING_ACCOUNT, INDEX_RECURRING_CATEGORY,
        TABLE_RECURRING_BOOKING, INDEX_RECURRING_BOOKING_TX,
        TABLE_TOTAL_BUDGET, INDEX_TOTAL_BUDGET_MONTH,
    )

    /** v2 → v3: Planung (Aufgaben, Termine, Erinnerungen) und Projekte. */
    val MIGRATION_2_3: List<String> = listOf(
        TABLE_PROJECT, TABLE_MILESTONE, INDEX_MILESTONE_PROJECT,
        TABLE_TASK, INDEX_TASK_PROJECT, INDEX_TASK_DUE,
        TABLE_TASK_COMPLETION, INDEX_COMPLETION_TASK,
        TABLE_CALENDAR_EVENT, INDEX_EVENT_START,
        TABLE_REMINDER, INDEX_REMINDER_NODE,
    )

    /** v3 → v4: Fahrzeuge, Inventar, Dokumente, Weiterbildung (Fächer, Themen, Lernzeit, Prüfungen). Rein additiv. */
    val MIGRATION_3_4: List<String> = listOf(
        TABLE_VEHICLE, TABLE_VEHICLE_EVENT, INDEX_VEHICLE_EVENT_VEHICLE,
        TABLE_ASSET,
        TABLE_DOCUMENT, INDEX_DOCUMENT_SHA,
        TABLE_SUBJECT, TABLE_TOPIC, INDEX_TOPIC_SUBJECT,
        TABLE_STUDY_SESSION, INDEX_SESSION_SUBJECT, INDEX_SESSION_START,
        TABLE_EXAM,
    )
}
