--liquibase formatted sql

--changeset feroz:20260428-002-create-spring-session-session-id-unique-index
CREATE UNIQUE INDEX IF NOT EXISTS spring_session_ix1
    ON spring_session (session_id);

--rollback DROP INDEX IF EXISTS spring_session_ix1;
