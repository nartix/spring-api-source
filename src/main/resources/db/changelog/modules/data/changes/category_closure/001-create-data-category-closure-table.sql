--liquibase formatted sql

--changeset feroz:20260508-003-create-data-category-closure-table
CREATE TABLE IF NOT EXISTS data_category_closure
(
    ancestor_id BIGINT NOT NULL,
    descendant_id BIGINT NOT NULL,
    depth INTEGER NOT NULL,
    PRIMARY KEY (ancestor_id, descendant_id),
    CONSTRAINT fk_data_category_closure_ancestor FOREIGN KEY (ancestor_id) REFERENCES data_category(id) ON DELETE CASCADE,
    CONSTRAINT fk_data_category_closure_descendant FOREIGN KEY (descendant_id) REFERENCES data_category(id) ON DELETE CASCADE,
    CONSTRAINT ck_data_category_closure_depth CHECK (depth >= 0)
);

--rollback DROP TABLE IF EXISTS data_category_closure;
