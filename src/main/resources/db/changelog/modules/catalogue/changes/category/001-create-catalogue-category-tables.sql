--liquibase formatted sql

--changeset feroz:20260512-003-create-catalogue-category-tables
CREATE TABLE IF NOT EXISTS catalogue_category
(
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(150) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_catalogue_category_slug UNIQUE (slug),
    CONSTRAINT fk_catalogue_category_parent FOREIGN KEY (parent_id) REFERENCES catalogue_category(id) ON DELETE RESTRICT,
    CONSTRAINT ck_catalogue_category_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE IF NOT EXISTS catalogue_category_closure
(
    ancestor_id BIGINT NOT NULL,
    descendant_id BIGINT NOT NULL,
    depth INTEGER NOT NULL,
    CONSTRAINT pk_catalogue_category_closure PRIMARY KEY (ancestor_id, descendant_id),
    CONSTRAINT fk_catalogue_category_closure_ancestor FOREIGN KEY (ancestor_id) REFERENCES catalogue_category(id) ON DELETE CASCADE,
    CONSTRAINT fk_catalogue_category_closure_descendant FOREIGN KEY (descendant_id) REFERENCES catalogue_category(id) ON DELETE CASCADE,
    CONSTRAINT ck_catalogue_category_closure_depth CHECK (depth >= 0)
);

CREATE TABLE IF NOT EXISTS catalogue_product_category
(
    product_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    CONSTRAINT pk_catalogue_product_category PRIMARY KEY (product_id, category_id),
    CONSTRAINT fk_catalogue_product_category_product FOREIGN KEY (product_id) REFERENCES catalogue_product(id) ON DELETE CASCADE,
    CONSTRAINT fk_catalogue_product_category_category FOREIGN KEY (category_id) REFERENCES catalogue_category(id) ON DELETE CASCADE
);

--rollback DROP TABLE IF EXISTS catalogue_product_category;
--rollback DROP TABLE IF EXISTS catalogue_category_closure;
--rollback DROP TABLE IF EXISTS catalogue_category;
