--liquibase formatted sql

--changeset feroz:20260512-004-create-catalogue-merchant-tables
CREATE TABLE IF NOT EXISTS catalogue_merchant_store
(
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(150) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_catalogue_merchant_store_slug UNIQUE (slug),
    CONSTRAINT ck_catalogue_merchant_store_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE IF NOT EXISTS catalogue_merchant_product_offer
(
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    merchant_store_id BIGINT NOT NULL,
    merchant_sku VARCHAR(255),
    merchant_product_code VARCHAR(255),
    offer_url VARCHAR(1000),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_catalogue_offer_product FOREIGN KEY (product_id) REFERENCES catalogue_product(id) ON DELETE CASCADE,
    CONSTRAINT fk_catalogue_offer_store FOREIGN KEY (merchant_store_id) REFERENCES catalogue_merchant_store(id) ON DELETE RESTRICT
);

--rollback DROP TABLE IF EXISTS catalogue_merchant_product_offer;
--rollback DROP TABLE IF EXISTS catalogue_merchant_store;
