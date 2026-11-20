--liquibase formatted sql

--changeset feroz:20260430-001-create-iam-user-table
CREATE TABLE IF NOT EXISTS iam_user
(
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(512) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_account_non_expired BOOLEAN NOT NULL DEFAULT true,
    is_account_non_locked BOOLEAN NOT NULL DEFAULT true,
    is_credentials_non_expired BOOLEAN NOT NULL DEFAULT true,
    last_login_at TIMESTAMP WITH TIME ZONE,
    last_activity_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

--rollback DROP TABLE IF EXISTS iam_user;
