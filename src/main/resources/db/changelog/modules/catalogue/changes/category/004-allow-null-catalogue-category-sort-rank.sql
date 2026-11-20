--liquibase formatted sql

--changeset feroz:20260613-004-allow-null-catalogue-category-sort-rank
ALTER TABLE catalogue_category
    ALTER COLUMN sort_rank DROP NOT NULL;

--rollback UPDATE catalogue_category
--rollback SET sort_rank = generated.generated_rank
--rollback FROM (
--rollback     SELECT
--rollback         id,
--rollback         ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY name ASC, id ASC) * 1000000000::BIGINT AS generated_rank
--rollback     FROM catalogue_category
--rollback ) generated
--rollback WHERE catalogue_category.id = generated.id
--rollback   AND catalogue_category.sort_rank IS NULL;
--rollback ALTER TABLE catalogue_category
--rollback     ALTER COLUMN sort_rank SET NOT NULL;
