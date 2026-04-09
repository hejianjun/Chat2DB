CREATE TABLE IF NOT EXISTS login_attempt (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    client_fingerprint VARCHAR(64) UNIQUE NOT NULL,
    attempts INT DEFAULT 0,
    last_attempt_time TIMESTAMP,
    locked_until TIMESTAMP
);