--liquibase formatted sql

--changeset feroz:20260509-001-create-data-category-hierarchy-guardrail-indexes
CREATE INDEX IF NOT EXISTS idx_data_category_parent_id_id ON data_category(parent_id, id);

--rollback DROP INDEX IF EXISTS idx_data_category_parent_id_id;
