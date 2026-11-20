--liquibase formatted sql

--changeset feroz:20260428-004-create-spring-session-principal-name-index
CREATE INDEX IF NOT EXISTS spring_session_ix3
    ON spring_session (principal_name);

--rollback DROP INDEX IF EXISTS spring_session_ix3;
