--liquibase formatted sql

--changeset feroz:20260430-005-create-iam-permission-table
CREATE TABLE IF NOT EXISTS iam_permission
(
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(1000),
    resource VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

--rollback DROP TABLE IF EXISTS iam_permission;
