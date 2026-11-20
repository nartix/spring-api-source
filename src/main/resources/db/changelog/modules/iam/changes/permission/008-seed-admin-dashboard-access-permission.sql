--liquibase formatted sql

--changeset feroz:20261117-001-seed-admin-dashboard-access-permission
INSERT INTO iam_permission (code, name, description, resource, action, is_active, created_by, updated_by)
VALUES ('admin:access', 'Access admin dashboard', 'Allows admin sign-in without granting resource mutation permissions', 'admin-dashboard', 'access', true, 'system', 'system')
ON CONFLICT (code) DO NOTHING;

--rollback DELETE FROM iam_permission WHERE code = 'admin:access';
