--liquibase formatted sql

--changeset feroz:20260512-006-create-catalogue-indexes
CREATE INDEX IF NOT EXISTS ix_catalogue_product_active ON catalogue_product (is_active);
CREATE INDEX IF NOT EXISTS ix_catalogue_product_slug ON catalogue_product (slug);
CREATE INDEX IF NOT EXISTS ix_catalogue_product_family ON catalogue_product (product_family);
CREATE INDEX IF NOT EXISTS ix_catalogue_product_brand ON catalogue_product (brand_id);
CREATE INDEX IF NOT EXISTS ix_catalogue_product_manufacturer ON catalogue_product (manufacturer_id);
CREATE INDEX IF NOT EXISTS ix_catalogue_product_created_at ON catalogue_product (created_at);
CREATE INDEX IF NOT EXISTS ix_catalogue_storage_capacity ON catalogue_storage_product (capacity_gb);
CREATE INDEX IF NOT EXISTS ix_catalogue_storage_type_capacity ON catalogue_storage_product (storage_type, capacity_gb);
CREATE INDEX IF NOT EXISTS ix_catalogue_cpu_core_count ON catalogue_cpu_product (core_count);
CREATE INDEX IF NOT EXISTS ix_catalogue_cpu_boost_clock ON catalogue_cpu_product (boost_clock_ghz);
CREATE INDEX IF NOT EXISTS ix_catalogue_gpu_vram ON catalogue_graphics_card_product (vram_gb);
CREATE INDEX IF NOT EXISTS ix_catalogue_gpu_boost_clock ON catalogue_graphics_card_product (boost_clock_mhz);
CREATE INDEX IF NOT EXISTS ix_catalogue_category_slug ON catalogue_category (slug);
CREATE INDEX IF NOT EXISTS ix_catalogue_category_closure_descendant ON catalogue_category_closure (descendant_id);
CREATE INDEX IF NOT EXISTS ix_catalogue_category_closure_ancestor_depth ON catalogue_category_closure (ancestor_id, depth);
CREATE INDEX IF NOT EXISTS ix_catalogue_product_category_category ON catalogue_product_category (category_id);
CREATE INDEX IF NOT EXISTS ix_catalogue_product_category_product ON catalogue_product_category (product_id);
CREATE INDEX IF NOT EXISTS ix_catalogue_offer_product_store ON catalogue_merchant_product_offer (product_id, merchant_store_id);
CREATE INDEX IF NOT EXISTS ix_catalogue_current_price_product_market ON catalogue_product_current_price (product_id, marketplace_country);
CREATE INDEX IF NOT EXISTS ix_catalogue_current_price_store_market ON catalogue_product_current_price (merchant_store_id, marketplace_country);
CREATE INDEX IF NOT EXISTS ix_catalogue_current_price_market_amount ON catalogue_product_current_price (marketplace_country, amount);
CREATE INDEX IF NOT EXISTS ix_catalogue_price_history_offer_market ON catalogue_product_price_history (merchant_product_offer_id, marketplace_country);

--rollback DROP INDEX IF EXISTS ix_catalogue_price_history_offer_market;
--rollback DROP INDEX IF EXISTS ix_catalogue_current_price_market_amount;
--rollback DROP INDEX IF EXISTS ix_catalogue_current_price_store_market;
--rollback DROP INDEX IF EXISTS ix_catalogue_current_price_product_market;
--rollback DROP INDEX IF EXISTS ix_catalogue_offer_product_store;
--rollback DROP INDEX IF EXISTS ix_catalogue_product_category_product;
--rollback DROP INDEX IF EXISTS ix_catalogue_product_category_category;
--rollback DROP INDEX IF EXISTS ix_catalogue_category_closure_ancestor_depth;
--rollback DROP INDEX IF EXISTS ix_catalogue_category_closure_descendant;
--rollback DROP INDEX IF EXISTS ix_catalogue_category_slug;
--rollback DROP INDEX IF EXISTS ix_catalogue_gpu_boost_clock;
--rollback DROP INDEX IF EXISTS ix_catalogue_gpu_vram;
--rollback DROP INDEX IF EXISTS ix_catalogue_cpu_boost_clock;
--rollback DROP INDEX IF EXISTS ix_catalogue_cpu_core_count;
--rollback DROP INDEX IF EXISTS ix_catalogue_storage_type_capacity;
--rollback DROP INDEX IF EXISTS ix_catalogue_storage_capacity;
--rollback DROP INDEX IF EXISTS ix_catalogue_product_created_at;
--rollback DROP INDEX IF EXISTS ix_catalogue_product_manufacturer;
--rollback DROP INDEX IF EXISTS ix_catalogue_product_brand;
--rollback DROP INDEX IF EXISTS ix_catalogue_product_family;
--rollback DROP INDEX IF EXISTS ix_catalogue_product_slug;
--rollback DROP INDEX IF EXISTS ix_catalogue_product_active;
