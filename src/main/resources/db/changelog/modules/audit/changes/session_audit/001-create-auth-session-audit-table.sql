--liquibase formatted sql

--changeset feroz:20260531-001-create-auth-session-audit-table
CREATE TABLE IF NOT EXISTS auth_session_audit
(
    id BIGSERIAL PRIMARY KEY,
    session_scope VARCHAR(32),
    event_type VARCHAR(64) NOT NULL,
    user_id BIGINT,
    username VARCHAR(120),
    session_id_hash VARCHAR(128),
    ip_address VARCHAR(128),
    user_agent VARCHAR(512),
    failure_reason VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS auth_session_audit_user_id_idx
    ON auth_session_audit (user_id);

CREATE INDEX IF NOT EXISTS auth_session_audit_created_at_idx
    ON auth_session_audit (created_at);

CREATE INDEX IF NOT EXISTS auth_session_audit_session_scope_idx
    ON auth_session_audit (session_scope);

--rollback DROP TABLE IF EXISTS auth_session_audit;
