CREATE TABLE installations (
    installation_id TEXT PRIMARY KEY NOT NULL,
    first_seen_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL,
    app_version TEXT NOT NULL
) WITHOUT ROWID;

CREATE INDEX installations_last_seen ON installations(last_seen_at);

CREATE TABLE service_metadata (
    key TEXT PRIMARY KEY NOT NULL,
    value TEXT NOT NULL
) WITHOUT ROWID;

INSERT INTO service_metadata(key, value)
VALUES ('started_at', CAST(CAST(strftime('%s', 'now') AS INTEGER) * 1000 AS TEXT));
