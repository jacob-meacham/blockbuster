ALTER TABLE media_library ADD COLUMN title TEXT;
UPDATE media_library SET title = json_extract(config_json, '$.title');
