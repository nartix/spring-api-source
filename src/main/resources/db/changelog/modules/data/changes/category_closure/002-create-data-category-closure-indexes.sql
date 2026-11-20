--liquibase formatted sql

--changeset feroz:20260508-004-create-data-category-closure-indexes
CREATE INDEX IF NOT EXISTS idx_data_category_closure_descendant ON data_category_closure(descendant_id);
CREATE INDEX IF NOT EXISTS idx_data_category_closure_ancestor_depth ON data_category_closure(ancestor_id, depth);
CREATE INDEX IF NOT EXISTS idx_data_category_closure_descendant_depth ON data_category_closure(descendant_id, depth);

--rollback DROP INDEX IF EXISTS idx_data_category_closure_descendant_depth;
--rollback DROP INDEX IF EXISTS idx_data_category_closure_ancestor_depth;
--rollback DROP INDEX IF EXISTS idx_data_category_closure_descendant;
