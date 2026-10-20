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

    def test_triggers_are_idempotent(self):
        for stmt in ordered("TRIGGER_"):
            self.db.execute(stmt)  # zweites Mal – wie bei jeder Migration
        names = {r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type='trigger'")}
        self.assertEqual(len(ordered("TRIGGER_")), len(names))


if __name__ == "__main__":
    unittest.main()
