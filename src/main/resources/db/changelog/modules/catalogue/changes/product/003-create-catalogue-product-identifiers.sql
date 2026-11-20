--liquibase formatted sql

--changeset feroz:20260529-001-create-catalogue-product-identifiers
CREATE TABLE IF NOT EXISTS catalogue_product_identifier
(
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    family VARCHAR(20) NOT NULL,
    value VARCHAR(14) NOT NULL,
    canonical_gtin14 CHAR(14) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_catalogue_product_identifier_product FOREIGN KEY (product_id) REFERENCES catalogue_product(id) ON DELETE CASCADE,
    CONSTRAINT ck_catalogue_product_identifier_family CHECK (family IN ('UPC', 'EAN', 'JAN', 'ISBN', 'GTIN_14')),
    CONSTRAINT ck_catalogue_product_identifier_value CHECK (value ~ '^[0-9]{12,14}$'),
    CONSTRAINT ck_catalogue_product_identifier_gtin14 CHECK (canonical_gtin14 ~ '^[0-9]{14}$')
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_catalogue_product_identifier_gtin14
    ON catalogue_product_identifier (canonical_gtin14);

CREATE UNIQUE INDEX IF NOT EXISTS ux_catalogue_product_identifier_primary
    ON catalogue_product_identifier (product_id)
    WHERE is_primary;

CREATE INDEX IF NOT EXISTS ix_catalogue_product_identifier_product
    ON catalogue_product_identifier (product_id);

--rollback DROP TABLE IF EXISTS catalogue_product_identifier;
