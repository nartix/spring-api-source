--liquibase formatted sql

--changeset feroz:20260428-005-create-spring-session-attributes-table
CREATE TABLE IF NOT EXISTS spring_session_attributes
(
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk
        FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id)
        ON DELETE CASCADE
);

--rollback DROP TABLE IF EXISTS spring_session_attributes;
