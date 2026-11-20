--liquibase formatted sql

--changeset feroz:20260512-002-create-catalogue-product-tables
CREATE TABLE IF NOT EXISTS catalogue_product
(
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    product_family VARCHAR(30) NOT NULL,
    brand_id BIGINT NOT NULL,
    manufacturer_id BIGINT NOT NULL,
    main_image_url VARCHAR(1000),
    short_description VARCHAR(1000),
    is_active BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_catalogue_product_brand FOREIGN KEY (brand_id) REFERENCES catalogue_brand(id) ON DELETE RESTRICT,
    CONSTRAINT fk_catalogue_product_manufacturer FOREIGN KEY (manufacturer_id) REFERENCES catalogue_manufacturer(id) ON DELETE RESTRICT,
    CONSTRAINT ck_catalogue_product_family CHECK (product_family IN ('STORAGE', 'CPU', 'GPU')),
    CONSTRAINT ck_catalogue_product_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE IF NOT EXISTS catalogue_storage_product
(
    product_id BIGINT PRIMARY KEY,
    storage_type VARCHAR(60),
    capacity_gb INTEGER,
    interface_type VARCHAR(80),
    form_factor VARCHAR(80),
    read_speed_mbps INTEGER,
    write_speed_mbps INTEGER,
    rpm INTEGER,
    cache_mb INTEGER,
    CONSTRAINT fk_catalogue_storage_product_product FOREIGN KEY (product_id) REFERENCES catalogue_product(id) ON DELETE CASCADE,
    CONSTRAINT ck_catalogue_storage_capacity CHECK (capacity_gb IS NULL OR capacity_gb >= 0),
    CONSTRAINT ck_catalogue_storage_read_speed CHECK (read_speed_mbps IS NULL OR read_speed_mbps >= 0),
    CONSTRAINT ck_catalogue_storage_write_speed CHECK (write_speed_mbps IS NULL OR write_speed_mbps >= 0),
    CONSTRAINT ck_catalogue_storage_rpm CHECK (rpm IS NULL OR rpm >= 0),
    CONSTRAINT ck_catalogue_storage_cache CHECK (cache_mb IS NULL OR cache_mb >= 0)
);

CREATE TABLE IF NOT EXISTS catalogue_cpu_product
(
    product_id BIGINT PRIMARY KEY,
    socket VARCHAR(80),
    core_count INTEGER,
    thread_count INTEGER,
    base_clock_ghz NUMERIC(6,2),
    boost_clock_ghz NUMERIC(6,2),
    architecture VARCHAR(120),
    integrated_graphics BOOLEAN,
    tdp_watts INTEGER,
    CONSTRAINT fk_catalogue_cpu_product_product FOREIGN KEY (product_id) REFERENCES catalogue_product(id) ON DELETE CASCADE,
    CONSTRAINT ck_catalogue_cpu_core_count CHECK (core_count IS NULL OR core_count > 0),
    CONSTRAINT ck_catalogue_cpu_thread_count CHECK (thread_count IS NULL OR thread_count > 0),
    CONSTRAINT ck_catalogue_cpu_base_clock CHECK (base_clock_ghz IS NULL OR base_clock_ghz > 0),
    CONSTRAINT ck_catalogue_cpu_boost_clock CHECK (boost_clock_ghz IS NULL OR boost_clock_ghz > 0),
    CONSTRAINT ck_catalogue_cpu_tdp CHECK (tdp_watts IS NULL OR tdp_watts > 0)
);

CREATE TABLE IF NOT EXISTS catalogue_graphics_card_product
(
    product_id BIGINT PRIMARY KEY,
    gpu_chipset VARCHAR(120),
    vram_gb INTEGER,
    memory_type VARCHAR(80),
    boost_clock_mhz INTEGER,
    interface_type VARCHAR(80),
    length_mm INTEGER,
    tdp_watts INTEGER,
    CONSTRAINT fk_catalogue_graphics_card_product_product FOREIGN KEY (product_id) REFERENCES catalogue_product(id) ON DELETE CASCADE,
    CONSTRAINT ck_catalogue_gpu_vram CHECK (vram_gb IS NULL OR vram_gb > 0),
    CONSTRAINT ck_catalogue_gpu_boost_clock CHECK (boost_clock_mhz IS NULL OR boost_clock_mhz > 0),
    CONSTRAINT ck_catalogue_gpu_length CHECK (length_mm IS NULL OR length_mm > 0),
    CONSTRAINT ck_catalogue_gpu_tdp CHECK (tdp_watts IS NULL OR tdp_watts > 0)
);

--rollback DROP TABLE IF EXISTS catalogue_graphics_card_product;
--rollback DROP TABLE IF EXISTS catalogue_cpu_product;
--rollback DROP TABLE IF EXISTS catalogue_storage_product;
--rollback DROP TABLE IF EXISTS catalogue_product;
