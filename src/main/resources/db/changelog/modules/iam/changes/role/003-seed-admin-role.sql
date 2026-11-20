--liquibase formatted sql

--changeset feroz:20260502-001-seed-default-roles
INSERT INTO iam_role (code, name, description, is_active, created_by, updated_by)
VALUES
    ('ADMIN', 'Administrator', 'Default administrator role', true, 'system', 'system'),
    ('USER', 'User', 'Default application user role', true, 'system', 'system')
ON CONFLICT (code) DO NOTHING;

--rollback DELETE FROM iam_role WHERE code IN ('ADMIN', 'USER');
