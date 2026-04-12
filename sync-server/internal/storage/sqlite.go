package storage

import (
	"database/sql"
	"fmt"

	_ "github.com/mattn/go-sqlite3"
)

const sqliteSchema = `
CREATE TABLE IF NOT EXISTS sync_mutations (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  mutation_id TEXT NOT NULL UNIQUE,
  origin_node TEXT NOT NULL,
  counter INTEGER NOT NULL,
  payload BLOB NOT NULL,
  created_at INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS sync_mutations_origin_counter_idx
ON sync_mutations(origin_node, counter);

CREATE TABLE IF NOT EXISTS sync_checkpoints (
  node_id TEXT PRIMARY KEY,
  checkpoint_payload BLOB NOT NULL,
  updated_at INTEGER NOT NULL DEFAULT (unixepoch())
);
`

func OpenSQLite(dbPath string) (*sql.DB, error) {
	if dbPath == "" {
		return nil, fmt.Errorf("sqlite path is required")
	}

	db, err := sql.Open("sqlite3", dbPath)
	if err != nil {
		return nil, fmt.Errorf("open sqlite db: %w", err)
	}

	if _, err = db.Exec(sqliteSchema); err != nil {
		db.Close()
		return nil, fmt.Errorf("initialize sqlite schema: %w", err)
	}

	return db, nil
}