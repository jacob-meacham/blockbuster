CREATE TABLE IF NOT EXISTS auth_tokens (
    plugin     TEXT NOT NULL,
    token_type TEXT NOT NULL,
    token_value TEXT NOT NULL,
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP DEFAULT (datetime('now')),
    updated_at  TIMESTAMP DEFAULT (datetime('now')),
    PRIMARY KEY (plugin, token_type)
);
