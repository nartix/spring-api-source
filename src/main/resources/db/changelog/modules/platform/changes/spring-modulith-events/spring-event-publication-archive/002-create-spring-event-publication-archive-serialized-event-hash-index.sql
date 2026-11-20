--liquibase formatted sql

--changeset feroz:20260427-002-create-spring-event-publication-archive-serialized-event-hash-index
CREATE INDEX IF NOT EXISTS event_publication_archive_serialized_event_hash_idx
    ON spring_event_publication_archive USING hash (serialized_event);

--rollback DROP INDEX IF EXISTS event_publication_archive_serialized_event_hash_idx;
