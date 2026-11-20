--liquibase formatted sql

--changeset feroz:20260601-001-seed-admin-user-management-permissions
INSERT INTO iam_permission (code, name, description, resource, action, is_active, created_by, updated_by)
VALUES
    ('admin:users:read', 'Read admin users', 'Allows reading user records through the admin API', 'admin-users', 'read', true, 'system', 'system'),
    ('admin:users:write', 'Write admin users', 'Allows creating, updating, and deleting user records through the admin API', 'admin-users', 'write', true, 'system', 'system')
ON CONFLICT (code) DO NOTHING;

--rollback DELETE FROM iam_permission WHERE code IN ('admin:users:read', 'admin:users:write');
