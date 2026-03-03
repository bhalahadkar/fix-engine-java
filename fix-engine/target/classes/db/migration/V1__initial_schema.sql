-- ============================================================
-- FIX Engine Database Schema
-- Migration: V1__initial_schema.sql
-- ============================================================

-- Sequence for fix_messages primary key (batch allocation)
CREATE SEQUENCE IF NOT EXISTS fix_messages_seq
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    CACHE 50;

-- ── FIX Message Log ─────────────────────────────────────────
CREATE TABLE fix_messages (
    db_id           BIGINT PRIMARY KEY DEFAULT nextval('fix_messages_seq'),
    message_id      UUID NOT NULL DEFAULT gen_random_uuid(),
    session_id      VARCHAR(200) NOT NULL,
    sender_comp_id  VARCHAR(50)  NOT NULL,
    target_comp_id  VARCHAR(50)  NOT NULL,
    begin_string    VARCHAR(10),
    msg_type        VARCHAR(10)  NOT NULL,
    msg_seq_num     BIGINT,
    direction       CHAR(1)      NOT NULL CHECK (direction IN ('I','O')),
    raw_message     TEXT         NOT NULL,
    parsed_fields   TEXT,
    sending_time    TIMESTAMPTZ,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    node_id         VARCHAR(50),

    CONSTRAINT uq_fix_msg_id UNIQUE (message_id)
);

CREATE INDEX idx_fix_msg_session   ON fix_messages(session_id);
CREATE INDEX idx_fix_msg_type      ON fix_messages(msg_type);
CREATE INDEX idx_fix_msg_time      ON fix_messages(received_at DESC);
CREATE INDEX idx_fix_msg_sender    ON fix_messages(sender_comp_id);
CREATE INDEX idx_fix_msg_target    ON fix_messages(target_comp_id);
CREATE INDEX idx_fix_msg_seqnum    ON fix_messages(session_id, direction, msg_seq_num);
CREATE INDEX idx_fix_msg_search    ON fix_messages USING gin(to_tsvector('english', COALESCE(parsed_fields,'')));

COMMENT ON TABLE fix_messages IS 'All inbound and outbound FIX protocol messages';
COMMENT ON COLUMN fix_messages.direction IS 'I=Inbound (received), O=Outbound (sent)';

-- ── FIX Session Configuration ────────────────────────────────
CREATE TABLE fix_sessions (
    session_id          VARCHAR(200) PRIMARY KEY,
    begin_string        VARCHAR(10)  NOT NULL,
    sender_comp_id      VARCHAR(50)  NOT NULL,
    target_comp_id      VARCHAR(50)  NOT NULL,
    mode                VARCHAR(10)  NOT NULL CHECK (mode IN ('INITIATOR','ACCEPTOR')),
    host                VARCHAR(255),
    port                INTEGER      NOT NULL,
    heartbeat_interval  INTEGER      DEFAULT 30,
    reconnect_interval  INTEGER      DEFAULT 10,
    reset_on_logon      BOOLEAN      DEFAULT FALSE,
    reset_on_logout     BOOLEAN      DEFAULT FALSE,
    reset_on_disconnect BOOLEAN      DEFAULT FALSE,
    enabled             BOOLEAN      DEFAULT TRUE,
    created_at          TIMESTAMPTZ  DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  DEFAULT NOW()
);

COMMENT ON TABLE fix_sessions IS 'FIX session configuration — each row is one counterparty connection';

-- ── Cluster Node Registry ────────────────────────────────────
CREATE TABLE fix_nodes (
    node_id         VARCHAR(50)  PRIMARY KEY,
    host            VARCHAR(255) NOT NULL,
    port            INTEGER      NOT NULL,
    status          VARCHAR(20)  DEFAULT 'UNKNOWN',
    is_leader       BOOLEAN      DEFAULT FALSE,
    cpu_percent     NUMERIC(5,1),
    mem_percent     NUMERIC(5,1),
    active_sessions INTEGER      DEFAULT 0,
    last_heartbeat  TIMESTAMPTZ,
    app_version     VARCHAR(50),
    created_at      TIMESTAMPTZ  DEFAULT NOW()
);

-- ── GUI User Accounts ────────────────────────────────────────
CREATE TABLE fix_users (
    username        VARCHAR(100) PRIMARY KEY,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'VIEWER' CHECK (role IN ('VIEWER','OPERATOR','ADMIN')),
    enabled         BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMPTZ  DEFAULT NOW(),
    last_login      TIMESTAMPTZ
);

-- Default admin user (password: admin123 — CHANGE IN PRODUCTION)
INSERT INTO fix_users (username, password_hash, role)
VALUES ('admin', '$2a$12$K.a3mSGqERxPUMQfqlIEGOXtBz7cI9qPbE6MXBP7qOvqO7.sTmIuu', 'ADMIN');

-- Viewer user (password: viewer123)
INSERT INTO fix_users (username, password_hash, role)
VALUES ('viewer', '$2a$12$yVrdVGUkBdMvEaXXlGDsVOvFTq3X8X6H6O9SfL.wPbJ.M0O2UVSM2', 'VIEWER');

-- ── Audit Log ────────────────────────────────────────────────
CREATE TABLE fix_audit_log (
    id              BIGSERIAL    PRIMARY KEY,
    username        VARCHAR(100),
    action          VARCHAR(100) NOT NULL,
    target          VARCHAR(200),
    detail          TEXT,
    performed_at    TIMESTAMPTZ  DEFAULT NOW(),
    ip_address      VARCHAR(50)
);

CREATE INDEX idx_audit_log_time ON fix_audit_log(performed_at DESC);

COMMENT ON TABLE fix_audit_log IS 'Audit trail of all GUI actions (session start/stop/reset, config changes)';
