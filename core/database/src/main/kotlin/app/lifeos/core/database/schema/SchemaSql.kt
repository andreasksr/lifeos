package app.lifeos.core.database.schema

/**
 * Kanonisches SQL des LifeOS-Schemas (aktuell v2).
 *
 * - `TABLE_*` / `INDEX_*`: Referenz-DDL. Room erzeugt die Tabellen selbst aus den Entities; diese
 *   Texte dokumentieren das erwartete Ergebnis und werden von `tools/schema_test.py` (SQLite) sowie
 *   vom instrumentierten `SchemaReferenceTest` gegen Room geprüft.
 * - `TRIGGER_*`: Invarianten, die Room nicht abbilden kann. Werden bei jedem Öffnen der DB und in
 *   jeder Migration **gelöscht und neu angelegt** ([TriggerInstaller]), damit geänderte Definitionen
 *   garantiert wirksam werden (`CREATE TRIGGER IF NOT EXISTS` allein würde alte Fassungen behalten).
 * - `QUERY_*`: Prüfabfragen für den Integritätsprüfer.
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

    const val TABLE_TOTAL_BUDGET = """CREATE TABLE IF NOT EXISTS `total_budget` (`id` TEXT NOT NULL, `valid_from` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, PRIMARY KEY(`id`))"""

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
    const val INDEX_TOTAL_BUDGET_MONTH = """CREATE UNIQUE INDEX IF NOT EXISTS `index_total_budget_valid_from` ON `total_budget` (`valid_from`)"""

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
            WHEN NEW.`booking_date` NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]' OR date(NEW.`booking_date`) IS NOT NEW.`booking_date` THEN RAISE(ABORT, 'LIFEOS_TX_DATE')
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
            WHEN NEW.`booking_date` NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]' OR date(NEW.`booking_date`) IS NOT NEW.`booking_date` THEN RAISE(ABORT, 'LIFEOS_TX_DATE')
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
            WHEN date(NEW.`anchor_date`) IS NOT NEW.`anchor_date` THEN RAISE(ABORT, 'LIFEOS_RECURRING_DATE')
            WHEN NEW.`end_date` IS NOT NULL AND (date(NEW.`end_date`) IS NOT NEW.`end_date` OR NEW.`end_date` < NEW.`anchor_date`) THEN RAISE(ABORT, 'LIFEOS_RECURRING_DATE')
            WHEN (SELECT `direction` FROM `category` WHERE `node_id` = NEW.`category_id`) <> NEW.`kind` THEN RAISE(ABORT, 'LIFEOS_RECURRING_DIRECTION')
            WHEN NEW.`currency` <> (SELECT `currency` FROM `account` WHERE `node_id` = NEW.`account_id`) THEN RAISE(ABORT, 'LIFEOS_RECURRING_CURRENCY')
        END; END"""

    const val TRIGGER_RECURRING_UPDATE = """CREATE TRIGGER IF NOT EXISTS `trg_recurring_payment_check_update`
        BEFORE UPDATE ON `recurring_payment`
        BEGIN SELECT CASE
            WHEN NEW.`amount_minor` <= 0 THEN RAISE(ABORT, 'LIFEOS_RECURRING_AMOUNT')
            WHEN NEW.`kind` NOT IN ('EXPENSE', 'INCOME') THEN RAISE(ABORT, 'LIFEOS_RECURRING_KIND')
            WHEN NEW.`interval_months` NOT IN (1, 2, 3, 6, 12) THEN RAISE(ABORT, 'LIFEOS_RECURRING_INTERVAL')
            WHEN date(NEW.`anchor_date`) IS NOT NEW.`anchor_date` THEN RAISE(ABORT, 'LIFEOS_RECURRING_DATE')
            WHEN NEW.`end_date` IS NOT NULL AND (date(NEW.`end_date`) IS NOT NEW.`end_date` OR NEW.`end_date` < NEW.`anchor_date`) THEN RAISE(ABORT, 'LIFEOS_RECURRING_DATE')
            WHEN (SELECT `direction` FROM `category` WHERE `node_id` = NEW.`category_id`) <> NEW.`kind` THEN RAISE(ABORT, 'LIFEOS_RECURRING_DIRECTION')
            WHEN NEW.`currency` <> (SELECT `currency` FROM `account` WHERE `node_id` = NEW.`account_id`) THEN RAISE(ABORT, 'LIFEOS_RECURRING_CURRENCY')
        END; END"""

    const val TRIGGER_RECURRING_BOOKING_INSERT = """CREATE TRIGGER IF NOT EXISTS `trg_recurring_booking_check_insert`
        BEFORE INSERT ON `recurring_booking`
        BEGIN SELECT CASE
            WHEN NEW.`status` NOT IN ('BOOKED', 'SKIPPED') THEN RAISE(ABORT, 'LIFEOS_BOOKING_STATUS')
            WHEN NEW.`status` = 'BOOKED' AND NEW.`transaction_id` IS NULL THEN RAISE(ABORT, 'LIFEOS_BOOKING_TX')
            WHEN NEW.`status` = 'SKIPPED' AND NEW.`transaction_id` IS NOT NULL THEN RAISE(ABORT, 'LIFEOS_BOOKING_TX')
            WHEN date(NEW.`due_date`) IS NOT NEW.`due_date` THEN RAISE(ABORT, 'LIFEOS_BOOKING_DATE')
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
        ORDER BY n.`id`"""

    const val QUERY_TRANSACTION_COUNT = """SELECT COUNT(*) FROM `ledger_transaction`"""

    // ───────────────────────────── Listen ─────────────────────────────

    val REFERENCE_TABLES: List<String> = listOf(
        TABLE_NODE, TABLE_LINK, TABLE_ACCOUNT, TABLE_CATEGORY, TABLE_LEDGER_TRANSACTION,
        TABLE_TRANSACTION_SPLIT, TABLE_BUDGET, TABLE_APP_SETTING,
        TABLE_RECURRING_PAYMENT, TABLE_RECURRING_BOOKING, TABLE_TOTAL_BUDGET,
    )

    val REFERENCE_INDICES: List<String> = listOf(
        INDEX_NODE_TYPE, INDEX_LINK_UNIQUE, INDEX_LINK_TO, INDEX_CATEGORY_PARENT, INDEX_TX_DATE,
        INDEX_TX_ACCOUNT, INDEX_TX_COUNTER, INDEX_SPLIT_TX, INDEX_SPLIT_CATEGORY, INDEX_BUDGET_UNIQUE,
        INDEX_RECURRING_ACCOUNT, INDEX_RECURRING_CATEGORY, INDEX_RECURRING_BOOKING_TX, INDEX_TOTAL_BUDGET_MONTH,
    )

    /** Werden in `onCreate` und in jeder Migration ausgeführt. Reihenfolge egal (idempotent). */
    val TRIGGERS: List<String> = listOf(
        TRIGGER_NODE_TYPE_IMMUTABLE, TRIGGER_LINK_INSERT, TRIGGER_ACCOUNT_INSERT, TRIGGER_ACCOUNT_UPDATE,
        TRIGGER_CATEGORY_INSERT, TRIGGER_CATEGORY_UPDATE, TRIGGER_TX_INSERT, TRIGGER_TX_UPDATE,
        TRIGGER_SPLIT_INSERT, TRIGGER_SPLIT_UPDATE, TRIGGER_BUDGET_INSERT, TRIGGER_BUDGET_UPDATE,
        TRIGGER_RECURRING_INSERT, TRIGGER_RECURRING_UPDATE, TRIGGER_RECURRING_BOOKING_INSERT,
        TRIGGER_RECURRING_BOOKING_UPDATE, TRIGGER_TOTAL_BUDGET_INSERT, TRIGGER_TOTAL_BUDGET_UPDATE,
    )

    /** Namen von Triggern früherer Versionen, die nicht mehr existieren sollen (werden gelöscht). */
    val RETIRED_TRIGGER_NAMES: List<String> = emptyList()

    /** v1 → v2: Wiederkehrende Zahlungen, Gesamtbudget. Trigger erneuert der [TriggerInstaller]. */
    val MIGRATION_1_2: List<String> = listOf(
        TABLE_RECURRING_PAYMENT, INDEX_RECURRING_ACCOUNT, INDEX_RECURRING_CATEGORY,
        TABLE_RECURRING_BOOKING, INDEX_RECURRING_BOOKING_TX,
        TABLE_TOTAL_BUDGET, INDEX_TOTAL_BUDGET_MONTH,
    )
}
