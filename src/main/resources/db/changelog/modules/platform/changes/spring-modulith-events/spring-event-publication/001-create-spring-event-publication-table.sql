--liquibase formatted sql

--changeset feroz:20260427-001-create-spring-event-publication-table
CREATE TABLE IF NOT EXISTS spring_event_publication
(
    id UUID NOT NULL,
    listener_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    status TEXT,
    completion_attempts INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

--rollback DROP TABLE IF EXISTS spring_event_publication;
