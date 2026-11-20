--liquibase formatted sql

--changeset feroz:20260428-001-create-spring-session-table
CREATE TABLE IF NOT EXISTS spring_session
(
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

--rollback DROP TABLE IF EXISTS spring_session;
