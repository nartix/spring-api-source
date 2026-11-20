--liquibase formatted sql

--changeset feroz:20260508-002-create-data-category-indexes
CREATE INDEX IF NOT EXISTS idx_data_category_parent_id ON data_category(parent_id);
CREATE INDEX IF NOT EXISTS idx_data_category_slug ON data_category(slug);
CREATE INDEX IF NOT EXISTS idx_data_category_is_active ON data_category(is_active);
CREATE UNIQUE INDEX IF NOT EXISTS uc_data_category_root_slug ON data_category(slug) WHERE parent_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uc_data_category_parent_slug ON data_category(parent_id, slug) WHERE parent_id IS NOT NULL;

--rollback DROP INDEX IF EXISTS uc_data_category_parent_slug;
--rollback DROP INDEX IF EXISTS uc_data_category_root_slug;
--rollback DROP INDEX IF EXISTS idx_data_category_is_active;
--rollback DROP INDEX IF EXISTS idx_data_category_slug;
--rollback DROP INDEX IF EXISTS idx_data_category_parent_id;
