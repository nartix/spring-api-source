--liquibase formatted sql

--changeset feroz:20260606-004-seed-admin-user-status-permission
INSERT INTO iam_permission (code, name, description, resource, action, is_active, created_by, updated_by)
VALUES
    ('admin:users:status', 'Manage admin user status', 'Allows changing user account status fields through the admin API', 'admin-users', 'status', true, 'system', 'system')
ON CONFLICT (code) DO NOTHING;

--rollback DELETE FROM iam_permission WHERE code = 'admin:users:status';
