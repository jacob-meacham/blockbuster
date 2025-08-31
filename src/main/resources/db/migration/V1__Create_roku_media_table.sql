-- Create roku_media table for storing Roku-specific media content
CREATE TABLE IF NOT EXISTS roku_media (
    uuid TEXT PRIMARY KEY,
    channel_id TEXT NOT NULL,
    ecp_command TEXT DEFAULT 'launch',
    content_id TEXT NOT NULL,
    media_type TEXT,
    title TEXT,
    created_at TIMESTAMP DEFAULT (datetime('now')),
    updated_at TIMESTAMP DEFAULT (datetime('now'))
);
