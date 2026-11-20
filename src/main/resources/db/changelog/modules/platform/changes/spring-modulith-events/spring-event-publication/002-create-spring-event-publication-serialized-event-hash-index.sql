--liquibase formatted sql

--changeset feroz:20260427-002-create-spring-event-publication-serialized-event-hash-index
CREATE INDEX IF NOT EXISTS spring_event_publication_serialized_event_hash_idx
    ON spring_event_publication USING hash (serialized_event);

--rollback DROP INDEX IF EXISTS spring_event_publication_serialized_event_hash_idx;
