--liquibase formatted sql

--changeset feroz:20260529-001-seed-api-authorization-permissions
INSERT INTO iam_permission (code, name, description, resource, action, is_active, created_by, updated_by)
VALUES
    ('users:read', 'Read users', 'Allows reading user records', 'users', 'read', true, 'system', 'system'),
    ('users:write', 'Write users', 'Allows creating, updating, and deleting user records', 'users', 'write', true, 'system', 'system'),
    ('categories:read', 'Read categories', 'Allows reading data categories and category hierarchies', 'categories', 'read', true, 'system', 'system'),
    ('categories:write', 'Write categories', 'Allows creating, updating, moving, and deleting data categories', 'categories', 'write', true, 'system', 'system')
ON CONFLICT (code) DO NOTHING;

--rollback DELETE FROM iam_permission WHERE code IN ('users:read', 'users:write', 'categories:read', 'categories:write');
