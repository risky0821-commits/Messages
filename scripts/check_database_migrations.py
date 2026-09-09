"""SQLite migration smoke tests; these do not replace Android/Room device tests."""
import json
import pathlib
import re
import sqlite3
import unittest

SOURCE = pathlib.Path('app/src/main/kotlin/org/fossify/messages/databases/MessagesDatabase.kt').read_text(encoding='utf-8')
MIGRATIONS = {}
for match in re.finditer(r'private val MIGRATION_(\d+)_(\d+).*?(?=private val MIGRATION_|\Z)', SOURCE, re.S):
    statements = []
    for sql in re.finditer(r'execSQL\(\s*((?:"(?:\\.|[^"\\])*"\s*(?:\+\s*)?)+)\)', match.group(0)):
        statements.append(''.join(json.loads(part) for part in re.findall(r'"(?:\\.|[^"\\])*"', sql.group(1))))
    MIGRATIONS[int(match[1])] = (int(match[2]), statements)

def legacy_database():
    db = sqlite3.connect(':memory:')
    db.execute('CREATE TABLE conversations (thread_id INTEGER NOT NULL PRIMARY KEY, snippet TEXT NOT NULL, date INTEGER NOT NULL, read INTEGER NOT NULL, title TEXT NOT NULL, photo_uri TEXT NOT NULL, is_group_conversation INTEGER NOT NULL, phone_number TEXT NOT NULL)')
    db.execute("INSERT INTO conversations VALUES (42, 'sample', 1, 0, 'Contact', '', 0, '555')")
    return db

def migrate(db, start, end):
    for version in range(start, end):
        target, statements = MIGRATIONS[version]
        if target != version + 1 or not statements:
            raise AssertionError(f'Missing migration {version}')
        with db:
            for statement in statements:
                db.execute(statement)

class DatabaseMigrationSmokeTests(unittest.TestCase):
    def test_known_chain_keeps_conversation(self):
        with legacy_database() as db:
            migrate(db, 1, 13)
            self.assertEqual(db.execute('SELECT thread_id, snippet FROM conversations').fetchall(), [(42, 'sample')])
            self.assertEqual(db.execute('SELECT unread_count, archived FROM conversations').fetchone(), (0, 0))

    def test_12_to_13_keeps_categories_mappings_and_drafts(self):
        with legacy_database() as db:
            migrate(db, 1, 12)
            db.execute("INSERT INTO message_categories VALUES (7, 'Personal', 2, 123)")
            db.execute('INSERT INTO category_conversations VALUES (7, 42)')
            db.execute("INSERT INTO drafts VALUES (42, 'draft to keep', 456)")
            migrate(db, 12, 13)
            self.assertEqual(db.execute('SELECT id, name, sort_order, created_at, show_in_all FROM message_categories').fetchone(), (7, 'Personal', 2, 123, 1))
            self.assertEqual(db.execute('SELECT * FROM category_conversations').fetchall(), [(7, 42)])
            self.assertEqual(db.execute('SELECT * FROM drafts').fetchall(), [(42, 'draft to keep', 456)])

    def test_no_destructive_fallback_is_configured(self):
        self.assertNotRegex(SOURCE, r'\.fallbackToDestructiveMigration\w*\s*\(')

if __name__ == '__main__':
    unittest.main(verbosity=2)

