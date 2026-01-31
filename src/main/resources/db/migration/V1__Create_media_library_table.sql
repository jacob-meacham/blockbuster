-- Unified library table to store plugin + config_json per UUID
CREATE TABLE IF NOT EXISTS media_library (
    uuid TEXT PRIMARY KEY,
    plugin TEXT NOT NULL,
    config_json TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT (datetime('now')),
    updated_at TIMESTAMP DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_library_plugin ON media_library(plugin);


