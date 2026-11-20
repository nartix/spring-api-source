--liquibase formatted sql

--changeset feroz:20260601-002-grant-admin-user-management-permissions
INSERT INTO iam_role_permission (role_id, permission_id, created_by)
SELECT role.id, permission.id, 'system'
FROM iam_role role
JOIN iam_permission permission
    ON permission.code IN ('admin:users:read', 'admin:users:write')
WHERE role.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

--rollback DELETE FROM iam_role_permission role_permission USING iam_role role, iam_permission permission WHERE role_permission.role_id = role.id AND role_permission.permission_id = permission.id AND role.code = 'ADMIN' AND permission.code IN ('admin:users:read', 'admin:users:write');
