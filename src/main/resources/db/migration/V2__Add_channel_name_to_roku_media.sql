-- Add channel_name column to roku_media to support display metadata
ALTER TABLE roku_media ADD COLUMN channel_name TEXT;

