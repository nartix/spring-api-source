--liquibase formatted sql

--changeset feroz:20260611-001-add-data-category-sort-rank
ALTER TABLE data_category
    ADD COLUMN IF NOT EXISTS sort_rank BIGINT;

WITH ranked_categories AS (
    SELECT
        id,
        ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY name ASC, id ASC) * 1000000000::BIGINT AS generated_rank
    FROM data_category
)
UPDATE data_category category
SET sort_rank = ranked_categories.generated_rank
FROM ranked_categories
WHERE category.id = ranked_categories.id
  AND category.sort_rank IS NULL;

ALTER TABLE data_category
    ALTER COLUMN sort_rank SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_data_category_parent_sort_rank_id
    ON data_category(parent_id, sort_rank, id);

--rollback DROP INDEX IF EXISTS idx_data_category_parent_sort_rank_id;
--rollback ALTER TABLE data_category DROP COLUMN IF EXISTS sort_rank;
