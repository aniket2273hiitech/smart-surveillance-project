-- ================================================================
-- Smart Surveillance System — Database Schema
-- Student: Aniket Faguna (LNCDBTC11036) | LNCT University
-- ER Diagram entities: User, Criminal, DetectionLog, Alert
-- ================================================================

CREATE DATABASE IF NOT EXISTS surveillance_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE surveillance_db;

-- ── Table: users ─────────────────────────────────────────────
-- Roles: ADMIN, POLICE_OFFICER (as per ER diagram)
CREATE TABLE IF NOT EXISTS users (
    user_id   BIGINT       NOT NULL AUTO_INCREMENT,
    username  VARCHAR(50)  NOT NULL UNIQUE,
    password  VARCHAR(255) NOT NULL,
    role      VARCHAR(20)  NOT NULL,   -- 'ADMIN' or 'POLICE_OFFICER'
    enabled   TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB;

-- ── Table: criminals ─────────────────────────────────────────
-- ER Diagram: id, name, image (stored as path), case_details
CREATE TABLE IF NOT EXISTS criminals (
    criminal_id   BIGINT        NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100)  NOT NULL,
    image_path    VARCHAR(500)  NOT NULL,
    case_details  TEXT,
    crime_type    VARCHAR(100),
    registered_at DATETIME      NOT NULL,
    is_active     TINYINT(1)    NOT NULL DEFAULT 1,
    PRIMARY KEY (criminal_id)
) ENGINE=InnoDB;

-- ── Table: detection_logs ─────────────────────────────────────
-- ER Diagram: id, timestamp, location, match_status
-- References: User & Criminal (FK)
CREATE TABLE IF NOT EXISTS detection_logs (
    log_id              BIGINT        NOT NULL AUTO_INCREMENT,
    timestamp           DATETIME      NOT NULL,
    location            VARCHAR(200),
    match_status        VARCHAR(20)   NOT NULL,  -- MATCHED / NO_MATCH / PROCESSING
    confidence_score    DOUBLE,
    captured_frame_path VARCHAR(500),
    criminal_id         BIGINT,
    user_id             BIGINT        NOT NULL,
    PRIMARY KEY (log_id),
    CONSTRAINT fk_log_criminal FOREIGN KEY (criminal_id)
        REFERENCES criminals(criminal_id) ON DELETE SET NULL,
    CONSTRAINT fk_log_user     FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ── Table: alerts ─────────────────────────────────────────────
-- ER Diagram: AlertID (PK), AlertType
-- Triggered by DetectionLog
CREATE TABLE IF NOT EXISTS alerts (
    alert_id       BIGINT       NOT NULL AUTO_INCREMENT,
    alert_type     VARCHAR(30)  NOT NULL,
    message        TEXT,
    created_at     DATETIME     NOT NULL,
    is_acknowledged TINYINT(1)  NOT NULL DEFAULT 0,
    log_id         BIGINT       NOT NULL UNIQUE,
    PRIMARY KEY (alert_id),
    CONSTRAINT fk_alert_log FOREIGN KEY (log_id)
        REFERENCES detection_logs(log_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ================================================================
-- Default Admin User (password: admin123)
-- BCrypt hash of 'admin123' generated with strength 10
-- ================================================================
INSERT INTO users (username, password, role, enabled)
VALUES (
    'admin',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'ADMIN',
    1
) ON DUPLICATE KEY UPDATE username = username;

-- ================================================================
-- Default Police Officer (password: officer123)
-- BCrypt hash of 'officer123'
-- ================================================================
INSERT INTO users (username, password, role, enabled)
VALUES (
    'officer1',
    '$2a$10$TqsQbN9kBwE3eI0SFRv8p.RZ1r/Ic3jMMnvO1B.7F1S1U7cHq4BLG',
    'POLICE_OFFICER',
    1
) ON DUPLICATE KEY UPDATE username = username;
