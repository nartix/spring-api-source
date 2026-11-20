--liquibase formatted sql

--changeset feroz:20260427-003-create-spring-event-publication-completion-date-index
CREATE INDEX IF NOT EXISTS spring_event_publication_by_completion_date_idx
    ON spring_event_publication (completion_date);

--rollback DROP INDEX IF EXISTS spring_event_publication_by_completion_date_idx;
