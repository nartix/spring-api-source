--liquibase formatted sql

--changeset feroz:20260508-001-create-data-category-table
CREATE TABLE IF NOT EXISTS data_category
(
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT,
    slug VARCHAR(150) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_data_category_parent FOREIGN KEY (parent_id) REFERENCES data_category(id) ON DELETE RESTRICT,
    CONSTRAINT ck_data_category_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

--rollback DROP TABLE IF EXISTS data_category;
