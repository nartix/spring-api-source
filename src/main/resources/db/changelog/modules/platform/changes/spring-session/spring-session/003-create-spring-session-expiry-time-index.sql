--liquibase formatted sql

--changeset feroz:20260428-003-create-spring-session-expiry-time-index
CREATE INDEX IF NOT EXISTS spring_session_ix2
    ON spring_session (expiry_time);

--rollback DROP INDEX IF EXISTS spring_session_ix2;
