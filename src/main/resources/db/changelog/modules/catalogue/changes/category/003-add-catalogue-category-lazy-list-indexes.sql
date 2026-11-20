--liquibase formatted sql

--changeset feroz:20260613-003-add-catalogue-category-lazy-list-indexes
-- Supports ALL default pages ordered by updated_at DESC, id ASC.
CREATE INDEX IF NOT EXISTS idx_catalogue_category_updated_id
    ON catalogue_category(updated_at DESC, id ASC);

-- Supports parent-scoped pages when admins explicitly sort by updatedAt.
CREATE INDEX IF NOT EXISTS idx_catalogue_category_parent_updated_id
    ON catalogue_category(parent_id, updated_at DESC, id ASC);

-- Supports root/direct-child default pages with active filtering.
CREATE INDEX IF NOT EXISTS idx_catalogue_category_parent_active_sort_rank_id
    ON catalogue_category(parent_id, is_active, sort_rank ASC, id ASC);

-- Supports ALL pages filtered by active state with the global default sort.
CREATE INDEX IF NOT EXISTS idx_catalogue_category_active_updated_id
    ON catalogue_category(is_active, updated_at DESC, id ASC);

-- EXPLAIN evidence for production-like million-row datasets is recorded during
-- implementation handoff because it depends on the benchmark environment.

--rollback DROP INDEX IF EXISTS idx_catalogue_category_active_updated_id;
--rollback DROP INDEX IF EXISTS idx_catalogue_category_parent_active_sort_rank_id;
--rollback DROP INDEX IF EXISTS idx_catalogue_category_parent_updated_id;
--rollback DROP INDEX IF EXISTS idx_catalogue_category_updated_id;
