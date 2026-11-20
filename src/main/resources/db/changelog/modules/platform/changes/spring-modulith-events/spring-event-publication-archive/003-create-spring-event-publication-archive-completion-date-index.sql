--liquibase formatted sql

--changeset feroz:20260427-003-create-spring-event-publication-archive-completion-date-index
CREATE INDEX IF NOT EXISTS event_publication_archive_by_completion_date_idx
    ON spring_event_publication_archive (completion_date);

--rollback DROP INDEX IF EXISTS event_publication_archive_by_completion_date_idx;
