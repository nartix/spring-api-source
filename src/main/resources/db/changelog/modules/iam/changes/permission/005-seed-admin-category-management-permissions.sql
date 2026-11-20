--liquibase formatted sql

--changeset feroz:20260606-001-seed-admin-category-management-permissions
INSERT INTO iam_permission (code, name, description, resource, action, is_active, created_by, updated_by)
VALUES
    ('admin:categories:read', 'Read admin categories', 'Allows reading catalogue category records through the admin API', 'admin-categories', 'read', true, 'system', 'system'),
    ('admin:categories:write', 'Write admin categories', 'Allows creating, updating, moving, activating, deactivating, and deleting catalogue category records through the admin API', 'admin-categories', 'write', true, 'system', 'system')
ON CONFLICT (code) DO NOTHING;

--rollback DELETE FROM iam_permission WHERE code IN ('admin:categories:read', 'admin:categories:write');
