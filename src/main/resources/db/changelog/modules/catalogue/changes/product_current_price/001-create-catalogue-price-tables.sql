--liquibase formatted sql

--changeset feroz:20260512-005-create-catalogue-price-tables
CREATE TABLE IF NOT EXISTS catalogue_product_current_price
(
    id BIGSERIAL PRIMARY KEY,
    merchant_product_offer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    merchant_store_id BIGINT NOT NULL,
    marketplace_country VARCHAR(2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    availability VARCHAR(40) NOT NULL,
    source VARCHAR(40) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_catalogue_current_price_offer_market UNIQUE (merchant_product_offer_id, marketplace_country),
    CONSTRAINT fk_catalogue_current_price_offer FOREIGN KEY (merchant_product_offer_id) REFERENCES catalogue_merchant_product_offer(id) ON DELETE CASCADE,
    CONSTRAINT fk_catalogue_current_price_product FOREIGN KEY (product_id) REFERENCES catalogue_product(id) ON DELETE CASCADE,
    CONSTRAINT fk_catalogue_current_price_store FOREIGN KEY (merchant_store_id) REFERENCES catalogue_merchant_store(id) ON DELETE RESTRICT,
    CONSTRAINT ck_catalogue_current_price_amount CHECK (amount > 0),
    CONSTRAINT ck_catalogue_current_price_marketplace CHECK (marketplace_country ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_catalogue_current_price_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE IF NOT EXISTS catalogue_product_price_history
(
    id BIGSERIAL PRIMARY KEY,
    merchant_product_offer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    merchant_store_id BIGINT NOT NULL,
    marketplace_country VARCHAR(2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    availability VARCHAR(40) NOT NULL,
    source VARCHAR(40) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_catalogue_price_history_offer FOREIGN KEY (merchant_product_offer_id) REFERENCES catalogue_merchant_product_offer(id) ON DELETE CASCADE,
    CONSTRAINT fk_catalogue_price_history_product FOREIGN KEY (product_id) REFERENCES catalogue_product(id) ON DELETE CASCADE,
    CONSTRAINT fk_catalogue_price_history_store FOREIGN KEY (merchant_store_id) REFERENCES catalogue_merchant_store(id) ON DELETE RESTRICT,
    CONSTRAINT ck_catalogue_price_history_amount CHECK (amount > 0),
    CONSTRAINT ck_catalogue_price_history_marketplace CHECK (marketplace_country ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_catalogue_price_history_currency CHECK (currency ~ '^[A-Z]{3}$')
);

--rollback DROP TABLE IF EXISTS catalogue_product_price_history;
--rollback DROP TABLE IF EXISTS catalogue_product_current_price;
