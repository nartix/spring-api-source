--liquibase formatted sql

--changeset feroz:20260702-001-seed-admin-catalogue-category-system-permissions
INSERT INTO iam_permission (code, name, description, resource, action, is_active, created_by, updated_by)
VALUES
    ('admin:catalogue-categories:read', 'Read admin catalogue categories', 'Allows reading catalogue category records through the generic category-system admin API', 'admin-catalogue-categories', 'read', true, 'system', 'system'),
    ('admin:catalogue-categories:write', 'Write admin catalogue categories', 'Allows creating, updating, moving, activating, deactivating, and deleting catalogue category records through the generic category-system admin API', 'admin-catalogue-categories', 'write', true, 'system', 'system')
ON CONFLICT (code) DO NOTHING;

--rollback DELETE FROM iam_permission WHERE code IN ('admin:catalogue-categories:read', 'admin:catalogue-categories:write');
