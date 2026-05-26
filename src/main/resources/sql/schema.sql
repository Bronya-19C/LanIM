CREATE TABLE IF NOT EXISTS peers (
    peer_id          TEXT PRIMARY KEY,
    nickname         TEXT NOT NULL,
    address          TEXT NOT NULL,
    port             INTEGER NOT NULL,
    cert_fingerprint TEXT,
    last_seen        INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS messages (
    message_id   TEXT PRIMARY KEY,
    room_id      TEXT NOT NULL,
    sender_id    TEXT NOT NULL,
    sequence     INTEGER NOT NULL,
    type         TEXT NOT NULL,
    timestamp    INTEGER NOT NULL,
    payload_json TEXT NOT NULL,
    UNIQUE(sender_id, sequence)
);

CREATE TABLE IF NOT EXISTS files (
    file_id         TEXT PRIMARY KEY,
    message_id      TEXT NOT NULL,
    file_name       TEXT NOT NULL,
    content_type    TEXT,
    total_size      INTEGER NOT NULL,
    total_chunks    INTEGER NOT NULL,
    checksum        TEXT,
    local_path      TEXT,
    received_chunks INTEGER NOT NULL DEFAULT 0,
    status          TEXT NOT NULL DEFAULT 'PENDING',
    FOREIGN KEY (message_id) REFERENCES messages(message_id)
);

CREATE INDEX IF NOT EXISTS idx_messages_room_sender_seq
    ON messages(room_id, sender_id, sequence);
CREATE INDEX IF NOT EXISTS idx_messages_room_time
    ON messages(room_id, timestamp);
