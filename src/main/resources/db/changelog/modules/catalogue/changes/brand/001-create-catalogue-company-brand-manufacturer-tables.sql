--liquibase formatted sql

--changeset feroz:20260512-001-create-catalogue-company-brand-manufacturer-tables
CREATE TABLE IF NOT EXISTS catalogue_company
(
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(150) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_catalogue_company_slug UNIQUE (slug),
    CONSTRAINT ck_catalogue_company_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE IF NOT EXISTS catalogue_brand
(
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(150) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_catalogue_brand_slug UNIQUE (slug),
    CONSTRAINT fk_catalogue_brand_company FOREIGN KEY (company_id) REFERENCES catalogue_company(id) ON DELETE SET NULL,
    CONSTRAINT ck_catalogue_brand_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE IF NOT EXISTS catalogue_manufacturer
(
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(150) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_catalogue_manufacturer_slug UNIQUE (slug),
    CONSTRAINT fk_catalogue_manufacturer_company FOREIGN KEY (company_id) REFERENCES catalogue_company(id) ON DELETE SET NULL,
    CONSTRAINT ck_catalogue_manufacturer_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

--rollback DROP TABLE IF EXISTS catalogue_manufacturer;
--rollback DROP TABLE IF EXISTS catalogue_brand;
--rollback DROP TABLE IF EXISTS catalogue_company;
