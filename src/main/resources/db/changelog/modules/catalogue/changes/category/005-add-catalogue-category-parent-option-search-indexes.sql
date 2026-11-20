--liquibase formatted sql

--changeset feroz:catalogue-category-005-add-parent-option-search-indexes
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_catalogue_category_lower_name_trgm
    ON catalogue_category USING gin (lower(name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_catalogue_category_lower_slug_trgm
    ON catalogue_category USING gin (lower(slug) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_catalogue_category_closure_descendant_depth
    ON catalogue_category_closure(descendant_id, depth, ancestor_id);

--rollback DROP INDEX IF EXISTS idx_catalogue_category_closure_descendant_depth;
--rollback DROP INDEX IF EXISTS idx_catalogue_category_lower_slug_trgm;
--rollback DROP INDEX IF EXISTS idx_catalogue_category_lower_name_trgm;
-- pg_trgm is intentionally left installed because other database objects may depend on it.
