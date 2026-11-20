--liquibase formatted sql

--changeset feroz:20260509-002-create-data-category-closure-covering-indexes
CREATE INDEX IF NOT EXISTS idx_data_category_closure_ancestor_depth_descendant
ON data_category_closure(ancestor_id, depth, descendant_id);

CREATE INDEX IF NOT EXISTS idx_data_category_closure_descendant_depth_ancestor
ON data_category_closure(descendant_id, depth, ancestor_id);

--rollback DROP INDEX IF EXISTS idx_data_category_closure_descendant_depth_ancestor;
--rollback DROP INDEX IF EXISTS idx_data_category_closure_ancestor_depth_descendant;
