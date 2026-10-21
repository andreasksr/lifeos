#!/usr/bin/env python3
"""
Prüft das kanonische LifeOS-Schema (SchemaSql.kt) gegen eine echte SQLite-Engine.

Getestet werden Referenz-DDL, Fremdschlüssel-Kaskaden, alle Trigger-Invarianten und die
Prüfabfragen des Integritätsprüfers. Das SQL wird direkt aus der Kotlin-Datei gelesen, damit
App und Test garantiert denselben Text verwenden.

Aufruf:  python3 tools/schema_test.py -v
"""
import pathlib
import re
import sqlite3
import unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCHEMA_KT = ROOT / "core/database/src/main/kotlin/app/lifeos/core/database/schema/SchemaSql.kt"


def load_sql():
    text = SCHEMA_KT.read_text(encoding="utf-8")
    consts = dict(re.findall(r'const val (\w+) = """(.*?)"""', text, re.S))
    if not consts:
        raise RuntimeError("Keine SQL-Konstanten gefunden")
    return consts


SQL = load_sql()


def ordered(prefix):
    return [v for k, v in SQL.items() if k.startswith(prefix)]


UUID = "00000000-0000-4000-8000-{:012d}"


def uid(n):
    return UUID.format(n)


class SchemaTest(unittest.TestCase):

    def setUp(self):
        self.db = sqlite3.connect(":memory:", isolation_level=None)
        self.db.execute("PRAGMA foreign_keys = ON")
        for stmt in ordered("TABLE_") + ordered("INDEX_") + ordered("TRIGGER_"):
            self.db.execute(stmt)
        # Basisdaten
        self.giro = self.account(1, "EUR")
        self.spar = self.account(2, "EUR")
        self.usd = self.account(3, "USD")
        self.food = self.category(10, "EXPENSE")
        self.car = self.category(11, "EXPENSE")
        self.salary = self.category(12, "INCOME")

    def tearDown(self):
        self.db.close()

    # ───── Hilfen ─────
    def node(self, n, type_, title="x"):
        self.db.execute(
            "INSERT INTO node (id, type, title, created_at, updated_at) VALUES (?,?,?,0,0)",
            (uid(n), type_, title),
        )
        return uid(n)

    def account(self, n, currency, kind="CHECKING"):
        nid = self.node(n, "ACCOUNT")
        self.db.execute(
            "INSERT INTO account (node_id, kind, currency, opening_balance_minor, sort_order) VALUES (?,?,?,0,0)",
            (nid, kind, currency),
        )
        return nid

    def category(self, n, direction):
        nid = self.node(n, "CATEGORY")
        self.db.execute(
            "INSERT INTO category (node_id, direction, sort_order) VALUES (?,?,0)", (nid, direction)
        )
        return nid

    def tx(self, n, kind, amount, account=None, counter=None, currency="EUR", date="2026-10-20"):
        nid = self.node(n, "TRANSACTION")
        self.db.execute(
            "INSERT INTO ledger_transaction (node_id, kind, account_id, counter_account_id, amount_minor,"
            " currency, booking_date) VALUES (?,?,?,?,?,?,?)",
            (nid, kind, account or self.giro, counter, amount, currency, date),
        )
        return nid

    def split(self, n, tx, category, amount, position=0):
        self.db.execute(
            "INSERT INTO transaction_split (id, transaction_id, category_id, amount_minor, position)"
            " VALUES (?,?,?,?,?)",
            (uid(n), tx, category, amount, position),
        )
        return uid(n)

    def assertAbort(self, code, fn, *args, **kwargs):
        with self.assertRaises(sqlite3.DatabaseError) as ctx:
            fn(*args, **kwargs)
        self.assertIn(code, str(ctx.exception))

    def count(self, table):
        return self.db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]

    # ───── Tests: Buchungen ─────
    def test_valid_expense_with_splits(self):
        t = self.tx(100, "EXPENSE", 10000)
        self.split(200, t, self.food, 3333)
        self.split(201, t, self.car, 6667, 1)
        self.assertEqual([], self.db.execute(SQL["QUERY_SPLIT_VIOLATIONS"]).fetchall())

    def test_amount_must_be_positive(self):
        self.assertAbort("LIFEOS_TX_AMOUNT", self.tx, 100, "EXPENSE", 0)
        self.assertAbort("LIFEOS_TX_AMOUNT", self.tx, 101, "EXPENSE", -1)
        t = self.tx(102, "EXPENSE", 5)
        self.assertAbort("LIFEOS_TX_AMOUNT", self.db.execute,
                         "UPDATE ledger_transaction SET amount_minor = 0 WHERE node_id = ?", (t,))

    def test_transfer_shape(self):
        self.assertAbort("LIFEOS_TX_TRANSFER", self.tx, 100, "TRANSFER", 500)
        self.assertAbort("LIFEOS_TX_TRANSFER", self.tx, 101, "TRANSFER", 500, counter=self.giro)
        self.assertAbort("LIFEOS_TX_COUNTER_ACCOUNT", self.tx, 102, "EXPENSE", 500, counter=self.spar)
        t = self.tx(103, "TRANSFER", 500, counter=self.spar)
        self.assertAbort("LIFEOS_SPLIT_ON_TRANSFER", self.split, 200, t, self.food, 500)

    def test_currency_must_match_account(self):
        self.assertAbort("LIFEOS_TX_CURRENCY", self.tx, 100, "EXPENSE", 500, currency="USD")
        self.assertAbort("LIFEOS_TX_CURRENCY", self.tx, 101, "TRANSFER", 500, counter=self.usd)

    def test_booking_date_must_be_real_iso_date(self):
        self.assertAbort("LIFEOS_TX_DATE", self.tx, 100, "EXPENSE", 1, date="20.10.2026")
        self.assertAbort("LIFEOS_TX_DATE", self.tx, 101, "EXPENSE", 1, date="2026-02-30")
        self.assertAbort("LIFEOS_TX_DATE", self.tx, 102, "EXPENSE", 1, date="2026-13-01")
        self.tx(103, "EXPENSE", 1, date="2028-02-29")

    def test_node_type_must_match_table(self):
        wrong = self.node(100, "ACCOUNT")
        self.assertAbort("LIFEOS_NODE_TYPE", self.db.execute,
                         "INSERT INTO ledger_transaction (node_id, kind, account_id, amount_minor, currency,"
                         " booking_date) VALUES (?, 'EXPENSE', ?, 1, 'EUR', '2026-10-20')", (wrong, self.giro))
        self.assertAbort("LIFEOS_NODE_TYPE_IMMUTABLE", self.db.execute,
                         "UPDATE node SET type = 'PROJECT' WHERE id = ?", (self.giro,))

    def test_split_rules(self):
        t = self.tx(100, "EXPENSE", 1000)
        self.assertAbort("LIFEOS_SPLIT_AMOUNT", self.split, 200, t, self.food, 0)
        self.assertAbort("LIFEOS_SPLIT_DIRECTION", self.split, 201, t, self.salary, 1000)
        s = self.split(202, t, self.food, 1000)
        self.assertAbort("LIFEOS_SPLIT_DIRECTION", self.db.execute,
                         "UPDATE transaction_split SET category_id = ? WHERE id = ?", (self.salary, s))
        self.assertAbort("LIFEOS_TX_KIND_LOCKED", self.db.execute,
                         "UPDATE ledger_transaction SET kind = 'INCOME' WHERE node_id = ?", (t,))

    def test_unknown_references_fail_on_foreign_keys(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.tx(100, "EXPENSE", 1, account=uid(999))
        t = self.tx(101, "EXPENSE", 1)
        with self.assertRaises(sqlite3.IntegrityError):
            self.split(200, t, uid(998), 1)

    # ───── Tests: Löschen & Kaskaden ─────
    def test_deleting_transaction_node_cascades_to_splits_and_links(self):
        vehicle = self.node(500, "VEHICLE", "Golf VII")
        project = self.node(501, "PROJECT", "Bremsen")
        t = self.tx(100, "EXPENSE", 8990)
        self.split(200, t, self.car, 8990)
        self.db.execute("INSERT INTO link VALUES (?,?,?,?,0)", (uid(300), t, vehicle, "CONCERNS"))
        self.db.execute("INSERT INTO link VALUES (?,?,?,?,0)", (uid(301), t, project, "PART_OF"))
        self.db.execute("DELETE FROM node WHERE id = ?", (t,))
        self.assertEqual(0, self.count("ledger_transaction"))
        self.assertEqual(0, self.count("transaction_split"))
        self.assertEqual(0, self.count("link"))
        # Fahrzeug und Projekt bleiben bestehen
        self.assertEqual(2, self.db.execute("SELECT COUNT(*) FROM node WHERE type IN ('VEHICLE','PROJECT')").fetchone()[0])

    def test_account_and_category_in_use_cannot_be_deleted(self):
        t = self.tx(100, "EXPENSE", 1)
        self.split(200, t, self.food, 1)
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("DELETE FROM node WHERE id = ?", (self.giro,))
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("DELETE FROM node WHERE id = ?", (self.food,))
        # unbenutzte Kategorie darf weg
        self.db.execute("DELETE FROM node WHERE id = ?", (self.car,))
        self.assertEqual(0, self.db.execute("SELECT COUNT(*) FROM category WHERE node_id = ?", (self.car,)).fetchone()[0])

    def test_insert_or_replace_on_node_would_destroy_data(self):
        """Begründet die Regel in CLAUDE.md: Nodes niemals mit REPLACE schreiben."""
        t = self.tx(100, "EXPENSE", 1)
        self.split(200, t, self.food, 1)
        self.db.execute(
            "INSERT OR REPLACE INTO node (id, type, title, created_at, updated_at) VALUES (?, 'TRANSACTION', 'neu', 0, 0)",
            (t,),
        )
        self.assertEqual(0, self.count("ledger_transaction"))  # ← Datenverlust durch Kaskade

    def test_account_currency_locked_after_first_booking(self):
        self.db.execute("UPDATE account SET currency = 'CHF' WHERE node_id = ?", (self.spar,))
        self.tx(100, "EXPENSE", 1)
        self.assertAbort("LIFEOS_ACCOUNT_CURRENCY_LOCKED", self.db.execute,
                         "UPDATE account SET currency = 'CHF' WHERE node_id = ?", (self.giro,))

    def test_category_direction_locked_when_used(self):
        self.db.execute("UPDATE category SET direction = 'INCOME' WHERE node_id = ?", (self.car,))
        t = self.tx(100, "EXPENSE", 1)
        self.split(200, t, self.food, 1)
        self.assertAbort("LIFEOS_CATEGORY_DIRECTION_LOCKED", self.db.execute,
                         "UPDATE category SET direction = 'INCOME' WHERE node_id = ?", (self.food,))

    # ───── Tests: Links ─────
    def test_links(self):
        a = self.node(500, "VEHICLE")
        self.assertAbort("LIFEOS_LINK_SELF", self.db.execute,
                         "INSERT INTO link VALUES (?,?,?,?,0)", (uid(300), a, a, "CONCERNS"))
        self.assertAbort("LIFEOS_LINK_RELATION", self.db.execute,
                         "INSERT INTO link VALUES (?,?,?,?,0)", (uid(301), a, self.giro, "LOVES"))
        self.db.execute("INSERT INTO link VALUES (?,?,?,?,0)", (uid(302), a, self.giro, "RELATES_TO"))
        with self.assertRaises(sqlite3.IntegrityError):  # Duplikat
            self.db.execute("INSERT INTO link VALUES (?,?,?,?,0)", (uid(303), a, self.giro, "RELATES_TO"))

    # ───── Tests: Budgets ─────
    def test_budget_rules(self):
        ins = "INSERT INTO budget VALUES (?,?,?,?, 'EUR')"
        self.db.execute(ins, (uid(400), self.food, "2026-10", 40000))
        self.assertAbort("LIFEOS_BUDGET_AMOUNT", self.db.execute, ins, (uid(401), self.car, "2026-10", 0))
        self.assertAbort("LIFEOS_BUDGET_MONTH", self.db.execute, ins, (uid(402), self.car, "10/2026", 1))
        self.assertAbort("LIFEOS_BUDGET_DIRECTION", self.db.execute, ins, (uid(403), self.salary, "2026-10", 1))
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(ins, (uid(404), self.food, "2026-10", 1))

    # ───── Tests: Integritätsprüfer ─────
    def test_integrity_queries_find_violations(self):
        ok = self.tx(100, "EXPENSE", 1000)
        self.split(200, ok, self.food, 1000)
        missing = self.tx(101, "EXPENSE", 500)            # keine Splits
        partial = self.tx(102, "EXPENSE", 500)
        self.split(201, partial, self.food, 499)           # Summe falsch
        self.tx(103, "TRANSFER", 500, counter=self.spar)   # korrekt ohne Splits
        orphan = self.node(104, "TRANSACTION")             # Node ohne Fachzeile

        rows = self.db.execute(SQL["QUERY_SPLIT_VIOLATIONS"]).fetchall()
        self.assertEqual([(missing, "EXPENSE", 500, 0, 0), (partial, "EXPENSE", 500, 499, 1)], rows)
        self.assertEqual([(orphan, "TRANSACTION")], self.db.execute(SQL["QUERY_ORPHAN_NODES"]).fetchall())
        self.assertEqual(4, self.db.execute(SQL["QUERY_TRANSACTION_COUNT"]).fetchone()[0])
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    # ───── Tests: v2 – Erstattung, Korrektur ─────
    def test_refund_uses_expense_categories(self):
        t = self.tx(100, "REFUND", 500)
        self.assertAbort("LIFEOS_SPLIT_DIRECTION", self.split, 200, t, self.salary, 500)
        self.split(201, t, self.food, 500)
        self.assertEqual([], self.db.execute(SQL["QUERY_SPLIT_VIOLATIONS"]).fetchall())

    def test_corrections_have_no_splits_and_no_counter_account(self):
        t = self.tx(100, "CORRECTION_OUT", 500)
        self.assertAbort("LIFEOS_SPLIT_ON_TRANSFER", self.split, 200, t, self.food, 500)
        self.assertAbort("LIFEOS_TX_COUNTER_ACCOUNT", self.tx, 101, "CORRECTION_IN", 5, counter=self.spar)
        self.assertAbort("LIFEOS_TX_KIND", self.tx, 102, "GIFT", 5)
        self.assertEqual([], self.db.execute(SQL["QUERY_SPLIT_VIOLATIONS"]).fetchall())

    def test_refund_without_splits_is_found_by_integrity_check(self):
        t = self.tx(100, "REFUND", 500)
        self.assertEqual([(t, "REFUND", 500, 0, 0)], self.db.execute(SQL["QUERY_SPLIT_VIOLATIONS"]).fetchall())

    # ───── Tests: v2 – wiederkehrende Zahlungen ─────
    def recurring(self, n, kind="EXPENSE", category=None, amount=95000, interval=1, anchor="2026-10-01", end=None, currency="EUR"):
        nid = self.node(n, "RECURRING", "Miete")
        self.db.execute(
            "INSERT INTO recurring_payment (node_id, kind, account_id, category_id, amount_minor, currency,"
            " interval_months, anchor_date, end_date, active, is_fixed_cost) VALUES (?,?,?,?,?,?,?,?,?,1,1)",
            (nid, kind, self.giro, category or self.food, amount, currency, interval, anchor, end),
        )
        return nid

    def test_recurring_rules(self):
        self.recurring(300)
        self.assertAbort("LIFEOS_RECURRING_AMOUNT", self.recurring, 301, amount=0)
        self.assertAbort("LIFEOS_RECURRING_KIND", self.recurring, 302, kind="TRANSFER")
        self.assertAbort("LIFEOS_RECURRING_INTERVAL", self.recurring, 303, interval=5)
        self.assertAbort("LIFEOS_RECURRING_DATE", self.recurring, 304, anchor="2026-02-30")
        self.assertAbort("LIFEOS_RECURRING_DATE", self.recurring, 305, end="2026-09-01")
        self.assertAbort("LIFEOS_RECURRING_DIRECTION", self.recurring, 306, category=self.salary)
        self.assertAbort("LIFEOS_RECURRING_CURRENCY", self.recurring, 307, currency="USD")
        wrong = self.node(308, "PROJECT")
        self.assertAbort("LIFEOS_NODE_TYPE", self.db.execute,
                         "INSERT INTO recurring_payment VALUES (?, 'EXPENSE', ?, ?, 1, 'EUR', 1, '2026-10-01', NULL, NULL, NULL, 1, 1)",
                         (wrong, self.giro, self.food))

    def test_occurrence_can_only_be_booked_once(self):
        r = self.recurring(300)
        t1 = self.tx(100, "EXPENSE", 95000)
        t2 = self.tx(101, "EXPENSE", 95000)
        ins = "INSERT INTO recurring_booking (recurring_id, due_date, status, transaction_id) VALUES (?,?,?,?)"
        self.db.execute(ins, (r, "2026-10-01", "BOOKED", t1))
        with self.assertRaises(sqlite3.IntegrityError):  # gleiche Fälligkeit zweimal
            self.db.execute(ins, (r, "2026-10-01", "BOOKED", t2))
        with self.assertRaises(sqlite3.IntegrityError):  # gleiche Buchung für zwei Fälligkeiten
            self.db.execute(ins, (r, "2026-11-01", "BOOKED", t1))
        self.assertAbort("LIFEOS_BOOKING_TX", self.db.execute, ins, (r, "2026-12-01", "BOOKED", None))
        self.assertAbort("LIFEOS_BOOKING_TX", self.db.execute, ins, (r, "2026-12-01", "SKIPPED", t2))
        self.assertAbort("LIFEOS_BOOKING_STATUS", self.db.execute, ins, (r, "2026-12-01", "MAYBE", None))
        self.assertAbort("LIFEOS_BOOKING_IMMUTABLE", self.db.execute,
                         "UPDATE recurring_booking SET due_date = '2026-10-02'")

    def test_deleting_booked_transaction_reopens_occurrence_and_plan_delete_keeps_transactions(self):
        r = self.recurring(300)
        t = self.tx(100, "EXPENSE", 95000)
        self.split(200, t, self.food, 95000)
        self.db.execute("INSERT INTO recurring_booking VALUES (?, '2026-10-01', 'BOOKED', ?)", (r, t))
        self.db.execute("DELETE FROM node WHERE id = ?", (t,))
        self.assertEqual(0, self.count("recurring_booking"))  # Fälligkeit wieder offen
        t2 = self.tx(101, "EXPENSE", 95000)
        self.split(201, t2, self.food, 95000)
        self.db.execute("INSERT INTO recurring_booking VALUES (?, '2026-10-01', 'BOOKED', ?)", (r, t2))
        self.db.execute("DELETE FROM node WHERE id = ?", (r,))  # Plan löschen
        self.assertEqual(0, self.count("recurring_booking"))
        self.assertEqual(1, self.count("ledger_transaction"))  # Buchung bleibt

    def test_total_budget(self):
        ins = "INSERT INTO total_budget VALUES (?,?,?, 'EUR')"
        self.db.execute(ins, (uid(400), "2026-10", 200000))
        self.assertAbort("LIFEOS_BUDGET_AMOUNT", self.db.execute, ins, (uid(401), "2026-11", 0))
        self.assertAbort("LIFEOS_BUDGET_MONTH", self.db.execute, ins, (uid(402), "2026/11", 1))
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(ins, (uid(403), "2026-10", 1))

    def test_triggers_are_idempotent(self):
        for stmt in ordered("TRIGGER_"):
            self.db.execute(stmt)  # zweites Mal – wie bei jeder Migration
        names = {r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type='trigger'")}
        self.assertEqual(len(ordered("TRIGGER_")), len(names))


class MigrationTest(unittest.TestCase):
    """v1 (exakt aus Rooms 1.json) + Daten → MIGRATION_1_2 + Trigger-Neuinstallation → Daten intakt, Schema = Referenz."""

    SCHEMA_DIR = ROOT / "core/database/schemas/app.lifeos.core.database.LifeOsDatabase"

    def build(self, version):
        import json
        j = json.loads((self.SCHEMA_DIR / f"{version}.json").read_text())
        db = sqlite3.connect(":memory:", isolation_level=None)
        db.execute("PRAGMA foreign_keys = ON")
        for e in j["database"]["entities"]:
            db.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
            for i in e.get("indices", []):
                db.execute(i["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        return db

    def describe(self, db):
        out = {}
        for (t,) in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite%' AND name <> 'room_master_table'"):
            cols = db.execute(f"PRAGMA table_info(`{t}`)").fetchall()
            fks = sorted(r[2:] for r in db.execute(f"PRAGMA foreign_key_list(`{t}`)").fetchall())
            idx = sorted(r[1:3] for r in db.execute(f"PRAGMA index_list(`{t}`)").fetchall())
            out[t] = (cols, fks, idx)
        return out

    def migration_statements(self):
        text = SCHEMA_KT.read_text(encoding="utf-8")
        block = re.search(r"val MIGRATION_1_2: List<String> = listOf\((.*?)\)", text, re.S).group(1)
        names = [n.strip() for n in block.split(",") if n.strip()]
        return [SQL[n] for n in names]

    def reinstall_triggers(self, db):
        for stmt in ordered("TRIGGER_"):
            name = re.search(r"CREATE TRIGGER IF NOT EXISTS `(\w+)`", stmt).group(1)
            db.execute(f"DROP TRIGGER IF EXISTS `{name}`")
            db.execute(stmt)

    def test_v1_to_v2_keeps_data_and_matches_reference(self):
        db = self.build(1)
        # v1-Daten anlegen
        db.execute("INSERT INTO node VALUES (?, 'ACCOUNT', 'Giro', 0, 0, NULL, 0)", (uid(1),))
        db.execute("INSERT INTO account VALUES (?, 'CHECKING', 'EUR', 1000, 0)", (uid(1),))
        db.execute("INSERT INTO node VALUES (?, 'CATEGORY', 'Lebensmittel', 0, 0, NULL, 0)", (uid(2),))
        db.execute("INSERT INTO category VALUES (?, NULL, 'EXPENSE', 0)", (uid(2),))
        db.execute("INSERT INTO node VALUES (?, 'TRANSACTION', 'Markt', 0, 0, NULL, 0)", (uid(3),))
        db.execute("INSERT INTO ledger_transaction VALUES (?, 'EXPENSE', ?, NULL, 4299, 'EUR', '2026-10-18', 'Markt', NULL)", (uid(3), uid(1)))
        db.execute("INSERT INTO transaction_split VALUES (?, ?, ?, 4299, NULL, 0)", (uid(4), uid(3), uid(2)))
        db.execute("INSERT INTO budget VALUES (?, ?, '2026-10', 40000, 'EUR')", (uid(5), uid(2)))
        before = {t: db.execute(f"SELECT * FROM {t} ORDER BY 1").fetchall()
                  for t in ["node", "account", "category", "ledger_transaction", "transaction_split", "budget"]}

        db.execute("BEGIN")
        for stmt in self.migration_statements():
            db.execute(stmt)
        self.reinstall_triggers(db)
        db.execute("COMMIT")

        for t, rows in before.items():
            self.assertEqual(rows, db.execute(f"SELECT * FROM {t} ORDER BY 1").fetchall(), t)
        self.assertEqual([], db.execute(SQL["QUERY_SPLIT_VIOLATIONS"]).fetchall())
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())

        ref = sqlite3.connect(":memory:")
        for stmt in ordered("TABLE_") + ordered("INDEX_"):
            ref.execute(stmt)
        self.assertEqual(self.describe(ref), self.describe(db))

        # v2-Regeln greifen nach der Migration
        with self.assertRaises(sqlite3.DatabaseError):
            db.execute("INSERT INTO node VALUES (?, 'TRANSACTION', 'x', 0, 0, NULL, 0)", (uid(6),))
            db.execute("INSERT INTO ledger_transaction VALUES (?, 'REFUND', ?, ?, 5, 'EUR', '2026-10-18', NULL, NULL)", (uid(6), uid(1), uid(1)))

    def test_room_v2_schema_matches_reference_if_present(self):
        path = self.SCHEMA_DIR / "2.json"
        if not path.exists():
            self.skipTest("2.json entsteht beim ersten Gradle-Build")
        ref = sqlite3.connect(":memory:")
        for stmt in ordered("TABLE_") + ordered("INDEX_"):
            ref.execute(stmt)
        self.assertEqual(self.describe(ref), self.describe(self.build(2)))


if __name__ == "__main__":
    unittest.main()
